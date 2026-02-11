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
        // Lower token limit for concise answers for presentation
        private const val DEFAULT_MAX_TOKENS = 128

        private const val PROMPT_TEMPLATE = """<|system|>
You are a helpful assistant.</s>
<|user|>
%s</s>
<|assistant|>
"""
    }

    suspend fun initializeModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        // ... (lines 44-119 same until generateResponse)
        try {
            if (_inferenceState.value is InferenceState.Ready) {
                // ... same
                return@withContext Result.success(Unit)
            }
            // ... same
            _inferenceState.value = InferenceState.Initializing
            Log.i(TAG, "Initializing model from: $modelPath")

            // On some Android versions, native fopen fails with private storage paths.
            // ... (keep FD code)
            val file = java.io.File(modelPath)
            
            // ... (keep Debug File Check logs)
            Log.d(TAG, "Debug File Check:")
            Log.d(TAG, "Path: ${file.absolutePath}")
            Log.d(TAG, "Exists: ${file.exists()}")
            Log.d(TAG, "Can Read: ${file.canRead()}")
            Log.d(TAG, "Length: ${file.length()}")
            
            // ... (keep parent check)
            val parent = file.parentFile
            if (parent != null && parent.exists()) {
                 // ...
            }

            // ... (keep FD opening)
            val pfd = try {
                 android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open PFD for file: ${file.absolutePath}", e)
                throw e
            }
            val fd = pfd.fd
            val fdPath = "/proc/self/fd/$fd"
            Log.i(TAG, "Opened file descriptor: $fd, passing path: $fdPath")

            val engine = LlamaEngine()
            // Keep pfd referenced so it doesn't get GC'd and closed before native loads it
            val result = try {
                engine.initialize(fdPath)
            } finally {
                // ... (keep safe close)
                try {
                    pfd.close() 
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing PFD", e)
                }
            }

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
            // ...
            val errorMsg = e.message ?: "Unknown error during initialization"
            _inferenceState.value = InferenceState.Error(errorMsg)
            Log.e(TAG, "Initialization error", e)
            Result.failure(e)
        }
    }

    /**
     * @param prompt The full prompt (including history and special tokens) to send to the model.
     */
    suspend fun generateResponse(
        prompt: String,
        onToken: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val engine = llamaEngine
            if (engine == null) {
                val errorMsg = "Engine not initialized"
                _inferenceState.value = InferenceState.Error(errorMsg)
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            // Allow restarting if in Error state, or Ready. Stuck in Generating is bad but we can force it.
            if (_inferenceState.value is InferenceState.Generating) {
                 Log.w(TAG, "Engine was busy. Forcing stop to accept new request.")
                 engine.stopGeneration()
                 // Give it a moment to reset? 
                 // Native stop is async flag, but we check IsReady. 
            }
            
            // Get sampling parameters
            val maxTokens = prefs.getInt(PREF_MAX_TOKENS, DEFAULT_MAX_TOKENS)

            // Clear previous context to ensure stateless generation (we pass full history)
            engine.clearCache()

            Log.i(TAG, "Starting generation")
            _inferenceState.value = InferenceState.Generating(0)

            var tokenCount = 0
            val fullResponseInfo = StringBuilder()
            // Stop tokens
            val stopSequences = listOf("<|user|>", "<|system|>", "<|assistant|>", "</s>")

            val result = engine.generate(
                prompt = prompt,
                maxTokens = maxTokens,
                onToken = { token ->
                    tokenCount++
                    fullResponseInfo.append(token)
                    val fullText = fullResponseInfo.toString()
                    
                    // Check for stop sequences logic
                    var shouldStop = false
                    for (stopSeq in stopSequences) {
                         // Check tail of string or if token contains it
                        if (fullText.endsWith(stopSeq) || token.contains(stopSeq)) {
                            Log.i(TAG, "Stop sequence detected: $stopSeq")
                            shouldStop = true
                            break
                        }
                    }

                    if (shouldStop) {
                        engine.stopGeneration()
                    } else {
                        // Update state
                        _inferenceState.value = InferenceState.Generating(tokenCount)
                        // Call user callback direct (non-blocking)
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
                _inferenceState.value = InferenceState.Ready // Reset to Ready so user can try again!
                Log.e(TAG, "Generation failed", error)
                Result.failure(error ?: Exception(errorMsg))
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error during generation"
            // Reset to Ready logic here too? Or stick to Error?
            // If we throw exception, we probably want to allow retry.
            // But Error state typically shows error in UI.
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
