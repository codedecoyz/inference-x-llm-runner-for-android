package com.mobilellama.data.repository

import android.util.Log
import com.mobilellama.data.database.ChatDao
import com.mobilellama.data.database.MessageDao
import com.mobilellama.data.model.Chat
import com.mobilellama.data.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val inferenceRepository: InferenceRepository
) {
    companion object {
        private const val TAG = "MemoryManager"
        private const val CONTEXT_SIZE = 2048
        private const val COMPRESSION_THRESHOLD = 0.75
        private const val TARGET_RATIO = 0.35
        private const val MAX_SUMMARY_TOKENS = 300
    }

    /**
     * Build the list of messages to use for inference, handling memory compression
     * if the active token count is approaching the context window limit.
     */
    suspend fun buildContextForInference(chat: Chat): List<Message> = withContext(Dispatchers.IO) {
        val activeMessages = messageDao.getActiveMessagesList(chat.id)
        val activeTokenCount = messageDao.getActiveTokenCount(chat.id)

        Log.i(TAG, "Chat ${chat.id}: activeTokenCount=$activeTokenCount, contextSize=$CONTEXT_SIZE, threshold=${(CONTEXT_SIZE * COMPRESSION_THRESHOLD).toInt()}")

        if (activeTokenCount < (CONTEXT_SIZE * COMPRESSION_THRESHOLD).toInt()) {
            // No compression needed — prepend summary if available
            return@withContext prependSummary(chat, activeMessages)
        }

        // Compression needed
        Log.i(TAG, "Compressing old messages for chat ${chat.id}")
        try {
            compressMessages(chat, activeMessages)
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed, using raw messages", e)
        }

        // Fetch fresh active messages after compression
        val freshMessages = messageDao.getActiveMessagesList(chat.id)
        val updatedChat = chatDao.getChatById(chat.id) ?: chat
        return@withContext prependSummary(updatedChat, freshMessages)
    }

    private fun prependSummary(chat: Chat, messages: List<Message>): List<Message> {
        if (chat.summary.isBlank()) return messages
        val summaryMessage = Message(
            chatId = chat.id,
            role = "system",
            content = "Previous conversation summary: ${chat.summary}",
            timestamp = 0L,
            tokenCount = chat.summary.length / 4
        )
        return listOf(summaryMessage) + messages
    }

    private suspend fun compressMessages(chat: Chat, activeMessages: List<Message>) {
        if (activeMessages.size <= 2) return // nothing meaningful to compress

        val targetTokens = (CONTEXT_SIZE * TARGET_RATIO).toInt()

        // Walk from oldest, accumulate token count of messages to compress
        // until remaining messages fit within target window
        var accumulatedTokens = 0
        val totalTokens = activeMessages.sumOf { it.tokenCount }
        val messagesToCompress = mutableListOf<Message>()

        for (msg in activeMessages) {
            val remaining = totalTokens - accumulatedTokens - msg.tokenCount
            if (remaining <= targetTokens) break
            messagesToCompress.add(msg)
            accumulatedTokens += msg.tokenCount
        }

        if (messagesToCompress.isEmpty()) return

        Log.i(TAG, "Compressing ${messagesToCompress.size} messages (${accumulatedTokens} tokens)")

        // Build summarization prompt
        val existingSummary = if (chat.summary.isNotBlank()) {
            "Previous summary: ${chat.summary}\n\n"
        } else ""

        val messagesText = messagesToCompress.joinToString("\n") { "${it.role}: ${it.content}" }

        val summarizationPrompt = buildString {
            append("<|im_start|>system\n")
            append("You are a summarization assistant. Write a concise third-person factual summary ")
            append("capturing key facts, user preferences, decisions made, and important context. ")
            append("Max 300 tokens. Do not add opinions or speculation.<|im_end|>\n")
            append("<|im_start|>user\n")
            append(existingSummary)
            append("Conversation to summarize:\n")
            append(messagesText)
            append("\n\nWrite a concise summary:<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }

        // Run summarization via inference
        val summaryBuilder = StringBuilder()
        val result = inferenceRepository.generateResponse(summarizationPrompt) { token ->
            summaryBuilder.append(token)
        }

        if (result.isFailure) {
            Log.e(TAG, "Summarization inference failed: ${result.exceptionOrNull()?.message}")
            return
        }

        val newSummary = summaryBuilder.toString().trim()
        if (newSummary.isBlank()) {
            Log.w(TAG, "Summarization returned empty result, skipping compression")
            return
        }

        Log.i(TAG, "Generated summary (${newSummary.length} chars): ${newSummary.take(100)}...")

        // Mark old messages as compressed
        val idsToCompress = messagesToCompress.map { it.id }
        messageDao.markAsCompressed(idsToCompress)

        // Update chat with new summary
        val lastCompressedMsg = messagesToCompress.last()
        chatDao.upsert(
            chat.copy(
                summary = newSummary,
                summaryUpToMessageId = lastCompressedMsg.id
            )
        )
    }
}
