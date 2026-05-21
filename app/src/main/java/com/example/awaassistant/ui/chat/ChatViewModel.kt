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
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sources: List<CaptureRecord> = emptyList() // 引用的本地笔记来源
)

class ChatViewModel(context: Context) : ViewModel() {

    private val db = AppDatabase.getDatabase(context.applicationContext)
    private val dao = db.appDao()

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                role = "assistant",
                content = "你好！我是 Awa 智能助手。你可以向我提问关于已保存截图或工作手记的任何内容。例如：“我上周二记录的会议结论是什么？”"
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 发送提问并运行本地 RAG 问答
     */
    fun sendMessage(context: Context, queryText: String) {
        if (queryText.trim().isEmpty()) return

        // 1. 立即展示用户消息
        val userMsg = ChatMessage(role = "user", content = queryText)
        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // 2. 本地检索相关内容 (RAG Context)
                val retrievedContext = retrieveLocalContext(queryText)

                // 3. 构建历史对话记录格式给大模型
                val history = _messages.value.drop(1).dropLast(0).map { msg ->
                    Pair(msg.role, msg.content)
                }

                // 4. 调用配置的国产/主流 LLM 接口
                val aiResponse = OpenAiCompatibleClient.chatWithContext(
                    context = context,
                    query = queryText,
                    retrievedRecords = retrievedContext,
                    chatHistory = history
                )

                // 5. 展示 AI 回复
                val aiMsg = ChatMessage(
                    role = "assistant",
                    content = aiResponse,
                    sources = retrievedContext
                )
                _messages.value = _messages.value + aiMsg
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    role = "assistant",
                    content = "抱歉，检索或生成回答时发生错误: ${e.message}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 智能检索本地文本
     */
    private suspend fun retrieveLocalContext(query: String): List<CaptureRecord> {
        // 清理搜索词，过滤无用标点
        val cleanedQuery = query.replace(Regex("[.,?，。？!]"), " ").trim()
        if (cleanedQuery.isEmpty()) return emptyList()

        // 1. 尝试使用 FTS MATCH 全文搜索。将搜索词用空格分割并加上 * 支持模糊前缀
        val terms = cleanedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val ftsQuery = terms.joinToString(" AND ") { "$it*" }
        
        var results = emptyList<CaptureRecord>()
        try {
            results = dao.searchCaptures(ftsQuery)
        } catch (e: Exception) {
            Log.e("ChatViewModel", "FTS search failed, fallback to manual filters", e)
        }

        // 2. 如果 FTS 没有匹配结果，降级使用传统数据库的模糊查询 (LIKE) 遍历匹配
        if (results.isEmpty()) {
            val allRecords = dao.getAllCaptures()
            results = allRecords.filter { record ->
                terms.any { term ->
                    record.title.contains(term, ignoreCase = true) ||
                    record.summary.contains(term, ignoreCase = true) ||
                    record.rawContent.contains(term, ignoreCase = true) ||
                    record.tags.contains(term, ignoreCase = true)
                }
            }
        }

        // 限制上下文最多 5 条记录，避免 Prompt 溢出
        return results.take(5)
    }

    fun clearHistory() {
        _messages.value = listOf(
            ChatMessage(
                role = "assistant",
                content = "对话历史已清空。您可以继续向我提问关于已保存记录的内容！"
            )
        )
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(context) as T
        }
    }
}
