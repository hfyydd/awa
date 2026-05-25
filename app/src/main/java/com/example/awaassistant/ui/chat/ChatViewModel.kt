package com.example.awaassistant.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.awaassistant.data.AppDatabase
import com.example.awaassistant.data.CaptureRecord
import com.example.awaassistant.data.OpenAiCompatibleClient
import com.example.awaassistant.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ChatAttachment {
    data class Image(val uri: Uri, val name: String, val ocrText: String) : ChatAttachment
    data class File(val uri: Uri, val name: String, val content: String) : ChatAttachment
}

data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sources: List<CaptureRecord> = emptyList()
)

class ChatViewModel(context: Context) : ViewModel() {

    private val db = AppDatabase.getDatabase(context.applicationContext)
    private val dao = db.appDao()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _attachment = MutableStateFlow<ChatAttachment?>(null)
    val attachment: StateFlow<ChatAttachment?> = _attachment.asStateFlow()

    private val _isAttachmentLoading = MutableStateFlow(false)
    val isAttachmentLoading: StateFlow<Boolean> = _isAttachmentLoading.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    private val _currentSessionTitle = MutableStateFlow<String>("新对话")
    val currentSessionTitle: StateFlow<String> = _currentSessionTitle.asStateFlow()

    private val _sessions = MutableStateFlow<List<com.example.awaassistant.data.ChatSession>>(emptyList())
    val sessions: StateFlow<List<com.example.awaassistant.data.ChatSession>> = _sessions.asStateFlow()

    init {
        loadSessionsAndLatest()
    }

    private fun loadSessionsAndLatest() {
        viewModelScope.launch {
            val allSessions = dao.getAllChatSessions()
            _sessions.value = allSessions
            if (allSessions.isNotEmpty()) {
                selectSession(allSessions.first().id)
            } else {
                startNewSession()
            }
        }
    }

    fun selectSession(sessionId: Long) {
        viewModelScope.launch {
            val session = dao.getAllChatSessions().find { it.id == sessionId } ?: return@launch
            _currentSessionId.value = session.id
            _currentSessionTitle.value = session.title

            val dbMsgs = dao.getMessagesForSession(sessionId)
            val mappedMsgs = dbMsgs.map { dbMsg ->
                val attachedRecord = dbMsg.attachedRecordId?.let { dao.getCaptureById(it) }
                ChatMessage(
                    role = dbMsg.role,
                    content = dbMsg.content,
                    timestamp = dbMsg.timestamp,
                    sources = if (attachedRecord != null) listOf(attachedRecord) else emptyList()
                )
            }
            _messages.value = mappedMsgs
        }
    }

    fun startNewSession() {
        _currentSessionId.value = null
        _currentSessionTitle.value = "新对话"
        _messages.value = emptyList()
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            dao.deleteChatSession(sessionId)
            dao.deleteMessagesForSession(sessionId)
            val allSessions = dao.getAllChatSessions()
            _sessions.value = allSessions

            if (_currentSessionId.value == sessionId) {
                if (allSessions.isNotEmpty()) {
                    selectSession(allSessions.first().id)
                } else {
                    startNewSession()
                }
            }
        }
    }

    fun clearAttachment() {
        _attachment.value = null
    }

    fun selectImage(context: Context, uri: Uri) {
        _isAttachmentLoading.value = true
        _attachment.value = null
        viewModelScope.launch {
            try {
                val name = getFileName(context, uri)
                val ocrText = com.example.awaassistant.util.LocalOcrHelper.recognizeText(context, uri)
                _attachment.value = ChatAttachment.Image(uri, name, ocrText)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "OCR failed", e)
                _attachment.value = ChatAttachment.Image(uri, "图片.jpg", "")
            } finally {
                _isAttachmentLoading.value = false
            }
        }
    }

    fun selectFile(context: Context, uri: Uri) {
        _isAttachmentLoading.value = true
        _attachment.value = null
        viewModelScope.launch {
            try {
                val name = getFileName(context, uri)
                val content = readFileContent(context, uri)
                _attachment.value = ChatAttachment.File(uri, name, content)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "File read failed", e)
            } finally {
                _isAttachmentLoading.value = false
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "file"
    }

    private fun readFileContent(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = ByteArray(10240) // 10KB max
                val bytesRead = inputStream.read(bytes)
                if (bytesRead > 0) {
                    val content = String(bytes, 0, bytesRead, Charsets.UTF_8)
                    if (content.contains('\u0000')) {
                        "【注意：该文件似乎为二进制文件，无法提取纯文本内容】"
                    } else {
                        content
                    }
                } else {
                    ""
                }
            } ?: ""
        } catch (e: Exception) {
            "读取文件失败: ${e.message}"
        }
    }

    private suspend fun saveUriToPermanentStorage(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "photos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val permanentFile = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            permanentFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        permanentFile
    }

    fun sendMessage(context: Context, queryText: String) {
        if (queryText.trim().isEmpty()) return

        val currentAtt = _attachment.value
        _attachment.value = null // Clear attachment state immediately

        val userMsg = ChatMessage(role = "user", content = queryText)
        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Ensure we have an active session
                var sId = _currentSessionId.value
                if (sId == null) {
                    val title = if (queryText.length > 15) queryText.take(15) + "..." else queryText
                    val newSession = com.example.awaassistant.data.ChatSession(title = title, lastUpdated = System.currentTimeMillis())
                    sId = dao.insertChatSession(newSession)
                    _currentSessionId.value = sId
                    _currentSessionTitle.value = title
                    _sessions.value = dao.getAllChatSessions()
                }

                var attachedRecord: CaptureRecord? = null
                if (currentAtt != null) {
                    when (currentAtt) {
                        is ChatAttachment.Image -> {
                            val permanentFile = saveUriToPermanentStorage(context, currentAtt.uri)
                            val record = CaptureRecord(
                                title = "导入的图片",
                                summary = "从聊天框导入的图片。\n\n[OCR 识别文字]\n${currentAtt.ocrText}",
                                rawContent = currentAtt.ocrText,
                                imagePath = permanentFile.absolutePath,
                                timestamp = System.currentTimeMillis(),
                                tags = "聊天导入",
                                sourceType = "PHOTO"
                            )
                            val recordId = dao.insertCapture(record)
                            attachedRecord = record.copy(id = recordId)
                        }
                        is ChatAttachment.File -> {
                            val record = CaptureRecord(
                                title = currentAtt.name,
                                summary = "从聊天框导入的文件：${currentAtt.name}。\n\n[内容]\n${currentAtt.content}",
                                rawContent = currentAtt.content,
                                imagePath = null,
                                timestamp = System.currentTimeMillis(),
                                tags = "聊天导入",
                                sourceType = "TEXT"
                            )
                            val recordId = dao.insertCapture(record)
                            attachedRecord = record.copy(id = recordId)
                        }
                    }
                }

                // Save user message to database
                val userMsgEntity = com.example.awaassistant.data.ChatMessageEntity(
                    sessionId = sId,
                    role = "user",
                    content = queryText,
                    timestamp = userMsg.timestamp,
                    attachedRecordId = attachedRecord?.id
                )
                dao.insertChatMessage(userMsgEntity)

                // Update the user message to contain the attachedRecord in its sources for rendering
                if (attachedRecord != null) {
                    val currentList = _messages.value.toMutableList()
                    val userMsgIndex = currentList.indexOfLast { it.role == "user" }
                    if (userMsgIndex != -1) {
                        currentList[userMsgIndex] = currentList[userMsgIndex].copy(sources = listOf(attachedRecord))
                        _messages.value = currentList
                    }
                }

                val retrievedContext = retrieveLocalContext(queryText)
                val finalContext = if (attachedRecord != null) {
                    listOf(attachedRecord) + retrievedContext.filter { it.id != attachedRecord.id }
                } else {
                    retrievedContext
                }

                val history = _messages.value.dropLast(1).map { msg ->
                    Pair(msg.role, msg.content)
                }

                // Check API Key
                val apiKey = SettingsManager.getApiKey(context)
                if (apiKey.isEmpty()) {
                    val fallbackResponse = "请先去设置页面配置您的 API Key。"
                    _messages.value = _messages.value + ChatMessage(role = "assistant", content = fallbackResponse, sources = finalContext)
                    
                    val errorMsgEntity = com.example.awaassistant.data.ChatMessageEntity(
                        sessionId = sId,
                        role = "assistant",
                        content = fallbackResponse,
                        timestamp = System.currentTimeMillis()
                    )
                    dao.insertChatMessage(errorMsgEntity)
                    _isLoading.value = false
                    return@launch
                }

                // Add empty placeholder
                val initialAssistantMsg = ChatMessage(role = "assistant", content = "", sources = finalContext)
                _messages.value = _messages.value + initialAssistantMsg
                val assistantMsgIndex = _messages.value.size - 1

                var accumulatedContent = ""
                OpenAiCompatibleClient.chatWithContextStream(
                    context = context,
                    query = queryText,
                    retrievedRecords = finalContext,
                    chatHistory = history
                ).collect { chunk ->
                    accumulatedContent += chunk
                    val currentList = _messages.value.toMutableList()
                    if (assistantMsgIndex < currentList.size) {
                        currentList[assistantMsgIndex] = currentList[assistantMsgIndex].copy(content = accumulatedContent)
                        _messages.value = currentList
                    }
                }

                // Save assistant message to database after streaming completes
                val assistantMsgEntity = com.example.awaassistant.data.ChatMessageEntity(
                    sessionId = sId,
                    role = "assistant",
                    content = accumulatedContent,
                    timestamp = System.currentTimeMillis()
                )
                dao.insertChatMessage(assistantMsgEntity)

                // Update session time
                val currentSession = dao.getAllChatSessions().find { it.id == sId }
                if (currentSession != null) {
                    dao.updateChatSession(currentSession.copy(lastUpdated = System.currentTimeMillis()))
                }
                _sessions.value = dao.getAllChatSessions()

            } catch (e: Exception) {
                val errMsg = "抱歉，检索或生成回答时发生错误: ${e.message}"
                _messages.value = _messages.value + ChatMessage(role = "assistant", content = errMsg)
                
                val sId = _currentSessionId.value
                if (sId != null) {
                    dao.insertChatMessage(
                        com.example.awaassistant.data.ChatMessageEntity(
                            sessionId = sId,
                            role = "assistant",
                            content = errMsg,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun retrieveLocalContext(query: String): List<CaptureRecord> {
        val results = mutableListOf<CaptureRecord>()
        val q = query.trim()
        val allRecords = dao.getAllCaptures()
        
        // 1. 时效性/序号类查询
        val recencyPatterns = listOf(
            "最后一张", "最后一篇", "最新一张", "最新一个", "上一个", "上一张", "上一条", 
            "刚才", "刚刚", "最近", "最新", "刚", "上一次", "前一个", "前一张",
            "我刚才", "我刚刚", "我最近", "我上一个", "我上一张", "我上一条"
        )
        val hasRecency = recencyPatterns.any { q.contains(it) }
        
        // 2. 截图类查询 → 过滤 SCREENSHOT 记录
        val screenshotPatterns = listOf(
            "截图", "截屏", "屏幕截图", "刚才截的", "刚刚截的", "最后一张截图",
            "最后截的", "上一张截图", "上一截", "屏幕内容", "屏幕上的"
        )
        val hasScreenshot = screenshotPatterns.any { q.contains(it) }
        
        if (hasScreenshot) {
            val screenshots = allRecords.filter { it.sourceType == "SCREENSHOT" }.take(3)
            results.addAll(screenshots)
        }
        
        if (hasRecency && results.isEmpty()) {
            results.addAll(allRecords.take(3))
        }
        
        // 3. 搜索词提取
        val searchTerms = q
            .replace(Regex("[.,?，。？！、…~@#\$%^&()（）【】《》\"']"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in listOf("什么", "哪个", "怎么", "如何", "为什么", "是不是", "可以", "帮我", "我想", "我要") }
        
        if (searchTerms.isNotEmpty()) {
            val ftsQuery = searchTerms.joinToString(" AND ") { "$it*" }
            try {
                val ftsResults = dao.searchCaptures(ftsQuery)
                for (record in ftsResults) {
                    if (results.none { it.id == record.id }) {
                        results.add(record)
                    }
                }
            } catch (_: Exception) {}
            
            if (results.isEmpty()) {
                val likeResults = allRecords.filter { record ->
                    searchTerms.any { term ->
                        record.title.contains(term, ignoreCase = true) ||
                        record.summary.contains(term, ignoreCase = true) ||
                        record.rawContent.contains(term, ignoreCase = true) ||
                        record.tags.contains(term, ignoreCase = true)
                    }
                }
                results.addAll(likeResults)
            }
        }
        
        if (results.isEmpty()) {
            results.addAll(allRecords.take(3))
        }
        
        return results.distinctBy { it.id }.take(5)
    }

    fun clearHistory() {
        val sId = _currentSessionId.value
        if (sId != null) {
            viewModelScope.launch {
                dao.deleteChatSession(sId)
                loadSessionsAndLatest()
            }
        } else {
            startNewSession()
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(context) as T
        }
    }
}
