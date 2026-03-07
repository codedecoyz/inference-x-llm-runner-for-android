#pragma once
#include <string>
#include <vector>
#include <functional>
#include <memory>
#include <unordered_map>
#include <list>
#include <mutex>
#include "memory_pool.h"
#include "kv_cache.h"

#ifdef HAS_ONNXRUNTIME
#include "onnxruntime_cxx_api.h"
#endif

// ─────────────────────────────────────────────
// Model config loaded from config.json
// ─────────────────────────────────────────────
struct ModelConfig {
    int vocab_size   = 32000;
    int hidden_dim   = 2048;
    int num_layers   = 22;      // Llama 3.2 1B = 16, 3B = 28
    int num_heads    = 32;
    int num_kv_heads = 8;       // GQA
    int head_dim     = 64;
    int max_seq_len  = 2048;
    int intermediate = 8192;
    std::string model_dir;
};

#ifdef HAS_ONNXRUNTIME

// ─────────────────────────────────────────────
// LRU Layer Cache
// Keeps recently used layer ONNX sessions alive
// ─────────────────────────────────────────────
class LRULayerCache {
public:
    explicit LRULayerCache(size_t max_cached_layers = 4)
        : max_layers_(max_cached_layers) {}

    Ort::Session* get(const std::string& key) {
        auto it = map_.find(key);
        if (it == map_.end()) return nullptr;
        lru_.splice(lru_.begin(), lru_, it->second.second);
        return it->second.first.get();
    }

    void put(const std::string& key, std::unique_ptr<Ort::Session> session) {
        if (map_.size() >= max_layers_) {
            auto lru_key = lru_.back();
            lru_.pop_back();
            map_.erase(lru_key);
        }
        lru_.push_front(key);
        map_[key] = { std::move(session), lru_.begin() };
    }

    void clear() { map_.clear(); lru_.clear(); }

private:
    size_t max_layers_;
    std::list<std::string> lru_;
    std::unordered_map<std::string,
        std::pair<std::unique_ptr<Ort::Session>,
                  std::list<std::string>::iterator>> map_;
};

// ─────────────────────────────────────────────
// Token sampling config
// ─────────────────────────────────────────────
struct SamplingConfig {
    float temperature = 0.7f;
    float top_p       = 0.9f;
    int   top_k       = 40;
    int   eos_token   = 2;        // </s>
    int   max_new_tokens = 512;
};

// ─────────────────────────────────────────────
// Main LLM Layer Streaming Engine
// ─────────────────────────────────────────────
class LLMEngine {
public:
    using TokenCallback = std::function<void(const std::string& token, bool done)>;

    explicit LLMEngine(const ModelConfig& config);
    ~LLMEngine();

    LLMEngine(const LLMEngine&) = delete;
    LLMEngine& operator=(const LLMEngine&) = delete;

    void generate(const std::vector<int>& input_tokens,
                  const SamplingConfig& sampling,
                  TokenCallback callback);

    void reset_context();
    void warmup(int num_layers = 4);

    bool is_ready() const { return ready_; }

private:
    std::vector<float> forward(const std::vector<int>& tokens, int seq_pos);
    std::vector<float> run_layer(const std::string& layer_path,
                                 const std::vector<float>& hidden,
                                 int layer_idx,
                                 int seq_pos);
    Ort::Session* get_or_load_session(const std::string& path);

    int  sample_token(const std::vector<float>& logits, const SamplingConfig& cfg);
    void apply_temperature(std::vector<float>& logits, float temp);
    int  top_p_sample(std::vector<float>& probs, float top_p, int top_k);

    ModelConfig          config_;
    Ort::Env             env_;
    Ort::SessionOptions  session_opts_;
    LRULayerCache        layer_cache_;
    std::unique_ptr<KVCache> kv_cache_;
    bool                 ready_ = false;
    std::mutex           gen_mutex_;
};

#else // !HAS_ONNXRUNTIME

// Stub sampling config
struct SamplingConfig {
    float temperature = 0.7f;
    float top_p       = 0.9f;
    int   top_k       = 40;
    int   eos_token   = 2;
    int   max_new_tokens = 512;
};

// Stub when ONNX Runtime is not available
class LLMEngine {
public:
    using TokenCallback = std::function<void(const std::string& token, bool done)>;
    explicit LLMEngine(const ModelConfig&) {}
    ~LLMEngine() = default;
    void generate(const std::vector<int>&, const SamplingConfig&, TokenCallback) {}
    void reset_context() {}
    void warmup(int = 4) {}
    bool is_ready() const { return false; }
};

#endif // HAS_ONNXRUNTIME
