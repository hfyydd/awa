package com.example.awaassistant.ui.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.awaassistant.data.AppDao
import com.example.awaassistant.data.AppDatabase
import com.example.awaassistant.data.CaptureRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NoteDetailViewModel(private val dao: AppDao) : ViewModel() {

    private val _relatedRecords = MutableStateFlow<List<CaptureRecord>>(emptyList())
    val relatedRecords: StateFlow<List<CaptureRecord>> = _relatedRecords.asStateFlow()

    // P1: 加载关联旧思绪（基于 FTS 全文检索）
    fun loadRelatedRecords(record: CaptureRecord) {
        viewModelScope.launch {
            val keywords = extractKeywords(record.summary)
            if (keywords.isNotBlank()) {
                try {
                    val related = dao.searchRelated(record.id, keywords)
                    _relatedRecords.value = related
                } catch (e: Exception) {
                    // FTS 查询失败时静默降级
                    _relatedRecords.value = emptyList()
                }
            } else {
                _relatedRecords.value = emptyList()
            }
        }
    }

    /**
     * 从文本中提取关键词，用于 FTS5 MATCH 查询
     * 
     * SQLite FTS5 MATCH 语法：
     * - 直接词: "张三" (精确匹配)
     * - 前缀: "张三*" (匹配以张三开头的词)
     * - 布尔: "张三 OR 李四" (或匹配)
     * 
     * 我们使用前缀+或组合: "张三* OR 二季度*" 
     */
    private fun extractKeywords(text: String): String {
        // 1. 清理 Markdown 和特殊符号，保留中文/英文/数字
        val clean = text
            .replace(Regex("\\\\[\\#\\*`\\-_~\\[\\](){}]"), " ") // 去掉 Markdown 符号
            .replace(Regex("[，。、；;！？…～·「」『』（）()\\[\\]{}:;'\"，、。]+"), " ") // 去掉标点
        
        // 2. 按空白字符分割
        val words = clean.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { word ->
                word.isNotBlank() &&
                word.length >= 2 &&
                !STOP_WORDS.contains(word) &&
                // 至少包含一个中文字符或两个以上英文字母
                (word.any { it in '\u4e00'..'\u9fff' } || word.length >= 2)
            }
            .distinct()
            .take(8)
        
        if (words.isEmpty()) return ""
        
        // 3. 构造 FTS 前缀匹配查询（避免特殊字符问题）
        return words.joinToString(" OR ") { word ->
            // 如果是纯英文，加 * 后缀（前缀匹配）
            if (word.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) {
                "$word*"
            } else {
                // 中文：直接用引号包裹
                "\"$word\""
            }
        }
    }

    companion object {
        fun Factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getDatabase(context)
                    return NoteDetailViewModel(db.appDao()) as T
                }
            }
        }

        // 停用词（短句/常用连词/无实义词）
        private val STOP_WORDS = setOf(
            "这个", "那个", "什么", "怎么", "如何", "为什么", "是不是",
            "但是", "而且", "所以", "因为", "如果", "虽然", "即使",
            "可以", "能够", "需要", "应该", "已经", "正在",
            "没有", "不是", "就是", "还是", "或者", "以及",
            "一个", "一下", "一些", "这样", "那样", "这里", "那里",
            "自己", "所有", "还有", "现在", "今天", "明天", "昨天",
            "然后", "之后", "之前", "时候", "之后", "关于",
            "然后", "最后", "首先", "可能", "大概", "应该"
        )
    }
}
