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
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Settings
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
import kotlinx.coroutines.launch

@Composable
fun MainPagerScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    
    // Shared ChatViewModel to trigger clear history in the header
    val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(LocalContext.current))

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
                // Left Title / Logo
                Text(
                    text = "Awa",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                // Center: Custom Glassmorphic Page Switcher
                Row(
                    modifier = Modifier
                        .background(Color(0x0DFFFFFF), shape = RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val pages = listOf("问答", "最近")
                    pages.forEachIndexed { index, title ->
                        val selected = pagerState.currentPage == index
                        val backgroundColor by animateColorAsState(
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
                                .background(backgroundColor)
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
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

                // Right Actions
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (pagerState.currentPage == 0) {
                        IconButton(onClick = { chatViewModel.clearHistory() }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "清空历史", tint = Color.LightGray)
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color.White)
                    }
                }
            }
        },
        containerColor = Color(0xFF0F0C1B), // Consistent deep space background color
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            when (page) {
                0 -> {
                    ChatScreen(
                        showTopBar = false,
                        viewModel = chatViewModel,
                        onBack = {},
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
                1 -> {
                    DashboardScreen(
                        showTopBar = false,
                        onNavigateToChat = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
            }
        }
    }
}
