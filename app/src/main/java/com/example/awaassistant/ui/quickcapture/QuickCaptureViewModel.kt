package com.example.awaassistant.ui.quickcapture

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.awaassistant.data.AppDao
import com.example.awaassistant.data.AppDatabase
import com.example.awaassistant.data.CaptureRecord
import com.example.awaassistant.widget.WidgetRefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QuickCaptureViewModel(
    private val dao: AppDao,
    private val context: Context
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun quickSave(content: String, onComplete: () -> Unit) {
        if (content.isBlank()) {
            onComplete()
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val record = CaptureRecord(
                    title = content.take(50).replace("\n", " "),
                    summary = content,
                    rawContent = content,
                    imagePath = null,
                    timestamp = System.currentTimeMillis(),
                    tags = "快速录入",
                    sourceType = "TEXT",
                    isCompleted = false
                )
                dao.insertCapture(record)
                _saveSuccess.value = true
                onComplete()
                // 刷新桌面小组件
                WidgetRefreshWorker.triggerNow(context)
            } catch (e: Exception) {
                _saveSuccess.value = false
            } finally {
                _isSaving.value = false
            }
        }
    }

    companion object {
        fun Factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getDatabase(context)
                    return QuickCaptureViewModel(db.appDao(), context) as T
                }
            }
        }
    }
}
