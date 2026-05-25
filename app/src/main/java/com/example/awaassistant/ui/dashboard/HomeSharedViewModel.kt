package com.example.awaassistant.ui.dashboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.awaassistant.data.AppDao
import com.example.awaassistant.data.AppDatabase
import com.example.awaassistant.data.CapsuleData
import com.example.awaassistant.data.TimeCapsuleEngine
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

    private val _memoryCapsule = MutableStateFlow<CapsuleData?>(null)
    val memoryCapsule: StateFlow<CapsuleData?> = _memoryCapsule.asStateFlow()

    private val _activityStats = MutableStateFlow<List<com.example.awaassistant.data.DailySourceCount>>(emptyList())
    val activityStats: StateFlow<List<com.example.awaassistant.data.DailySourceCount>> = _activityStats.asStateFlow()

    private val _capsuleLoading = MutableStateFlow(true)
    val capsuleLoading: StateFlow<Boolean> = _capsuleLoading.asStateFlow()

    private val _statsLoading = MutableStateFlow(true)
    val statsLoading: StateFlow<Boolean> = _statsLoading.asStateFlow()

    private val capsuleEngine = TimeCapsuleEngine(dao)

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
            try {
                val capsule = withContext(Dispatchers.IO) {
                    capsuleEngine.loadCapsule()
                }
                _memoryCapsule.value = capsule
            } catch (e: Exception) {
                Log.e(TAG, "Error loading memory capsule", e)
                _memoryCapsule.value = null
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

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(context)
            return HomeSharedViewModel(db.appDao()) as T
        }
    }
}
