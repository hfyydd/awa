package com.example.awaassistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.awaassistant.MainActivity
import com.example.awaassistant.R
import com.example.awaassistant.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时光胶囊桌面小组件
 *
 * 功能：每天随机抽取一条历史记录展示在桌面上，点击打开详情页。
 */
class MemoryWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MemoryWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.awaassistant.ACTION_REFRESH_WIDGET"

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        /** 触发所有小组件刷新 */
        fun triggerRefresh(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MemoryWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }

        /** 更新单个小组件 */
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager? = null,
            appWidgetId: Int
        ) {
            val manager = appWidgetManager ?: AppWidgetManager.getInstance(context)

            scope.launch {
                val capsule = loadRandomCapsule(context)
                val views = buildRemoteViews(context, capsule)

                with(Dispatchers.Main) {
                    manager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        private suspend fun loadRandomCapsule(context: Context): CapsuleData? {
            return try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.appDao()
                val now = System.currentTimeMillis()
                val dayMs = 86400000L

                // 从 3-7天、7-30天、30-365天 三个窗口随机抽取
                val windows = listOf(3L to 7L, 7L to 30L, 30L to 365L)

                for ((fromDays, toDays) in windows) {
                    val toTs = now - dayMs * fromDays
                    val fromTs = now - dayMs * toDays
                    val record = dao.getRandomRecordInRange(fromTs, toTs)
                    if (record != null) {
                        return@loadRandomCapsule CapsuleData(
                            title = record.title,
                            summary = cleanSummary(record.summary),
                            daysAgo = ((now - record.timestamp) / dayMs).toInt(),
                            recordId = record.id,
                            timestamp = record.timestamp
                        )
                    }
                }

                // 备用：从所有历史记录中随机
                val oneDayAgo = now - dayMs
                val fallback = dao.getRandomHistoricalRecord(oneDayAgo)
                fallback?.let {
                    return@loadRandomCapsule CapsuleData(
                        title = it.title,
                        summary = cleanSummary(it.summary),
                        daysAgo = ((now - it.timestamp) / dayMs).toInt(),
                        recordId = it.id,
                        timestamp = it.timestamp
                    )
                }
                null
            } catch (e: Exception) {
                null
            }
        }

        private fun cleanSummary(text: String): String {
            return text
                .replace(Regex("[\\#\\*`\\-]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(100)
        }

        private fun buildRemoteViews(context: Context, capsule: CapsuleData?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_memory)
            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            if (capsule != null) {
                views.setTextViewText(R.id.widget_title, capsule.title)
                views.setTextViewText(R.id.widget_summary, capsule.summary)
                views.setTextViewText(R.id.widget_days_ago, "${capsule.daysAgo}天前")
                views.setTextViewText(R.id.widget_date, dateFmt.format(Date(capsule.timestamp)))

                val (label, emoji) = when {
                    capsule.daysAgo <= 7 -> "7天前的灵感" to "✨"
                    capsule.daysAgo <= 30 -> "30天前的回顾" to "📅"
                    capsule.daysAgo <= 365 -> "1年前的珍藏" to "🏆"
                    else -> "旧日时光" to "💫"
                }
                views.setTextViewText(R.id.widget_label, label)
                views.setTextViewText(R.id.widget_emoji, emoji)

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("open_record_id", capsule.recordId)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, capsule.recordId.toInt(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            } else {
                views.setTextViewText(R.id.widget_title, "尚无历史记录")
                views.setTextViewText(R.id.widget_summary, "开始在 Awa 记录你的想法吧 ✨")
                views.setTextViewText(R.id.widget_days_ago, "新开始")
                views.setTextViewText(R.id.widget_label, "时光胶囊")
                views.setTextViewText(R.id.widget_emoji, "🌱")
                views.setTextViewText(R.id.widget_date, dateFmt.format(Date()))

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }

            return views
        }
    }
}

/** 小组件数据结构 */
private data class CapsuleData(
    val title: String,
    val summary: String,
    val daysAgo: Int,
    val recordId: Long,
    val timestamp: Long
)
