package com.example.awaassistant.data

/**
 * 时光胶囊引擎
 *
 * 统一算法：为桌面小组件（和首页）提供历史记忆随机抽取功能。
 * 策略：从多个时间窗口收集候选记录，再统一随机选择一条展示。
 */
class TimeCapsuleEngine(private val dao: AppDao) {

    private val dayMs = 86400000L

    /**
     * 加载一条时光胶囊记录
     *
     * 时间窗口优先级：
     * 1. 3-7天前的记录
     * 2. 7-30天前的记录
     * 3. 30-365天前的记录
     * 4. 0-3天内的记录（过渡期）
     * 5. 所有历史记录（排除最近24小时，作为兜底）
     *
     * @return 时光胶囊数据，包含记录和元信息
     */
    suspend fun loadCapsule(): CapsuleData? {
        val now = System.currentTimeMillis()
        val candidates = mutableListOf<CandidateRecord>()

        // 收集 3-7天、7-30天、30-365天 窗口的随机记录
        val windows = listOf(3L to 7L, 7L to 30L, 30L to 365L)
        for ((fromDays, toDays) in windows) {
            val toTs = now - dayMs * fromDays
            val fromTs = now - dayMs * toDays
            val record = dao.getRandomRecordInRange(fromTs, toTs)
            if (record != null) {
                candidates.add(CandidateRecord(record, daysAgoOf(now, record.timestamp)))
            }
        }

        // 如果依然为空，收集 0-3天 窗口的随机记录作为过渡
        if (candidates.isEmpty()) {
            val toTs = now
            val fromTs = now - dayMs * 3
            val record = dao.getRandomRecordInRange(fromTs, toTs)
            if (record != null) {
                candidates.add(CandidateRecord(record, daysAgoOf(now, record.timestamp)))
            }
        }

        // 如果依然没有任何候选，从所有历史记录中随机一条作为兜底（排除最近24小时）
        if (candidates.isEmpty()) {
            val oneDayAgo = now - dayMs
            val fallback = dao.getRandomHistoricalRecord(oneDayAgo)
            if (fallback != null) {
                candidates.add(CandidateRecord(fallback, daysAgoOf(now, fallback.timestamp)))
            }
        }

        if (candidates.isNotEmpty()) {
            val selected = candidates.random()
            return CapsuleData(
                record = selected.record,
                daysAgo = selected.daysAgo,
                label = labelOf(selected.daysAgo),
                emoji = emojiOf(selected.daysAgo)
            )
        }
        return null
    }

    private fun daysAgoOf(now: Long, timestamp: Long): Int {
        val calNow = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val calThen = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        
        calNow.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calNow.set(java.util.Calendar.MINUTE, 0)
        calNow.set(java.util.Calendar.SECOND, 0)
        calNow.set(java.util.Calendar.MILLISECOND, 0)
        
        calThen.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calThen.set(java.util.Calendar.MINUTE, 0)
        calThen.set(java.util.Calendar.SECOND, 0)
        calThen.set(java.util.Calendar.MILLISECOND, 0)
        
        val diffMillis = calNow.timeInMillis - calThen.timeInMillis
        return (diffMillis / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)
    }

    private fun labelOf(daysAgo: Int): String = when {
        daysAgo == 0 -> "今日的灵感"
        daysAgo <= 2 -> "最近的思绪"
        daysAgo <= 7 -> "7天前的灵感"
        daysAgo <= 30 -> "30天前的回顾"
        daysAgo <= 365 -> "1年前的珍藏"
        else -> "旧日时光"
    }

    private fun emojiOf(daysAgo: Int): String = when {
        daysAgo == 0 -> "✨"
        daysAgo <= 2 -> "🌱"
        daysAgo <= 7 -> "✨"
        daysAgo <= 30 -> "📅"
        daysAgo <= 365 -> "🏆"
        else -> "💫"
    }

    /** 候选记录（包含计算后的天数字段） */
    private data class CandidateRecord(
        val record: CaptureRecord,
        val daysAgo: Int
    )
}

/**
 * 时光胶囊数据结构
 */
data class CapsuleData(
    val record: CaptureRecord,
    val daysAgo: Int,
    val label: String,
    val emoji: String
)
