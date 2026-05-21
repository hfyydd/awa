package com.example.awaassistant.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.awaassistant.data.SettingsManager
import com.example.awaassistant.service.AwaAccessibilityService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // 加载已有配置
    var apiKey by remember { mutableStateOf(SettingsManager.getApiKey(context)) }
    var baseUrl by remember { mutableStateOf(SettingsManager.getBaseUrl(context)) }
    var modelName by remember { mutableStateOf(SettingsManager.getModelName(context)) }

    var isApiKeyVisible by remember { mutableStateOf(false) }

    // 测试连接的状态变量
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }

    // 辅助功能与悬浮球权限状态
    var isAccessibilityActive by remember { mutableStateOf(AwaAccessibilityService.isServiceRunning) }
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isOverlayRunning by remember { mutableStateOf(com.example.awaassistant.service.FloatingOverlayService.isRunning) }

    // 各种触发方式的状态
    var isFloatingBallEnabled by remember { mutableStateOf(SettingsManager.isFloatingBallEnabled(context)) }
    var isVolumeShortcutEnabled by remember { mutableStateOf(SettingsManager.isVolumeShortcutEnabled(context)) }
    var isAutoAnalyzeScreenshotsEnabled by remember { mutableStateOf(SettingsManager.isAutoAnalyzeScreenshotsEnabled(context)) }

    // 监听生命周期变化，返回前台时重新刷新权限与服务状态
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityActive = AwaAccessibilityService.isServiceRunning
                val nowGranted = Settings.canDrawOverlays(context)
                isOverlayGranted = nowGranted
                isOverlayRunning = com.example.awaassistant.service.FloatingOverlayService.isRunning
                
                // If they enabled the setting, and just came back after granting overlay permission, start service automatically!
                if (isFloatingBallEnabled && nowGranted && !com.example.awaassistant.service.FloatingOverlayService.isRunning) {
                    com.example.awaassistant.service.FloatingOverlayService.start(context)
                    isOverlayRunning = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isAutoAnalyzeScreenshotsEnabled = true
            SettingsManager.setAutoAnalyzeScreenshotsEnabled(context, true)
            Toast.makeText(context, "已授权，系统截屏自动分析已开启", Toast.LENGTH_SHORT).show()
        } else {
            isAutoAnalyzeScreenshotsEnabled = false
            SettingsManager.setAutoAnalyzeScreenshotsEnabled(context, false)
            Toast.makeText(context, "未授权存储权限，无法自动分析系统截屏", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统与模型设置", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            SettingsManager.setApiKey(context, apiKey.trim())
                            SettingsManager.setBaseUrl(context, baseUrl.trim())
                            SettingsManager.setModelName(context, modelName.trim())
                            Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "保存", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0C1B))
            )
        },
        containerColor = Color(0xFF0F0C1B)
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F0C1B), Color(0xFF150E26), Color(0xFF0A0512))
                    )
                )
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. 快速填充预设大模型配置
            Text(
                "大模型服务商预设",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetButton("DeepSeek", modifier = Modifier.weight(1f)) {
                    baseUrl = "https://api.deepseek.com/v1"
                    modelName = "deepseek-chat"
                }
                PresetButton("通义千问", modifier = Modifier.weight(1f)) {
                    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
                    modelName = "qwen-plus"
                }
                PresetButton("智谱 GLM", modifier = Modifier.weight(1f)) {
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4"
                    modelName = "glm-4-flash"
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetButton("火山引擎", modifier = Modifier.weight(1f)) {
                    baseUrl = "https://ark.cn-beijing.volces.com/api/v3"
                    modelName = "ep-xxxxxxxx-xxxx" // 提示用户替换火山 endpoint
                }
                PresetButton("MiniMax", modifier = Modifier.weight(1f)) {
                    baseUrl = "https://api.minimaxi.com/v1"
                    modelName = "MiniMax-M2.7"
                }
                PresetButton("自定义", modifier = Modifier.weight(1f)) {
                    baseUrl = ""
                    modelName = ""
                }
            }

            Divider(color = Color(0x11FFFFFF))

            // 2. 模型核心输入参数表单
            Text(
                "大模型 API 配置",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )

            // Base URL 输入
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL (API 接口地址)", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8E2DE2),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedLabelColor = Color(0xFF8E2DE2),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // API Key 输入 (密码模式可选可见)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key (密钥)", color = Color.Gray) },
                singleLine = true,
                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                        Icon(
                            imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isApiKeyVisible) "隐藏密码" else "显示密码",
                            tint = Color.Gray
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8E2DE2),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedLabelColor = Color(0xFF8E2DE2),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // Model Name 输入
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model Name (模型名称)", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8E2DE2),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedLabelColor = Color(0xFF8E2DE2),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // 测试连接按钮与结果展示
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isTesting = true
                        testResult = "正在测试与大模型的连接，请稍候..."
                        isTestSuccess = false
                        coroutineScope.launch {
                            val (success, result) = com.example.awaassistant.data.OpenAiCompatibleClient.testConnection(
                                baseUrl = baseUrl.trim(),
                                apiKey = apiKey.trim(),
                                model = modelName.trim()
                            )
                            isTesting = false
                            isTestSuccess = success
                            testResult = if (success) "连接测试成功！模型回复: \"$result\"" else "连接测试失败: $result"
                        }
                    },
                    enabled = !isTesting && apiKey.isNotEmpty() && baseUrl.isNotEmpty() && modelName.isNotEmpty(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x228E2DE2),
                        contentColor = Color(0xFFE0C3FC),
                        disabledContainerColor = Color(0x11FFFFFF),
                        disabledContentColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            color = Color(0xFF8E2DE2),
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("测试大模型连接")
                }

                testResult?.let { result ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isTesting) Color(0x11FFFFFF)
                                            else if (isTestSuccess) Color(0x2200E676)
                                            else Color(0x22FF1744)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = result,
                            fontSize = 11.sp,
                            color = if (isTesting) Color.LightGray
                                    else if (isTestSuccess) Color.Green
                                    else Color.Red,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Divider(color = Color(0x11FFFFFF))

            // 3. 助手触发方式配置
            Text(
                "助手触发方式配置",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. 悬浮球开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("显示屏幕截屏悬浮球", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("在屏幕边缘显示圆形快捷悬浮球", fontSize = 10.sp, color = Color.LightGray)
                        }
                        val isBallActive = isFloatingBallEnabled && isOverlayGranted && isOverlayRunning
                        Switch(
                            checked = isBallActive,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (Settings.canDrawOverlays(context)) {
                                        SettingsManager.setFloatingBallEnabled(context, true)
                                        isFloatingBallEnabled = true
                                        com.example.awaassistant.service.FloatingOverlayService.start(context)
                                        isOverlayRunning = true
                                    } else {
                                        // 引导授权
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    }
                                } else {
                                    SettingsManager.setFloatingBallEnabled(context, false)
                                    isFloatingBallEnabled = false
                                    com.example.awaassistant.service.FloatingOverlayService.stop(context)
                                    isOverlayRunning = false
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF8E2DE2),
                                checkedTrackColor = Color(0x338E2DE2)
                            )
                        )
                    }

                    Divider(color = Color(0x08FFFFFF))

                    // 2. 音量按键开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("音量键快捷触发", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("双击音量下键或同时按音量上下键触发分析", fontSize = 10.sp, color = Color.LightGray)
                        }
                        Switch(
                            checked = isVolumeShortcutEnabled,
                            onCheckedChange = { enabled ->
                                isVolumeShortcutEnabled = enabled
                                SettingsManager.setVolumeShortcutEnabled(context, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF8E2DE2),
                                checkedTrackColor = Color(0x338E2DE2)
                            )
                        )
                    }

                    Divider(color = Color(0x08FFFFFF))

                    // 3. 系统截屏开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("系统截屏自动分析", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("检测到系统自带截屏时，自动提取并智能分析", fontSize = 10.sp, color = Color.LightGray)
                        }
                        Switch(
                            checked = isAutoAnalyzeScreenshotsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.READ_MEDIA_IMAGES
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    } else {
                                        androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    }
                                    if (hasPerm) {
                                        isAutoAnalyzeScreenshotsEnabled = true
                                        SettingsManager.setAutoAnalyzeScreenshotsEnabled(context, true)
                                    } else {
                                        storagePermissionLauncher.launch(permissionToRequest)
                                    }
                                } else {
                                    isAutoAnalyzeScreenshotsEnabled = false
                                    SettingsManager.setAutoAnalyzeScreenshotsEnabled(context, false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF8E2DE2),
                                checkedTrackColor = Color(0x338E2DE2)
                            )
                        )
                    }
                }
            }

            Divider(color = Color(0x11FFFFFF))

            // 3. 系统权限设置与快速通道
            Text(
                "助手系统权限管理",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PermissionRow(
                        title = "辅助服务状态",
                        description = "用于手势监听、静默截图及分析文字树",
                        isGranted = isAccessibilityActive,
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    )

                    Divider(color = Color(0x08FFFFFF))

                    PermissionRow(
                        title = "悬浮窗层叠权限",
                        description = "允许在其他应用上方显示截屏触发悬浮球",
                        isGranted = isOverlayGranted,
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // 4. 温馨提示
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x10FF5722)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFF5722))
                    Text(
                        "大模型服务由用户自行提供密钥，App 本地不做收集。如果提取失败，请检查手机是否联网，以及 API Key 与 URL 是否拼写正确。",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PresetButton(
    name: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0x15FFFFFF),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier
    ) {
        Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(description, fontSize = 10.sp, color = Color.LightGray)
        }
        
        Badge(
            containerColor = if (isGranted) Color(0x2200E676) else Color(0x22FF1744),
            contentColor = if (isGranted) Color.Green else Color.Red,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(
                if (isGranted) "已授权" else "去设置",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
