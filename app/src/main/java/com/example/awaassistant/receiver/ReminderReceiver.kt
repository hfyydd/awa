package com.example.awaassistant.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.awaassistant.MainActivity
import com.example.awaassistant.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "awa_reminder_channel"
        private const val CHANNEL_NAME = "Awa 助手智能提醒"
        
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        const val EXTRA_RECORD_ID = "record_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val reminderTitle = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "您的备忘事件到期了"
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)

        Log.d(TAG, "Received reminder: id=$reminderId, title=$reminderTitle, recordId=$recordId")

        if (reminderId == -1L) return

        // 使用 goAsync() 让 BroadcastReceiver 可以安全执行挂起操作（如写数据库）
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.appDao()
                
                // 将数据库中的提醒更新为已触发
                val reminder = dao.getReminderById(reminderId)
                if (reminder != null) {
                    dao.updateReminder(reminder.copy(isTriggered = true))
                }

                // 弹出通知
                showNotification(context, reminderId, reminderTitle, recordId)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling reminder broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, reminderId: Long, title: String, recordId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0+ 创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于发送由 AI 生成的每日工作与截屏笔记提醒"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 点击通知跳转到主 Activity（并传递 recordId 以便定位记录）
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_RECORD_ID, recordId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // 构建高优先级通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("智能提醒助手")
            .setContentText(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(reminderId.toInt(), notification)
    }
}
