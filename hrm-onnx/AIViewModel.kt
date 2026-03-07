package com.yourapp.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────
// Model configs — tune to your model
// ─────────────────────────────────────────────
object LlamaConfig {
    // Llama 3.2 1B
    const val NUM_LAYERS  = 16
    const val HIDDEN_DIM  = 2048
    const val NUM_HEADS   = 32
    const val NUM_KV_HEADS = 8
    const val VOCAB_SIZE  = 128256
    const val MAX_SEQ_LEN = 2048
}

object HRMModelConfig {
    const val INPUT_DIM  = 64
    const val H_DIM      = 256
    const val L_DIM      = 128
    const val OUTPUT_DIM = 64
    const val MAX_OUTER  = 20
    const val MAX_INNER  = 10
    const val HALT_THRESH = 0.9f
}

// ─────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val engine: String = ""  // "LLM" or "HRM"
)

sealed class EngineState {
    object Uninitialized : EngineState()
    object Loading : EngineState()
    data class Ready(val llm: Boolean, val hrm: Boolean) : EngineState()
    data class Error(val message: String) : EngineState()
}

// ─────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────
class AIViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Uninitialized)
    val engineState: StateFlow<EngineState> = _engineState

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    // Simple intent router — expands over time
    private val reasoningKeywords = setOf(
        "solve", "calculate", "math", "logic", "puzzle",
        "find the", "shortest", "optimal", "how many",
        "prove", "pattern", "sequence", "sudoku", "maze",
        "step by step", "reason", "derive"
    )

    // ─────────────────────────────────────────
    // Init both engines
    // ─────────────────────────────────────────
    fun initialize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _engineState.value = EngineState.Loading

            val modelRoot = context.filesDir.absolutePath

            val llmOk = AIBridge.initLLM(
                modelDir    = "$modelRoot/llm",
                numLayers   = LlamaConfig.NUM_LAYERS,
                hiddenDim   = LlamaConfig.HIDDEN_DIM,
                numHeads    = LlamaConfig.NUM_HEADS,
                numKVHeads  = LlamaConfig.NUM_KV_HEADS,
                vocabSize   = LlamaConfig.VOCAB_SIZE,
                maxSeqLen   = LlamaConfig.MAX_SEQ_LEN
            )

            val hrmOk = AIBridge.initHRM(
                modelPath  = "$modelRoot/hrm/hrm.onnx",
                inputDim   = HRMModelConfig.INPUT_DIM,
                hDim       = HRMModelConfig.H_DIM,
                lDim       = HRMModelConfig.L_DIM,
                outputDim  = HRMModelConfig.OUTPUT_DIM,
                maxOuter   = HRMModelConfig.MAX_OUTER,
                maxInner   = HRMModelConfig.MAX_INNER,
                haltThresh = HRMModelConfig.HALT_THRESH
            )

            _engineState.value = EngineState.Ready(llm = llmOk, hrm = hrmOk)
        }
    }

    // ─────────────────────────────────────────
    // Send a message
    // ─────────────────────────────────────────
    fun sendMessage(userInput: String) {
        if (_isGenerating.value) return

        val userMsg = ChatMessage(text = userInput, isUser = true)
        _messages.value = _messages.value + userMsg

        viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true

            if (isReasoningTask(userInput)) {
                runHRM(userInput)
            } else {
                runLLM(userInput)
            }

            _isGenerating.value = false
        }
    }

    // ─────────────────────────────────────────
    // Route to HRM
    // ─────────────────────────────────────────
    private fun runHRM(input: String) {
        val hrmInput = encodeForHRM(input)
        val result = AIBridge.inferHRM(hrmInput)
        val diag = AIBridge.getHRMDiagnostics(hrmInput)

        val text = if (result != null) {
            decodeHRMOutput(result, input)
        } else {
            "HRM inference failed — falling back to LLM"
        }

        val diagText = diag?.let {
            "\n\n[HRM: ${it[0]} outer steps, ${it[1]} inner steps, " +
            "confidence ${it[2] / 10.0f}%]"
        } ?: ""

        val aiMsg = ChatMessage(
            text = text + diagText,
            isUser = false,
            engine = "HRM"
        )
        _messages.value = _messages.value + aiMsg
    }

    // ─────────────────────────────────────────
    // Route to LLM (streaming)
    // ─────────────────────────────────────────
    private fun runLLM(input: String) {
        val tokens = tokenize(input)

        // Add placeholder streaming message
        val placeholder = ChatMessage(
            text = "",
            isUser = false,
            isStreaming = true,
            engine = "LLM"
        )
        _messages.value = _messages.value + placeholder

        val accumulated = StringBuilder()

        AIBridge.generateLLM(
            inputTokens = tokens.toIntArray(),
            maxNewTokens = 512,
            temperature = 0.7f,
            topP = 0.9f,
            callback = object : AIBridge.TokenCallback {
                override fun onToken(token: String, done: Boolean) {
                    accumulated.append(detokenize(token))

                    // Update streaming message in place
                    val updated = _messages.value.toMutableList()
                    val lastIdx = updated.lastIndex
                    updated[lastIdx] = ChatMessage(
                        text = accumulated.toString(),
                        isUser = false,
                        isStreaming = !done,
                        engine = "LLM"
                    )
                    _messages.value = updated
                }
            }
        )
    }

    // ─────────────────────────────────────────
    // Intent router
    // ─────────────────────────────────────────
    private fun isReasoningTask(input: String): Boolean {
        val lower = input.lowercase()
        return reasoningKeywords.any { lower.contains(it) }
    }

    // ─────────────────────────────────────────
    // Tokenizer placeholders
    // Replace these with real BPE tokenizer (SentencePiece / tiktoken)
    // ─────────────────────────────────────────
    private fun tokenize(text: String): List<Int> {
        // TODO: Implement real BPE tokenizer
        // Options: SentencePiece Java binding, or call C++ tokenizer via JNI
        return text.split(" ").mapIndexed { i, _ -> i + 1 }
    }

    private fun detokenize(tokenId: String): String {
        // TODO: Map token id back to string
        return " $tokenId"
    }

    private fun encodeForHRM(input: String): FloatArray {
        // TODO: Proper HRM input encoding
        // For now: simple character encoding as proof of concept
        val encoded = FloatArray(HRMModelConfig.INPUT_DIM) { 0f }
        input.forEachIndexed { i, c ->
            if (i < HRMModelConfig.INPUT_DIM) encoded[i] = c.code / 128.0f
        }
        return encoded
    }

    private fun decodeHRMOutput(output: FloatArray, originalInput: String): String {
        // TODO: Task-specific output decoding
        // For now return raw confidence
        val maxVal = output.max()
        val maxIdx = output.indexOfFirst { it == maxVal }
        return "HRM result: class $maxIdx (confidence: ${"%.2f".format(maxVal)})"
    }

    // ─────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        AIBridge.destroyLLM()
        AIBridge.destroyHRM()
    }

    fun resetConversation() {
        _messages.value = emptyList()
        AIBridge.resetLLMContext()
    }
}
