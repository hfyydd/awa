package com.example.awaassistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.awaassistant.ui.chat.ChatScreen
import com.example.awaassistant.ui.chat.ChatViewModel
import com.example.awaassistant.ui.dashboard.DashboardScreen
import com.example.awaassistant.ui.dashboard.DashboardViewModel
import com.example.awaassistant.ui.dashboard.HomeSharedViewModel
import com.example.awaassistant.ui.quickcapture.QuickActionsScreen
import com.example.awaassistant.ui.quickcapture.QuickCaptureBottomSheet
import kotlinx.coroutines.launch

@Composable
fun MainPagerScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showQuickCapture: Boolean = false,
    initialQuickCaptureText: String = "",
    onQuickCaptureDismissed: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // 共享 ViewModel：只在 MainPager 创建一次，避免切换时重复加载数据
    val sharedViewModel: HomeSharedViewModel = viewModel(
        factory = HomeSharedViewModel.Factory(context)
    )
    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(context)
    )
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.Factory(context)
    )

    val initialPage = remember {
        com.example.awaassistant.data.SettingsManager.getDefaultHomepage(context)
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 3 })

    var isQuickCaptureVisible by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState) {
        // 首次显示时初始化共享数据（只触发一次）
        sharedViewModel.initialize()
    }

    LaunchedEffect(showQuickCapture) {
        if (showQuickCapture) isQuickCaptureVisible = true
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Awa",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                Row(
                    modifier = Modifier
                        .background(Color(0x0DFFFFFF), shape = RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("问答", "快捷", "记录").forEachIndexed { index, title ->
                        val selected = pagerState.currentPage == index
                        val bgColor by animateColorAsState(
                            targetValue = if (selected) Color(0xFF8E2DE2) else Color.Transparent,
                            label = "tabBg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (selected) Color.White else Color.LightGray,
                            label = "tabText"
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(bgColor)
                                .clickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (pagerState.currentPage == 0) {
                        IconButton(onClick = { chatViewModel.clearHistory() }) {
                            Icon(Icons.Default.ClearAll, "清空历史", tint = Color.LightGray)
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "设置", tint = Color.White)
                    }
                }
            }
        },
        containerColor = Color(0xFF0F0C1B),
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) { page ->
            when (page) {
                0 -> ChatScreen(
                    showTopBar = false,
                    viewModel = chatViewModel,
                    onBack = {},
                    onNavigateToDetail = onNavigateToDetail
                )
                1 -> QuickActionsScreen(
                    onNavigateToDetail = onNavigateToDetail,
                    sharedViewModel = sharedViewModel,
                    dashboardViewModel = dashboardViewModel
                )
                2 -> DashboardScreen(
                    showTopBar = false,
                    onNavigateToChat = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToDetail = onNavigateToDetail,
                    viewModel = dashboardViewModel
                )
            }
        }
    }

    if (isQuickCaptureVisible) {
        QuickCaptureBottomSheet(
            initialText = initialQuickCaptureText,
            onDismiss = {
                isQuickCaptureVisible = false
                onQuickCaptureDismissed()
            }
        )
    }
}
