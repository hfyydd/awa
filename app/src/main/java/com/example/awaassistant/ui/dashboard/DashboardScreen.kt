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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val focusManager = LocalFocusManager.current
    val records by viewModel.captureRecords.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var showNoteDialog by remember { mutableStateOf(false) }
    var noteInputText by remember { mutableStateOf("") }
    var isAsrRecording by remember { mutableStateOf(false) }
    var asrStatusText by remember { mutableStateOf("点击麦克风开始说话") }
    var isAsrProcessing by remember { mutableStateOf(false) }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = com.example.awaassistant.util.AsrManager.startRecording()
            if (started) {
                isAsrRecording = true
                asrStatusText = "正在录音，再次点击结束..."
            } else {
                android.widget.Toast.makeText(context, "语音引擎初始化中，请稍候...", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, "未授权录音权限", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        contentWindowInsets = if (showTopBar) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    noteInputText = ""
                    showNoteDialog = true
                },
                containerColor = Color(0xFF8E2DE2),
                contentColor = Color.White,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(Icons.Default.Add, "新建便签", modifier = Modifier.size(24.dp))
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
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { focusManager.clearFocus() }
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalItemSpacing = 10.dp,
                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("搜索标题、内容或标签...", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "搜索",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "清空",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0x1FFFFFFF),
                                unfocusedContainerColor = Color(0x0DFFFFFF),
                                focusedBorderColor = Color(0xFF8E2DE2),
                                unfocusedBorderColor = Color(0x1AFFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF8E2DE2)
                            )
                        )
                    }
                }

                val pendingRecords = records.filter { !it.isCompleted }
                val completedRecords = records.filter { it.isCompleted }

                if (pendingRecords.isEmpty() && completedRecords.isEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        EmptyStateCard()
                    }
                } else {
                    // 进行中的记录
                    items(pendingRecords, key = { it.id }) { record ->
                        StreamRecordCard(
                            record = record,
                            onClick = { onNavigateToDetail(record.id) },
                            onDelete = { viewModel.deleteRecord(context, record) },
                            onToggleComplete = { viewModel.toggleRecordCompletion(context, record) }
                        )
                    }

                    // 已办结分隔栏
                    if (completedRecords.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(0.5.dp)
                                        .background(Color.Gray.copy(alpha = 0.3f))
                                )
                                Text(
                                    text = "已办结 (${completedRecords.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray.copy(alpha = 0.6f)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(0.5.dp)
                                        .background(Color.Gray.copy(alpha = 0.3f))
                                )
                            }
                        }

                        // 已完成的记录
                        items(completedRecords, key = { it.id }) { record ->
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

    if (showNoteDialog) {
        NoteInputDialog(
            noteText = noteInputText,
            onTextChange = { noteInputText = it },
            isAsrRecording = isAsrRecording,
            asrStatusText = asrStatusText,
            isAsrProcessing = isAsrProcessing,
            onMicClick = {
                val hasAudioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (isAsrRecording) {
                    asrStatusText = "正在识别..."
                    isAsrProcessing = true
                    com.example.awaassistant.util.AsrManager.stopRecording { text ->
                        isAsrRecording = false
                        isAsrProcessing = false
                        if (text.isNotBlank()) {
                            noteInputText = if (noteInputText.isBlank()) text else "$noteInputText\n$text"
                            asrStatusText = "录入完成 ✓"
                        } else {
                            asrStatusText = "未检测到语音"
                        }
                    }
                } else {
                    if (hasAudioPermission) {
                        val started = com.example.awaassistant.util.AsrManager.startRecording()
                        if (started) {
                            isAsrRecording = true
                            asrStatusText = "正在录音，点击结束..."
                        }
                    } else {
                        recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            onSave = {
                if (noteInputText.isNotBlank()) {
                    viewModel.saveQuickNote(noteInputText)
                    noteInputText = ""
                    showNoteDialog = false
                    android.widget.Toast.makeText(context, "已保存 ✨", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                if (isAsrRecording) {
                    com.example.awaassistant.util.AsrManager.stopRecording {}
                    isAsrRecording = false
                }
                showNoteDialog = false
            }
        )
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

@Composable
private fun NoteInputDialog(
    noteText: String,
    onTextChange: (String) -> Unit,
    isAsrRecording: Boolean,
    asrStatusText: String,
    isAsrProcessing: Boolean,
    onMicClick: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建便签/待办", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = onTextChange,
                    placeholder = { Text("写下你的便签...", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8E2DE2),
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isAsrProcessing) "正在识别语音..." else asrStatusText,
                            fontSize = 11.sp,
                            color = if (isAsrRecording) Color(0xFF00E5FF) else Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        IconButton(onClick = onMicClick, modifier = Modifier.size(56.dp)) {
                            Text(text = if (isAsrRecording) "⬛" else "🎤", fontSize = 24.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E2DE2)),
                shape = RoundedCornerShape(20.dp),
                enabled = noteText.isNotBlank()
            ) { Text("保存", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color.LightGray) } },
        containerColor = Color(0xFF1A1430),
        shape = RoundedCornerShape(20.dp)
    )
}

