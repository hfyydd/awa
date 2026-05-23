package com.example.awaassistant.ui.quickcapture

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.awaassistant.ui.dashboard.ActivityHeatmap
import com.example.awaassistant.ui.dashboard.MemoryCapsuleCard
import com.example.awaassistant.ui.dashboard.DashboardViewModel
import java.io.File

// P0: 快捷入口页（从 DashboardScreen 中拆分出来）
@Composable
fun QuickActionsScreen(
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    var tempPhotoPath by remember { mutableStateOf<String?>(null) }
    var tempPhotoUriString by remember { mutableStateOf<String?>(null) }
    var captureMode by remember { mutableStateOf("NOTE") }
    var showRecipeDialog by remember { mutableStateOf(false) }
    var showCalorieOptionDialog by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.processCalorieGalleryPhoto(context, uri)
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

    val isProcessing by viewModel.isProcessingPhoto.collectAsStateWithLifecycle()
    val processingType by viewModel.processingType.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F0C1B), Color(0xFF1E1430), Color(0xFF0D061A))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 时光胶囊
        MemoryCapsuleCard(
            onDismiss = { },
            onViewDetail = { }
        )

        // 2. 热力图
        ActivityHeatmap()

        // 3. 处理中加载状态
        if (isProcessing) {
            ProcessingLoaderCard(processingType = processingType ?: "PHOTO")
        }

        // 4. 快捷行动按钮
        Text(
            text = "快捷入口",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 8.dp)
        )

        Column(
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
                    onClick = { showCalorieOptionDialog = true },
                    modifier = Modifier.weight(1f)
                )

                ActionCard(
                    title = "拍照配菜谱",
                    subtitle = "智能搭配家常食谱",
                    icon = Icons.Default.MenuBook,
                    gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
                    onClick = { showRecipeDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // 便签输入弹窗（复用 Dashboard 的 AlertDialog 逻辑）
    if (showNoteDialog) {
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
        val pulseScale by if (isAsrRecording) {
            infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.15f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
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
                Text("新建便签/待办", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = noteInputText,
                        onValueChange = { noteInputText = it },
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                            IconButton(
                                onClick = {
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
                                                asrStatusText = "语音录入完成！"
                                            } else {
                                                asrStatusText = "未检测到有效语音"
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
                                modifier = Modifier.size(56.dp)
                            ) {
                                Text(text = if (isAsrRecording) "⬛" else "🎤", fontSize = 24.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteInputText.isNotBlank()) {
                            viewModel.saveQuickNote(noteInputText)
                            noteInputText = ""
                            showNoteDialog = false
                            android.widget.Toast.makeText(context, "已保存 ✨", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E2DE2)),
                    shape = RoundedCornerShape(20.dp),
                    enabled = noteInputText.isNotBlank()
                ) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("取消", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1A1430),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun ActionCard(
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
        modifier = modifier.height(120.dp).clickable(onClick = onClick)
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
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Column {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ProcessingLoaderCard(processingType: String) {
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
        "CALORIE" -> "正在分析食物卡路里..."
        else -> "正在 OCR 提取..."
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
            CircularProgressIndicator(color = accentColor, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Column {
                Text(titleText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor)
                Text("请勿关闭页面", fontSize = 11.sp, color = Color.LightGray)
            }
        }
    }
}

private fun createTempPhotoFile(context: Context): Pair<File, Uri> {
    val tempFile = File(context.cacheDir, "camera_temp_${System.currentTimeMillis()}.jpg")
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.awaassistant.provider", tempFile)
    return Pair(tempFile, uri)
}
