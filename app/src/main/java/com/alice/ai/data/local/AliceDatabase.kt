package com.alice.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatSessionEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AliceDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var instance: AliceDatabase? = null

        fun getInstance(context: Context): AliceDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AliceDatabase::class.java,
                    "alice_chat.db"
                ).fallbackToDestructiveMigration().build().also { db ->
                    instance = db
                }
            }
        }
    }
}
