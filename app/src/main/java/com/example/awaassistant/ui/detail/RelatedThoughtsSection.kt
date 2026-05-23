package com.example.awaassistant.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.awaassistant.data.CaptureRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// P1: 详情页底部 - 关联旧思绪 Section
@Composable
fun RelatedThoughtsSection(
    relatedRecords: List<CaptureRecord>,
    onRecordClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (relatedRecords.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = "💡",
                fontSize = 16.sp
            )
            Text(
                text = "关联旧思绪",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(relatedRecords, key = { it.id }) { record ->
                RelatedThoughtCard(
                    record = record,
                    onClick = { onRecordClick(record.id) }
                )
            }
        }
    }
}

// P1: 关联思绪卡片
@Composable
fun RelatedThoughtCard(
    record: CaptureRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1F2DE28E)),
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = record.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = record.summary.replace(Regex("[#*`\\-]"), "").trim(),
                fontSize = 10.sp,
                color = Color.LightGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF00E676))
                )
                Text(
                    text = SimpleDateFormat("MM-dd", Locale.getDefault())
                        .format(Date(record.timestamp)),
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
