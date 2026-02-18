package com.mobilellama.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.mobilellama.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // Legacy — kept for backward compat during migration
    @Insert
    suspend fun insertMessage(message: Message): Long

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getAllMessages(chatId: String = ""): List<Message>

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessages(chatId: String = "")

    // New multi-chat queries

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isCompressed = 0 ORDER BY timestamp ASC")
    fun getActiveMessages(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isCompressed = 0 ORDER BY timestamp ASC")
    suspend fun getActiveMessagesList(chatId: String): List<Message>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPaged(chatId: String, limit: Int, offset: Int): List<Message>

    @Query("SELECT COALESCE(SUM(tokenCount), 0) FROM messages WHERE chatId = :chatId AND isCompressed = 0")
    suspend fun getActiveTokenCount(chatId: String): Int

    @Query("UPDATE messages SET isCompressed = 1 WHERE id IN (:ids)")
    suspend fun markAsCompressed(ids: List<Long>)

    @Upsert
    suspend fun upsert(message: Message)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllForChat(chatId: String)
}
