package com.mobilellama.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.mobilellama.data.model.InferenceState
import com.mobilellama.native.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InferenceRepository @Inject constructor(
    private val prefs: SharedPreferences
) {
    private val _inferenceState = MutableStateFlow<InferenceState>(InferenceState.Uninitialized)
    val inferenceState: StateFlow<InferenceState> = _inferenceState.asStateFlow()

    private var llamaEngine: LlamaEngine? = null

    companion object {
        private const val TAG = "InferenceRepository"
        private const val PREF_TEMPERATURE = "sampling_temperature"
        private const val PREF_TOP_P = "sampling_top_p"
        private const val PREF_TOP_K = "sampling_top_k"
        private const val PREF_MAX_TOKENS = "max_tokens"

        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_TOP_P = 0.9f
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_MAX_TOKENS = 512

        private const val PROMPT_TEMPLATE = """<|system|>
You are a helpful assistant.
<|user|>
%s
<|assistant|>
"""
    }

    suspend fun initializeModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (_inferenceState.value is InferenceState.Ready) {
                Log.i(TAG, "Model already initialized")
                return@withContext Result.success(Unit)
            }

            _inferenceState.value = InferenceState.Initializing
            Log.i(TAG, "Initializing model from: $modelPath")

            val engine = LlamaEngine()
            val result = engine.initialize(modelPath)

            if (result.isSuccess) {
                llamaEngine = engine
                _inferenceState.value = InferenceState.Ready
                Log.i(TAG, "Model initialized successfully")
                Result.success(Unit)
            } else {
                val error = result.exceptionOrNull()
                val errorMsg = error?.message ?: "Failed to initialize model"
                _inferenceState.value = InferenceState.Error(errorMsg)
                Log.e(TAG, "Model initialization failed", error)
                Result.failure(error ?: Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error during initialization"
            _inferenceState.value = InferenceState.Error(errorMsg)
            Log.e(TAG, "Initialization error", e)
            Result.failure(e)
        }
    }

    suspend fun generateResponse(
        userMessage: String,
        onToken: suspend (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val engine = llamaEngine
            if (engine == null) {
                val errorMsg = "Engine not initialized"
                _inferenceState.value = InferenceState.Error(errorMsg)
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            if (_inferenceState.value !is InferenceState.Ready) {
                val errorMsg = "Engine not ready for inference"
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            // Get sampling parameters
            val maxTokens = prefs.getInt(PREF_MAX_TOKENS, DEFAULT_MAX_TOKENS)

            // Build prompt with template
            val prompt = String.format(PROMPT_TEMPLATE, userMessage)

            Log.i(TAG, "Starting generation")
            _inferenceState.value = InferenceState.Generating(0)

            var tokenCount = 0

            val result = engine.generate(
                prompt = prompt,
                maxTokens = maxTokens,
                onToken = { token ->
                    tokenCount++
                    // Update state
                    _inferenceState.value = InferenceState.Generating(tokenCount)
                    // Call user callback (will be suspended on main thread)
                    kotlinx.coroutines.runBlocking {
                        onToken(token)
                    }
                }
            )

            if (result.isSuccess) {
                _inferenceState.value = InferenceState.Ready
                Log.i(TAG, "Generation completed. Tokens: $tokenCount")
                Result.success(Unit)
            } else {
                val error = result.exceptionOrNull()
                val errorMsg = error?.message ?: "Generation failed"
                _inferenceState.value = InferenceState.Error(errorMsg)
                Log.e(TAG, "Generation failed", error)
                Result.failure(error ?: Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error during generation"
            _inferenceState.value = InferenceState.Error(errorMsg)
            Log.e(TAG, "Generation error", e)
            Result.failure(e)
        }
    }

    fun stopGeneration() {
        llamaEngine?.stopGeneration()
        if (_inferenceState.value is InferenceState.Generating) {
            _inferenceState.value = InferenceState.Ready
            Log.i(TAG, "Generation stopped by user")
        }
    }

    fun release() {
        llamaEngine?.release()
        llamaEngine = null
        _inferenceState.value = InferenceState.Uninitialized
        Log.i(TAG, "Engine released")
    }

    // Sampling parameter getters
    fun getTemperature(): Float = prefs.getFloat(PREF_TEMPERATURE, DEFAULT_TEMPERATURE)
    fun getTopP(): Float = prefs.getFloat(PREF_TOP_P, DEFAULT_TOP_P)
    fun getTopK(): Int = prefs.getInt(PREF_TOP_K, DEFAULT_TOP_K)
    fun getMaxTokens(): Int = prefs.getInt(PREF_MAX_TOKENS, DEFAULT_MAX_TOKENS)

    // Sampling parameter setters
    fun setTemperature(value: Float) {
        prefs.edit().putFloat(PREF_TEMPERATURE, value.coerceIn(0.0f, 2.0f)).apply()
    }

    fun setTopP(value: Float) {
        prefs.edit().putFloat(PREF_TOP_P, value.coerceIn(0.0f, 1.0f)).apply()
    }

    fun setTopK(value: Int) {
        prefs.edit().putInt(PREF_TOP_K, value.coerceIn(1, 100)).apply()
    }

    fun setMaxTokens(value: Int) {
        prefs.edit().putInt(PREF_MAX_TOKENS, value.coerceIn(1, 2048)).apply()
    }

    fun resetToDefaults() {
        prefs.edit()
            .putFloat(PREF_TEMPERATURE, DEFAULT_TEMPERATURE)
            .putFloat(PREF_TOP_P, DEFAULT_TOP_P)
            .putInt(PREF_TOP_K, DEFAULT_TOP_K)
            .putInt(PREF_MAX_TOKENS, DEFAULT_MAX_TOKENS)
            .apply()
    }
}
