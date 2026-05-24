package com.example.awaassistant.ui.dashboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.awaassistant.data.AppDao
import com.example.awaassistant.data.AppDatabase
import com.example.awaassistant.data.CaptureRecord
import com.example.awaassistant.data.DailySourceCount
import com.example.awaassistant.ui.detail.MemoryCapsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HomePager 共享 ViewModel
 * 
 * 将热力图数据和时光胶囊数据集中在这里加载一次，
 * 避免每个 Composable 各自 LaunchedEffect 重复查询数据库。
 */
class HomeSharedViewModel(
    private val dao: AppDao
) : ViewModel() {

    companion object {
        private const val TAG = "HomeSharedViewModel"
    }

    private val _memoryCapsule = MutableStateFlow<MemoryCapsule?>(null)
    val memoryCapsule: StateFlow<MemoryCapsule?> = _memoryCapsule.asStateFlow()

    private val _activityStats = MutableStateFlow<List<DailySourceCount>>(emptyList())
    val activityStats: StateFlow<List<DailySourceCount>> = _activityStats.asStateFlow()

    private val _capsuleLoading = MutableStateFlow(true)
    val capsuleLoading: StateFlow<Boolean> = _capsuleLoading.asStateFlow()

    private val _statsLoading = MutableStateFlow(true)
    val statsLoading: StateFlow<Boolean> = _statsLoading.asStateFlow()

    init {
        Log.d(TAG, "Initializing HomeSharedViewModel and collecting captures flow...")
        viewModelScope.launch {
            dao.getAllCapturesFlow().collect { captures ->
                Log.d(TAG, "getAllCapturesFlow emitted: size=${captures.size}. Reloading capsule & stats...")
                loadAll()
            }
        }
    }

    /** 全量初始化（保留方法用于兼容，实际由 init 块中 Flow 驱动自动更新） */
    fun initialize() {
        Log.d(TAG, "initialize() called (no-op)")
    }

    private fun loadAll() {
        loadMemoryCapsule()
        loadActivityStats()
    }

    fun refreshCapsule() { loadMemoryCapsule() }

    private fun loadMemoryCapsule() {
        viewModelScope.launch {
            _capsuleLoading.value = true
            withContext(Dispatchers.IO) {
                try {
                    val now = System.currentTimeMillis()
                    val dayMs = 86400000L
                    val windows = listOf(
                        3L to 7L,
                        7L to 30L,
                        30L to 365L,
                        0L to 3L // 增加 0-3 天兜底，保证测试或初始使用时时光胶囊不为空
                    )
                    Log.d(TAG, "loadMemoryCapsule: now=$now")
                    for ((fromDays, toDays) in windows) {
                        val toTs = now - dayMs * fromDays
                        val fromTs = now - dayMs * toDays
                        Log.d(TAG, "Searching capsule in window: $fromDays to $toDays days ago. range=[$fromTs, $toTs)")
                        val record = dao.getRandomRecordInRange(fromTs, toTs)
                        if (record != null) {
                            val daysAgo = ((now - record.timestamp) / dayMs).toInt()
                            Log.d(TAG, "Found memory capsule candidate: id=${record.id}, title=${record.title}, daysAgo=$daysAgo")
                            _memoryCapsule.value = MemoryCapsule(record, daysAgo, labelOf(daysAgo))
                            _capsuleLoading.value = false
                            return@withContext
                        }
                    }
                    Log.d(TAG, "No memory capsule candidate found in any window.")
                    _memoryCapsule.value = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading memory capsule", e)
                    _memoryCapsule.value = null
                }
            }
            _capsuleLoading.value = false
        }
    }

    private fun loadActivityStats() {
        viewModelScope.launch {
            _statsLoading.value = true
            withContext(Dispatchers.IO) {
                try {
                    val threeMonthsAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                    Log.d(TAG, "loadActivityStats: threeMonthsAgo=$threeMonthsAgo")
                    val stats = dao.getActivityStats(threeMonthsAgo)
                    Log.d(TAG, "Loaded activity stats: size=${stats.size}, data=$stats")
                    _activityStats.value = stats
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading activity stats", e)
                    _activityStats.value = emptyList()
                }
            }
            _statsLoading.value = false
        }
    }

    private fun labelOf(daysAgo: Int): String = when {
        daysAgo == 0 -> "今日的灵感"
        daysAgo <= 2 -> "最近的思绪"
        daysAgo <= 7 -> "7天前的灵感"
        daysAgo <= 30 -> "30天前的回顾"
        daysAgo <= 365 -> "1年前的珍藏"
        else -> "旧日时光"
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(context)
            return HomeSharedViewModel(db.appDao()) as T
        }
    }
}
