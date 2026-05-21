package com.example.awaassistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.awaassistant.receiver.ReminderReceiver
import com.example.awaassistant.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val recordIdToShow = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(recordIdToShow = recordIdToShow.value)
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
        if (intent != null && intent.hasExtra(ReminderReceiver.EXTRA_RECORD_ID)) {
            val recordId = intent.getLongExtra(ReminderReceiver.EXTRA_RECORD_ID, -1L)
            if (recordId != -1L) {
                recordIdToShow.value = recordId
            }
        }
    }
}
