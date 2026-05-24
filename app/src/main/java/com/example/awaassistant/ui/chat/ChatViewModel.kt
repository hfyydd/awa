package com.example.awaassistant.ui.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.awaassistant.data.AppDatabase
import com.example.awaassistant.data.CaptureRecord
import com.example.awaassistant.data.OpenAiCompatibleClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun sendMessage(context: Context, queryText: String) {
        if (queryText.trim().isEmpty()) return

        val userMsg = ChatMessage(role = "user", content = queryText)
        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val retrievedContext = retrieveLocalContext(queryText)
                val history = _messages.value.drop(1).dropLast(0).map { msg ->
                    Pair(msg.role, msg.content)
                }
                val aiResponse = OpenAiCompatibleClient.chatWithContext(
                    context = context,
                    query = queryText,
                    retrievedRecords = retrievedContext,
                    chatHistory = history
                )
                val aiMsg = ChatMessage(role = "assistant", content = aiResponse, sources = retrievedContext)
                _messages.value = _messages.value + aiMsg
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(role = "assistant", content = "抱歉，检索或生成回答时发生错误: ${e.message}")
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
        _messages.value = emptyList()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(context) as T
        }
    }
}
