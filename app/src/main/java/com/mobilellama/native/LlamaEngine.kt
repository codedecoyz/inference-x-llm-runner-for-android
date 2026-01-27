package com.mobilellama.native

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlamaEngine {
    private var handle: Long = 0
    private val isInitialized: Boolean
        get() = handle != 0L

    companion object {
        private const val TAG = "LlamaEngine"

        init {
            try {
                Log.i(TAG, "Attempting to load libomp (v4)...")
                System.loadLibrary("omp")
                Log.i(TAG, "Attempting to load libggml-base (v3)...")
                System.loadLibrary("ggml-base")
                Log.i(TAG, "Attempting to load libggml-cpu (v3)...")
                System.loadLibrary("ggml-cpu")
                Log.i(TAG, "Attempting to load libggml (v3)...")
                System.loadLibrary("ggml")
                Log.i(TAG, "Loaded libggml. Attempting to load libllama (v3)...")
                System.loadLibrary("llama")
                Log.i(TAG, "Loaded libllama. Attempting to load libllama_jni (v3)...")
                System.loadLibrary("llama_jni")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                throw e
            }
        }
    }

    // Native method declarations
    private external fun nativeInit(modelPath: String, contextSize: Int, numThreads: Int): Long
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, callback: (String) -> Unit): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeClearCache(handle: Long)
    private external fun nativeFree(handle: Long)

    /**
     * Initialize the model with the given path.
     * Should be called from background thread (Dispatchers.IO).
     */
    suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                return@withContext Result.failure(IllegalStateException("Engine already initialized"))
            }

            Log.i(TAG, "Initializing model from: $modelPath")

            val contextSize = 2048
            // Use optimal thread count: 
            // User requested ~50% usage or better. 
            // Leaving 2 cores for UI/System is usually safe.
            val availableProcessors = Runtime.getRuntime().availableProcessors()
            val numThreads = (availableProcessors - 2).coerceAtLeast(1).coerceAtMost(8)

            Log.i(TAG, "Initializing with threads: $numThreads (available: $availableProcessors)")

            handle = nativeInit(modelPath, contextSize, numThreads)

            if (handle == 0L) {
                return@withContext Result.failure(RuntimeException("Failed to initialize model"))
            }

            Log.i(TAG, "Model initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize model", e)
            Result.failure(e)
        }
    }

    /**
     * Clear the KV cache.
     * Use this if you want to start a completely new conversation or regenerate prompt.
     */
    fun clearCache() {
        if (isInitialized) {
            nativeClearCache(handle)
        }
    }

    /**
     * Generate text from the given prompt.
     * MUST be called from background thread (Dispatchers.IO).
     * The onToken callback will be invoked from a native thread.
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        onToken: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                return@withContext Result.failure(IllegalStateException("Engine not initialized"))
            }

            Log.i(TAG, "Starting generation (maxTokens=$maxTokens)")

            val success = nativeGenerate(handle, prompt, maxTokens, onToken)

            if (success) {
                Log.i(TAG, "Generation completed successfully")
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException("Generation failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation error", e)
            Result.failure(e)
        }
    }

    /**
     * Stop ongoing generation.
     * Safe to call from any thread.
     */
    fun stopGeneration() {
        if (isInitialized) {
            Log.i(TAG, "Stopping generation")
            nativeStop(handle)
        }
    }

    /**
     * Release all native resources.
     * After calling this, the engine cannot be reused.
     * Safe to call from any thread.
     */
    fun release() {
        if (isInitialized) {
            Log.i(TAG, "Releasing model resources")
            nativeFree(handle)
            handle = 0
        }
    }
}
