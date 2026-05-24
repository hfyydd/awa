package com.example.awaassistant.widget

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * 小组件刷新 Worker
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            MemoryWidget.triggerRefresh(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "memory_widget_daily_refresh"

        /** 安排每日凌晨自动刷新 */
        fun scheduleDaily(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val now = java.util.Calendar.getInstance()
            val deadline = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            val delayMs = deadline.timeInMillis - now.timeInMillis

            val workRequest = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        /** 触发立即刷新（所有保存路径统一调用） */
        fun triggerNow(context: Context) {
            try {
                WorkManager.getInstance(context).enqueue(
                    OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
                )
            } catch (e: Exception) {
                // 降级：直接同步刷新
                MemoryWidget.triggerRefresh(context)
            }
        }
    }
}
