package com.example.awaassistant.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "capture_records")
data class CaptureRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val summary: String,
    val rawContent: String,
    val imagePath: String?,
    val timestamp: Long,
    val tags: String, // 逗号分隔的标签字符串，例如 "工作,笔记,会议"
    val sourceType: String, // "SCREENSHOT" (截屏), "PHOTO" (拍照), "TEXT" (直接提取文字)
    val isCompleted: Boolean = false
)

@Fts4(contentEntity = CaptureRecord::class)
@Entity(tableName = "fts_capture_records")
data class FtsCaptureRecord(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Int,
    val title: String,
    val summary: String,
    val rawContent: String,
    val tags: String
)
