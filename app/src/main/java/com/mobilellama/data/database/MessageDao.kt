package com.mobilellama.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mobilellama.data.model.Message

@Dao
interface MessageDao {
    @Insert
    suspend fun insertMessage(message: Message): Long

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getAllMessages(conversationId: Long = 0): List<Message>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteAllMessages(conversationId: Long = 0)
}
