package com.example.awaassistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.awaassistant.ui.MainPagerScreen
import com.example.awaassistant.ui.dashboard.DashboardScreen
import com.example.awaassistant.ui.chat.ChatScreen
import com.example.awaassistant.ui.settings.SettingsScreen
import com.example.awaassistant.ui.detail.NoteDetailScreen

@Composable
fun MainNavigation(
    recordIdToShow: Long? = null,
    showQuickCapture: Boolean = false,
    initialQuickCaptureText: String = "",
    onQuickCaptureDismissed: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(Main)

    // 如果通过通知传来具体的 recordId，自动跳往详情页
    LaunchedEffect(recordIdToShow) {
        if (recordIdToShow != null && recordIdToShow != -1L) {
            val currentKey = backStack.lastOrNull()
            if (currentKey !is NoteDetail || currentKey.recordId != recordIdToShow) {
                backStack.add(NoteDetail(recordIdToShow))
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                MainPagerScreen(
                    onNavigateToSettings = { backStack.add(Settings) },
                    onNavigateToDetail = { id -> backStack.add(NoteDetail(id)) },
                    showQuickCapture = showQuickCapture,
                    initialQuickCaptureText = initialQuickCaptureText,
                    onQuickCaptureDismissed = onQuickCaptureDismissed
                )
            }
            entry<Chat> {
                ChatScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToDetail = { id -> backStack.add(NoteDetail(id)) }
                )
            }
            entry<Settings> {
                SettingsScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<NoteDetail> { key ->
                NoteDetailScreen(
                    recordId = key.recordId,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToDetail = { id -> backStack.add(NoteDetail(id)) }
                )
            }
        }
    )
}
