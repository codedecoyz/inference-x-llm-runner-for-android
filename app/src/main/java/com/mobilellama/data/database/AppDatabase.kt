package com.mobilellama.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mobilellama.data.model.Chat
import com.mobilellama.data.model.Message

@Database(
    entities = [Message::class, Chat::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
}
