package com.mobilellama.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,  // "user" or "assistant"
    val content: String,
    val timestamp: Long,
    val conversationId: Long = 0  // Always 0 for single conversation MVP
)
