package com.example.awaassistant.ui.dashboard

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.awaassistant.data.CaptureRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val records by viewModel.captureRecords.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            "Awa 智能助手",
                            fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0C1B))
                )
            }
        },
        containerColor = if (showTopBar) Color(0xFF0F0C1B) else Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F0C1B), Color(0xFF1E1430), Color(0xFF0D061A))
                    )
                )
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 10.dp,
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        text = "全部记录",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                    )
                }

                if (records.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        EmptyStateCard()
                    }
                } else {
                    items(records, key = { it.id }) { record ->
                        StreamRecordCard(
                            record = record,
                            onClick = { onNavigateToDetail(record.id) },
                            onDelete = { viewModel.deleteRecord(context, record) },
                            onToggleComplete = { viewModel.toggleRecordCompletion(context, record) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x08FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Inbox,
                contentDescription = null,
                tint = Color.DarkGray,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "尚无任何整理记录",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Text(
                "点击「快捷」页开启记录，AI 助手将自动帮您总结整理并建立知识库。",
                fontSize = 11.sp,
                color = Color.DarkGray,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
