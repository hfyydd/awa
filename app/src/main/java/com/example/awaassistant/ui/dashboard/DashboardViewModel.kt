package com.example.awaassistant.ui.dashboard

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.awaassistant.data.AppDatabase
import com.example.awaassistant.data.CaptureRecord
import com.example.awaassistant.data.OpenAiCompatibleClient
import com.example.awaassistant.data.ReminderItem
import com.example.awaassistant.service.AwaAccessibilityService
import com.example.awaassistant.util.LocalOcrHelper
import com.example.awaassistant.util.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardViewModel(context: Context) : ViewModel() {

    private val db = AppDatabase.getDatabase(context.applicationContext)
    private val dao = db.appDao()

    // 屏幕捕获历史记录 Flow
    val captureRecords: StateFlow<List<CaptureRecord>> = dao.getAllCapturesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 活跃状态的提醒 Flow
    val activeReminders: StateFlow<List<ReminderItem>> = dao.getActiveRemindersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isProcessingPhoto = MutableStateFlow(false)
    val isProcessingPhoto: StateFlow<Boolean> = _isProcessingPhoto.asStateFlow()

    private val _isAccessibilityActive = MutableStateFlow(false)
    val isAccessibilityActive: StateFlow<Boolean> = _isAccessibilityActive.asStateFlow()

    init {
        checkAccessibilityStatus()
    }

    fun checkAccessibilityStatus() {
        _isAccessibilityActive.value = AwaAccessibilityService.isServiceRunning
    }

    /**
     * 删除记录（并取消与之关联的所有未触发闹钟）
     */
    fun deleteRecord(context: Context, record: CaptureRecord) {
        viewModelScope.launch {
            val reminders = dao.getRemindersForRecord(record.id)
            reminders.forEach { reminder ->
                ReminderScheduler.cancelReminder(context, reminder)
            }
            dao.deleteCapture(record)
            
            // 删除本地关联的图片文件
            record.imagePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            Toast.makeText(context, "记录已删除", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 处理拍摄的笔记照片
     */
    fun processPhotoNote(context: Context, photoUri: Uri, imageFile: File) {
        viewModelScope.launch {
            _isProcessingPhoto.value = true
            Toast.makeText(context, "开始识别笔记，请稍后...", Toast.LENGTH_SHORT).show()
            try {
                // 1. 本地 OCR 提取中英文文字
                val ocrText = LocalOcrHelper.recognizeText(context, photoUri)
                if (ocrText.trim().isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "未能从照片中提取到任何文字，请重试", Toast.LENGTH_LONG).show()
                    }
                    _isProcessingPhoto.value = false
                    return@launch
                }

                // 2. 调用大模型提炼总结
                val result = OpenAiCompatibleClient.analyzeText(context, ocrText)

                val title: String
                val summary: String
                val tags: String

                if (result != null) {
                    title = result.title
                    summary = result.summary
                    tags = result.tags.joinToString(",")
                } else {
                    title = "手拍笔记 (${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())})"
                    summary = "（AI 分析失败，展示本地 OCR 文字）\n\n$ocrText"
                    tags = "手拍笔记"
                }

                // 3. 将拍摄临时文件移动到永久存储目录
                val permanentFile = saveImageToPermanentStorage(context, imageFile)
                
                val record = CaptureRecord(
                    title = title,
                    summary = summary,
                    rawContent = ocrText,
                    imagePath = permanentFile.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    tags = tags,
                    sourceType = "PHOTO"
                )

                // 4. 插入数据库并注册闹钟
                val recordId = dao.insertCapture(record)

                if (result != null && result.reminders.isNotEmpty()) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    for (suggestion in result.reminders) {
                        try {
                            val parsedTime = sdf.parse(suggestion.timeString)
                            if (parsedTime != null && parsedTime.time > System.currentTimeMillis()) {
                                val reminder = ReminderItem(
                                    recordId = recordId,
                                    title = suggestion.title,
                                    reminderTime = parsedTime.time,
                                    isActive = true,
                                    isTriggered = false
                                )
                                val reminderId = dao.insertReminder(reminder)
                                ReminderScheduler.scheduleReminder(context, reminder.copy(id = reminderId))
                            }
                        } catch (e: Exception) {
                            Log.e("DashboardVM", "Failed to parse photo reminder: ${suggestion.timeString}", e)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "笔记分析整理成功: $title", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("DashboardVM", "Error processing photo note", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "识别笔记出错: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isProcessingPhoto.value = false
            }
        }
    }

    /**
     * 添加新的文本便签/待办并进行 AI 分析
     */
    fun addNewNote(context: Context, noteText: String) {
        viewModelScope.launch {
            if (noteText.trim().isEmpty()) return@launch
            _isProcessingPhoto.value = true
            Toast.makeText(context, "开始整理便签，请稍后...", Toast.LENGTH_SHORT).show()
            try {
                // 1. 预先插入一条记录，让用户能立刻看到“处理中”状态的便签
                val initialRecord = CaptureRecord(
                    title = "便签整理中...",
                    summary = "（AI 正在处理便签...）\n\n$noteText",
                    rawContent = noteText,
                    imagePath = null,
                    timestamp = System.currentTimeMillis(),
                    tags = "便签,处理中",
                    sourceType = "TEXT"
                )
                val recordId = dao.insertCapture(initialRecord)

                // 2. 后台调用 AI 进行分析
                val result = OpenAiCompatibleClient.analyzeText(context, noteText)

                val title: String
                val summary: String
                val tags: String

                if (result != null) {
                    title = result.title
                    summary = result.summary
                    tags = result.tags.joinToString(",")
                } else {
                    title = "便签 (${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())})"
                    summary = "（AI 分析失败，展示原始文字）\n\n$noteText"
                    tags = "便签"
                }

                // 3. 更新数据库记录
                val updatedRecord = CaptureRecord(
                    id = recordId,
                    title = title,
                    summary = summary,
                    rawContent = noteText,
                    imagePath = null,
                    timestamp = System.currentTimeMillis(),
                    tags = tags,
                    sourceType = "TEXT"
                )
                dao.updateCapture(updatedRecord)

                // 4. 解析并注册闹钟
                if (result != null && result.reminders.isNotEmpty()) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    for (suggestion in result.reminders) {
                        try {
                            val parsedTime = sdf.parse(suggestion.timeString)
                            if (parsedTime != null && parsedTime.time > System.currentTimeMillis()) {
                                val reminder = ReminderItem(
                                    recordId = recordId,
                                    title = suggestion.title,
                                    reminderTime = parsedTime.time,
                                    isActive = true,
                                    isTriggered = false
                                )
                                val reminderId = dao.insertReminder(reminder)
                                ReminderScheduler.scheduleReminder(context, reminder.copy(id = reminderId))
                            }
                        } catch (e: Exception) {
                            Log.e("DashboardVM", "Failed to parse note reminder: ${suggestion.timeString}", e)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "便签整理成功: $title", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("DashboardVM", "Error processing note", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "整理便签出错: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isProcessingPhoto.value = false
            }
        }
    }

    private suspend fun saveImageToPermanentStorage(context: Context, tempFile: File): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "photos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val permanentFile = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        tempFile.renameTo(permanentFile)
        permanentFile
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(context) as T
        }
    }
}
