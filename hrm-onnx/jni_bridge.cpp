#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#include "llm_engine.h"
#include "hrm_engine.h"

#define JNI_TAG "AIBridge"
#define JLOGI(...) __android_log_print(ANDROID_LOG_INFO,  JNI_TAG, __VA_ARGS__)
#define JLOGE(...) __android_log_print(ANDROID_LOG_ERROR, JNI_TAG, __VA_ARGS__)

// ─────────────────────────────────────────────
// Global engine instances — live for app lifetime
// ─────────────────────────────────────────────
static std::unique_ptr<LLMEngine> g_llm = nullptr;
static std::unique_ptr<HRMEngine> g_hrm = nullptr;

// ─────────────────────────────────────────────
// Helper: JString → std::string
// ─────────────────────────────────────────────
static std::string jstring_to_str(JNIEnv* env, jstring js) {
    if (!js) return "";
    const char* chars = env->GetStringUTFChars(js, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(js, chars);
    return result;
}

// ─────────────────────────────────────────────
// Helper: jfloatArray → std::vector<float>
// ─────────────────────────────────────────────
static std::vector<float> jfloat_to_vec(JNIEnv* env, jfloatArray arr) {
    jsize len = env->GetArrayLength(arr);
    std::vector<float> result(len);
    env->GetFloatArrayRegion(arr, 0, len, result.data());
    return result;
}

// ─────────────────────────────────────────────
// Helper: jintArray → std::vector<int>
// ─────────────────────────────────────────────
static std::vector<int> jint_to_vec(JNIEnv* env, jintArray arr) {
    jsize len = env->GetArrayLength(arr);
    std::vector<int> result(len);
    jint* data = env->GetIntArrayElements(arr, nullptr);
    for (int i = 0; i < len; ++i) result[i] = data[i];
    env->ReleaseIntArrayElements(arr, data, JNI_ABORT);
    return result;
}

extern "C" {

// ═════════════════════════════════════════════
// LLM ENGINE — Init / Generate / Reset
// ═════════════════════════════════════════════

// Call from Kotlin: AIBridge.initLLM(modelDir, numLayers, hiddenDim, numHeads, numKVHeads)
JNIEXPORT jboolean JNICALL
Java_com_yourapp_ai_AIBridge_initLLM(
    JNIEnv* env, jobject /*this*/,
    jstring model_dir,
    jint num_layers,
    jint hidden_dim,
    jint num_heads,
    jint num_kv_heads,
    jint vocab_size,
    jint max_seq_len)
{
    ModelConfig cfg;
    cfg.model_dir    = jstring_to_str(env, model_dir);
    cfg.num_layers   = num_layers;
    cfg.hidden_dim   = hidden_dim;
    cfg.num_heads    = num_heads;
    cfg.num_kv_heads = num_kv_heads;
    cfg.head_dim     = hidden_dim / num_heads;
    cfg.vocab_size   = vocab_size;
    cfg.max_seq_len  = max_seq_len;

    try {
        g_llm = std::make_unique<LLMEngine>(cfg);
        g_llm->warmup(4);  // preload first 4 layers
        JLOGI("LLM engine initialized | dir=%s", cfg.model_dir.c_str());
        return JNI_TRUE;
    } catch (const std::exception& e) {
        JLOGE("LLM init failed: %s", e.what());
        return JNI_FALSE;
    }
}

// Streaming generate — calls back into Kotlin for each token
// Kotlin signature: external fun generateLLM(tokens: IntArray, maxTokens: Int, temp: Float, callback: TokenCallback)
JNIEXPORT void JNICALL
Java_com_yourapp_ai_AIBridge_generateLLM(
    JNIEnv* env, jobject /*this*/,
    jintArray input_tokens,
    jint max_new_tokens,
    jfloat temperature,
    jfloat top_p,
    jobject callback)   // Kotlin interface: fun onToken(token: String, done: Boolean)
{
    if (!g_llm || !g_llm->is_ready()) {
        JLOGE("LLM not initialized");
        return;
    }

    auto tokens = jint_to_vec(env, input_tokens);

    SamplingConfig sampling;
    sampling.temperature   = temperature;
    sampling.top_p         = top_p;
    sampling.max_new_tokens = max_new_tokens;

    // Get Kotlin callback method
    jclass cb_class  = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb_class, "onToken",
                                           "(Ljava/lang/String;Z)V");

    // JavaVM needed for thread-safe callback
    JavaVM* jvm = nullptr;
    env->GetJavaVM(&jvm);

    g_llm->generate(tokens, sampling,
        [&](const std::string& token_str, bool done) {
            JNIEnv* cb_env = env;  // same thread for now
            jstring jtoken = cb_env->NewStringUTF(token_str.c_str());
            cb_env->CallVoidMethod(callback, on_token, jtoken, (jboolean)done);
            cb_env->DeleteLocalRef(jtoken);
        });
}

// Reset conversation context
JNIEXPORT void JNICALL
Java_com_yourapp_ai_AIBridge_resetLLMContext(
    JNIEnv* /*env*/, jobject /*this*/)
{
    if (g_llm) {
        g_llm->reset_context();
        JLOGI("LLM context reset");
    }
}

// Free LLM engine memory
JNIEXPORT void JNICALL
Java_com_yourapp_ai_AIBridge_destroyLLM(
    JNIEnv* /*env*/, jobject /*this*/)
{
    g_llm.reset();
    JLOGI("LLM engine destroyed");
}

// ═════════════════════════════════════════════
// HRM ENGINE — Init / Infer
// ═════════════════════════════════════════════

JNIEXPORT jboolean JNICALL
Java_com_yourapp_ai_AIBridge_initHRM(
    JNIEnv* env, jobject /*this*/,
    jstring model_path,
    jint input_dim,
    jint h_dim,
    jint l_dim,
    jint output_dim,
    jint max_outer,
    jint max_inner,
    jfloat halt_thresh)
{
    HRMConfig cfg;
    cfg.model_path  = jstring_to_str(env, model_path);
    cfg.input_dim   = input_dim;
    cfg.h_dim       = h_dim;
    cfg.l_dim       = l_dim;
    cfg.output_dim  = output_dim;
    cfg.max_outer   = max_outer;
    cfg.max_inner   = max_inner;
    cfg.halt_thresh = halt_thresh;

    try {
        g_hrm = std::make_unique<HRMEngine>(cfg);
        JLOGI("HRM engine initialized | path=%s", cfg.model_path.c_str());
        return g_hrm->is_ready() ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        JLOGE("HRM init failed: %s", e.what());
        return JNI_FALSE;
    }
}

// Run HRM inference
// Returns: float[] output tensor, or null on failure
// Kotlin: external fun inferHRM(input: FloatArray): FloatArray?
JNIEXPORT jfloatArray JNICALL
Java_com_yourapp_ai_AIBridge_inferHRM(
    JNIEnv* env, jobject /*this*/,
    jfloatArray input_arr)
{
    if (!g_hrm || !g_hrm->is_ready()) {
        JLOGE("HRM not initialized");
        return nullptr;
    }

    auto input = jfloat_to_vec(env, input_arr);
    HRMResult result = g_hrm->infer_verbose(input);

    if (result.output.empty()) return nullptr;

    jfloatArray out = env->NewFloatArray((jsize)result.output.size());
    env->SetFloatArrayRegion(out, 0, (jsize)result.output.size(), result.output.data());
    return out;
}

// Get HRM diagnostics from last inference (outer steps, inner steps, confidence)
// Returns: int[3] = { outer_steps, total_inner_steps, confidence_as_int_permille }
JNIEXPORT jintArray JNICALL
Java_com_yourapp_ai_AIBridge_getHRMDiagnostics(
    JNIEnv* env, jobject /*this*/,
    jfloatArray input_arr)
{
    if (!g_hrm || !g_hrm->is_ready()) return nullptr;

    auto input = jfloat_to_vec(env, input_arr);
    HRMResult result = g_hrm->infer(input);

    jintArray diag = env->NewIntArray(3);
    jint vals[3] = {
        result.outer_steps_taken,
        result.total_inner_steps,
        (jint)(result.confidence * 1000.0f)  // permille
    };
    env->SetIntArrayRegion(diag, 0, 3, vals);
    return diag;
}

JNIEXPORT void JNICALL
Java_com_yourapp_ai_AIBridge_destroyHRM(
    JNIEnv* /*env*/, jobject /*this*/)
{
    g_hrm.reset();
    JLOGI("HRM engine destroyed");
}

// ═════════════════════════════════════════════
// UTILITY
// ═════════════════════════════════════════════

// Check which engines are ready
// Returns: int bitmask — bit 0 = LLM ready, bit 1 = HRM ready
JNIEXPORT jint JNICALL
Java_com_yourapp_ai_AIBridge_getEngineStatus(
    JNIEnv* /*env*/, jobject /*this*/)
{
    int status = 0;
    if (g_llm && g_llm->is_ready()) status |= 1;
    if (g_hrm && g_hrm->is_ready()) status |= 2;
    return status;
}

} // extern "C"
