package com.example.awaassistant.ui.detail

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.awaassistant.data.AppDatabase
import com.example.awaassistant.data.CaptureRecord
import com.example.awaassistant.data.ReminderItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    
    val db = remember { AppDatabase.getDatabase(context) }
    val dao = remember { db.appDao() }

    var record by remember { mutableStateOf<CaptureRecord?>(null) }
    var reminders by remember { mutableStateOf<List<ReminderItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showRawText by remember { mutableStateOf(false) }

    LaunchedEffect(recordId) {
        isLoading = true
        record = dao.getCaptureById(recordId)
        reminders = dao.getRemindersForRecord(recordId)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(record?.title ?: "记录详情", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0C1B))
            )
        },
        containerColor = Color(0xFF0F0C1B)
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF8E2DE2))
            }
        } else if (record == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("未找到该条记录，可能已被删除。", color = Color.LightGray)
            }
        } else {
            val currentRecord = record!!
            val scrollState = rememberScrollState()

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F0C1B), Color(0xFF140E23), Color(0xFF0C0715))
                        )
                    )
                    .verticalScroll(scrollState)
            ) {
                // 1. 如果有截图/照片，居中全幅显示
                if (currentRecord.imagePath != null) {
                    Card(
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        AsyncImage(
                            model = currentRecord.imagePath,
                            contentDescription = "图片内容",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .background(Color.Black)
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 2. 元数据面板 (标签 & 来源 & 时间)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val typeText = when (currentRecord.sourceType) {
                            "SCREENSHOT" -> "来自屏幕截取"
                            "PHOTO" -> "来自手拍笔记"
                            else -> "纯文字提取"
                        }
                        Text(
                            typeText,
                            fontSize = 12.sp,
                            color = Color(0xFF8E2DE2),
                            fontWeight = FontWeight.SemiBold
                        )

                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        Text(
                            sdf.format(Date(currentRecord.timestamp)),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }

                    // 标签流
                    if (currentRecord.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            currentRecord.tags.split(",").forEach { tag ->
                                if (tag.trim().isNotEmpty()) {
                                    Badge(
                                        containerColor = Color(0x1F2DE28E),
                                        contentColor = Color(0xFF00E676),
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(tag, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = Color(0x11FFFFFF))

                    // 3. AI 总结板块
                    Text("AI 提炼总结", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = currentRecord.summary,
                                fontSize = 13.sp,
                                color = Color.White,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // 4. 定时提醒事项列表
                    if (reminders.isNotEmpty()) {
                        Divider(color = Color(0x11FFFFFF))
                        Text("关联的提醒日程", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        
                        reminders.forEach { reminder ->
                            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(reminder.reminderTime))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x0CFFFFFF))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                    Text(
                                        reminder.title,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    timeStr,
                                    fontSize = 11.sp,
                                    color = Color(0xFF00E676)
                                )
                            }
                        }
                    }

                    Divider(color = Color(0x11FFFFFF))

                    // 5. 原始识别文本 (折叠展示)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRawText = !showRawText }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("查看提取的原文字幕", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Icon(
                            imageVector = if (showRawText) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.LightGray
                        )
                    }

                    AnimatedVisibility(visible = showRawText) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x06FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("原始文本信息", fontSize = 11.sp, color = Color.Gray)
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(currentRecord.rawContent))
                                            Toast.makeText(context, "已复制原文到剪贴板", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "复制",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = currentRecord.rawContent,
                                    fontSize = 12.sp,
                                    color = Color.LightGray,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
