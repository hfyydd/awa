package com.example.awaassistant.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.awaassistant.data.CaptureRecord
import com.example.awaassistant.data.ReminderItem
import com.example.awaassistant.util.NutrientParser
import com.example.awaassistant.service.FloatingOverlayService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val reminders by viewModel.activeReminders.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingPhoto.collectAsStateWithLifecycle()
    val processingType by viewModel.processingType.collectAsStateWithLifecycle()
    val isAccessibilityActive by viewModel.isAccessibilityActive.collectAsStateWithLifecycle()

    // 监控悬浮球是否开启的本地状态
    var isOverlayRunning by remember { mutableStateOf(FloatingOverlayService.isRunning) }

    // 监听生命周期变化，在返回前台（ON_RESUME）时刷新辅助功能与悬浮球服务状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAccessibilityStatus()
                isOverlayRunning = FloatingOverlayService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 拍照所用的临时文件 and Uri
    var tempPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var tempPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var captureMode by rememberSaveable { mutableStateOf("NOTE") }
    var showRecipeDialog by remember { mutableStateOf(false) }
    var showCalorieOptionDialog by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processCalorieGalleryPhoto(context, uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val path = tempPhotoPath
            val uriStr = tempPhotoUriString
            if (path != null && uriStr != null) {
                val file = File(path)
                val uri = Uri.parse(uriStr)
                if (captureMode == "CALORIE") {
                    viewModel.processCaloriePhoto(context, uri, file)
                } else {
                    viewModel.processPhotoNote(context, uri, file)
                }
            }
        }
    }

    var showNoteDialog by remember { mutableStateOf(false) }
    var noteInputText by remember { mutableStateOf("") }

    var isAsrRecording by remember { mutableStateOf(false) }
    var asrStatusText by remember { mutableStateOf("点击下方麦克风开始说话") }
    var isAsrProcessing by remember { mutableStateOf(false) }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = com.example.awaassistant.util.AsrManager.startRecording()
            if (started) {
                isAsrRecording = true
                asrStatusText = "正在录音，再次点击麦克风结束..."
            } else {
                android.widget.Toast.makeText(context, "语音引擎正在初始化，请稍候...", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, "未授权录音权限，无法使用语音输入", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F0C1B) // 深空色顶栏
                    )
                )
            }
        },
        containerColor = if (showTopBar) Color(0xFF0F0C1B) else Color.Transparent // 极简深色大底
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 1. 助手运行状态和欢迎卡
                item {
                    StatusHeader(
                        isAccessibilityActive = isAccessibilityActive,
                        isOverlayRunning = isOverlayRunning,
                        recordsCount = records.size
                    )
                }

                // P1: 时光胶囊卡片
                item {
                    MemoryCapsuleCard(
                        onDismiss = { },
                        onViewDetail = { }
                    )
                }

                // P2: 思考足迹热力图
                item {
                    ActivityHeatmap()
                }

                // 2. 快捷行动按钮
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActionCard(
                                title = "拍照记笔记",
                                subtitle = "纸质笔记 OCR 提炼",
                                icon = Icons.Default.CameraAlt,
                                gradientColors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
                                onClick = {
                                    captureMode = "NOTE"
                                    val pair = createTempPhotoFile(context)
                                    tempPhotoPath = pair.first.absolutePath
                                    tempPhotoUriString = pair.second.toString()
                                    cameraLauncher.launch(pair.second)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            ActionCard(
                                title = "记录便签",
                                subtitle = "手动输入便签/待办",
                                icon = Icons.Default.Edit,
                                gradientColors = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)),
                                onClick = {
                                    noteInputText = ""
                                    showNoteDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActionCard(
                                title = "卡路里识别",
                                subtitle = "拍照估算食物热量",
                                icon = Icons.Default.Restaurant,
                                gradientColors = listOf(Color(0xFFFF9966), Color(0xFFFF5E62)),
                                onClick = {
                                    showCalorieOptionDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            )

                            ActionCard(
                                title = "拍照配菜谱",
                                subtitle = "智能搭配家常食谱",
                                icon = Icons.Default.MenuBook,
                                gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
                                onClick = {
                                    showRecipeDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. 正在处理中的加载状态
                if (isProcessing) {
                    item {
                        ProcessingLoaderCard(processingType = processingType ?: "PHOTO")
                    }
                }

                // 4. 代办智能提醒
                if (reminders.isNotEmpty()) {
                    item {
                        RemindersSection(
                            reminders = reminders,
                            onReminderClick = { recordId -> onNavigateToDetail(recordId) }
                        )
                    }
                }

                // 5. 笔记记录标题
                item {
                    Text(
                        text = "最近记录整理",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // 6. 记录卡片列表
                if (records.isEmpty()) {
                    item {
                        EmptyStateCard()
                    }
                } else {
                    items(records, key = { it.id }) { record ->
                        CaptureRecordCard(
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

    if (showNoteDialog) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by if (isAsrRecording) {
            infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )
        } else {
            remember { mutableStateOf(1.0f) }
        }

        AlertDialog(
            onDismissRequest = {
                if (isAsrRecording) {
                    com.example.awaassistant.util.AsrManager.stopRecording {}
                    isAsrRecording = false
                }
                showNoteDialog = false
            },
            title = {
                Text(
                    text = "新建便签/待办",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = noteInputText,
                        onValueChange = { noteInputText = it },
                        placeholder = {
                            Text(
                                text = "写下你的便签，例如：\n“明天下午3点打电话给张三开会”\n“周五下班前买牛奶”",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8E2DE2),
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // 语音输入行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isAsrProcessing) "正在识别语音，请稍候..." else asrStatusText,
                                fontSize = 11.sp,
                                color = if (isAsrRecording) Color(0xFF00E5FF) else Color.LightGray,
                                fontWeight = FontWeight.Medium
                            )

                            if (isAsrRecording) {
                                Spacer(modifier = Modifier.height(4.dp))
                                VoiceWaveform(isRecording = true)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            IconButton(
                                onClick = {
                                    val hasAudioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                    if (isAsrRecording) {
                                        asrStatusText = "正在识别..."
                                        isAsrProcessing = true
                                        com.example.awaassistant.util.AsrManager.stopRecording { text ->
                                            isAsrRecording = false
                                            isAsrProcessing = false
                                            if (text.trim().isNotEmpty()) {
                                                noteInputText = if (noteInputText.isEmpty()) text else "$noteInputText\n$text"
                                                asrStatusText = "语音录入完成！"
                                            } else {
                                                asrStatusText = "未检测到有效语音，请点击重试"
                                            }
                                        }
                                    } else {
                                        if (hasAudioPermission) {
                                            val started = com.example.awaassistant.util.AsrManager.startRecording()
                                            if (started) {
                                                isAsrRecording = true
                                                asrStatusText = "正在录音，再次点击麦克风结束..."
                                            } else {
                                                android.widget.Toast.makeText(context, "语音引擎正在初始化，请稍候...", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .graphicsLayer(
                                        scaleX = pulseScale,
                                        scaleY = pulseScale
                                    )
                                    .background(
                                        brush = if (isAsrRecording) {
                                            Brush.radialGradient(
                                                colors = listOf(Color(0xFF00E5FF), Color(0x3300E5FF), Color.Transparent)
                                            )
                                        } else {
                                            Brush.radialGradient(
                                                colors = listOf(Color(0xFF8E2DE2), Color(0x228E2DE2), Color.Transparent)
                                            )
                                        },
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isAsrRecording) Color(0xFF00E5FF) else Color(0x33FFFFFF),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            ) {
                                if (isAsrProcessing) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF8E2DE2),
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isAsrRecording) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = "语音输入",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteInputText.trim().isNotEmpty()) {
                            viewModel.addNewNote(context, noteInputText)
                            showNoteDialog = false
                        }
                    },
                    enabled = noteInputText.trim().isNotEmpty() && !isAsrRecording && !isAsrProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8E2DE2),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0x338E2DE2),
                        disabledContentColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (isAsrRecording) {
                            com.example.awaassistant.util.AsrManager.stopRecording {}
                            isAsrRecording = false
                        }
                        showNoteDialog = false
                    }
                ) {
                    Text("取消", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1E1430),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showRecipeDialog) {
        AlertDialog(
            onDismissRequest = { showRecipeDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = Color(0xFF38EF7D)
                    )
                    Text(
                        text = "拍照配菜谱",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Text(
                    text = "AI 智能菜谱生成功能正在开发中，敬请期待！\n未来您只需拍摄食材照片，AI 将为您智能搭配并推荐定制的健康食谱。",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showRecipeDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8E2DE2),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("我知道了")
                }
            },
            containerColor = Color(0xFF1E1430),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showCalorieOptionDialog) {
        AlertDialog(
            onDismissRequest = { showCalorieOptionDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = Color(0xFFFF9966)
                    )
                    Text(
                        text = "选择照片来源",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Text(
                    text = "请选择估算食物卡路里的照片来源。您可以直接拍摄照片，也可以从手机相册中进行选择。",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCalorieOptionDialog = false
                        captureMode = "CALORIE"
                        val pair = createTempPhotoFile(context)
                        tempPhotoPath = pair.first.absolutePath
                        tempPhotoUriString = pair.second.toString()
                        cameraLauncher.launch(pair.second)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9966),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("拍摄新照片")
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showCalorieOptionDialog = false
                        galleryLauncher.launch("image/*")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x22FFFFFF),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("相册中选择")
                    }
                }
            },
            containerColor = Color(0xFF1E1430),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun StatusHeader(
    isAccessibilityActive: Boolean,
    isOverlayRunning: Boolean,
    recordsCount: Int
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x0AFFFFFF) // Super subtle white glassmorphism
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x10FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Awa 智能助手",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    // Small status badge
                    val statusText = if (isAccessibilityActive) "服务就绪" else "服务未开启"
                    val statusColor = if (isAccessibilityActive) Color(0xFF00E676) else Color(0xFFFF1744)
                    val statusBg = if (isAccessibilityActive) Color(0x2200E676) else Color(0x22FF1744)
                    
                    Badge(
                        containerColor = statusBg,
                        contentColor = statusColor
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(statusColor)
                            )
                            Text(statusText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (recordsCount > 0) "已为您智能总结并建立 ${recordsCount} 个知识点" else "随时检测屏幕或截图，开启您的智能知识库",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        modifier = modifier
            .height(120.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                
                Column {
                    Text(
                        title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        subtitle,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessingLoaderCard(processingType: String) {
    val cardColor = when (processingType) {
        "TEXT" -> Color(0x228E2DE2)
        "CALORIE" -> Color(0x2200E676)
        else -> Color(0x22FFB300)
    }
    val accentColor = when (processingType) {
        "TEXT" -> Color(0xFFC51162)
        "CALORIE" -> Color(0xFF00E676)
        else -> Color.Yellow
    }
    val titleText = when (processingType) {
        "TEXT" -> "正在调用 AI 整理便签..."
        "CALORIE" -> "正在分析食物卡路里并提供建议..."
        else -> "正在 OCR 提取并调用 AI 总结..."
    }
    val subtitleText = when (processingType) {
        "TEXT" -> "正在通过 AI 智能提炼便签并提取提醒，请勿关闭页面"
        "CALORIE" -> "正在通过 AI 估算餐食营养成分，请稍候"
        else -> "正在通过本地 ML Kit 提取文字并组织语言，请勿关闭页面"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = accentColor,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp
            )
            Column {
                Text(
                    titleText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    subtitleText,
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun RemindersSection(
    reminders: List<ReminderItem>,
    onReminderClick: (Long) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1F2DE28E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Alarm,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "待办智能提醒",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reminders.take(3).forEach { reminder ->
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val formattedTime = sdf.format(Date(reminder.reminderTime))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x11FFFFFF))
                            .clickable { onReminderClick(reminder.recordId) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            reminder.title,
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            formattedTime,
                            fontSize = 10.sp,
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureRecordCard(
    record: CaptureRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleComplete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (record.isCompleted) Color(0x05FFFFFF) else Color(0x0CFFFFFF)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 本地截图/照片的预览图 (缩略图)
            if (record.imagePath != null) {
                AsyncImage(
                    model = record.imagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                )
            } else {
                // 纯文本便签/待办使用圆形复选框交互设计
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (record.isCompleted) Color(0x1F00E676) else Color(0x1F8E2DE2))
                        .clickable { onToggleComplete() },
                    contentAlignment = Alignment.Center
                ) {
                    if (record.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "已完成",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Transparent)
                                .border(2.dp, Color(0xFF8E2DE2), RoundedCornerShape(12.dp))
                        )
                    }
                }
            }

            // 文档总结和标题
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (record.isCompleted) Color.Gray else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (record.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = record.summary.replace(Regex("[#*`\\-]"), "").trim(), // 剥离 Markdown 符号用于预览
                    fontSize = 11.sp,
                    color = if (record.isCompleted) Color.Gray else Color.LightGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (record.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )

                if (record.sourceType == "CALORIE") {
                    val info = NutrientParser.parseNutrients(record.summary)
                    if (info != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val total = info.protein + info.fat + info.carbs
                            if (total > 0f) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                ) {
                                    if (info.protein > 0f) {
                                        Box(modifier = Modifier.weight(info.protein).fillMaxHeight().background(Color(0xFF00E676)))
                                    }
                                    if (info.fat > 0f) {
                                        Box(modifier = Modifier.weight(info.fat).fillMaxHeight().background(Color(0xFFFFB300)))
                                    }
                                    if (info.carbs > 0f) {
                                        Box(modifier = Modifier.weight(info.carbs).fillMaxHeight().background(Color(0xFF8E2DE2)))
                                    }
                                }
                            }
                            Text(
                                text = "${info.calories} kcal",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 标签展示
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 添加时间戳 Badge
                    val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
                    Badge(
                        containerColor = Color(0x22FFFFFF),
                        contentColor = Color.LightGray
                    ) {
                        Text(dateStr, fontSize = 9.sp)
                    }

                    // 添加来源类型 Badge
                    val typeText = when (record.sourceType) {
                        "SCREENSHOT" -> "屏幕截取"
                        "PHOTO" -> "工作笔记"
                        "CALORIE" -> "卡路里记录"
                        else -> "纯文抓取"
                    }
                    val badgeBg = when (record.sourceType) {
                        "CALORIE" -> Color(0x3300E676)
                        else -> Color(0x334A00E0)
                    }
                    val badgeColor = when (record.sourceType) {
                        "CALORIE" -> Color(0xFF00E676)
                        else -> Color(0xFFB39DFF)
                    }
                    Badge(
                        containerColor = badgeBg,
                        contentColor = badgeColor
                    ) {
                        Text(typeText, fontSize = 9.sp)
                    }

                    if (record.tags.isNotEmpty()) {
                        record.tags.split(",").take(3).forEach { tag ->
                            val cleanTag = tag.trim()
                            if (cleanTag.isNotEmpty()) {
                                val isProcessingTag = cleanTag == "处理中"
                                Badge(
                                    containerColor = if (isProcessingTag) Color(0x22FFB300) else Color(0x1F2DE28E),
                                    contentColor = if (isProcessingTag) Color.Yellow else Color(0xFF00E676)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isProcessingTag) {
                                            CircularProgressIndicator(
                                                color = Color.Yellow,
                                                modifier = Modifier.size(8.dp),
                                                strokeWidth = 1.dp
                                            )
                                        }
                                        Text(cleanTag, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
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
                "请点击开启屏幕截屏悬浮球，或者点击拍照记录每日笔记，AI 助手将自动帮您总结整理并建立知识库。",
                fontSize = 11.sp,
                color = Color.DarkGray,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun createTempPhotoFile(context: Context): Pair<File, Uri> {
    val tempFile = File(context.cacheDir, "camera_temp_${System.currentTimeMillis()}.jpg")
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "com.example.awaassistant.provider",
        tempFile
    )
    return Pair(tempFile, uri)
}

@Composable
fun VoiceWaveform(isRecording: Boolean, modifier: Modifier = Modifier) {
    if (!isRecording) return

    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    
    val animValues = List(7) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400 + index * 80, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Row(
        modifier = modifier
            .height(32.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        animValues.forEach { animValue ->
            val barHeight = 24.dp * animValue.value
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                Color(0xFF8E2DE2)
                            )
                        )
                    )
            )
        }
    }
}

