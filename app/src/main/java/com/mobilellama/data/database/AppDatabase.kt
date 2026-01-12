package com.mobilellama.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mobilellama.data.model.Message

@Database(entities = [Message::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
