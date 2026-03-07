package com.yourapp.ai

import android.content.Context
import android.util.Log

/**
 * Kotlin interface to the C++ double inference engine.
 * One class, two engines (LLM + HRM), single .so file.
 */
object AIBridge {

    private const val TAG = "AIBridge"
    private var loaded = false

    init {
        try {
            System.loadLibrary("ai_engine")
            loaded = true
            Log.i(TAG, "ai_engine.so loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load ai_engine.so: ${e.message}")
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
    // Utility
    // ─────────────────────────────────────────

    external fun getEngineStatus(): Int  // bitmask: 0b01=LLM, 0b10=HRM, 0b11=both

    val isLLMReady: Boolean get() = (getEngineStatus() and 1) != 0
    val isHRMReady: Boolean get() = (getEngineStatus() and 2) != 0
    val isBothReady: Boolean get() = getEngineStatus() == 3
}
