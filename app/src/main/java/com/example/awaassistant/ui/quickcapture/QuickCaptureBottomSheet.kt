package com.example.awaassistant.ui.quickcapture

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.awaassistant.util.AsrManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickCaptureViewModel = viewModel(factory = QuickCaptureViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var isAsrRecording by remember { mutableStateOf(false) }
    var asrStatusText by remember { mutableStateOf("点击麦克风开始说话") }
    var isAsrProcessing by remember { mutableStateOf(false) }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = AsrManager.startRecording()
            if (started) {
                isAsrRecording = true
                asrStatusText = "正在录音，再次点击结束..."
            } else {
                asrStatusText = "语音引擎初始化中，请稍候..."
            }
        } else {
            asrStatusText = "未授权录音权限"
        }
    }

    // 录音脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by if (isAsrRecording) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isAsrRecording) {
                AsrManager.stopRecording {}
                isAsrRecording = false
            }
            onDismiss()
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color(0xFF1A1430),
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = "✨ AWA 极速录入",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // 输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = "写下此刻的想法...",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8E2DE2),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF8E2DE2)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                    }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 语音输入行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isAsrProcessing) "正在识别..." else asrStatusText,
                        fontSize = 12.sp,
                        color = if (isAsrRecording) Color(0xFF00E5FF) else Color.LightGray
                    )

                    // 录音波形动画
                    if (isAsrRecording) {
                        VoiceWaveformQuickCapture(modifier = Modifier.height(24.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 麦克风按钮
                    IconButton(
                        onClick = {
                            val hasAudioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (isAsrRecording) {
                                asrStatusText = "正在识别..."
                                isAsrProcessing = true
                                AsrManager.stopRecording { text ->
                                    isAsrRecording = false
                                    isAsrProcessing = false
                                    if (text.isNotBlank()) {
                                        inputText = if (inputText.isBlank()) text else "$inputText\n$text"
                                        asrStatusText = "录入完成 ✓"
                                    } else {
                                        asrStatusText = "未检测到语音，请重试"
                                    }
                                }
                            } else {
                                if (hasAudioPermission) {
                                    val started = AsrManager.startRecording()
                                    if (started) {
                                        isAsrRecording = true
                                        asrStatusText = "正在录音，点击结束..."
                                    } else {
                                        asrStatusText = "语音引擎初始化中..."
                                    }
                                } else {
                                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .scale(pulseScale)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = if (isAsrRecording) {
                                        listOf(Color(0xFFFF5722), Color(0xFFFF9800))
                                    } else {
                                        listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
                                    }
                                )
                            )
                    ) {
                        Text(
                            text = if (isAsrRecording) "⬛" else "🎤",
                            fontSize = 24.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 保存按钮
            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.quickSave(inputText) {
                        android.widget.Toast.makeText(
                            context,
                            "已保存 ✨",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = inputText.isNotBlank() && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8E2DE2),
                    disabledContainerColor = Color(0xFF8E2DE2).copy(alpha = 0.3f)
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存中...", fontSize = 16.sp, color = Color.White)
                } else {
                    Text("✨ 极速保存", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun VoiceWaveformQuickCapture(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_qc")
    
    val animValues = List(7) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(400 + index * 80, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Row(
        modifier = modifier.padding(horizontal = 16.dp),
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
                            colors = listOf(Color(0xFF00E5FF), Color(0xFF8E2DE2))
                        )
                    )
            )
        }
    }
}
