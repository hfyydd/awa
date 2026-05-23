package com.example.awaassistant.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.awaassistant.data.AppDatabase
import com.example.awaassistant.data.DailySourceCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// P2: 思考足迹热力图
@Composable
fun ActivityHeatmap(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activityStats by remember { mutableStateOf<List<DailySourceCount>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val threeMonthsAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                activityStats = db.appDao().getActivityStats(threeMonthsAgo)
            } catch (e: Exception) {
                activityStats = emptyList()
            }
        }
        isLoading = false
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🧠",
                    fontSize = 14.sp
                )
                Text(
                    text = "思考足迹",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF8E2DE2),
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                HeatmapGrid(stats = activityStats)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HeatmapLegendItem(color = Color(0xFF00E676), label = "便签")
                    HeatmapLegendItem(color = Color(0xFF8E2DE2), label = "拍照")
                    HeatmapLegendItem(color = Color(0xFF00C9FF), label = "截屏")
                    HeatmapLegendItem(color = Color(0xFFFFB300), label = "卡路里")
                }
            }
        }
    }
}

@Composable
private fun HeatmapGrid(stats: List<DailySourceCount>) {
    val today = remember { Calendar.getInstance() }
    val cellSize = 12.dp
    val cellGap = 3.dp
    val weeks = 13

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val cellSizePx = cellSize.toPx()
        val cellGapPx = cellGap.toPx()
        val cellTotal = cellSizePx + cellGapPx

        val dateColorMap = buildDateColorMap(stats)
        val todayWeek = today.get(Calendar.WEEK_OF_YEAR)
        val todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK) - 1

        for (week in 0 until weeks) {
            for (day in 0 until 7) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.WEEK_OF_YEAR, todayWeek - (weeks - 1 - week))
                    set(Calendar.DAY_OF_WEEK, day + 1)
                }

                if (cal.after(today)) continue

                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                val color = dateColorMap[dateStr] ?: Color(0x0DFFFFFF)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(
                        x = week * cellTotal,
                        y = day * cellTotal
                    ),
                    size = Size(cellSizePx, cellSizePx),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

private fun DrawScope.buildDateColorMap(stats: List<DailySourceCount>): Map<String, Color> {
    val map = mutableMapOf<String, Color>()
    val byDate = stats.groupBy { it.day }

    byDate.forEach { (day, counts) ->
        val total = counts.sumOf { it.cnt }
        val dominant = counts.maxByOrNull { it.cnt }

        val intensity = when {
            total == 0 -> 0.1f
            total <= 2 -> 0.3f
            total <= 5 -> 0.5f
            total <= 10 -> 0.7f
            else -> 1.0f
        }

        val baseColor = when (dominant?.sourceType) {
            "CALORIE" -> Color(0xFF00E676)
            "TEXT" -> Color(0xFF8E2DE2)
            "PHOTO" -> Color(0xFF00C9FF)
            "SCREENSHOT" -> Color(0xFFFFB300)
            else -> Color(0xFF8E2DE2)
        }

        map[day] = baseColor.copy(alpha = intensity)
    }

    return map
}

@Composable
private fun HeatmapLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = 0.7f))
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}
