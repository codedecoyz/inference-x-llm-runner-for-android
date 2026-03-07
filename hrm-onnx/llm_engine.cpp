#include "llm_engine.h"
#include <algorithm>
#include <cmath>
#include <numeric>
#include <random>
#include <fstream>
#include <sstream>
#include <android/log.h>

#define LLM_TAG "LLMEngine"
#define LLOGI(...) __android_log_print(ANDROID_LOG_INFO,  LLM_TAG, __VA_ARGS__)
#define LLOGE(...) __android_log_print(ANDROID_LOG_ERROR, LLM_TAG, __VA_ARGS__)

// ─────────────────────────────────────────────
// Constructor
// ─────────────────────────────────────────────
LLMEngine::LLMEngine(const ModelConfig& config)
    : config_(config),
      env_(ORT_LOGGING_LEVEL_WARNING, "LLMEngine"),
      layer_cache_(6)  // keep 6 layers hot in memory
{
    // Session options — NNAPI for NPU, fallback to CPU
    session_opts_.SetIntraOpNumThreads(4);
    session_opts_.SetGraphOptimizationLevel(ORT_ENABLE_ALL);

    // Enable NNAPI (Snapdragon/Dimensity NPU)
    uint32_t nnapi_flags = 0;
    nnapi_flags |= NNAPI_FLAG_USE_FP16;        // fp16 on NPU
    nnapi_flags |= NNAPI_FLAG_CPU_DISABLED;    // force NPU/GPU, no CPU fallback
    OrtSessionOptionsAppendExecutionProvider_Nnapi(session_opts_, nnapi_flags);

    // KV cache
    kv_cache_ = std::make_unique<KVCache>(
        config_.num_layers,
        config_.num_kv_heads,  // GQA — KV heads < Q heads
        config_.head_dim,
        config_.max_seq_len
    );

    ready_ = true;
    LLOGI("LLMEngine ready | layers=%d hidden=%d heads=%d",
          config_.num_layers, config_.hidden_dim, config_.num_heads);
}

LLMEngine::~LLMEngine() {
    layer_cache_.clear();
}

// ─────────────────────────────────────────────
// Public: Generate
// ─────────────────────────────────────────────
void LLMEngine::generate(const std::vector<int>& input_tokens,
                         const SamplingConfig& sampling,
                         TokenCallback callback) {
    std::lock_guard<std::mutex> lock(gen_mutex_);

    std::vector<int> all_tokens = input_tokens;
    int seq_pos = (int)input_tokens.size() - 1;

    // Prefill: process prompt tokens (seq_pos advances internally)
    // For simplicity: full forward on whole prompt first
    auto logits = forward(all_tokens, seq_pos);

    for (int step = 0; step < sampling.max_new_tokens; ++step) {
        int next_token = sample_token(logits, sampling);

        if (next_token == sampling.eos_token) {
            callback("", true);
            break;
        }

        all_tokens.push_back(next_token);
        seq_pos++;

        // Decode token id → string (placeholder — real tokenizer in JNI layer)
        std::string token_str = std::to_string(next_token);
        bool is_last = (step == sampling.max_new_tokens - 1);
        callback(token_str, is_last);

        if (is_last) break;

        // Next token forward pass (decode phase — single token)
        logits = forward({ next_token }, seq_pos);
    }
}

// ─────────────────────────────────────────────
// Private: Full Forward Pass
// ─────────────────────────────────────────────
std::vector<float> LLMEngine::forward(const std::vector<int>& tokens, int seq_pos) {
    int T = (int)tokens.size();

    // ── Step 1: Embedding ────────────────────
    std::string embed_path = config_.model_dir + "/embed.onnx";
    Ort::Session* embed_sess = get_or_load_session(embed_path);

    auto mem_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

    // Input: token ids [T]
    std::vector<int64_t> token_data(tokens.begin(), tokens.end());
    std::vector<int64_t> token_shape = { 1, (int64_t)T };
    auto token_tensor = Ort::Value::CreateTensor<int64_t>(
        mem_info, token_data.data(), token_data.size(),
        token_shape.data(), token_shape.size());

    const char* embed_in[]  = { "input_ids" };
    const char* embed_out[] = { "hidden_states" };
    auto embed_result = embed_sess->Run(
        Ort::RunOptions{nullptr},
        embed_in, &token_tensor, 1,
        embed_out, 1);

    // hidden: [1, T, hidden_dim]
    float* hidden_data = embed_result[0].GetTensorMutableData<float>();
    size_t hidden_size = (size_t)T * config_.hidden_dim;
    std::vector<float> hidden(hidden_data, hidden_data + hidden_size);

    // ── Step 2: Stream through transformer layers ─
    for (int layer_idx = 0; layer_idx < config_.num_layers; ++layer_idx) {
        std::string layer_path = config_.model_dir
            + "/layer_" + (layer_idx < 10 ? "0" : "") + std::to_string(layer_idx) + ".onnx";

        hidden = run_layer(layer_path, hidden, layer_idx, seq_pos);

        LLOGI("Layer %02d/%d done", layer_idx + 1, config_.num_layers);
    }

    // ── Step 3: Output head → logits ─────────
    std::string head_path = config_.model_dir + "/head.onnx";
    Ort::Session* head_sess = get_or_load_session(head_path);

    // Take last token's hidden state for next-token prediction
    std::vector<float> last_hidden(
        hidden.end() - config_.hidden_dim, hidden.end());

    std::vector<int64_t> head_shape = { 1, 1, (int64_t)config_.hidden_dim };
    auto head_input = Ort::Value::CreateTensor<float>(
        mem_info, last_hidden.data(), last_hidden.size(),
        head_shape.data(), head_shape.size());

    const char* head_in[]  = { "hidden_states" };
    const char* head_out[] = { "logits" };
    auto head_result = head_sess->Run(
        Ort::RunOptions{nullptr},
        head_in, &head_input, 1,
        head_out, 1);

    float* logit_data = head_result[0].GetTensorMutableData<float>();
    return std::vector<float>(logit_data, logit_data + config_.vocab_size);
}

// ─────────────────────────────────────────────
// Private: Run Single Layer
// ─────────────────────────────────────────────
std::vector<float> LLMEngine::run_layer(const std::string& layer_path,
                                         const std::vector<float>& hidden,
                                         int layer_idx,
                                         int seq_pos) {
    Ort::Session* session = get_or_load_session(layer_path);
    auto mem_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

    int T = (int)hidden.size() / config_.hidden_dim;

    // Input 0: hidden_states [1, T, hidden_dim]
    std::vector<int64_t> hidden_shape = { 1, (int64_t)T, (int64_t)config_.hidden_dim };
    auto hidden_tensor = Ort::Value::CreateTensor<float>(
        mem_info,
        const_cast<float*>(hidden.data()), hidden.size(),
        hidden_shape.data(), hidden_shape.size());

    // Input 1: past_key [1, num_kv_heads, seq_pos, head_dim]
    int kv_seq = kv_cache_->seq_len(layer_idx);
    std::vector<int64_t> kv_shape = {
        1, (int64_t)config_.num_kv_heads,
        (int64_t)kv_seq, (int64_t)config_.head_dim
    };
    size_t kv_size = config_.num_kv_heads * kv_seq * config_.head_dim;

    auto past_key_tensor = Ort::Value::CreateTensor<float>(
        mem_info,
        const_cast<float*>(kv_cache_->get_keys(layer_idx)),
        kv_size, kv_shape.data(), kv_shape.size());

    auto past_val_tensor = Ort::Value::CreateTensor<float>(
        mem_info,
        const_cast<float*>(kv_cache_->get_values(layer_idx)),
        kv_size, kv_shape.data(), kv_shape.size());

    // Input 2: position_ids [1, T]
    std::vector<int64_t> pos_ids(T);
    for (int i = 0; i < T; ++i) pos_ids[i] = seq_pos - T + 1 + i;
    std::vector<int64_t> pos_shape = { 1, (int64_t)T };
    auto pos_tensor = Ort::Value::CreateTensor<int64_t>(
        mem_info, pos_ids.data(), pos_ids.size(),
        pos_shape.data(), pos_shape.size());

    // Run
    std::vector<Ort::Value> inputs;
    inputs.push_back(std::move(hidden_tensor));
    inputs.push_back(std::move(past_key_tensor));
    inputs.push_back(std::move(past_val_tensor));
    inputs.push_back(std::move(pos_tensor));

    const char* in_names[]  = { "hidden_states", "past_key", "past_value", "position_ids" };
    const char* out_names[] = { "hidden_states_out", "present_key", "present_value" };

    auto outputs = session->Run(
        Ort::RunOptions{nullptr},
        in_names,  inputs.data(), inputs.size(),
        out_names, 3);

    // Update KV cache with new K/V
    float* new_keys   = outputs[1].GetTensorMutableData<float>();
    float* new_values = outputs[2].GetTensorMutableData<float>();
    // Append last token's KV to cache
    int new_kv_size_per_head = config_.head_dim;
    for (int t = 0; t < T; ++t) {
        kv_cache_->append(layer_idx,
            new_keys   + t * config_.num_kv_heads * config_.head_dim,
            new_values + t * config_.num_kv_heads * config_.head_dim,
            seq_pos - T + 1 + t);
    }

    // Return output hidden states
    float* out_data = outputs[0].GetTensorMutableData<float>();
    size_t out_size = (size_t)T * config_.hidden_dim;
    return std::vector<float>(out_data, out_data + out_size);
}

// ─────────────────────────────────────────────
// Private: Session Cache
// ─────────────────────────────────────────────
Ort::Session* LLMEngine::get_or_load_session(const std::string& path) {
    Ort::Session* cached = layer_cache_.get(path);
    if (cached) return cached;

    LLOGI("Loading layer from disk: %s", path.c_str());
    auto session = std::make_unique<Ort::Session>(env_, path.c_str(), session_opts_);
    Ort::Session* raw = session.get();
    layer_cache_.put(path, std::move(session));
    return raw;
}

// ─────────────────────────────────────────────
// Private: Token Sampling
// ─────────────────────────────────────────────
void LLMEngine::apply_temperature(std::vector<float>& logits, float temp) {
    if (temp <= 0.0f) return;
    for (auto& l : logits) l /= temp;
}

int LLMEngine::top_p_sample(std::vector<float>& probs, float top_p, int top_k) {
    // Sort indices by probability descending
    std::vector<int> indices(probs.size());
    std::iota(indices.begin(), indices.end(), 0);
    std::partial_sort(indices.begin(),
                      indices.begin() + std::min(top_k, (int)probs.size()),
                      indices.end(),
                      [&](int a, int b) { return probs[a] > probs[b]; });

    // Truncate to top_k
    indices.resize(std::min(top_k, (int)probs.size()));

    // Accumulate until top_p threshold
    float cumsum = 0.0f;
    std::vector<int> nucleus;
    for (int idx : indices) {
        nucleus.push_back(idx);
        cumsum += probs[idx];
        if (cumsum >= top_p) break;
    }

    // Renormalize over nucleus
    float total = 0.0f;
    for (int idx : nucleus) total += probs[idx];
    std::vector<float> nucleus_probs;
    for (int idx : nucleus) nucleus_probs.push_back(probs[idx] / total);

    // Sample
    static std::mt19937 rng(std::random_device{}());
    std::discrete_distribution<int> dist(nucleus_probs.begin(), nucleus_probs.end());
    return nucleus[dist(rng)];
}

int LLMEngine::sample_token(const std::vector<float>& logits, const SamplingConfig& cfg) {
    std::vector<float> probs = logits;

    // Temperature
    apply_temperature(probs, cfg.temperature);

    // Softmax
    float max_l = *std::max_element(probs.begin(), probs.end());
    float sum = 0.0f;
    for (auto& p : probs) { p = std::exp(p - max_l); sum += p; }
    for (auto& p : probs) p /= sum;

    if (cfg.temperature == 0.0f) {
        // Greedy
        return (int)std::distance(probs.begin(),
                                  std::max_element(probs.begin(), probs.end()));
    }

    return top_p_sample(probs, cfg.top_p, cfg.top_k);
}

// ─────────────────────────────────────────────
// Public: Reset / Warmup
// ─────────────────────────────────────────────
void LLMEngine::reset_context() {
    kv_cache_->reset();
    LLOGI("KV cache reset");
}

void LLMEngine::warmup(int num_layers) {
    LLOGI("Warming up first %d layers...", num_layers);
    for (int i = 0; i < std::min(num_layers, config_.num_layers); ++i) {
        std::string path = config_.model_dir
            + "/layer_" + (i < 10 ? "0" : "") + std::to_string(i) + ".onnx";
        get_or_load_session(path);
    }
    LLOGI("Warmup complete");
}
