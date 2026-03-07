#pragma once
#include <string>
#include <vector>
#include <memory>
#include <android/log.h>

// Forward-declare ORT types to avoid hard dependency when ONNX Runtime is missing
#ifdef HAS_ONNXRUNTIME
#include "onnxruntime_cxx_api.h"
#endif

#define HRM_TAG "HRMEngine"
#define HRM_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  HRM_TAG, __VA_ARGS__)
#define HRM_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, HRM_TAG, __VA_ARGS__)

struct HRMConfig {
    int input_dim    = 64;    // problem encoding dimension
    int h_dim        = 256;   // high-level (Controller) hidden dim
    int l_dim        = 128;   // low-level  (Worker)     hidden dim
    int output_dim   = 64;    // output dimension
    int max_outer    = 20;    // max Controller steps
    int max_inner    = 10;    // max Worker steps per Controller step
    float halt_thresh = 0.9f; // halt when confidence > this
    std::string model_path;   // path to hrm.onnx
};

// Result from HRM inference
struct HRMResult {
    std::vector<float> output;       // raw output tensor
    int outer_steps_taken = 0;       // how many Controller iterations ran
    int total_inner_steps = 0;       // total Worker iterations
    float confidence      = 0.0f;    // final halt confidence
    bool halted_early     = false;   // did it halt before max steps?
};

#ifdef HAS_ONNXRUNTIME

// ─────────────────────────────────────────────
// HRM Engine — runs Hierarchical Reasoning Model
// Single ONNX session, small model (~100MB)
// Kept loaded in memory always (tiny footprint)
// ─────────────────────────────────────────────
class HRMEngine {
public:
    explicit HRMEngine(const HRMConfig& config);
    ~HRMEngine() = default;

    // Run inference on encoded input
    HRMResult infer(const std::vector<float>& input);

    // Convenience: infer with automatic halt diagnostics logging
    HRMResult infer_verbose(const std::vector<float>& input);

    bool is_ready() const { return session_ != nullptr; }
    const HRMConfig& config() const { return config_; }

private:
    HRMResult run_hierarchical(const std::vector<float>& input);
    std::vector<float> init_h_state() const;
    std::vector<float> init_l_state() const;

    HRMConfig                        config_;
    Ort::Env                         env_;
    Ort::SessionOptions              opts_;
    std::unique_ptr<Ort::Session>    session_;
};

// ─────────────────────────────────────────────
// Implementation
// ─────────────────────────────────────────────
inline HRMEngine::HRMEngine(const HRMConfig& config)
    : config_(config),
      env_(ORT_LOGGING_LEVEL_WARNING, "HRMEngine")
{
    opts_.SetIntraOpNumThreads(2);
    opts_.SetGraphOptimizationLevel(ORT_ENABLE_ALL);

    // NNAPI — HRM is tiny, runs entirely on NPU
    try {
        Ort::ThrowOnError(OrtSessionOptionsAppendExecutionProvider_Nnapi(opts_, 0x001 /* FP16 */));
        HRM_LOGI("NNAPI execution provider enabled");
    } catch (...) {
        HRM_LOGI("NNAPI not available, using CPU fallback");
    }

    try {
        session_ = std::make_unique<Ort::Session>(
            env_, config_.model_path.c_str(), opts_);
        HRM_LOGI("HRMEngine loaded: %s", config_.model_path.c_str());
    } catch (const Ort::Exception& e) {
        HRM_LOGE("Failed to load HRM model: %s", e.what());
        session_ = nullptr;
    }
}

inline std::vector<float> HRMEngine::init_h_state() const {
    return std::vector<float>(config_.h_dim, 0.0f);
}

inline std::vector<float> HRMEngine::init_l_state() const {
    return std::vector<float>(config_.l_dim, 0.0f);
}

inline HRMResult HRMEngine::run_hierarchical(const std::vector<float>& input) {
    if (!session_) return {};

    auto mem_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

    std::vector<float> h_state = init_h_state();
    std::vector<float> l_state = init_l_state();

    HRMResult result;
    result.halted_early = false;

    // ── Outer loop: Controller (H module) ────
    for (int outer = 0; outer < config_.max_outer; ++outer) {
        result.outer_steps_taken = outer + 1;

        // ── Inner loop: Worker (L module) ────
        for (int inner = 0; inner < config_.max_inner; ++inner) {
            result.total_inner_steps++;

            std::vector<int64_t> input_shape = { 1, (int64_t)config_.input_dim };
            std::vector<int64_t> h_shape     = { 1, (int64_t)config_.h_dim };
            std::vector<int64_t> l_shape     = { 1, (int64_t)config_.l_dim };

            auto input_t   = Ort::Value::CreateTensor<float>(
                mem_info, const_cast<float*>(input.data()), input.size(),
                input_shape.data(), input_shape.size());
            auto h_state_t = Ort::Value::CreateTensor<float>(
                mem_info, h_state.data(), h_state.size(),
                h_shape.data(), h_shape.size());
            auto l_state_t = Ort::Value::CreateTensor<float>(
                mem_info, l_state.data(), l_state.size(),
                l_shape.data(), l_shape.size());

            std::vector<Ort::Value> inputs;
            inputs.push_back(std::move(input_t));
            inputs.push_back(std::move(h_state_t));
            inputs.push_back(std::move(l_state_t));

            const char* in_names[]  = { "input", "h_state", "l_state" };
            const char* out_names[] = { "new_l_state", "output", "halt_score" };

            auto outputs = session_->Run(
                Ort::RunOptions{nullptr},
                in_names, inputs.data(), inputs.size(),
                out_names, 3);

            float* new_l = outputs[0].GetTensorMutableData<float>();
            l_state.assign(new_l, new_l + config_.l_dim);

            float* out_data = outputs[1].GetTensorMutableData<float>();
            result.output.assign(out_data, out_data + config_.output_dim);

            float halt = outputs[2].GetTensorMutableData<float>()[0];
            result.confidence = halt;

            if (halt >= config_.halt_thresh) {
                result.halted_early = true;
                goto done;
            }
        }

        // ── H module update (after L converges) ──
        {
            std::vector<int64_t> l_shape = { 1, (int64_t)config_.l_dim };
            std::vector<int64_t> h_shape = { 1, (int64_t)config_.h_dim };

            auto l_t = Ort::Value::CreateTensor<float>(
                mem_info, l_state.data(), l_state.size(),
                l_shape.data(), l_shape.size());
            auto h_t = Ort::Value::CreateTensor<float>(
                mem_info, h_state.data(), h_state.size(),
                h_shape.data(), h_shape.size());

            std::vector<Ort::Value> h_inputs;
            h_inputs.push_back(std::move(l_t));
            h_inputs.push_back(std::move(h_t));

            const char* h_in[]  = { "l_state_in", "h_state_in" };
            const char* h_out[] = { "h_state_out" };

            auto h_outputs = session_->Run(
                Ort::RunOptions{nullptr},
                h_in, h_inputs.data(), h_inputs.size(),
                h_out, 1);

            float* new_h = h_outputs[0].GetTensorMutableData<float>();
            h_state.assign(new_h, new_h + config_.h_dim);

            l_state = init_l_state();
        }
    }

done:
    return result;
}

inline HRMResult HRMEngine::infer(const std::vector<float>& input) {
    return run_hierarchical(input);
}

inline HRMResult HRMEngine::infer_verbose(const std::vector<float>& input) {
    auto result = run_hierarchical(input);
    HRM_LOGI("HRM inference: outer=%d inner=%d confidence=%.3f halted=%s",
             result.outer_steps_taken,
             result.total_inner_steps,
             result.confidence,
             result.halted_early ? "yes" : "no (max steps)");
    return result;
}

#else // !HAS_ONNXRUNTIME

// Stub when ONNX Runtime is not available
class HRMEngine {
public:
    explicit HRMEngine(const HRMConfig& config) {
        HRM_LOGE("HRM Engine: ONNX Runtime not available. Build with HAS_ONNXRUNTIME=1.");
    }
    ~HRMEngine() = default;
    HRMResult infer(const std::vector<float>&) { return {}; }
    HRMResult infer_verbose(const std::vector<float>&) { return {}; }
    bool is_ready() const { return false; }
    const HRMConfig& config() const { return config_; }
private:
    HRMConfig config_;
};

#endif // HAS_ONNXRUNTIME
