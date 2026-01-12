package com.mobilellama.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilellama.data.database.MessageDao
import com.mobilellama.data.model.InferenceState
import com.mobilellama.data.model.Message
import com.mobilellama.data.repository.InferenceRepository
import com.mobilellama.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val inferenceRepository: InferenceRepository,
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _currentAssistantMessage = MutableStateFlow("")
    val currentAssistantMessage: StateFlow<String> = _currentAssistantMessage.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val inferenceState: StateFlow<InferenceState> = inferenceRepository.inferenceState

    companion object {
        private const val TAG = "ChatViewModel"
    }

    init {
        loadMessages()
        initializeModelIfNeeded()
    }

    private fun loadMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loadedMessages = messageDao.getAllMessages()
                _messages.value = loadedMessages
                Log.i(TAG, "Loaded ${loadedMessages.size} messages")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load messages", e)
            }
        }
    }

    private fun initializeModelIfNeeded() {
        viewModelScope.launch {
            if (inferenceRepository.inferenceState.value is InferenceState.Uninitialized) {
                val modelPath = modelRepository.getModelPath()
                Log.i(TAG, "Initializing model from: $modelPath")
                inferenceRepository.initializeModel(modelPath)
            }
        }
    }

    fun sendMessage(userMessage: String) {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty()) {
            _errorMessage.value = "Message cannot be empty"
            return
        }

        viewModelScope.launch {
            try {
                // Create and save user message
                val message = Message(
                    role = "user",
                    content = trimmed,
                    timestamp = System.currentTimeMillis()
                )

                // Save to database
                withContext(Dispatchers.IO) {
                    messageDao.insertMessage(message)
                }

                // Update UI optimistically
                _messages.value = _messages.value + message

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
            val result = inferenceRepository.generateResponse(userMessage) { token ->
                withContext(Dispatchers.Main) {
                    _currentAssistantMessage.value += token
                }
            }

            if (result.isSuccess) {
                // Save complete assistant message
                val assistantMessage = Message(
                    role = "assistant",
                    content = _currentAssistantMessage.value,
                    timestamp = System.currentTimeMillis()
                )

                withContext(Dispatchers.IO) {
                    messageDao.insertMessage(assistantMessage)
                }

                _messages.value = _messages.value + assistantMessage
                _currentAssistantMessage.value = ""
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

    fun stopGeneration() {
        inferenceRepository.stopGeneration()
        _isGenerating.value = false

        // Save partial response if exists
        viewModelScope.launch {
            val partial = _currentAssistantMessage.value
            if (partial.isNotEmpty()) {
                val assistantMessage = Message(
                    role = "assistant",
                    content = "$partial [Interrupted]",
                    timestamp = System.currentTimeMillis()
                )

                withContext(Dispatchers.IO) {
                    messageDao.insertMessage(assistantMessage)
                }

                _messages.value = _messages.value + assistantMessage
                _currentAssistantMessage.value = ""
            }
        }
    }

    fun clearMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                messageDao.deleteAllMessages()
                _messages.value = emptyList()
                Log.i(TAG, "Messages cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear messages", e)
                _errorMessage.value = "Failed to clear messages: ${e.message}"
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
