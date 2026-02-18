package com.mobilellama.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val modelId: String,
    val title: String = "",
    val summary: String = "",
    val summaryUpToMessageId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessageAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)
