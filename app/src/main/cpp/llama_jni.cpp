#include "llama_jni.h"
#include <llama.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <atomic>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaInstance {
    llama_model* model;
    llama_context* context;
    std::atomic<bool> stop_requested;

    LlamaInstance(llama_model* m, llama_context* c)
        : model(m), context(c), stop_requested(false) {}
};

JNIEXPORT jlong JNICALL
Java_com_mobilellama_native_LlamaEngine_nativeInit(
    JNIEnv* env, jobject thiz, jstring modelPath, jint contextSize, jint numThreads) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to get model path");
        return 0;
    }

    LOGI("Initializing model from: %s", path);

    // Initialize llama backend
    llama_backend_init(false);

    // Load model
    llama_model_params model_params = llama_model_default_params();
    llama_model* model = llama_load_model_from_file(path, model_params);

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

    llama_context* context = llama_new_context_with_model(model, ctx_params);
    if (!context) {
        LOGE("Failed to create context");
        llama_free_model(model);
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"),
            "Failed to create inference context");
        return 0;
    }

    LlamaInstance* instance = new LlamaInstance(model, context);
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

    // Tokenize prompt
    int n_prompt_tokens = -llama_tokenize(instance->context, prompt_text, 0, nullptr, 0, true, true);
    std::vector<llama_token> tokens_prompt(n_prompt_tokens);
    llama_tokenize(instance->context, prompt_text, n_prompt_tokens, tokens_prompt.data(), tokens_prompt.size(), true, true);

    env->ReleaseStringUTFChars(prompt, prompt_text);

    if (tokens_prompt.empty()) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to tokenize prompt");
        return JNI_FALSE;
    }

    // Evaluate prompt tokens in batches
    llama_batch batch = llama_batch_init(512, 0, 1);

    for (size_t i = 0; i < tokens_prompt.size(); i += batch.n_tokens) {
        llama_batch_clear(batch);

        size_t batch_size = std::min(size_t(batch.n_tokens), tokens_prompt.size() - i);
        for (size_t j = 0; j < batch_size; j++) {
            llama_batch_add(batch, tokens_prompt[i + j], i + j, {0}, false);
        }

        // Mark last token as logits position
        if (i + batch_size == tokens_prompt.size()) {
            batch.logits[batch.n_tokens - 1] = true;
        }

        if (llama_decode(instance->context, batch) != 0) {
            LOGE("Failed to decode prompt batch");
            llama_batch_free(batch);
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
        float* logits = llama_get_logits_ith(instance->context, batch.n_tokens - 1);
        int n_vocab = llama_n_vocab(llama_get_model(instance->context));

        std::vector<llama_token_data> candidates;
        candidates.reserve(n_vocab);
        for (llama_token token_id = 0; token_id < n_vocab; token_id++) {
            candidates.push_back({token_id, logits[token_id], 0.0f});
        }

        llama_token_data_array candidates_p = {candidates.data(), candidates.size(), false};

        // Apply sampling: top_k, top_p, temperature
        llama_sample_top_k(instance->context, &candidates_p, 40, 1);
        llama_sample_top_p(instance->context, &candidates_p, 0.9f, 1);
        llama_sample_temp(instance->context, &candidates_p, 0.7f);
        llama_token new_token_id = llama_sample_token(instance->context, &candidates_p);

        // Check for EOS
        if (new_token_id == llama_token_eos(llama_get_model(instance->context))) {
            LOGI("Generation complete (EOS)");
            break;
        }

        // Decode token to string
        char token_str[256];
        int n_token_str = llama_token_to_piece(llama_get_model(instance->context), new_token_id,
                                                token_str, sizeof(token_str), false);
        if (n_token_str < 0) {
            LOGE("Failed to decode token");
            break;
        }
        token_str[n_token_str] = '\0';

        // Call Kotlin callback with token
        jstring jtoken = env->NewStringUTF(token_str);
        env->CallObjectMethod(callback, invokeMethod, jtoken);
        env->DeleteLocalRef(jtoken);

        // Check for exceptions in callback
        if (env->ExceptionCheck()) {
            LOGE("Exception in callback");
            llama_batch_free(batch);
            return JNI_FALSE;
        }

        // Add token to batch for next iteration
        llama_batch_clear(batch);
        llama_batch_add(batch, new_token_id, n_cur, {0}, true);

        if (llama_decode(instance->context, batch) != 0) {
            LOGE("Failed to decode token");
            break;
        }

        n_cur++;
        n_generated++;
    }

    llama_batch_free(batch);
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
Java_com_mobilellama_native_LlamaEngine_nativeFree(
    JNIEnv* env, jobject thiz, jlong handle) {

    LlamaInstance* instance = reinterpret_cast<LlamaInstance*>(handle);
    if (instance) {
        LOGI("Freeing model resources");
        if (instance->context) {
            llama_free(instance->context);
        }
        if (instance->model) {
            llama_free_model(instance->model);
        }
        delete instance;
        llama_backend_free();
        LOGI("Model freed");
    }
}
