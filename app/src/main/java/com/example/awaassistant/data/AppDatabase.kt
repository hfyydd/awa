package com.example.awaassistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CaptureRecord::class, FtsCaptureRecord::class, ReminderItem::class, ChatSession::class, ChatMessageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "awa_assistant_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                
                // 监听 capture_records 表的变化，实时自动更新桌面小组件
                instance.invalidationTracker.addObserver(
                    object : androidx.room.InvalidationTracker.Observer("capture_records") {
                        override fun onInvalidated(tables: Set<String>) {
                            com.example.awaassistant.widget.MemoryWidget.triggerRefresh(context.applicationContext)
                        }
                    }
                )
                
                INSTANCE = instance
                instance
            }
        }
    }
}
