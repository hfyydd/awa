package com.example.awaassistant.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.awaassistant.data.DailySourceCount
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ActivityHeatmap(
    stats: List<DailySourceCount>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
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
                Text(text = "🧠", fontSize = 14.sp)
                Text("思考足迹", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF8E2DE2),
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                HeatmapGrid(stats = stats)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LegendItem(color = Color(0xFF00E676), label = "便签")
                    LegendItem(color = Color(0xFF8E2DE2), label = "拍照")
                    LegendItem(color = Color(0xFF00C9FF), label = "截屏")
                    LegendItem(color = Color(0xFFFFB300), label = "卡路里")
                }
            }
        }
    }
}

@Composable
private fun HeatmapGrid(stats: List<DailySourceCount>) {
    val today = Calendar.getInstance()
    val weeks = 13
    val dateColorMap = buildDateColorMap(stats)

    Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
        val cellSize = 12.dp.toPx()
        val cellGap = 3.dp.toPx()
        val cellTotal = cellSize + cellGap
        val todayWeek = today.get(Calendar.WEEK_OF_YEAR)

        for (week in 0 until weeks) {
            for (day in 0 until 7) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.WEEK_OF_YEAR, todayWeek - (weeks - 1 - week))
                    set(Calendar.DAY_OF_WEEK, day + 1)
                }
                if (cal.after(today)) continue

                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                val color = dateColorMap[dateStr] ?: Color(0x1AFFFFFF)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(week * cellTotal, day * cellTotal),
                    size = Size(cellSize, cellSize),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
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
        Text(text = label, fontSize = 10.sp, color = Color.Gray)
    }
}

private fun buildDateColorMap(stats: List<DailySourceCount>): Map<String, Color> {
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
