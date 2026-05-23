package com.example.awaassistant.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.awaassistant.data.CaptureRecord
import com.example.awaassistant.util.NutrientParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// P1: 瀑布流记忆卡片（玻璃态设计）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamRecordCard(
    record: CaptureRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // 右滑 -> 标记完成
                    onToggleComplete()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // 左滑 -> 删除
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF00E676)
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFFF5252)
                    else -> Color.Transparent
                },
                label = "swipe_bg"
            )
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.CheckCircle
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                else -> Icons.Default.Delete
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(24.dp)
                )
            }
        },
        modifier = modifier,
        content = {
            GlassRecordContent(
                record = record,
                onClick = onClick,
                onToggleComplete = onToggleComplete
            )
        }
    )
}

// 玻璃态卡片内容
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlassRecordContent(
    record: CaptureRecord,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit
) {
    val isCompleted = record.isCompleted
    val bgAlpha = if (isCompleted) 0.05f else 0.12f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = bgAlpha + 0.08f),
                        Color.White.copy(alpha = bgAlpha)
                    )
                )
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶部来源 Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (badgeColor, badgeBg, typeText) = when (record.sourceType) {
                    "CALORIE" -> Triple(Color(0xFF00E676), Color(0x3300E676), "卡路里")
                    "SCREENSHOT" -> Triple(Color(0xFF00C9FF), Color(0x3300C9FF), "截屏")
                    "PHOTO" -> Triple(Color(0xFF8E2DE2), Color(0x338E2DE2), "拍照")
                    else -> Triple(Color(0xFFFFB300), Color(0x33FFB300), "便签")
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = typeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }

                // 完成状态
                if (isCompleted) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "已完成",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 缩略图（如果有）
            if (record.imagePath != null) {
                AsyncImage(
                    model = record.imagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                )
            } else if (record.sourceType != "CALORIE") {
                // 纯文字便签：显示完成状态图标
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isCompleted) Color(0x1F00E676) else Color(0x1F8E2DE2)
                        )
                        .clickable { onToggleComplete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "标记完成",
                        tint = if (isCompleted) Color(0xFF00E676) else Color(0xFF8E2DE2).copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 标题
            Text(
                text = record.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCompleted) Color.Gray else Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )

            // 摘要
            val cleanSummary = record.summary
                .replace(Regex("[#*`\\-]"), "")
                .replace(Regex("\\n+"), " ")
                .trim()
            Text(
                text = cleanSummary,
                fontSize = 11.sp,
                color = if (isCompleted) Color.Gray else Color.LightGray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )

            // 卡路里进度条（如果是卡路里记录）
            if (record.sourceType == "CALORIE") {
                val info = NutrientParser.parseNutrients(record.summary)
                if (info != null) {
                    val total = info.protein + info.fat + info.carbs
                    if (total > 0f) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                        Text(
                            text = "${info.calories} kcal",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }
                }
            }

            // 底部：时间戳 + 标签
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(Date(record.timestamp))
                Badge(
                    containerColor = Color(0x22FFFFFF),
                    contentColor = Color.LightGray
                ) {
                    Text(dateStr, fontSize = 9.sp)
                }

                record.tags.split(",").take(2).forEach { tag ->
                    val cleanTag = tag.trim()
                    if (cleanTag.isNotEmpty() && cleanTag != "处理中") {
                        Badge(
                            containerColor = Color(0x1F2DE28E),
                            contentColor = Color(0xFF00E676)
                        ) {
                            Text(cleanTag, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}
