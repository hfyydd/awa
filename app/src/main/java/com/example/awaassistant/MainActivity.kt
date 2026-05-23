package com.example.awaassistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.awaassistant.receiver.ReminderReceiver
import com.example.awaassistant.theme.MyApplicationTheme
import com.example.awaassistant.util.AsrManager
import com.example.awaassistant.widget.WidgetRefreshWorker

class MainActivity : ComponentActivity() {

    private val recordIdToShow = mutableStateOf<Long?>(null)
    // P0: Quick Capture 触发状态
    private val showQuickCapture = mutableStateOf(false)

    companion object {
        const val ACTION_SHOW_QUICK_CAPTURE = "com.example.awaassistant.SHOW_QUICK_CAPTURE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ASR engine (loads model in background thread)
        AsrManager.init(applicationContext)

        // 安排桌面小组件每日刷新
        WidgetRefreshWorker.scheduleDaily(applicationContext)
        
        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        recordIdToShow = recordIdToShow.value,
                        showQuickCapture = showQuickCapture.value,
                        onQuickCaptureDismissed = { showQuickCapture.value = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when {
            intent?.action == ACTION_SHOW_QUICK_CAPTURE -> {
                showQuickCapture.value = true
            }
            intent?.hasExtra("open_record_id") == true -> {
                // 来自桌面小组件的点击
                val recordId = intent.getLongExtra("open_record_id", -1L)
                if (recordId != -1L) {
                    recordIdToShow.value = recordId
                }
            }
            intent?.hasExtra(ReminderReceiver.EXTRA_RECORD_ID) == true -> {
                val recordId = intent.getLongExtra(ReminderReceiver.EXTRA_RECORD_ID, -1L)
                if (recordId != -1L) {
                    recordIdToShow.value = recordId
                }
            }
        }
    }
}
