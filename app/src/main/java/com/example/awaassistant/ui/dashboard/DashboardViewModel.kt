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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardViewModel(context: Context) : ViewModel() {

    private val db = AppDatabase.getDatabase(context.applicationContext)
    private val dao = db.appDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // 屏幕捕获历史记录 Flow（集成实时搜索过滤逻辑）
    val captureRecords: StateFlow<List<CaptureRecord>> = combine(
        dao.getAllCapturesFlow(),
        _searchQuery
    ) { records, query ->
        if (query.isBlank()) {
            records
        } else {
            records.filter { record ->
                record.title.contains(query, ignoreCase = true) ||
                record.summary.contains(query, ignoreCase = true) ||
                record.rawContent.contains(query, ignoreCase = true) ||
                record.tags.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 活跃状态的提醒 Flow
    val activeReminders: StateFlow<List<ReminderItem>> = dao.getActiveRemindersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _processingType = MutableStateFlow<String?>(null)
    val processingType: StateFlow<String?> = _processingType.asStateFlow()

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
            _processingType.value = "PHOTO"
            _isProcessingPhoto.value = true
            Toast.makeText(context, "开始识别笔记，请稍后...", Toast.LENGTH_SHORT).show()
            try {
                // 1. 本地 OCR 提取中英文文字
                val ocrText = LocalOcrHelper.recognizeText(context, photoUri)
                if (ocrText.trim().isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "未能从照片中提取到任何文字，请重试", Toast.LENGTH_LONG).show()
                    }
                    _processingType.value = null
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
                _processingType.value = null
                _isProcessingPhoto.value = false
            }
        }
    }

    /**
     * 处理拍摄的食物卡路里照片
     */
    fun processCaloriePhoto(context: Context, photoUri: Uri, imageFile: File) {
        viewModelScope.launch {
            _processingType.value = "CALORIE"
            _isProcessingPhoto.value = true
            Toast.makeText(context, "开始识别食物并估算卡路里，请稍后...", Toast.LENGTH_SHORT).show()
            try {
                // 1. 本地 OCR 提取中英文文字作为辅助文本
                val ocrText = try {
                    LocalOcrHelper.recognizeText(context, photoUri)
                } catch (e: Exception) {
                    ""
                }

                // 2. 本地图像分类识别 (离线模型)
                val imageLabels = try {
                    com.example.awaassistant.util.LocalImageLabelingHelper.labelImage(context, photoUri)
                } catch (e: Exception) {
                    emptyList()
                }

                // 3. 调用大模型分析卡路里 (离线标签 + 文本大模型模式)
                val result = OpenAiCompatibleClient.analyzeCalorieImage(context, imageFile, ocrText, imageLabels)

                val title: String
                val summary: String
                val tags: String

                if (result != null) {
                    title = result.title
                    summary = result.summary
                    tags = result.tags.joinToString(",")
                } else {
                    title = "卡路里记录 (${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())})"
                    summary = "（AI 分析失败，无法估算卡路里。请检查网络连接或 API 配置）" + if (ocrText.isNotEmpty()) "\n\n提取到的文字信息：\n$ocrText" else ""
                    tags = "健康记录,卡路里"
                }

                // 3. 将拍摄临时文件移动到永久存储目录
                val permanentFile = saveImageToPermanentStorage(context, imageFile)
                
                val record = CaptureRecord(
                    title = title,
                    summary = summary,
                    rawContent = ocrText.ifEmpty { "食物图片" },
                    imagePath = permanentFile.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    tags = tags,
                    sourceType = "CALORIE"
                )

                // 4. 插入数据库
                dao.insertCapture(record)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "卡路里分析成功: $title", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("DashboardVM", "Error processing calorie photo", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "估算卡路里出错: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _processingType.value = null
                _isProcessingPhoto.value = false
            }
        }
    }

    /**
     * 从相册选择并处理卡路里照片
     */
    fun processCalorieGalleryPhoto(context: Context, photoUri: Uri) {
        viewModelScope.launch {
            _processingType.value = "CALORIE"
            _isProcessingPhoto.value = true
            Toast.makeText(context, "正在读取相册图片，请稍后...", Toast.LENGTH_SHORT).show()
            try {
                // 1. 将相册图片拷贝到临时文件或者直接保存到永久存储
                val tempFile = File(context.cacheDir, "gallery_temp_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(photoUri)?.use { inputStream ->
                        tempFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }

                // 2. 本地 OCR
                val ocrText = try {
                    LocalOcrHelper.recognizeText(context, photoUri)
                } catch (e: Exception) {
                    ""
                }

                // 3. 本地图像分类识别 (离线模型)
                val imageLabels = try {
                    com.example.awaassistant.util.LocalImageLabelingHelper.labelImage(context, photoUri)
                } catch (e: Exception) {
                    emptyList()
                }

                // 4. AI 分析
                val result = OpenAiCompatibleClient.analyzeCalorieImage(context, tempFile, ocrText, imageLabels)

                val title: String
                val summary: String
                val tags: String

                if (result != null) {
                    title = result.title
                    summary = result.summary
                    tags = result.tags.joinToString(",")
                } else {
                    title = "卡路里记录 (${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())})"
                    summary = "（AI 分析失败，无法估算卡路里。请检查网络连接或 API 配置）" + if (ocrText.isNotEmpty()) "\n\n提取到的文字信息：\n$ocrText" else ""
                    tags = "健康记录,卡路里"
                }

                // 4. 将临时文件移入永久存储
                val permanentFile = saveImageToPermanentStorage(context, tempFile)

                val record = CaptureRecord(
                    title = title,
                    summary = summary,
                    rawContent = ocrText.ifEmpty { "食物图片" },
                    imagePath = permanentFile.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    tags = tags,
                    sourceType = "CALORIE"
                )

                dao.insertCapture(record)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "卡路里分析成功: $title", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error processing gallery calorie photo", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "处理相册图片出错: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _processingType.value = null
                _isProcessingPhoto.value = false
            }
        }
    }


    /** 快捷保存便签（无需传入 Context） */
    fun saveQuickNote(noteText: String) {
        viewModelScope.launch {
            if (noteText.trim().isEmpty()) return@launch
            val firstLine = noteText.trim().split("\n").firstOrNull() ?: ""
            val cleanTitle = if (firstLine.length > 15) firstLine.take(15) + "..." else firstLine.ifEmpty { "快捷便签" }
            
            val inlineTags = com.example.awaassistant.util.TagHelper.extractInlineTags(noteText)
            val finalTags = if (inlineTags.isEmpty()) "快速录入" else inlineTags.distinct().joinToString(",")

            val record = CaptureRecord(
                title = cleanTitle,
                summary = noteText,
                rawContent = noteText,
                imagePath = null,
                timestamp = System.currentTimeMillis(),
                tags = finalTags,
                sourceType = "TEXT"
            )
            dao.insertCapture(record)
        }
    }

    /**
     * 添加新的文本便签并保存至数据库
     */
    fun addNewNote(context: Context, noteText: String) {
        viewModelScope.launch {
            if (noteText.trim().isEmpty()) return@launch
            try {
                // 提取首行作为便签的默认标题（最多15个字）
                val firstLine = noteText.trim().split("\n").firstOrNull() ?: ""
                val cleanTitle = if (firstLine.length > 15) firstLine.take(15) + "..." else firstLine.ifEmpty { "快捷便签" }

                val inlineTags = com.example.awaassistant.util.TagHelper.extractInlineTags(noteText)
                val finalTags = (listOf("便签") + inlineTags).distinct().joinToString(",")

                val newRecord = CaptureRecord(
                    title = cleanTitle,
                    summary = noteText,
                    rawContent = noteText,
                    imagePath = null,
                    timestamp = System.currentTimeMillis(),
                    tags = finalTags,
                    sourceType = "TEXT"
                )
                dao.insertCapture(newRecord)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "便签已保存", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error saving note", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存便签失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 切换便签/待办记录的完成状态
     */
    fun toggleRecordCompletion(context: Context, record: CaptureRecord) {
        viewModelScope.launch {
            try {
                val newStatus = !record.isCompleted
                val updatedRecord = record.copy(isCompleted = newStatus)
                dao.updateCapture(updatedRecord)
                
                val message = if (newStatus) "便签已完成" else "便签已重置为未完成"
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Failed to toggle completion status", e)
            }
        }
    }

    /**
     * 处理拍摄的配菜谱照片
     */
    fun processRecipePhoto(context: Context, photoUri: Uri, imageFile: File) {
        viewModelScope.launch {
            _processingType.value = "RECIPE"
            _isProcessingPhoto.value = true
            Toast.makeText(context, "开始识别食材并搭配菜谱，请稍后...", Toast.LENGTH_SHORT).show()
            try {
                // 1. 本地 OCR 提取中英文文字作为辅助
                val ocrText = try {
                    LocalOcrHelper.recognizeText(context, photoUri)
                } catch (e: Exception) {
                    ""
                }

                // 2. 调用大模型分析菜谱
                val result = OpenAiCompatibleClient.analyzeRecipeImage(context, imageFile, ocrText)

                val title: String
                val summary: String
                val tags: String

                if (result != null) {
                    title = result.title
                    summary = result.summary
                    tags = result.tags.joinToString(",")
                } else {
                    title = "推荐菜谱 (${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())})"
                    summary = "（AI 分析失败，无法生成菜谱。请检查网络配置或尝试使用支持 Vision 的大模型）" + if (ocrText.isNotEmpty()) "\n\n提取到的文字信息：\n$ocrText" else ""
                    tags = "配菜谱,美食"
                }

                // 3. 将拍摄临时文件移动到永久存储目录
                val permanentFile = saveImageToPermanentStorage(context, imageFile)
                
                val record = CaptureRecord(
                    title = title,
                    summary = summary,
                    rawContent = ocrText.ifEmpty { "食材图片" },
                    imagePath = permanentFile.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    tags = tags,
                    sourceType = "RECIPE"
                )

                // 4. 插入数据库
                dao.insertCapture(record)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "菜谱搭配成功: $title", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("DashboardVM", "Error processing recipe photo", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "搭配菜谱出错: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _processingType.value = null
                _isProcessingPhoto.value = false
            }
        }
    }

    /**
     * 从相册选择并处理配菜谱照片
     */
    fun processRecipeGalleryPhoto(context: Context, photoUri: Uri) {
        viewModelScope.launch {
            _processingType.value = "RECIPE"
            _isProcessingPhoto.value = true
            Toast.makeText(context, "正在读取相册图片，请稍后...", Toast.LENGTH_SHORT).show()
            try {
                // 1. 拷贝到临时文件
                val tempFile = File(context.cacheDir, "gallery_temp_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(photoUri)?.use { inputStream ->
                        tempFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }

                // 2. 本地 OCR
                val ocrText = try {
                    LocalOcrHelper.recognizeText(context, photoUri)
                } catch (e: Exception) {
                    ""
                }

                // 3. AI 分析
                val result = OpenAiCompatibleClient.analyzeRecipeImage(context, tempFile, ocrText)

                val title: String
                val summary: String
                val tags: String

                if (result != null) {
                    title = result.title
                    summary = result.summary
                    tags = result.tags.joinToString(",")
                } else {
                    title = "推荐菜谱 (${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())})"
                    summary = "（AI 分析失败，无法生成菜谱。请检查网络配置或尝试使用支持 Vision 的大模型）" + if (ocrText.isNotEmpty()) "\n\n提取到的文字信息：\n$ocrText" else ""
                    tags = "配菜谱,美食"
                }

                // 4. 移动到永久存储
                val permanentFile = saveImageToPermanentStorage(context, tempFile)

                val record = CaptureRecord(
                    title = title,
                    summary = summary,
                    rawContent = ocrText.ifEmpty { "食材图片" },
                    imagePath = permanentFile.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    tags = tags,
                    sourceType = "RECIPE"
                )

                dao.insertCapture(record)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "菜谱搭配成功: $title", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error processing gallery recipe photo", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "处理相册图片出错: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _processingType.value = null
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
