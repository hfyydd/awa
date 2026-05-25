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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val sessions by chatViewModel.sessions.collectAsState()
    val currentSessionId by chatViewModel.currentSessionId.collectAsState()

    LaunchedEffect(pagerState) {
        // 首次显示时初始化共享数据（只触发一次）
        sharedViewModel.initialize()
    }

    LaunchedEffect(showQuickCapture) {
        if (showQuickCapture) isQuickCaptureVisible = true
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF151026),
                modifier = Modifier.width(280.dp).fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Awa 历史对话",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // New Chat Button
                    Button(
                        onClick = {
                            chatViewModel.startNewSession()
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                                drawerState.close()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E2DE2))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新对话", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("新建对话", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Sessions List
                    if (sessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无历史对话", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(sessions) { session ->
                                val isSelected = session.id == currentSessionId
                                val dateStr = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.lastUpdated))
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) Color(0x338E2DE2) else Color(0x0CFFFFFF))
                                        .border(1.dp, if (isSelected) Color(0xFF8E2DE2) else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickable {
                                            chatViewModel.selectSession(session.id)
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(0)
                                                drawerState.close()
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = dateStr,
                                            color = Color.Gray,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = { chatViewModel.deleteSession(session.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除记录",
                                            tint = if (isSelected) Color.White else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
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
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                coroutineScope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "菜单",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Awa",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier
                            .background(Color(0x0DFFFFFF), shape = RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("问答", "记录", "快捷").forEachIndexed { index, title ->
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                    1 -> DashboardScreen(
                        showTopBar = false,
                        onNavigateToChat = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToDetail = onNavigateToDetail,
                        viewModel = dashboardViewModel
                    )
                    2 -> QuickActionsScreen(
                        onNavigateToDetail = onNavigateToDetail,
                        sharedViewModel = sharedViewModel,
                        dashboardViewModel = dashboardViewModel
                    )
                }
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
