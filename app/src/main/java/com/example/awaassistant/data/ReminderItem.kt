package com.example.awaassistant.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_items",
    foreignKeys = [
        ForeignKey(
            entity = CaptureRecord::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["recordId"])]
)
data class ReminderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val title: String,
    val reminderTime: Long, // 提醒的时间戳（毫秒）
    val isTriggered: Boolean = false,
    val isActive: Boolean = true
)
