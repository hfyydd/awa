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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NoteDetailViewModel(private val dao: AppDao) : ViewModel() {

    private val _relatedRecords = MutableStateFlow<List<CaptureRecord>>(emptyList())
    val relatedRecords: StateFlow<List<CaptureRecord>> = _relatedRecords.asStateFlow()

    private val _memoryCapsule = MutableStateFlow<MemoryCapsule?>(null)
    val memoryCapsule: StateFlow<MemoryCapsule?> = _memoryCapsule.asStateFlow()

    // P1: 加载关联旧思绪
    fun loadRelatedRecords(record: CaptureRecord) {
        viewModelScope.launch {
            val keywords = extractKeywords(record.summary)
            if (keywords.isNotBlank()) {
                try {
                    val related = dao.searchRelated(record.id, keywords)
                    _relatedRecords.value = related
                } catch (e: Exception) {
                    _relatedRecords.value = emptyList()
                }
            }
        }
    }

    // P1: 加载时光胶囊
    fun loadMemoryCapsule() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val dayMs = 86400000L

            // 按时间窗口查找：7天前、30天前
            val windows = listOf(
                7L to "7天前的灵感",
                30L to "30天前的回顾",
                365L to "1年前的珍藏"
            )

            for ((days, label) in windows) {
                val toTs = now - dayMs * (days - 1)
                val fromTs = now - dayMs * days
                val candidate = dao.getRandomRecordInRange(fromTs, toTs)
                if (candidate != null) {
                    _memoryCapsule.value = MemoryCapsule(
                        record = candidate,
                        daysAgo = days.toInt(),
                        label = label
                    )
                    return@launch
                }
            }
            _memoryCapsule.value = null
        }
    }

    // 提取关键词用于 FTS 查询
    private fun extractKeywords(text: String): String {
        val words = text
            .replace(Regex("[\\#\\*`\\-\\[\\]()]"), " ")
            .split(Regex("[\\s,.，。、；;！!？?】\\【「」『\"']"))
            .map { it.trim() }
            .filter { it.length > 2 && !STOP_WORDS.contains(it) }
            .take(5)
        
        return words.joinToString(" OR ") { "*$it*" }
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

        // 停用词
        private val STOP_WORDS = setOf(
            "这个", "那个", "什么", "怎么", "如何", "为什么",
            "但是", "而且", "所以", "因为", "如果", "虽然",
            "可以", "能够", "需要", "应该", "已经", "正在",
            "没有", "不是", "就是", "还是", "或者", "以及"
        )
    }
}

// P1: 时光胶囊数据结构
data class MemoryCapsule(
    val record: CaptureRecord,
    val daysAgo: Int,
    val label: String
)
