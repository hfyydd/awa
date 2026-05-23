package com.example.awaassistant.ui.dashboard

import android.content.Context
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

    private val _memoryCapsule = MutableStateFlow<MemoryCapsule?>(null)
    val memoryCapsule: StateFlow<MemoryCapsule?> = _memoryCapsule.asStateFlow()

    private val _activityStats = MutableStateFlow<List<DailySourceCount>>(emptyList())
    val activityStats: StateFlow<List<DailySourceCount>> = _activityStats.asStateFlow()

    private val _capsuleLoading = MutableStateFlow(true)
    val capsuleLoading: StateFlow<Boolean> = _capsuleLoading.asStateFlow()

    private val _statsLoading = MutableStateFlow(true)
    val statsLoading: StateFlow<Boolean> = _statsLoading.asStateFlow()

    private var _initialized = false

    /** 全量初始化（只在首次Pager显示时调用一次） */
    fun initialize() {
        if (_initialized) return
        _initialized = true
        loadAll()
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
                        30L to 365L
                    )
                    for ((fromDays, toDays) in windows) {
                        val toTs = now - dayMs * fromDays
                        val fromTs = now - dayMs * toDays
                        val record = dao.getRandomRecordInRange(fromTs, toTs)
                        if (record != null) {
                            val daysAgo = ((now - record.timestamp) / dayMs).toInt()
                            _memoryCapsule.value = MemoryCapsule(record, daysAgo, labelOf(daysAgo))
                            _capsuleLoading.value = false
                            return@withContext
                        }
                    }
                    _memoryCapsule.value = null
                } catch (e: Exception) {
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
                    _activityStats.value = dao.getActivityStats(threeMonthsAgo)
                } catch (e: Exception) {
                    _activityStats.value = emptyList()
                }
            }
            _statsLoading.value = false
        }
    }

    private fun labelOf(daysAgo: Int): String = when {
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
