package com.example.awaassistant.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // --- CaptureRecord 操作 ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapture(record: CaptureRecord): Long

    @Delete
    suspend fun deleteCapture(record: CaptureRecord)

    @Update
    suspend fun updateCapture(record: CaptureRecord)

    @Query("SELECT * FROM capture_records WHERE id = :id")
    suspend fun getCaptureById(id: Long): CaptureRecord?

    @Query("SELECT * FROM capture_records ORDER BY timestamp DESC")
    fun getAllCapturesFlow(): Flow<List<CaptureRecord>>

    @Query("SELECT * FROM capture_records ORDER BY timestamp DESC")
    suspend fun getAllCaptures(): List<CaptureRecord>

    // FTS5 全文搜索：通过 MATCH 查询包含特定词的记录
    @Query("""
        SELECT capture_records.* 
        FROM capture_records 
        JOIN fts_capture_records ON capture_records.id = fts_capture_records.rowid 
        WHERE fts_capture_records MATCH :query
        ORDER BY capture_records.timestamp DESC
    """)
    suspend fun searchCaptures(query: String): List<CaptureRecord>

    // P1: 关联旧思绪 - 基于关键词搜索相关记录（排除当前记录）
    @Query("""
        SELECT capture_records.* 
        FROM capture_records 
        JOIN fts_capture_records ON capture_records.id = fts_capture_records.rowid 
        WHERE fts_capture_records MATCH :query
          AND capture_records.id != :excludeId
        ORDER BY capture_records.timestamp DESC
        LIMIT 5
    """)
    suspend fun searchRelated(excludeId: Long, query: String): List<CaptureRecord>

    // P1: 时光胶囊 - 获取指定时间窗口内的随机记录
    @Query("""
        SELECT * FROM capture_records 
        WHERE timestamp >= :fromTs 
          AND timestamp < :toTs
        ORDER BY RANDOM() LIMIT 1
    """)
    suspend fun getRandomRecordInRange(fromTs: Long, toTs: Long): CaptureRecord?

    // P3: 小组件 - 获取历史随机记录（排除最近一天）
    @Query("""
        SELECT * FROM capture_records 
        WHERE timestamp < :cutoff
        ORDER BY RANDOM() LIMIT 1
    """)
    suspend fun getRandomHistoricalRecord(cutoff: Long): CaptureRecord?

    // P2: 热力图统计 - 按日期和来源类型统计
    @Query("""
        SELECT date(timestamp/1000, 'unixepoch') as day, 
               sourceType, 
               COUNT(*) as cnt
        FROM capture_records
        WHERE timestamp > :fromTs
        GROUP BY day, sourceType
    """)
    suspend fun getActivityStats(fromTs: Long): List<DailySourceCount>

    // --- ReminderItem 操作 ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(item: ReminderItem): Long

    @Update
    suspend fun updateReminder(item: ReminderItem)

    @Delete
    suspend fun deleteReminder(item: ReminderItem)

    @Query("SELECT * FROM reminder_items WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderItem?

    @Query("SELECT * FROM reminder_items WHERE recordId = :recordId")
    suspend fun getRemindersForRecord(recordId: Long): List<ReminderItem>

    @Query("SELECT * FROM reminder_items WHERE isActive = 1 AND isTriggered = 0 ORDER BY reminderTime ASC")
    fun getActiveRemindersFlow(): Flow<List<ReminderItem>>

    @Query("SELECT * FROM reminder_items WHERE isActive = 1 AND isTriggered = 0 ORDER BY reminderTime ASC")
    suspend fun getActiveReminders(): List<ReminderItem>
}

// P2: 热力图统计数据结构
data class DailySourceCount(
    val day: String,
    val sourceType: String,
    val cnt: Int
)
