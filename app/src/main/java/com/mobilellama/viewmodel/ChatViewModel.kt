package com.mobilellama.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilellama.data.database.ChatDao
import com.mobilellama.data.database.MessageDao
import com.mobilellama.data.model.Chat
import com.mobilellama.data.model.InferenceState
import com.mobilellama.data.model.Message
import com.mobilellama.data.repository.InferenceRepository
import com.mobilellama.data.repository.MemoryManager
import com.mobilellama.data.repository.ModelRepository
import com.mobilellama.ai.AIBridge
import com.mobilellama.ai.IntentRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val inferenceRepository: InferenceRepository,
    private val modelRepository: ModelRepository,
    private val memoryManager: MemoryManager
) : ViewModel() {

    private val chatId: String = savedStateHandle["chatId"] ?: ""

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _currentAssistantMessage = MutableStateFlow("")
    val currentAssistantMessage: StateFlow<String> = _currentAssistantMessage.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentChat = MutableStateFlow<Chat?>(null)
    val currentChat: StateFlow<Chat?> = _currentChat.asStateFlow()

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()

    val inferenceState: StateFlow<InferenceState> = inferenceRepository.inferenceState

    private var generationJob: Job? = null
    private var paginationOffset = 0
    private val pageSize = 30

    companion object {
        private const val TAG = "ChatViewModel"
    }

    init {
        loadChat()
        loadInitialMessages()
        observeModelChanges()
    }

    private fun loadChat() {
        viewModelScope.launch(Dispatchers.IO) {
            val chat = chatDao.getChatById(chatId)
            _currentChat.value = chat
            Log.i(TAG, "Loaded chat: ${chat?.id}, title='${chat?.title}'")
        }
    }

    private fun loadInitialMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val initialMessages = messageDao.getMessagesPaged(chatId, pageSize, 0)
                    .reversed() // DB returns newest-first, UI needs oldest-first
                paginationOffset = initialMessages.size
                _messages.value = initialMessages
                Log.i(TAG, "Loaded ${initialMessages.size} initial messages")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load messages", e)
            }
        }
    }

    private fun observeModelChanges() {
        viewModelScope.launch {
            modelRepository.selectedModel.collect { model ->
                Log.i(TAG, "Selected model changed to: ${model.name}")
                stopGeneration()
                reloadModel()
            }
        }
    }

    private suspend fun reloadModel() {
        val selectedModel = modelRepository.selectedModel.value

        // Vision models should only be used via the Vision Camera screen
        if (selectedModel.promptType == com.mobilellama.data.model.PromptType.VISION) {
            Log.i(TAG, "Selected model '${selectedModel.name}' is a Vision model — skipping text chat initialization.")
            return
        }

        val modelPath = modelRepository.getModelPath()
        val file = java.io.File(modelPath)

        if (file.exists()) {
            Log.i(TAG, "Requesting model load for: $modelPath")
            inferenceRepository.initializeModel(modelPath)
            
            // Try to initialize HRM if available
            val hrmFile = java.io.File(context.filesDir, "hrm/hrm.onnx")
            if (hrmFile.exists() && AIBridge.loaded && !AIBridge.isHRMReady) {
                Log.i(TAG, "HRM not initialized yet. Initializing HRM Engine.")
                val hrmOk = AIBridge.initHRM(
                    modelPath = hrmFile.absolutePath,
                    inputDim = 64, hDim = 256, lDim = 128, outputDim = 64,
                    maxOuter = 20, maxInner = 10, haltThresh = 0.9f
                )
                Log.i(TAG, "HRM Engine initialization: \${if (hrmOk) \"SUCCESS\" else \"FAILED\"}")
            } else if (!hrmFile.exists() || !AIBridge.loaded) {
                Log.i(TAG, "No hrm.onnx found at \${hrmFile.absolutePath} or AIBridge not loaded.")
            } else {
                Log.i(TAG, "HRM Engine is already initialized.")
            }
        } else {
            Log.w(TAG, "Selected model file not found: $modelPath")
        }
    }

    fun sendMessage(userMessage: String) {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty()) {
            _errorMessage.value = "Message cannot be empty"
            return
        }

        // Prevent simultaneous generations
        if (_isGenerating.value) {
            Log.w(TAG, "Already generating, ignoring send")
            return
        }

        generationJob = viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()

                // Create and save user message
                val message = Message(
                    chatId = chatId,
                    role = "user",
                    content = trimmed,
                    timestamp = now,
                    tokenCount = trimmed.length / 4
                )

                val insertedId = withContext(Dispatchers.IO) {
                    val id = messageDao.insertMessage(message)
                    chatDao.updateLastMessageAt(chatId, now)
                    id
                }

                // Update UI with real DB id
                _messages.value = _messages.value + message.copy(id = insertedId)
                paginationOffset++

                // Generate assistant response
                generateAssistantResponse(trimmed)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _errorMessage.value = "Failed to save message: ${e.message}"
            }
        }
    }

    private suspend fun generateAssistantResponse(userMessage: String) {
        _isGenerating.value = true
        _currentAssistantMessage.value = ""

        try {
            val now = System.currentTimeMillis()
            val chat = chatDao.getChatById(chatId) ?: return

            // --- Intent Routing: Check if this is a reasoning task for HRM ---
            if (IntentRouter.shouldUseHRM(userMessage)) {
                Log.i(TAG, "Routing query to HRM Engine: \"$userMessage\"")
                
                // Encode input (simple character encoding placeholder)
                val encoded = FloatArray(64) { 0f }
                userMessage.forEachIndexed { i, c ->
                    if (i < 64) encoded[i] = c.code / 128.0f
                }
                
                // Run inference instantly on NPU
                val result = AIBridge.inferHRM(encoded)
                val diag = AIBridge.getHRMDiagnostics(encoded)
                
                if (result != null && result.isNotEmpty()) {
                    val maxVal = result.maxOrNull() ?: 0f
                    var maxIdx = 0
                    for (i in result.indices) {
                        if (result[i] == maxVal) {
                            maxIdx = i
                            break
                        }
                    }
                    
                    val diagText = diag?.let {
                        "\n\n[HRM Engine: \${it[0]} outer steps, \${it[1]} inner steps, confidence \${it[2] / 10.0f}%]"
                    } ?: ""
                    
                    val responseText = "HRM Analysis:\nClass $maxIdx identified with ${"%.2f".format(maxVal)} confidence.$diagText"
                    
                    val assistantMessage = Message(
                        chatId = chatId, role = "assistant", content = responseText, 
                        timestamp = now, tokenCount = responseText.length / 4
                    )
                    
                    val insertedId = withContext(Dispatchers.IO) {
                        val id = messageDao.insertMessage(assistantMessage)
                        chatDao.updateLastMessageAt(chatId, now)
                        id
                    }
                    
                    _messages.value = _messages.value + assistantMessage.copy(id = insertedId)
                    paginationOffset++
                    
                    if (chat.title.isBlank()) generateTitle(userMessage)
                    return
                } else {
                    Log.w(TAG, "HRM Engine failed to produce output. Falling back to llama.cpp.")
                }
            } else if (IntentRouter.shouldUseLayerStreaming()) {
                Log.i(TAG, "Routing query to Layer Streaming Engine (ONNX)")
                
                // Mock Tokenization (To be replaced with a real tokenizer like SentencePiece)
                val dummyTokens = IntArray(userMessage.length) { i -> userMessage[i].code }
                
                withContext(Dispatchers.IO) {
                    AIBridge.generateLLM(
                        inputTokens = dummyTokens,
                        maxNewTokens = 512,
                        temperature = 0.7f,
                        topP = 0.9f,
                        callback = object : AIBridge.TokenCallback {
                            override fun onToken(token: String, done: Boolean) {
                                viewModelScope.launch(Dispatchers.Main) {
                                    _currentAssistantMessage.value += token
                                }
                            }
                        }
                    )
                }
                
                saveAssistantResponse(chat, userMessage)
                return
            }

            // --- Language Task (or Layer Streaming Fallback): Use existing llama.cpp ---
            val contextMessages = memoryManager.buildContextForInference(chat)
            val fullPrompt = getPromptStr(contextMessages)

            Log.d(TAG, "Sending prompt to engine (${fullPrompt.length} chars)")

            val modelPath = modelRepository.getModelPath()
            com.mobilellama.native.InferenceMetrics.startSession(modelPath, "Q4_K_M")
            var isFirstToken = true
            var lastTokenTime = System.nanoTime()

            val result = inferenceRepository.generateResponse(fullPrompt) { token ->
                val nowNs = System.nanoTime()
                if (isFirstToken) {
                    com.mobilellama.native.InferenceMetrics.recordFirstToken()
                    isFirstToken = false
                } else {
                    com.mobilellama.native.InferenceMetrics.recordToken(nowNs - lastTokenTime)
                }
                lastTokenTime = nowNs

                viewModelScope.launch(Dispatchers.Main) {
                    _currentAssistantMessage.value += token
                }
            }

            if (result.isSuccess) {
                com.mobilellama.native.InferenceMetrics.endSession(context, modelPath)
                saveAssistantResponse(chat, userMessage)
            } else {
                val error = result.exceptionOrNull()
                _errorMessage.value = error?.message ?: "Generation failed"
                Log.e(TAG, "Generation failed", error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during generation", e)
            _errorMessage.value = "Inference failed: ${e.message}"
        } finally {
            _isGenerating.value = false
        }
    }

    private suspend fun saveAssistantResponse(chat: Chat, userMessage: String) {
        val responseText = _currentAssistantMessage.value

        val assistantMessage = Message(
            chatId = chatId, role = "assistant", content = responseText,
            timestamp = System.currentTimeMillis(), tokenCount = responseText.length / 4
        )

        val insertedId = withContext(Dispatchers.IO) {
            val id = messageDao.insertMessage(assistantMessage)
            chatDao.updateLastMessageAt(chatId, System.currentTimeMillis())
            id
        }

        _messages.value = _messages.value + assistantMessage.copy(id = insertedId)
        paginationOffset++
        _currentAssistantMessage.value = ""

        if (chat.title.isBlank()) {
            generateTitle(userMessage)
        }
    }

    private suspend fun generateTitle(firstMessage: String) {
        withContext(Dispatchers.IO) {
            try {
                val titlePrompt = buildString {
                    append("<|im_start|>system\n")
                    append("You are a title generator. Reply with ONLY a 4-word max title, no punctuation, no quotes.<|im_end|>\n")
                    append("<|im_start|>user\n")
                    append("Write a 4-word max title for a conversation starting with: \"$firstMessage\"<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                }

                val titleBuilder = StringBuilder()
                val result = inferenceRepository.generateResponse(titlePrompt) { token ->
                    titleBuilder.append(token)
                }

                if (result.isSuccess) {
                    val title = titleBuilder.toString().trim().take(50) // cap length
                    if (title.isNotBlank()) {
                        val chat = chatDao.getChatById(chatId)
                        chat?.let {
                            chatDao.upsert(it.copy(title = title))
                            _currentChat.value = it.copy(title = title)
                        }
                        Log.i(TAG, "Auto-generated title: $title")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate title", e)
            }
        }
    }

    fun loadOlderMessages() {
        if (_isLoadingOlder.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingOlder.value = true
            try {
                val olderMessages = messageDao.getMessagesPaged(chatId, pageSize, paginationOffset)
                    .reversed()
                if (olderMessages.isNotEmpty()) {
                    paginationOffset += olderMessages.size
                    _messages.value = olderMessages + _messages.value
                    Log.i(TAG, "Loaded ${olderMessages.size} older messages (offset=$paginationOffset)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load older messages", e)
            } finally {
                _isLoadingOlder.value = false
            }
        }
    }

    fun stopGeneration() {
        inferenceRepository.stopGeneration()
        _isGenerating.value = false

        // Save partial response if exists
        viewModelScope.launch {
            val partial = _currentAssistantMessage.value
            if (partial.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val assistantMessage = Message(
                    chatId = chatId,
                    role = "assistant",
                    content = "$partial [Interrupted]",
                    timestamp = now,
                    tokenCount = partial.length / 4
                )

                val insertedId = withContext(Dispatchers.IO) {
                    val id = messageDao.insertMessage(assistantMessage)
                    chatDao.updateLastMessageAt(chatId, now)
                    id
                }

                _messages.value = _messages.value + assistantMessage.copy(id = insertedId)
                paginationOffset++
                _currentAssistantMessage.value = ""
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        stopGeneration()
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    private fun getPromptStr(contextMessages: List<Message>): String {
        val model = modelRepository.selectedModel.value
        val sb = StringBuilder()

        val SYSTEM_PROMPT = "You are a helpful, concise AI assistant running offline on an Android device. Answer briefly."

        when (model.promptType) {
            com.mobilellama.data.model.PromptType.CHATML, com.mobilellama.data.model.PromptType.TINYLLAMA -> {
                sb.append("<|im_start|>system\n$SYSTEM_PROMPT<|im_end|>\n")
                contextMessages.forEach { msg ->
                    sb.append("<|im_start|>${msg.role}\n${msg.content}<|im_end|>\n")
                }
                sb.append("<|im_start|>assistant\n")
            }

            com.mobilellama.data.model.PromptType.PHI3 -> {
                sb.append("<|system|>\n$SYSTEM_PROMPT<|end|>\n")
                contextMessages.forEach { msg ->
                    sb.append("<|${msg.role}|>\n${msg.content}<|end|>\n")
                }
                sb.append("<|assistant|>\n")
            }

            com.mobilellama.data.model.PromptType.MISTRAL -> {
                sb.append("<s>[INST] System: $SYSTEM_PROMPT\n\n")
                contextMessages.forEach { msg ->
                    if (msg.role == "user") {
                        sb.append("${msg.content} [/INST]")
                    } else {
                        sb.append(" ${msg.content} </s>[INST] ")
                    }
                }
            }
            com.mobilellama.data.model.PromptType.VISION -> {
                sb.append("Vision mode does not use this text prompt generator.")
            }
        }
        return sb.toString()
    }
}
