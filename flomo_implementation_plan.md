# Awa Assistant - flomo 灵感功能详细实现方案

基于 `flomo_inspiration_report.md` 的 5 大灵感，结合 Awa 现有技术底座（Room+FTS 全文检索、悬浮球服务、本地 ASR、OCR），制定以下分阶段实现方案。

---

## 阶段一：降阻极速录入（Priority: P0）

### 1.1 悬浮球双击 → 极简 Quick Capture Sheet

**现状**：双击悬浮球只触发截图  
**目标**：双击弹出轻量录入弹窗（打字/语音），10ms 保存

#### 实现细节

**修改文件**：`service/FloatingOverlayService.kt`

**核心逻辑**：

```kotlin
// 1. 区分单击/双击/长按手势
private var lastTapTime = 0L
private val DOUBLE_TAP_THRESHOLD = 300L // ms

// onTouch 事件中识别手势
MotionEvent.ACTION_UP -> {
    val now = System.currentTimeMillis()
    when {
        isLongPress -> onBubbleLongPressed()      // 长按：截图
        now - lastTapTime < DOUBLE_TAP_THRESHOLD -> {
            onBubbleDoubleTapped()                 // 双击：录入弹窗
            lastTapTime = 0
        }
        else -> lastTapTime = now                  // 记录单击等待二次点击
    }
}
```

**新增 QuickCaptureBottomSheet**：

```
┌──────────────────────────────────────┐
│  ● ● ●   AWA 极速录入                │
├──────────────────────────────────────┤
│  ┌────────────────────────────────┐  │
│  │ 写下此刻的想法...               │  │
│  │ (140dp 高，支持多行)            │  │
│  └────────────────────────────────┘  │
│                                      │
│  [ 🎤 语音 ]              [ 📝 文字 ]│
│                                      │
│            [ ✨ 极速保存 ]            │
└──────────────────────────────────────┘
```

**关键实现点**：

1. **ModalBottomSheet**（使用 Compose `ModalBottomSheet`）
   - 半透明玻璃态背景：`Surface(color = Color.Black.copy(alpha=0.85f))`

2. **极速保存路径**：
   ```kotlin
   suspend fun quickSave(content: String) {
       val record = CaptureRecord(
           title = content.take(50),
           summary = content,
           rawContent = content,
           imagePath = null,
           timestamp = System.currentTimeMillis(),
           tags = "快速录入",
           sourceType = "TEXT",
           isCompleted = false
       )
       appDao.insertCapture(record)
   }
   ```

3. **ASR 集成**：复用现有 `util/AsrManager`

---

### 1.2 状态与交互

| 状态 | 行为 |
|------|------|
| 双击悬浮球 | 弹出 QuickCaptureSheet |
| 长按悬浮球 | 触发截图（保持现状） |
| 拖拽悬浮球 | 吸附屏幕边缘 |
| 快速保存 | 关闭 Sheet，记录入 DB |

---

## 阶段二：唤醒与关联（Priority: P1）

### 2.1 详情页「💡 关联旧思绪」

**位置**：`ui/detail/NoteDetailScreen.kt` 底部

**DAO 扩展**：

```kotlin
@Query("""
    SELECT capture_records.* 
    FROM capture_records 
    JOIN fts_capture_records ON capture_records.id = fts_capture_records.rowid 
    WHERE fts_capture_records MATCH :query
      AND capture_records.id != :excludeId
    ORDER BY capture_records.timestamp DESC
    LIMIT 5
""")
suspend fun searchRelated(excludeId: Long, query: String): List<CaptureRecord>
```

**关键词提取**：

```kotlin
fun extractKeywords(text: String): String {
    val words = text.split(Regex("[\\s,.，。、]"))
        .filter { it.length > 2 }
        .take(3)
    return words.joinToString(" OR ") { "*$it*" }
}
```

### 2.2 时光胶囊 / 灵感唤醒卡片

**位置**：Dashboard 顶部，「状态卡」下方

**数据模型**：

```kotlin
data class MemoryCapsule(
    val record: CaptureRecord,
    val daysAgo: Int,
    val label: String // "7天前", "30天前", "1年前"
)
```

---

## 阶段三：习惯量化与流式主屏（Priority: P2）

### 3.1 思考足迹热力图（Canvas 绘制）

**位置**：Dashboard 顶部 / 「我的」页面

**DAO 查询**：

```kotlin
@Query("""
    SELECT date(timestamp/1000, 'unixepoch') as day, 
           sourceType, 
           COUNT(*) as cnt
    FROM capture_records
    WHERE timestamp > :fromTs
    GROUP BY day, sourceType
""")
suspend fun getActivityStats(fromTs: Long): List<DailySourceCount>
```

**Canvas 绘制**：

```kotlin
@Composable
fun ActivityHeatmap(stats: List<DailySourceCount>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(120.dp).padding(8.dp)) {
        val cellSize = 14.dp.toPx()
        val cellGap = 3.dp.toPx()
        
        for (day in stats) {
            val intensity = calculateIntensity(day.total)
            val color = sourceTypeColor(day.dominantType).copy(alpha = intensity)
            
            drawRoundRect(
                color = color,
                topLeft = Offset(col * (cellSize + cellGap), row * (cellSize + cellGap)),
                size = Size(cellSize, cellSize),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}
```

### 3.2 卡片式时间轴主屏（Staggered Grid）

**方案**：LazyVerticalStaggeredGrid（官方）

```kotlin
@Composable
fun CardStreamScreen(records: List<CaptureRecord>) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(records, key = { it.id }) { record ->
            StreamMemoryCard(record)
        }
    }
}
```

---

## 阶段四：桌面小组件（Priority: P3）

### 4.1 AppWidget 实现

**文件结构**：

```
app/src/main/
├── res/xml/memory_widget_info.xml
├── widget/MemoryWidget.kt
└── widget/MemoryWidgetView.kt
```

**widget_info.xml**：

```xml
<appwidget-provider
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:updatePeriodMillis="86400000"
    android:initialLayout="@layout/widget_layout"
/>
```

**MemoryWidget.kt**：

```kotlin
class MemoryWidget : AppWidgetProvider() {
    override fun onUpdate(...) {
        val record = runBlocking {
            AppDatabase.getDatabase(context)
                .appDao()
                .getRandomHistoricalRecord()
        }
        
        val views = RemoteViews(context.packageName, R.layout.widget_memory)
        views.setTextViewText(R.id.widget_title, record?.title ?: "今日尚无记录")
        
        // 点击打开 app
        val intent = Intent(context, MainActivity::class.java)
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(...))
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
```

**DAO 新增**：

```kotlin
@Query("""
    SELECT * FROM capture_records 
    WHERE timestamp < :cutoff
    ORDER BY RANDOM() LIMIT 1
""")
suspend fun getRandomHistoricalRecord(cutoff: Long): CaptureRecord?
```

---

## 数据库变更（v3 Migration）

```kotlin
// 可选：新增字段用于热力图统计
@Entity(tableName = "capture_records")
data class CaptureRecord(
    ...
    val mood: String? = null  // "productive", "creative", "normal"
)
```

---

## 技术依赖与里程碑

| 阶段 | 功能 | 关键依赖 | 工时估算 |
|------|------|----------|----------|
| **P0** | Quick Capture Sheet | FloatingOverlayService 改造、BottomSheet | 2-3d |
| **P1** | 关联旧思绪 + 时光胶囊 | FTS 查询复用、DAO 扩展 | 1-2d |
| **P2** | 热力图 + 卡片流 | Canvas 绘制、StaggeredGrid | 2-3d |
| **P3** | AppWidget | Android AppWidget API | 1-2d |

---

## 测试策略

1. **Quick Capture**：快速连续保存 10 条，验证无 ANR
2. **关联搜索**：已知记录关联召回率测试
3. **热力图**：横跨 3 个月数据渲染性能
4. **Widget**：桌面添加/移除生命周期

---

## 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| FTS 查询性能（大量数据） | 中 | 中 | 添加 LIMIT + 索引优化 |
| Widget 后台刷新耗电 | 低 | 中 | 使用 `WorkManager` 替代 `updatePeriodMillis` |
| BottomSheet 手势冲突 | 中 | 低 | 测试多种 Android 版本兼容 |
| Canvas 热力图掉帧 | 低 | 低 | 使用 `hardwareLayer()` 优化 |
