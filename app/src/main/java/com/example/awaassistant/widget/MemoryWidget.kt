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
import com.example.awaassistant.data.TimeCapsuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                val capsule = loadCapsule(context)
                val views = buildRemoteViews(context, capsule)

                withContext(Dispatchers.Main) {
                    manager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        private suspend fun loadCapsule(context: Context) = withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val engine = TimeCapsuleEngine(db.appDao())
                engine.loadCapsule()
            } catch (e: Exception) {
                null
            }
        }

        private fun buildRemoteViews(context: Context, capsule: com.example.awaassistant.data.CapsuleData?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_memory)
            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            if (capsule != null) {
                val cleanSummary = capsule.record.summary
                    .replace(Regex("[\\#\\*`\\-]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(100)

                views.setTextViewText(R.id.widget_title, capsule.record.title)
                views.setTextViewText(R.id.widget_summary, cleanSummary)
                views.setTextViewText(R.id.widget_days_ago, "${capsule.daysAgo}天前")
                views.setTextViewText(R.id.widget_date, dateFmt.format(Date(capsule.record.timestamp)))
                views.setTextViewText(R.id.widget_label, capsule.label)
                views.setTextViewText(R.id.widget_emoji, capsule.emoji)

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("open_record_id", capsule.record.id)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, capsule.record.id.toInt(), intent,
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
