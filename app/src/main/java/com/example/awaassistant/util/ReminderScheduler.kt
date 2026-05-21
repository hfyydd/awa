package com.example.awaassistant.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.awaassistant.data.ReminderItem
import com.example.awaassistant.receiver.ReminderReceiver

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    /**
     * 注册闹钟提醒到系统的 AlarmManager
     */
    fun scheduleReminder(context: Context, reminder: ReminderItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderReceiver.EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(ReminderReceiver.EXTRA_RECORD_ID, reminder.recordId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        if (reminder.reminderTime <= System.currentTimeMillis()) {
            Log.w(TAG, "Reminder time is in the past, skipping setting alarm: ${reminder.reminderTime}")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 以后引入 canScheduleExactAlarms 判定
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.reminderTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.reminderTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.reminderTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Successfully scheduled alarm for ID: ${reminder.id} at time: ${reminder.reminderTime}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling exact alarm, fallback to non-exact", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.reminderTime,
                pendingIntent
            )
        }
    }

    /**
     * 取消已注册的闹钟提醒
     */
    fun cancelReminder(context: Context, reminder: ReminderItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for ID: ${reminder.id}")
        }
    }
}
