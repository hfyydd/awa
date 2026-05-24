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
import com.example.awaassistant.ui.detail.NoteDetailViewModel
import com.example.awaassistant.data.ReminderItem
import com.example.awaassistant.data.OpenAiCompatibleClient
import com.example.awaassistant.util.ReminderScheduler
import androidx.compose.material.icons.filled.Delete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.awaassistant.util.NutrientInfo
import com.example.awaassistant.util.NutrientParser
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.shape.CircleShape
import android.util.Log
import androidx.compose.foundation.BorderStroke


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    
    val db = remember { AppDatabase.getDatabase(context) }
    val dao = remember { db.appDao() }
    val viewModel = remember { NoteDetailViewModel.Factory(context).create(NoteDetailViewModel::class.java) }

    var record by remember { mutableStateOf<CaptureRecord?>(null) }
    var reminders by remember { mutableStateOf<List<ReminderItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showRawText by remember { mutableStateOf(false) }
    var isAiAnalyzing by remember { mutableStateOf(false) }

    LaunchedEffect(recordId) {
        isLoading = true
        record = dao.getCaptureById(recordId)
        reminders = dao.getRemindersForRecord(recordId)
        isLoading = false
    }

    // P1: 加载关联旧思绪
    LaunchedEffect(record) {
        record?.let { viewModel.loadRelatedRecords(it) }
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
    val relatedRecords by viewModel.relatedRecords.collectAsState()

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
                            "CALORIE" -> "来自健康卡路里记录"
                            "RECIPE" -> "来自智能食谱搭配"
                            else -> "纯文字提取"
                        }
                        val typeColor = when (currentRecord.sourceType) {
                            "SCREENSHOT" -> Color(0xFF00C9FF)
                            "PHOTO" -> Color(0xFF8E2DE2)
                            "CALORIE" -> Color(0xFF00E676)
                            "RECIPE" -> Color(0xFF11998E)
                            else -> Color(0xFF8E2DE2)
                        }
                        Text(
                            typeText,
                            fontSize = 12.sp,
                            color = typeColor,
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
                                    val (tagBg, tagContent) = when (currentRecord.sourceType) {
                                        "CALORIE" -> Pair(Color(0x1F00E676), Color(0xFF00E676))
                                        "RECIPE" -> Pair(Color(0x1F11998E), Color(0xFF11998E))
                                        "SCREENSHOT" -> Pair(Color(0x1F00C9FF), Color(0xFF00C9FF))
                                        "PHOTO" -> Pair(Color(0x1F8E2DE2), Color(0xFF8E2DE2))
                                        else -> Pair(Color(0x1F8E2DE2), Color(0xFF8E2DE2))
                                    }
                                    Badge(
                                        containerColor = tagBg,
                                        contentColor = tagContent,
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
                    if (currentRecord.sourceType == "CALORIE") {
                        val nutrientInfo = remember(currentRecord.summary) {
                            NutrientParser.parseNutrients(currentRecord.summary)
                        }
                        if (nutrientInfo != null) {
                            Text("卡路里与营养素比例", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                NutrientPieChart(info = nutrientInfo)
                            }
                        }
                    }

                    Text("AI 提炼总结", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    
                    if (currentRecord.tags.contains("未分析")) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x0F8E2DE2)),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(Color(0x808E2DE2), Color(0x20F0C3FC))
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(Color(0x408E2DE2), Color(0x008E2DE2))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✨", fontSize = 24.sp)
                                }

                                Text(
                                    text = "快捷便签待整理",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )

                                Text(
                                    text = "此便签为快速保存的文本。点击下方按钮，将由 AI 提炼要点、生成结构化摘要、识别待办标签，并智能安排提醒日程。",
                                    fontSize = 12.sp,
                                    color = Color.LightGray,
                                    lineHeight = 18.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Button(
                                    onClick = {
                                        if (!isAiAnalyzing) {
                                            isAiAnalyzing = true
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val result = OpenAiCompatibleClient.analyzeText(context, currentRecord.rawContent)
                                                    if (result != null) {
                                                        val newTagsList = mutableListOf("便签")
                                                        newTagsList.addAll(result.tags)
                                                        val cleanTags = newTagsList.distinct().joinToString(",")

                                                        val updatedRecord = currentRecord.copy(
                                                            title = result.title,
                                                            summary = result.summary,
                                                            tags = cleanTags
                                                        )
                                                        dao.updateCapture(updatedRecord)

                                                        if (result.reminders.isNotEmpty()) {
                                                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                                            for (suggestion in result.reminders) {
                                                                try {
                                                                    val parsedTime = sdf.parse(suggestion.timeString)
                                                                    if (parsedTime != null && parsedTime.time > System.currentTimeMillis()) {
                                                                        val reminder = ReminderItem(
                                                                            recordId = currentRecord.id,
                                                                            title = suggestion.title,
                                                                            reminderTime = parsedTime.time,
                                                                            isActive = true,
                                                                            isTriggered = false
                                                                        )
                                                                        val reminderId = dao.insertReminder(reminder)
                                                                        ReminderScheduler.scheduleReminder(context, reminder.copy(id = reminderId))
                                                                    }
                                                                } catch (e: Exception) {
                                                                    Log.e("NoteDetailScreen", "Failed to parse reminder: ${suggestion.timeString}", e)
                                                                }
                                                            }
                                                        }

                                                        val updatedReminders = dao.getRemindersForRecord(currentRecord.id)
                                                        withContext(Dispatchers.Main) {
                                                            record = updatedRecord
                                                            reminders = updatedReminders
                                                            isAiAnalyzing = false
                                                            Toast.makeText(context, "AI 整理成功！", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        withContext(Dispatchers.Main) {
                                                            isAiAnalyzing = false
                                                            Toast.makeText(context, "AI 分析失败，请检查网络设置或稍后重试", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("NoteDetailScreen", "AI analysis error", e)
                                                    withContext(Dispatchers.Main) {
                                                        isAiAnalyzing = false
                                                        Toast.makeText(context, "出错: ${e.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isAiAnalyzing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
                                            )
                                        )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (isAiAnalyzing) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("AI 正在深度分析中...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        } else {
                                            Text("✨ 开始 AI 智能整理", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                FormattedSummary(summary = currentRecord.summary)
                            }
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        timeStr,
                                        fontSize = 11.sp,
                                        color = Color(0xFF00E676)
                                    )
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                ReminderScheduler.cancelReminder(context, reminder)
                                                dao.deleteReminder(reminder)
                                                val updatedList = dao.getRemindersForRecord(recordId)
                                                withContext(Dispatchers.Main) {
                                                    reminders = updatedList
                                                    Toast.makeText(context, "提醒已取消并删除", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "取消提醒",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
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

                    // P1: 关联旧思绪
                    RelatedThoughtsSection(
                        relatedRecords = relatedRecords,
                        onRecordClick = onNavigateToDetail
                    )
                }
            }
        }
    }
}

@Composable
fun NutrientPieChart(
    info: NutrientInfo,
    modifier: Modifier = Modifier
) {
    val total = info.protein + info.fat + info.carbs
    val proteinProportion = if (total > 0f) info.protein / total else 0f
    val fatProportion = if (total > 0f) info.fat / total else 0f
    val carbsProportion = if (total > 0f) info.carbs / total else 0f

    val proteinAngle = proteinProportion * 360f
    val fatAngle = fatProportion * 360f
    val carbsAngle = carbsProportion * 360f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left side: Donut Chart
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 10.dp.toPx()
                val sizeMin = size.minDimension
                val radius = (sizeMin - strokeWidth) / 2
                val center = this.center

                var startAngle = -90f // Start from top

                if (total == 0f) {
                    drawCircle(
                        color = Color(0x1AFFFFFF),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    // Draw base track first
                    drawCircle(
                        color = Color(0x0DFFFFFF),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )

                    // Draw Protein (Green)
                    if (proteinAngle > 0f) {
                        drawArc(
                            color = Color(0xFF00E676),
                            startAngle = startAngle,
                            sweepAngle = proteinAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += proteinAngle
                    }

                    // Draw Fat (Yellow/Orange)
                    if (fatAngle > 0f) {
                        drawArc(
                            color = Color(0xFFFFB300),
                            startAngle = startAngle,
                            sweepAngle = fatAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += fatAngle
                    }

                    // Draw Carbs (Purple)
                    if (carbsAngle > 0f) {
                        drawArc(
                            color = Color(0xFF8E2DE2),
                            startAngle = startAngle,
                            sweepAngle = carbsAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Central text in the Donut hole
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${info.calories}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "千卡(kcal)",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }

        // Right side: legends with progress indicators and percentages
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NutrientLegendItem(
                label = "蛋白质",
                amount = "${info.protein}g",
                proportion = proteinProportion,
                color = Color(0xFF00E676)
            )
            NutrientLegendItem(
                label = "脂肪",
                amount = "${info.fat}g",
                proportion = fatProportion,
                color = Color(0xFFFFB300)
            )
            NutrientLegendItem(
                label = "碳水",
                amount = "${info.carbs}g",
                proportion = carbsProportion,
                color = Color(0xFF8E2DE2)
            )
        }
    }
}

@Composable
fun NutrientLegendItem(
    label: String,
    amount: String,
    proportion: Float,
    color: Color
) {
    val percentage = (proportion * 100).toInt()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.LightGray
                )
            }
            Text(
                text = "$amount ($percentage%)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Custom progress bar using Row background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x1AFFFFFF))
        ) {
            if (proportion > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = proportion)
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
}

data class IngredientItem(
    val emoji: String,
    val name: String,
    val weight: String,
    val calories: String
)

fun parseIngredientLine(line: String): IngredientItem? {
    try {
        var cleanLine = line.trim()
        if (cleanLine.startsWith("-") || cleanLine.startsWith("*") || cleanLine.startsWith("•")) {
            cleanLine = cleanLine.substring(1).trim()
        }
        cleanLine = cleanLine.replace(Regex("^\\d+\\s*[\\.\\、]\\s*"), "").trim()
        
        if (cleanLine.isEmpty()) return null
        
        var emoji = "🍳"
        var namePart = cleanLine
        
        // 匹配开头是不是 Emoji 字符
        val emojiRegex = Regex("^([\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\u2300-\\u23FF\\u2B50\\u2934\\u2B06])")
        val emojiMatch = emojiRegex.find(cleanLine)
        if (emojiMatch != null) {
            emoji = emojiMatch.groupValues[1]
            namePart = cleanLine.substring(emoji.length).trim()
        } else {
            val firstCodePoint = if (cleanLine.isNotEmpty()) cleanLine.codePointAt(0) else 0
            if (firstCodePoint >= 0x1F300 && firstCodePoint <= 0x1F9FF || firstCodePoint >= 0x2600 && firstCodePoint <= 0x27BF) {
                val emojiCharCount = Character.charCount(firstCodePoint)
                emoji = cleanLine.substring(0, emojiCharCount)
                namePart = cleanLine.substring(emojiCharCount).trim()
            }
        }
        
        // 提取卡路里部分。支持匹配如 "： 150 kcal", ":150千卡", " - 150 kcal", " 150kcal", " 150"
        val calorieRegex = Regex("[:：\\-\\s]+(\\d+(?:\\.\\d+)?)\\s*(?:kcal|千卡|卡路里|卡)?\\s*$")
        val calorieMatch = calorieRegex.find(namePart)
        var calories = "0"
        if (calorieMatch != null) {
            calories = calorieMatch.groupValues[1]
            namePart = namePart.substring(0, calorieMatch.range.first).trim()
        } else {
            val simpleCalorieRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:kcal|千卡|卡路里|卡)?\\s*$")
            val simpleCalorieMatch = simpleCalorieRegex.find(namePart)
            if (simpleCalorieMatch != null) {
                calories = simpleCalorieMatch.groupValues[1]
                namePart = namePart.substring(0, simpleCalorieMatch.range.first).trim()
            }
        }
        
        // 提取分量部分
        val weightRegex = Regex("[\\(（]([^\\)）]+)[\\)）]")
        val weightMatch = weightRegex.find(namePart)
        var weight = ""
        if (weightMatch != null) {
            weight = weightMatch.groupValues[1].trim()
            namePart = namePart.replace(weightMatch.value, "").trim()
        } else {
            val weightSuffixRegex = Regex("(约?\\d+(?:\\.\\d+)?\\s*(?:g|ml|克|毫升|个|片|碗|盘|杯|根|只|条|瓣))\\s*$")
            val weightSuffixMatch = weightSuffixRegex.find(namePart)
            if (weightSuffixMatch != null) {
                weight = weightSuffixMatch.groupValues[1].trim()
                namePart = namePart.substring(0, weightSuffixMatch.range.first).trim()
            }
        }
        
        var name = namePart.replace(Regex("^[:：\\-\\s]+"), "").replace(Regex("[:：\\-\\s]+$"), "").trim()
        if (name.isEmpty()) {
            name = "未知食材"
        }
        
        if (weight.isEmpty()) {
            weight = "适量"
        }
        
        return IngredientItem(emoji = emoji, name = name, weight = weight, calories = calories)
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

@Composable
fun IngredientRow(
    item: IngredientItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x0CFFFFFF))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x1F8E2DE2)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.emoji,
                fontSize = 18.sp
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp
            )
            if (item.weight.isNotEmpty()) {
                Text(
                    text = if (item.weight.startsWith("约")) item.weight else "分量: ${item.weight}",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(Color(0x1A8E2DE2))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${item.calories} kcal",
                color = Color(0xFFE0C3FC),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FormattedSummary(summary: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        val cleanSummary = summary
            .replace("\\\\n", "\n")
            .replace("\\n", "\n")
            .replace("\r", "")
        
        val lines = cleanSummary.split("\n")
        var isFirstTitle = true
        var currentSection = ""
        
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            
            val isTitle = (line.startsWith("【") && line.contains("】")) ||
                    line.contains("识别出的食物") || 
                    line.contains("卡路里/营养成分估算") || 
                    line.contains("健康点评与建议") ||
                    line.contains("食材明细估算") ||
                    (line.startsWith("**") && line.endsWith("**") && line.length < 24) ||
                    (line.startsWith("###") && line.length < 32)
            
            when {
                isTitle -> {
                    val titleText = line
                        .replace("【", "")
                        .replace("】", "")
                        .replace("**", "")
                        .replace("###", "")
                        .replace(":", "")
                        .replace("：", "")
                        .trim()
                        
                    currentSection = when {
                        titleText.contains("食材") || titleText.contains("明细") -> "INGREDIENTS"
                        titleText.contains("食谱") || titleText.contains("菜谱") || titleText.contains("推荐") || titleText.contains("做法") -> "RECIPE"
                        titleText.contains("识别") || titleText.contains("食物") || titleText.contains("菜") -> "RECOGNITION"
                        titleText.contains("估算") || titleText.contains("卡路里") || titleText.contains("成分") -> "CALORIE"
                        titleText.contains("点评") || titleText.contains("建议") || titleText.contains("健康") || titleText.contains("烹饪") -> "ADVICE"
                        else -> "OTHER"
                    }
                    
                    val icon = when (currentSection) {
                        "INGREDIENTS" -> "🥗"
                        "RECIPE" -> "🍳"
                        "RECOGNITION" -> "🔍"
                        "CALORIE" -> "📊"
                        "ADVICE" -> "💡"
                        else -> "✨"
                    }
                    
                    if (!isFirstTitle) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    isFirstTitle = false
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "$icon $titleText",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE0C3FC)
                            )
                        }
                        Divider(
                            color = Color(0x1FFFFFFF),
                            thickness = 1.dp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                    }
                }
                
                line.startsWith("-") || line.startsWith("*") || line.startsWith("•") || line.matches(Regex("^\\d+\\..*$")) -> {
                    if (currentSection == "INGREDIENTS") {
                        val parsed = parseIngredientLine(line)
                        if (parsed != null) {
                            IngredientRow(item = parsed)
                        } else {
                            StandardBulletRow(line = line)
                        }
                    } else {
                        StandardBulletRow(line = line)
                    }
                }
                
                else -> {
                    Text(
                        text = parseMarkdownStyle(line),
                        fontSize = 13.sp,
                        color = Color(0xFFE0E0E0),
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StandardBulletRow(line: String) {
    val content = if (line.matches(Regex("^\\d+\\..*$"))) {
        line.trim()
    } else {
        line.substring(1).trim()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp, end = 8.dp)
                .size(5.dp)
                .background(Color(0xFFB388FF), CircleShape)
        )
        Text(
            text = parseMarkdownStyle(content),
            fontSize = 13.sp,
            color = Color(0xFFE0E0E0),
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

fun parseMarkdownStyle(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val boldStart = text.indexOf("**", cursor)
            if (boldStart != -1) {
                val boldEnd = text.indexOf("**", boldStart + 2)
                if (boldEnd != -1) {
                    append(text.substring(cursor, boldStart))
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                        append(text.substring(boldStart + 2, boldEnd))
                    }
                    cursor = boldEnd + 2
                } else {
                    append(text.substring(cursor))
                    break
                }
            } else {
                append(text.substring(cursor))
                break
            }
        }
    }
}
