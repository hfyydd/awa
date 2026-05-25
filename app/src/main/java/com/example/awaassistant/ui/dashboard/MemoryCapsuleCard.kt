package com.example.awaassistant.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.awaassistant.data.CapsuleData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时光胶囊卡片（从 DashboardSharedViewModel 读取数据）
 */
@Composable
fun MemoryCapsuleCard(
    capsule: CapsuleData?,
    isLoading: Boolean,
    onViewDetail: (Long) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLoading) return

    val currentCapsule = capsule ?: return

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1F8E2DE2)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewDetail(currentCapsule.record.id) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0x208E2DE2), Color(0x104A00E0))
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = currentCapsule.emoji, fontSize = 14.sp)
                        Text(
                            text = currentCapsule.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1A8E2DE2))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${currentCapsule.daysAgo}天",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC51162)
                        )
                    }
                }

                Text(
                    text = currentCapsule.record.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val cleanSummary = currentCapsule.record.summary
                    .replace(Regex("[\\#\\*`\\-]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                Text(
                    text = cleanSummary,
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentCapsule.record.imagePath != null) {
                        AsyncImage(
                            model = currentCapsule.record.imagePath,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(currentCapsule.record.timestamp)),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
