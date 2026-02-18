package com.mobilellama.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilellama.data.database.ChatDao
import com.mobilellama.data.model.Chat
import com.mobilellama.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _currentModelId = MutableStateFlow(modelRepository.selectedModel.value.name)
    val currentModelId: StateFlow<String> = _currentModelId.asStateFlow()

    val chats: Flow<List<Chat>> = _currentModelId.flatMapLatest { modelId ->
        chatDao.getChatsForModel(modelId)
    }

    companion object {
        private const val TAG = "ChatListViewModel"
    }

    init {
        observeModelChanges()
    }

    private fun observeModelChanges() {
        viewModelScope.launch {
            modelRepository.selectedModel.collect { model ->
                _currentModelId.value = model.name
                Log.i(TAG, "Model changed, showing chats for: ${model.name}")
            }
        }
    }

    fun createNewChat(): String {
        val modelId = _currentModelId.value
        val chat = Chat(
            id = UUID.randomUUID().toString(),
            modelId = modelId,
            createdAt = System.currentTimeMillis(),
            lastMessageAt = System.currentTimeMillis()
        )

        viewModelScope.launch(Dispatchers.IO) {
            chatDao.upsert(chat)
            Log.i(TAG, "Created new chat: ${chat.id} for model: $modelId")
        }

        return chat.id
    }

    fun deleteChat(chat: Chat) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.delete(chat)
            Log.i(TAG, "Deleted chat: ${chat.id}")
        }
    }

    fun togglePin(chat: Chat) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.upsert(chat.copy(isPinned = !chat.isPinned))
            Log.i(TAG, "Toggled pin for chat ${chat.id}: ${!chat.isPinned}")
        }
    }
}
