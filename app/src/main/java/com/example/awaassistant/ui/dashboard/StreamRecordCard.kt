package com.example.awaassistant.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

// P1: 瀑布流记忆卡片（玻璃态设计 + 删除确认）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamRecordCard(
    record: CaptureRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUncompleteDialog by remember { mutableStateOf(false) }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "确认删除这条记录？",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = record.title,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "删除后将无法恢复。",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("删除", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1A1430),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // 取消完成确认对话框（防误操作）
    if (showUncompleteDialog) {
        AlertDialog(
            onDismissRequest = { showUncompleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "确认重新标记为未完成？",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "此事项将移回待办列表。",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUncompleteDialog = false
                        onToggleComplete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB300)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("确认", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUncompleteDialog = false }) {
                    Text("取消", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1A1430),
            shape = RoundedCornerShape(20.dp)
        )
    }

    GlassRecordContent(
        record = record,
        onClick = onClick,
        onToggleComplete = {
            if (record.isCompleted) {
                // 已完成的项要确认才能取消
                showUncompleteDialog = true
            } else {
                // 未完成的项直接勾选完成
                onToggleComplete()
            }
        },
        onDeleteClick = { showDeleteDialog = true },
        modifier = modifier
    )
}

// 玻璃态卡片内容
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlassRecordContent(
    record: CaptureRecord,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompleted = record.isCompleted
    val bgAlpha = if (isCompleted) 0.05f else 0.12f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier
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
            // 顶部来源 Badge + 完成勾选
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (badgeColor, badgeBg, typeText) = when (record.sourceType) {
                    "CALORIE" -> Triple(Color(0xFF00E676), Color(0x3300E676), "卡路里")
                    "RECIPE" -> Triple(Color(0xFF11998E), Color(0x3311998E), "食谱")
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

                // 圆形勾选圈/已完成状态（仅便签/拍照/截屏等类型需要完成，卡路里和食谱等记录不需要）
                val showCheckbox = record.sourceType != "CALORIE" && record.sourceType != "RECIPE"
                if (showCheckbox) {
                    if (isCompleted) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已完成",
                            tint = Color(0xFF00E676),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onToggleComplete() }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(1.2.dp, Color.Gray.copy(alpha = 0.6f), RoundedCornerShape(9.dp))
                                .clickable { onToggleComplete() }
                        )
                    }
                }
            }

            // 缩略图 (不同 sourceType 使用不同高度)
            if (record.imagePath != null) {
                val imageHeight = when (record.sourceType) {
                    "SCREENSHOT" -> 160.dp
                    "PHOTO" -> 130.dp
                    "RECIPE" -> 120.dp
                    else -> 110.dp
                }
                AsyncImage(
                    model = record.imagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(imageHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                )
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

            // 卡路里进度条
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

            // 底部：时间戳 + 标签 + 删除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(Date(record.timestamp))
                    Badge(
                        containerColor = Color(0x11FFFFFF),
                        contentColor = Color.LightGray
                    ) {
                        Text(dateStr, fontSize = 8.sp)
                    }

                    record.tags.split(",").take(1).forEach { tag ->
                        val cleanTag = tag.trim()
                        if (cleanTag.isNotEmpty() && cleanTag != "处理中") {
                            val (tagBg, tagContent) = when (record.sourceType) {
                                "CALORIE" -> Pair(Color(0x1F00E676), Color(0xFF00E676))
                                "RECIPE" -> Pair(Color(0x1F11998E), Color(0xFF11998E))
                                "SCREENSHOT" -> Pair(Color(0x1F00C9FF), Color(0xFF00C9FF))
                                "PHOTO" -> Pair(Color(0x1F8E2DE2), Color(0xFF8E2DE2))
                                else -> Pair(Color(0x1F8E2DE2), Color(0xFF8E2DE2))
                            }
                            Badge(
                                containerColor = tagBg,
                                contentColor = tagContent
                            ) {
                                Text(cleanTag, fontSize = 8.sp)
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.LightGray.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
