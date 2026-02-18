package com.mobilellama.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chatId"])]
)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String = "",
    val role: String,  // "user", "assistant", or "system"
    val content: String,
    val timestamp: Long,
    val tokenCount: Int = 0,
    val isCompressed: Boolean = false
)
