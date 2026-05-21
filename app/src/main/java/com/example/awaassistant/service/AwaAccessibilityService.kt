package com.example.awaassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.awaassistant.data.AppDatabase
import com.example.awaassistant.data.CaptureRecord
import com.example.awaassistant.data.OpenAiCompatibleClient
import com.example.awaassistant.data.ReminderItem
import com.example.awaassistant.util.ChineseConverter
import com.example.awaassistant.util.LocalOcrHelper
import com.example.awaassistant.util.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AwaAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var lastVolumeDownTime = 0L
    private var isVolumeDownPressed = false
    private var isVolumeUpPressed = false
    private var screenshotObserver: android.database.ContentObserver? = null
    private val processedScreenshots = mutableMapOf<String, Long>()

    companion object {
        private const val TAG = "AwaAccessibility"
        
        @Volatile
        var instance: AwaAccessibilityService? = null
            private set
            
        val isServiceRunning: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AwaAccessibilityService Connected")
        instance = this
        Toast.makeText(this, "Awa 助手辅助功能已连接", Toast.LENGTH_SHORT).show()
        registerScreenshotObserver()
        
        // Automatically start FloatingOverlayService if overlay permission is granted and floating ball is enabled
        if (android.provider.Settings.canDrawOverlays(this) &&
            com.example.awaassistant.data.SettingsManager.isFloatingBallEnabled(this)) {
            FloatingOverlayService.start(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 此处不需要处理实时滚动或焦点事件，我们只在用户点击截屏时主动触发分析
    }

    override fun onInterrupt() {
        Log.d(TAG, "AwaAccessibilityService Interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "AwaAccessibilityService Unbound")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenshotObserver()
        serviceScope.cancel()
        instance = null
    }

    override fun onKeyEvent(event: android.view.KeyEvent?): Boolean {
        if (event == null) return false
        
        if (!com.example.awaassistant.data.SettingsManager.isVolumeShortcutEnabled(this)) {
            return super.onKeyEvent(event)
        }

        val keyCode = event.keyCode
        val action = event.action

        // 1. 组合按键触发 (音量上 + 音量下同时处于按下状态)
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            isVolumeDownPressed = (action == android.view.KeyEvent.ACTION_DOWN)
        } else if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            isVolumeUpPressed = (action == android.view.KeyEvent.ACTION_DOWN)
        }

        if (isVolumeDownPressed && isVolumeUpPressed) {
            Log.d(TAG, "Volume Up + Down pressed together, triggering capture")
            triggerScreenCapture()
            isVolumeDownPressed = false
            isVolumeUpPressed = false
            return true // 拦截音量调整
        }

        // 2. 双击音量下键触发
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && action == android.view.KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastVolumeDownTime < 500) {
                Log.d(TAG, "Volume Down double press, triggering capture")
                triggerScreenCapture()
                lastVolumeDownTime = 0L // 重置
                return true // 拦截音量调整
            }
            lastVolumeDownTime = currentTime
        }

        return super.onKeyEvent(event)
    }

    private fun registerScreenshotObserver() {
        if (screenshotObserver == null) {
            screenshotObserver = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                    super.onChange(selfChange, uri)
                    if (uri == null) return
                    Log.d(TAG, "ScreenshotObserver onChange: uri = $uri")
                    if (!com.example.awaassistant.data.SettingsManager.isAutoAnalyzeScreenshotsEnabled(this@AwaAccessibilityService)) return

                    if (uri.toString().contains("images/media")) {
                        Log.d(TAG, "ScreenshotObserver matching URI, querying MediaStore...")
                        try {
                            val projection = arrayOf(
                                android.provider.MediaStore.Images.Media._ID,
                                android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                                android.provider.MediaStore.Images.Media.DATA,
                                android.provider.MediaStore.Images.Media.DATE_ADDED
                            )
                            contentResolver.query(uri, projection, null, null, "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                                  if (cursor.moveToFirst()) {
                                      val nameIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                                      val dataIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                                      val dateIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_ADDED)
                                      
                                      val displayName = cursor.getString(nameIndex) ?: ""
                                      val path = cursor.getString(dataIndex) ?: ""
                                      val dateAdded = cursor.getLong(dateIndex)
                                      
                                      val isRecent = System.currentTimeMillis() / 1000 - dateAdded < 10
                                      Log.d(TAG, "Query result: displayName = $displayName, path = $path, dateAdded = $dateAdded, isRecent = $isRecent")
                                      if (isRecent && (displayName.lowercase().contains("screenshot") || path.lowercase().contains("screenshot"))) {
                                          val lastProcessedTime = processedScreenshots[path] ?: 0L
                                          val now = System.currentTimeMillis()
                                          if (now - lastProcessedTime > 4000) {
                                              processedScreenshots[path] = now
                                              // 清理过期数据
                                              processedScreenshots.entries.removeAll { now - it.value > 60000 }
                                              
                                              Log.d(TAG, "Screenshot matched criteria, handling...")
                                              handleDetectedScreenshot(uri)
                                          } else {
                                              Log.d(TAG, "Duplicate screenshot event ignored for path: $path")
                                          }
                                      }
                                  }
                              }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error querying screenshot URI", e)
                        }
                    }
                }
            }
            contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver!!
            )
            Log.d(TAG, "Screenshot observer registered")
        }
    }

    private fun unregisterScreenshotObserver() {
        screenshotObserver?.let {
            contentResolver.unregisterContentObserver(it)
            screenshotObserver = null
            Log.d(TAG, "Screenshot observer unregistered")
        }
    }

    private fun handleDetectedScreenshot(uri: android.net.Uri) {
        serviceScope.launch(Dispatchers.Main) {
            // 稍微延迟，等待系统写完图片文件
            delay(500)
            
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        android.graphics.ImageDecoder.decodeBitmap(
                            android.graphics.ImageDecoder.createSource(contentResolver, uri)
                        ).copy(Bitmap.Config.ARGB_8888, true)
                    } else {
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            .copy(Bitmap.Config.ARGB_8888, true)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode screenshot bitmap", e)
                    null
                }
            }

            if (bitmap != null) {
                // 触发闪屏特效提示用户
                FloatingOverlayService.instance?.triggerScreenFlash()
                Toast.makeText(this@AwaAccessibilityService, "检测到系统截屏，Awa 正在整理...", Toast.LENGTH_SHORT).show()

                val database = AppDatabase.getDatabase(this@AwaAccessibilityService)
                val dao = database.appDao()
                val timestamp = System.currentTimeMillis()

                serviceScope.launch(Dispatchers.IO) {
                    val initialRecord = CaptureRecord(
                        title = "系统截屏整理中...",
                        summary = "AI 正在识别屏幕内容并提炼总结，请稍候...",
                        rawContent = "",
                        imagePath = null,
                        timestamp = timestamp,
                        tags = "处理中",
                        sourceType = "SCREENSHOT"
                    )
                    val recordId = dao.insertCapture(initialRecord)
                    if (recordId == -1L) return@launch

                    // 将图片另存到 App 私有目录（防用户删除了原图）
                    val imagePath = saveBitmapToFile(bitmap)
                    
                    val currentRecord = dao.getCaptureById(recordId)
                    if (currentRecord != null) {
                        dao.updateCapture(currentRecord.copy(imagePath = imagePath, summary = "AI 正在分析提取的内容，请稍候..."))
                    }

                    var finalOcrText = ""
                    try {
                        finalOcrText = LocalOcrHelper.recognizeText(bitmap)
                        Log.d(TAG, "Extracted OCR Text from system screenshot: $finalOcrText")
                    } catch (e: Exception) {
                        Log.e(TAG, "OCR recognition failed", e)
                    }

                    if (finalOcrText.trim().isEmpty()) {
                        val failedRecord = dao.getCaptureById(recordId)
                        if (failedRecord != null) {
                            dao.updateCapture(failedRecord.copy(
                                title = "未识别到内容",
                                summary = "未能从当前系统截屏中读取到任何文字。",
                                tags = "识别失败"
                            ))
                        }
                        return@launch
                    }

                    processAndSaveCapturedContentAsync(dao, recordId, finalOcrText, imagePath, "SCREENSHOT")
                }
            }
        }
    }

    /**
     * 触发当前屏幕信息的获取与整理
     */
    fun triggerScreenCapture() {
        // 1. 立即抓取屏幕窗口布局文本（必须在主线程/辅助服务线程立即抓取，防止延迟后窗口树变化）
        val rawLayoutText = captureScreenText()
        val layoutText = ChineseConverter.toSimplified(rawLayoutText)
        Log.d(TAG, "Extracted Layout Text: $layoutText")

        // 2. 隐藏悬浮球，以免出现在截屏中
        FloatingOverlayService.instance?.hideFloatingBubble()

        // 3. 立即启动协程，在后台数据库中创建一条“处理中”的记录，让列表立即显示该记录
        val database = AppDatabase.getDatabase(this@AwaAccessibilityService)
        val dao = database.appDao()
        val timestamp = System.currentTimeMillis()

        var recordId: Long = -1
        val recordInsertJob = serviceScope.launch(Dispatchers.IO) {
            val initialRecord = CaptureRecord(
                title = "屏幕整理中...",
                summary = "AI 正在识别屏幕内容并提炼总结，请稍候...",
                rawContent = layoutText, // 优先保留已提取的布局文字
                imagePath = null,
                timestamp = timestamp,
                tags = "处理中",
                sourceType = "SCREENSHOT"
            )
            recordId = dao.insertCapture(initialRecord)
        }

        // 4. 获取截屏图片并处理
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceScope.launch(Dispatchers.Main) {
                // 等待 WindowManager 隐藏悬浮窗完成（大约需要几十毫秒渲染刷新）
                delay(150)
                captureScreenImage { bitmap ->
                    // 截图获取完毕后，无论成功失败，立即恢复悬浮窗显示
                    FloatingOverlayService.instance?.showFloatingBubble()
                    
                    serviceScope.launch(Dispatchers.Default) {
                        // 等待数据库插入任务完成，以获取 recordId
                        recordInsertJob.join()
                        val finalRecordId = recordId
                        if (finalRecordId == -1L) return@launch
                        
                        if (bitmap != null) {
                            // 提示用户截图已捕获并开始后台分析
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@AwaAccessibilityService, "屏幕截图已捕获，后台整理中...", Toast.LENGTH_SHORT).show()
                                // 触发闪屏特效提示用户
                                FloatingOverlayService.instance?.triggerScreenFlash()
                            }

                            // 保存图片文件
                            val imagePath = saveBitmapToFile(bitmap)

                            // 立即更新数据库记录，使缩略图和新的提示信息显示在界面上
                            val currentRecord = dao.getCaptureById(finalRecordId)
                            if (currentRecord != null) {
                                dao.updateCapture(currentRecord.copy(imagePath = imagePath, summary = "AI 正在分析提取的内容，请稍候..."))
                            }

                            // 启动后台任务进行耗时的 OCR 与大模型分析，完全不阻碍主进程
                            launch(Dispatchers.IO) {
                                var finalOcrText = ""
                                try {
                                    finalOcrText = LocalOcrHelper.recognizeText(bitmap)
                                    Log.d(TAG, "Extracted OCR Text: $finalOcrText")
                                } catch (e: Exception) {
                                    Log.e(TAG, "OCR recognition failed", e)
                                }

                                val combinedText = buildString {
                                    if (layoutText.trim().isNotEmpty()) {
                                        append("【屏幕窗口文本内容】\n")
                                        append(layoutText)
                                        append("\n\n")
                                    }
                                    if (finalOcrText.trim().isNotEmpty()) {
                                        append("【屏幕 OCR 图像识别内容】\n")
                                        append(finalOcrText)
                                    }
                                }.trim()

                                if (combinedText.isEmpty()) {
                                    val failedRecord = dao.getCaptureById(finalRecordId)
                                    if (failedRecord != null) {
                                        dao.updateCapture(failedRecord.copy(
                                            title = "未识别到内容",
                                            summary = "未能从当前屏幕中读取到任何文字。",
                                            tags = "识别失败"
                                        ))
                                    }
                                    return@launch
                                }

                                // 后台与模型交互并填充/更新记录
                                processAndSaveCapturedContentAsync(dao, finalRecordId, combinedText, imagePath, "SCREENSHOT")
                            }
                        } else {
                            // 截图失败，仅处理文本内容
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@AwaAccessibilityService, "截图失败，将仅对屏幕文本进行后台整理...", Toast.LENGTH_SHORT).show()
                            }

                            launch(Dispatchers.IO) {
                                if (layoutText.trim().isEmpty()) {
                                    val failedRecord = dao.getCaptureById(finalRecordId)
                                    if (failedRecord != null) {
                                        dao.updateCapture(failedRecord.copy(
                                            title = "未识别到内容",
                                            summary = "未能从当前屏幕的布局中提取到任何文字。",
                                            tags = "识别失败"
                                        ))
                                    }
                                    return@launch
                                }
                                processAndSaveCapturedContentAsync(dao, finalRecordId, layoutText, null, "SCREENSHOT")
                            }
                        }
                    }
                }
            }
        } else {
            // 低于 Android 11，仅使用布局文本
            FloatingOverlayService.instance?.showFloatingBubble()
            Toast.makeText(this@AwaAccessibilityService, "屏幕文本已捕获，后台整理中...", Toast.LENGTH_SHORT).show()

            serviceScope.launch(Dispatchers.Default) {
                recordInsertJob.join()
                val finalRecordId = recordId
                if (finalRecordId == -1L) return@launch

                launch(Dispatchers.IO) {
                    if (layoutText.trim().isEmpty()) {
                        val failedRecord = dao.getCaptureById(finalRecordId)
                        if (failedRecord != null) {
                            dao.updateCapture(failedRecord.copy(
                                id = finalRecordId,
                                title = "未识别到内容",
                                summary = "未能从当前屏幕的布局中提取到任何文字。",
                                tags = "识别失败"
                            ))
                        }
                        return@launch
                    }
                    processAndSaveCapturedContentAsync(dao, finalRecordId, layoutText, null, "SCREENSHOT")
                }
            }
        }
    }

    /**
     * 解析窗口视图树，提取纯文本
     */
    private fun captureScreenText(): String {
        val rootNode = rootInActiveWindow ?: return ""
        val builder = StringBuilder()
        traverseNode(rootNode, builder)
        rootNode.recycle()
        return builder.toString()
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return
        val text = node.text
        if (text != null && text.toString().trim().isNotEmpty() && node.isVisibleToUser) {
            builder.append(node.className).append(": ").append(text).append("\n")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseNode(child, builder)
            child?.recycle()
        }
    }

    /**
     * 使用 Android 11+ 提供的系统级 Accessibility takeScreenshot 接口
     */
    private fun captureScreenImage(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace
                        
                        // 从 HardwareBuffer 中包装并复制成可编辑 Bitmap
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, true)
                        
                        hardwareBuffer.close()
                        callback(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "takeScreenshot failed with error code: $errorCode")
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(this@AwaAccessibilityService, "屏幕截图失败，错误码: $errorCode", Toast.LENGTH_SHORT).show()
                        }
                        callback(null)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to call takeScreenshot", e)
                callback(null)
            }
        } else {
            callback(null)
        }
    }

    private suspend fun saveBitmapToFile(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val dir = File(filesDir, "captures")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "screen_${UUID.randomUUID()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        file.absolutePath
    }

    /**
     * 异步调用大模型分析，并更新数据库中的对应记录和闹钟
     */
    private suspend fun processAndSaveCapturedContentAsync(
        dao: com.example.awaassistant.data.AppDao,
        recordId: Long,
        rawText: String,
        imagePath: String?,
        sourceType: String
    ) {
        val result = OpenAiCompatibleClient.analyzeText(this@AwaAccessibilityService, rawText)
        
        val title: String
        val summary: String
        val tags: String
        
        if (result != null) {
            title = result.title
            summary = result.summary
            tags = result.tags.joinToString(",")
        } else {
            title = "自动分析记录 (${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())})"
            summary = "（AI 分析失败，这是原始文本提取）\n\n$rawText"
            tags = "屏幕提取"
        }

        // 更新 Room 数据库中的现有记录
        val updatedRecord = CaptureRecord(
            id = recordId,
            title = title,
            summary = summary,
            rawContent = rawText,
            imagePath = imagePath,
            timestamp = System.currentTimeMillis(),
            tags = tags,
            sourceType = sourceType
        )
        dao.updateCapture(updatedRecord)

        // 处理生成的智能提醒
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
                        // 注册到系统的 AlarmManager
                        ReminderScheduler.scheduleReminder(this@AwaAccessibilityService, reminder.copy(id = reminderId))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse reminder time: ${suggestion.timeString}", e)
                }
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(this@AwaAccessibilityService, "屏幕分析整理完成: $title", Toast.LENGTH_SHORT).show()
        }
    }
}
