package com.example.awaassistant.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.awaassistant.data.AppDatabase
import com.example.awaassistant.ui.detail.MemoryCapsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// P1: 时光胶囊卡片 - Dashboard 顶部
@Composable
fun MemoryCapsuleCard(
    onDismiss: () -> Unit,
    onViewDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var capsule by remember { mutableStateOf<MemoryCapsule?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.appDao()
                val now = System.currentTimeMillis()
                val dayMs = 86400000L

                val windows = listOf(
                    7L to "7天前的灵感",
                    30L to "30天前的回顾",
                    365L to "1年前的珍藏"
                )

                for ((days, label) in windows) {
                    val toTs = now - dayMs * (days - 1)
                    val fromTs = now - dayMs * days
                    val candidate = dao.getRandomRecordInRange(fromTs, toTs)
                    if (candidate != null) {
                        capsule = MemoryCapsule(candidate, days.toInt(), label)
                        break
                    }
                }
            } catch (e: Exception) {
                capsule = null
            }
        }
        isLoading = false
    }

    if (isLoading) return

    val currentCapsule = capsule ?: return

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1F8E2DE2)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewDetail() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x208E2DE2),
                            Color(0x104A00E0)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "✨",
                            fontSize = 14.sp
                        )
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
                            text = "${currentCapsule.daysAgo} days",
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

                Text(
                    text = currentCapsule.record.summary.replace(Regex("[#*`\\-]"), "").trim(),
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
