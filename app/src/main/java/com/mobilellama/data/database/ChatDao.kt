package com.mobilellama.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.mobilellama.data.model.Chat
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats WHERE modelId = :modelId ORDER BY isPinned DESC, lastMessageAt DESC")
    fun getChatsForModel(modelId: String): Flow<List<Chat>>

    @Upsert
    suspend fun upsert(chat: Chat)

    @Delete
    suspend fun delete(chat: Chat)

    @Query("UPDATE chats SET lastMessageAt = :time WHERE id = :chatId")
    suspend fun updateLastMessageAt(chatId: String, time: Long)

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): Chat?

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    fun observeChat(chatId: String): Flow<Chat?>
}
