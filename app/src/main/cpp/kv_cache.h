#pragma once
#include <vector>
#include <cstdint>
#include <cstring>
#include <android/log.h>

#define KV_LOG_TAG "KVCache"
#define KV_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  KV_LOG_TAG, __VA_ARGS__)

// Stores K and V tensors for each layer across all generated tokens
// Shape per layer: [num_heads, seq_len, head_dim]
struct LayerKVCache {
    std::vector<float> keys;
    std::vector<float> values;
    int current_seq_len = 0;
};

class KVCache {
public:
    KVCache(int num_layers, int num_heads, int head_dim, int max_seq_len)
        : num_layers_(num_layers),
          num_heads_(num_heads),
          head_dim_(head_dim),
          max_seq_len_(max_seq_len) {

        layers_.resize(num_layers);
        size_t per_layer = (size_t)num_heads * max_seq_len * head_dim;

        for (auto& layer : layers_) {
            layer.keys.resize(per_layer, 0.0f);
            layer.values.resize(per_layer, 0.0f);
            layer.current_seq_len = 0;
        }

        KV_LOGI("KVCache: %d layers, %d heads, head_dim=%d, max_seq=%d | %.1f MB",
            num_layers, num_heads, head_dim, max_seq_len,
            (2.0f * num_layers * per_layer * sizeof(float)) / (1024.0f * 1024.0f));
    }

    void append(int layer_idx,
                const float* new_keys,
                const float* new_values,
                int seq_pos) {
        auto& layer = layers_[layer_idx];
        if (seq_pos >= max_seq_len_) return;

        for (int h = 0; h < num_heads_; ++h) {
            float* k_dst = layer.keys.data()   + h * max_seq_len_ * head_dim_ + seq_pos * head_dim_;
            float* v_dst = layer.values.data() + h * max_seq_len_ * head_dim_ + seq_pos * head_dim_;
            std::memcpy(k_dst, new_keys   + h * head_dim_, head_dim_ * sizeof(float));
            std::memcpy(v_dst, new_values + h * head_dim_, head_dim_ * sizeof(float));
        }
        layer.current_seq_len = seq_pos + 1;
    }

    const float* get_keys(int layer_idx)   const { return layers_[layer_idx].keys.data(); }
    const float* get_values(int layer_idx) const { return layers_[layer_idx].values.data(); }
    int           seq_len(int layer_idx)   const { return layers_[layer_idx].current_seq_len; }

    void reset() {
        for (auto& layer : layers_) {
            std::fill(layer.keys.begin(),   layer.keys.end(),   0.0f);
            std::fill(layer.values.begin(), layer.values.end(), 0.0f);
            layer.current_seq_len = 0;
        }
    }

    int max_seq_len() const { return max_seq_len_; }
    int num_layers()  const { return num_layers_; }
    int num_heads()   const { return num_heads_; }
    int head_dim()    const { return head_dim_; }

private:
    int num_layers_, num_heads_, head_dim_, max_seq_len_;
    std::vector<LayerKVCache> layers_;
};
