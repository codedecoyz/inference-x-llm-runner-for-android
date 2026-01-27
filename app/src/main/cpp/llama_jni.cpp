#include "llama_jni.h"
#include <llama.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <atomic>
#include <algorithm>
#include <cerrno>
#include <cstring>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaInstance {
    llama_model* model;
    llama_context* context;
    int n_ctx;
    int n_threads;
    std::atomic<bool> stop_requested;

    LlamaInstance(llama_model* m, llama_context* c, int ctx_size, int threads)
        : model(m), context(c), n_ctx(ctx_size), n_threads(threads), stop_requested(false) {}
};

// Helper to add a token to the batch
void common_batch_add(struct llama_batch & batch, llama_token id, llama_pos pos, const std::vector<llama_seq_id> & seq_ids, bool logits) {
    batch.token[batch.n_tokens] = id;
    batch.pos[batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = seq_ids.size();
    for (size_t i = 0; i < seq_ids.size(); ++i) {
        batch.seq_id[batch.n_tokens][i] = seq_ids[i];
    }
    batch.logits[batch.n_tokens] = logits;
    batch.n_tokens++;
}

void common_batch_clear(struct llama_batch & batch) {
    batch.n_tokens = 0;
}

// Log callback for llama.cpp
void llama_log_callback(ggml_log_level level, const char * text, void * user_data) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, "LlamaNative", "%s", text);
    } else if (level == GGML_LOG_LEVEL_INFO) {
        __android_log_print(ANDROID_LOG_INFO, "LlamaNative", "%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        __android_log_print(ANDROID_LOG_WARN, "LlamaNative", "%s", text);
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, "LlamaNative", "%s", text);
    }
}

JNIEXPORT jlong JNICALL
Java_com_mobilellama_native_LlamaEngine_nativeInit(
    JNIEnv* env, jobject thiz, jstring modelPath, jint contextSize, jint numThreads) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to get model path");
        return 0;
    }

    LOGI("Initializing model from: %s", path);

    // Register log callback
    llama_log_set(llama_log_callback, nullptr);

    // Verify file access manually first
    FILE* f = fopen(path, "rb");
    if (!f) {
        LOGE("Failed to open file at path: %s", path);
        LOGE("Error code: %d, Message: %s", errno, strerror(errno));
        env->ReleaseStringUTFChars(modelPath, path);
         env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to open file (fopen check failed)");
        return 0;
    }
    fclose(f);
    LOGI("File exists and is readable");

    // Initialize llama backend - new API takes void
    llama_backend_init();

    // Load model
    llama_model_params model_params = llama_model_default_params();
    // New API: llama_model_load_from_file
    llama_model* model = llama_model_load_from_file(path, model_params);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("Failed to load model from file");
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"),
            "Failed to load model. File may be corrupted or invalid.");
        return 0;
    }

    // Create context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = numThreads;
    ctx_params.n_threads_batch = numThreads;

    // New API: llama_init_from_model
    llama_context* context = llama_init_from_model(model, ctx_params);
    if (!context) {
        LOGE("Failed to create context");
        llama_model_free(model);
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"),
            "Failed to create inference context");
        return 0;
    }

    LlamaInstance* instance = new LlamaInstance(model, context, contextSize, numThreads);
    LOGI("Model initialized successfully");

    return reinterpret_cast<jlong>(instance);
}

JNIEXPORT jboolean JNICALL
Java_com_mobilellama_native_LlamaEngine_nativeGenerate(
    JNIEnv* env, jobject thiz, jlong handle, jstring prompt, jint maxTokens, jobject callback) {

    LlamaInstance* instance = reinterpret_cast<LlamaInstance*>(handle);
    if (!instance || !instance->model || !instance->context) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Invalid model handle");
        return JNI_FALSE;
    }

    // Reset stop flag
    instance->stop_requested = false;

    const char* prompt_text = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_text) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to get prompt text");
        return JNI_FALSE;
    }

    LOGI("Starting generation with prompt: %s", prompt_text);

    // Get vocab needed for tokenization
    const llama_vocab* vocab = llama_model_get_vocab(instance->model);

    // Tokenize prompt
    // New API: llama_tokenize takes vocab
    // Reverting to auto-BOS (true) for standard debug.
    int n_prompt_tokens = -llama_tokenize(vocab, prompt_text, strlen(prompt_text), nullptr, 0, true, true);
    std::vector<llama_token> tokens_prompt(n_prompt_tokens);
    
    if (llama_tokenize(vocab, prompt_text, strlen(prompt_text), tokens_prompt.data(), tokens_prompt.size(), true, true) < 0) {
        env->ReleaseStringUTFChars(prompt, prompt_text);
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to tokenize prompt");
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(prompt, prompt_text);

    if (tokens_prompt.empty()) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to tokenize prompt");
        return JNI_FALSE;
    }

    // DEBUG: Log tokens
    std::string token_ids_str = "";
    for (auto id : tokens_prompt) {
        token_ids_str += std::to_string(id) + " ";
    }
    LOGI("Tokenized PROMPT (%zu tokens): %s", tokens_prompt.size(), token_ids_str.c_str());

    // Initialize sampler chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    
    // Add samplers (using the hardcoded values from original code)
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // Evaluate prompt tokens in batches
    llama_batch batch = llama_batch_init(512, 0, 1);

    for (size_t i = 0; i < tokens_prompt.size(); i += batch.n_tokens) {
        common_batch_clear(batch);

        size_t batch_size = std::min(size_t(batch.n_tokens), tokens_prompt.size() - i); // batch.n_tokens is just capacity here really? No, we shouldn't use batch.n_tokens as capacity check 
        // We should check vs 512.
        batch_size = std::min(size_t(512), tokens_prompt.size() - i);
        
        for (size_t j = 0; j < batch_size; j++) {
            common_batch_add(batch, tokens_prompt[i + j], i + j, {0}, false);
        }

        // Mark last token as logits position
        if (i + batch_size == tokens_prompt.size()) {
            batch.logits[batch.n_tokens - 1] = true;
        }

        if (llama_decode(instance->context, batch) != 0) {
            LOGE("Failed to decode prompt batch");
            llama_batch_free(batch);
            llama_sampler_free(smpl);
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to process prompt");
            return JNI_FALSE;
        }
    }

    // Get callback method
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");
    if (!invokeMethod) {
        LOGE("Failed to find callback invoke method");
        llama_batch_free(batch);
        llama_sampler_free(smpl);
        return JNI_FALSE;
    }

    // Generation loop
    int n_generated = 0;
    int n_ctx = llama_n_ctx(instance->context);
    int n_cur = tokens_prompt.size();

    while (n_generated < maxTokens && n_cur < n_ctx) {
        if (instance->stop_requested) {
            LOGI("Generation stopped by user");
            break;
        }

        // Sample next token
        // New API: llama_sampler_sample
        llama_token new_token_id = llama_sampler_sample(smpl, instance->context, -1);

        // Accept the token
        llama_sampler_accept(smpl, new_token_id);

        // Check for EOS
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            LOGI("Generation complete (EOS)");
            break;
        }

        // Decode token to string
        char token_str[256];
        // New API: llama_token_to_piece takes vocab
        int n_token_str = llama_token_to_piece(vocab, new_token_id,
                                                token_str, sizeof(token_str), 0, true);
        if (n_token_str < 0) {
            LOGE("Failed to decode token");
            break;
        }
        token_str[n_token_str] = '\0';

        // DEBUG: Log the actual generated piece
        LOGI("Generated Token: '%s' (id=%d)", token_str, new_token_id);

        // Check for stop tokens (basic string check)
        // Ideally we should process tokens, but for now checking the decoded string helps
        std::string piece(token_str);
        if (piece.find("<|user|>") != std::string::npos || 
            piece.find("<|system|>") != std::string::npos ||
            piece.find("<|assistant|>") != std::string::npos ||
            piece.find("</s>") != std::string::npos) {
            LOGI("Generation stopped (Stop token found)");
            break;
        }

        // Call Kotlin callback with token
        jstring jtoken = env->NewStringUTF(token_str);
        env->CallObjectMethod(callback, invokeMethod, jtoken);
        env->DeleteLocalRef(jtoken);

        // Check for exceptions in callback
        if (env->ExceptionCheck()) {
            LOGE("Exception in callback");
            break;
        }

        // Add token to batch for next iteration
        common_batch_clear(batch);
        common_batch_add(batch, new_token_id, n_cur, {0}, true);

        if (llama_decode(instance->context, batch) != 0) {
            LOGE("Failed to decode token");
            break;
        }

        n_cur++;
        n_generated++;
    }

    llama_batch_free(batch);
    llama_sampler_free(smpl);
    LOGI("Generation complete. Generated %d tokens", n_generated);

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_mobilellama_native_LlamaEngine_nativeStop(
    JNIEnv* env, jobject thiz, jlong handle) {

    LlamaInstance* instance = reinterpret_cast<LlamaInstance*>(handle);
    if (instance) {
        instance->stop_requested = true;
        LOGI("Stop requested");
    }
}

JNIEXPORT void JNICALL
Java_com_mobilellama_native_LlamaEngine_nativeClearCache(
    JNIEnv* env, jobject thiz, jlong handle) {
    
    LlamaInstance* instance = reinterpret_cast<LlamaInstance*>(handle);
    if (instance && instance->model) {
        LOGI("Recreating context to clear cache");
        
        // Free old context
        if (instance->context) {
            llama_free(instance->context);
            instance->context = nullptr;
        }

        // Create new context with same parameters
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = instance->n_ctx;
        ctx_params.n_threads = instance->n_threads;
        ctx_params.n_threads_batch = instance->n_threads;

        instance->context = llama_init_from_model(instance->model, ctx_params);
        
        if (!instance->context) {
            LOGE("Failed to recreate context during cache clear");
        } else {
            LOGI("Context recreated successfully");
        }
    }
}

JNIEXPORT void JNICALL
Java_com_mobilellama_native_LlamaEngine_nativeFree(
    JNIEnv* env, jobject thiz, jlong handle) {
    
    LlamaInstance* instance = reinterpret_cast<LlamaInstance*>(handle);
    if (instance) {
        LOGI("Freeing model resources");
        if (instance->context) {
            llama_free(instance->context);
        }
        if (instance->model) {
            llama_model_free(instance->model);
        }
        delete instance;
        llama_backend_free();
        LOGI("Model freed");
    }
}

