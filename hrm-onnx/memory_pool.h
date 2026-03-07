#pragma once
#include <vector>
#include <mutex>
#include <cstring>
#include <cstdint>
#include <android/log.h>

#define LOG_TAG "MemoryPool"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Fixed-size block memory pool — avoids malloc/free per layer
// All layer buffers are the same size (max_layer_size_bytes)
class MemoryPool {
public:
    explicit MemoryPool(size_t block_size, size_t num_blocks = 4)
        : block_size_(block_size), num_blocks_(num_blocks) {
        pool_.resize(block_size * num_blocks);
        for (size_t i = 0; i < num_blocks; ++i) {
            free_blocks_.push_back(pool_.data() + i * block_size);
        }
        LOGI("MemoryPool created: %zu blocks x %zu bytes = %zu MB",
             num_blocks, block_size, (block_size * num_blocks) / (1024 * 1024));
    }

    // Acquire a block — blocks until one is free
    uint8_t* acquire() {
        std::unique_lock<std::mutex> lock(mutex_);
        cv_.wait(lock, [this] { return !free_blocks_.empty(); });
        uint8_t* block = free_blocks_.back();
        free_blocks_.pop_back();
        return block;
    }

    // Release block back to pool
    void release(uint8_t* block) {
        std::unique_lock<std::mutex> lock(mutex_);
        free_blocks_.push_back(block);
        cv_.notify_one();
    }

    size_t block_size() const { return block_size_; }
    size_t available() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return free_blocks_.size();
    }

private:
    size_t block_size_;
    size_t num_blocks_;
    std::vector<uint8_t> pool_;           // contiguous backing memory
    std::vector<uint8_t*> free_blocks_;   // pointers into pool_
    mutable std::mutex mutex_;
    std::condition_variable cv_;
};
