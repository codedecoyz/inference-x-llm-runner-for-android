package com.mobilellama.ai

import android.util.Log

/**
 * Kotlin bridge to the C++ dual inference engine (HRM + Layer Streaming LLM).
 * Loads ai_engine.so which is separate from the existing llama_jni.so.
 */
object AIBridge {

    private const val TAG = "AIBridge"
    var loaded = false
        private set

    init {
        try {
            System.loadLibrary("ai_engine")
            loaded = true
            Log.i(TAG, "✅ ai_engine.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "⚠️ ai_engine.so not available: ${e.message}")
            Log.w(TAG, "   HRM and Layer Streaming engines will be unavailable.")
            Log.w(TAG, "   Build with ONNX Runtime to enable.")
        }
    }

    // ─────────────────────────────────────────
    // Token streaming callback interface
    // ─────────────────────────────────────────
    interface TokenCallback {
        fun onToken(token: String, done: Boolean)
    }

    // ─────────────────────────────────────────
    // LLM Engine (Layer Streaming)
    // ─────────────────────────────────────────

    external fun initLLM(
        modelDir: String,
        numLayers: Int,
        hiddenDim: Int,
        numHeads: Int,
        numKVHeads: Int,
        vocabSize: Int,
        maxSeqLen: Int
    ): Boolean

    external fun generateLLM(
        inputTokens: IntArray,
        maxNewTokens: Int,
        temperature: Float,
        topP: Float,
        callback: TokenCallback
    )

    external fun resetLLMContext()
    external fun destroyLLM()

    // ─────────────────────────────────────────
    // HRM Engine (Reasoning)
    // ─────────────────────────────────────────

    external fun initHRM(
        modelPath: String,
        inputDim: Int,
        hDim: Int,
        lDim: Int,
        outputDim: Int,
        maxOuter: Int,
        maxInner: Int,
        haltThresh: Float
    ): Boolean

    external fun inferHRM(input: FloatArray): FloatArray?
    external fun getHRMDiagnostics(input: FloatArray): IntArray?
    external fun destroyHRM()

    // ─────────────────────────────────────────
    // Vision Engine (Image Classification / Detection) [REMOVED - Replaced by LLaVA in LlamaEngine]
    // ─────────────────────────────────────────

    // ─────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────

    external fun getEngineStatus(): Int  // bitmask: 0b001=LLM, 0b010=HRM

    val isLLMReady: Boolean get() = loaded && (getEngineStatus() and 1) != 0
    val isHRMReady: Boolean get() = loaded && (getEngineStatus() and 2) != 0
    val isBothReady: Boolean get() = loaded && getEngineStatus() == 3
}
