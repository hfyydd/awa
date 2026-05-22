# Awa Assistant (Awa 智能个人助手)

Awa 智能助手是一款部署于 Android 客户端的个人助理应用。它通过系统级辅助功能服务与悬浮球，支持实时提取屏幕信息、识别照片文本（OCR），并通过 Room 数据库进行本地知识库归档。同时，应用结合了**时序感知 RAG（检索增强生成）上下文工程**和**极致科技感的 Compose 对话界面**，能够通过自定义大模型接口（兼容 DeepSeek、通义千问、智谱 GLM、火山引擎、MiniMax 等）为用户提供基于个人本地生活/工作记录的智能问答与任务提醒。

---

## 🌟 核心特性

### 1. 多源本地知识库摄入
*   **本地快速 OCR 引擎**：集成 Google ML Kit 文本识别，离线秒级提取屏幕截图、手拍笔记、会议白板等内容。
*   **本地 Room 归档**：提取的文本通过 Room 数据库存储并管理，支持自动打标（Tags）、提炼摘要（Summary）、提炼待办提醒（ReminderSuggestions）。

### 2. 系统级无缝触发
*   **屏幕截屏悬浮球**：无需跳出当前应用，点击边缘悬浮球即可捕获屏幕文字，快速分析并归档。
*   **音量键快捷键双击**：利用 Android 辅助功能服务，支持在任意界面双击音量下键快速分析并记录当前屏幕内容。
*   **系统截屏监听**：自动检测系统原生的截屏行为，实现静默提取和后台 AI 分析。

### 3. 时序感知上下文工程 (Time-Aware RAG)
*   **时序感知注入**：为检索出的本地文档附加人性化时间戳（如“刚刚”、“5分钟前”、“今天 15:30”、“昨天 14:00”），并在系统提示词中注入当前绝对系统时间，使大模型具备完整的时序感知，能够理解“帮我总结我上一张截图”、“昨天记的代码是什么”等时序指令。
*   **XML 结构化 Prompt 工程**：将设定、时间环境、检索上下文、约束规则通过规范的 XML 标签（`<system_role>`, `<current_environment>`, `<retrieved_context>`, `<generation_rules>`）传递给大模型，防止大模型在复杂上下文中被偏置或忽略约束条件。
*   **文献引用规范**：强制约束大模型在回答时对于引用的本地文档必须在句尾标注 `[Doc X]`。

### 4. 极致视觉体验的 Compose 界面
*   **动态星云背景**：采用 `Canvas` 与无限循环正弦波，绘制带有呼吸与起伏律动的双星云微光渐变，极富科技感。
*   **智能首屏引导**：当对话历史为空时，展示 AI 标志，并以 2x2 快捷卡片网格提供常见问答推荐（如“查找上张截图”等），点击即可快速填充并提问。
*   **交互式引用徽章**：聊天气泡会自动解析 AI 回复中的 `[Doc X]` 引用，将其动态替换为带超链接样式的行内徽章 **“ 🔗 [文档标题] ”**。用户可直接在气泡内点击徽章，平滑跳转到该本地截图或手拍笔记的原始详情页。
*   **思维链 `<think>` 折叠气泡**：支持流式和完整解析大模型输出的 `<think>` 标签，思考中呈现呼吸渐变动画，思考结束后默认收起以保持对话整洁，并支持手动点击展开查看思考过程。
*   **手写极速富文本引擎**：无缝支持 Markdown 代码块渲染（带有 monospace 等宽字体等深色卡片背景、语言类型标签和带防抖点击动效的“一键复制”按钮）、粗体、行内代码与列表排版。
*   **悬浮玻璃输入仓**：采用深色磨砂玻璃（Glassmorphism）与超细半透明发光边框，完美避让系统键盘（`imePadding`）。

---

## 🛠️ 项目架构设计

项目采用 Android 现代开发架构，基于 **Jetpack Compose** 声明式 UI 与 **Coroutines / Flow** 进行异步状态流控。

```text
app/src/main/java/com/example/awaassistant/
├── MainActivity.kt               # 应用主入口，处理权限回调与基础配置
├── Navigation.kt                 # 页面路由声明与 Navigation Graph 构建
├── NavigationKeys.kt             # 路由 Key 常量
├── data/                         # 数据处理模块
│   ├── AppDatabase.kt            # Room 数据库定义
│   ├── AppDao.kt                 # Room DAO，支持 FTS (全文搜索) 与 SQL 混合查询
│   ├── CaptureRecord.kt          # 本地屏幕截图/手拍笔记数据实体
│   ├── ReminderItem.kt           # 任务提醒数据实体
│   ├── SettingsManager.kt        # SharedPreferences 配置中心（大模型 API、开关状态等）
│   └── OpenAiCompatibleClient.kt # 大模型通信客户端（API 发送、XML Prompt 拼接、连接测试）
├── service/                      # 系统级常驻服务
│   ├── AwaAccessibilityService.kt # 辅助功能服务（手势监听、双击音量、OCR 触发）
│   └── FloatingOverlayService.kt # 屏幕侧边悬浮球层叠窗口服务
├── receiver/                     # 广播接收器
│   └── ReminderReceiver.kt       # 待办闹钟触发接收器，发送状态栏通知
├── util/                         # 通用工具类
│   ├── LocalOcrHelper.kt         # Google ML Kit 离线 OCR 识别封装
│   ├── ReminderScheduler.kt      # AlarmManager 定时闹钟调度封装
│   └── ChineseConverter.kt       # 繁简体文本转换工具
└── ui/                           # UI 页面与视图模型
    ├── MainPagerScreen.kt        # 主页双页切换管理器（HorizontalPager: "问答" <-> "最近"）
    ├── chat/                     # 智能知识库对话模块
    │   ├── ChatScreen.kt         # 对话气泡、 Markdown 渲染、宇宙星空背景、引用跳转
    │   └── ChatViewModel.kt      # 对话业务逻辑、混合检索 (FTS 全文搜索匹配 + LIKE 模糊检索兜底)
    ├── dashboard/                # 历史笔记列表看板模块
    │   ├── DashboardScreen.kt    # 近期记录瀑布流/网格展示、分类标签过滤、日期检索
    │   └── DashboardViewModel.kt # 归档记录多维度条件拉取与删除
    ├── detail/                   # 笔记详情展示模块
    │   └── NoteDetailScreen.kt   # 原图预览、AI 精炼总结、OCR 原文展示、提醒定时器管理
    └── settings/                 # 系统设置模块
        └── SettingsScreen.kt     # 大模型 API 表单、一键测试连接、权限状态追踪开关
```

---

## 🚀 编译与部署指南

### 开发环境要求
*   **JDK 17+**
*   **Android SDK 34+**
*   **Gradle 8.5+**
*   **连接的真机或模拟器** (Android 8.0 / API 26 及以上，实测推荐 Android 13+)

### 快速编译与运行

1.  **检查并配置 API 密钥**：
    在应用安装完成后，进入右上角 **设置** 页面。
    系统提供了 **DeepSeek**、**通义千问**、**智谱 GLM** 等一键配置预设，也可以输入自定义的 `Base URL` 与 `API Key`，点击下方的 **“测试大模型连接”** 确保连接通畅后再点击右上角 **“保存”**。

2.  **通过命令行部署到设备**：
    确保设备已开启 USB 调试且已通过 `adb devices` 正确连接：
    ```bash
    # 编译 Debug 版并安装到设备上
    ./gradlew installDebug
    ```

3.  **启用核心权限**：
    为保证悬浮球与音量键双击捕获功能正常工作，请在手机设置中开启：
    *   **悬浮窗权限**（在应用内“系统权限管理” -> “悬浮窗层叠权限”点击去设置）。
    *   **无障碍服务权限**（在设置页中开启“Awa 助手”无障碍服务）。

---

## 📝 XML 上下文工程模板 (Context Assembly Schema)

应用发往大模型的系统 Prompt 会以如下形式动态注入：

```xml
<system_role>
你是一个部署在用户手机端的个人智能 AI 助手，名为 Awa。你能够基于用户的屏幕截图和拍下的工作笔记（即下方提供的本地上下文数据）帮用户做回忆和检索。
</system_role>

<current_environment>
当前系统时间: 2026-05-22 09:15:30 (星期五)
当前活跃页面: 智能对话页
</current_environment>

<retrieved_context>
以下是本地检索到的相关笔记和屏幕截取记录，按照相关度排序（请优先根据这些内容，并结合记录的时间戳回答用户的问题）：

【文档 1】
记录时间: 10分钟前
记录类型: 屏幕截图
标题: 火山引擎模型调试
摘要: 调试 DeepSeek-R1 模型的 API Endpoint 与相关参数配置...
详细原文: endpoint is ep-20260522-xxxx...
标签: [火山引擎, API]
----------------------

【文档 2】
...
</retrieved_context>

<generation_rules>
1. 事实性约束：有本地文档时优先根据文档回答。如果检索到的文档无法回答该问题，请明确告知用户：“在本地笔记中未检索到相关内容，基于我自身知识推测...”。
2. 引用约束：回答必须引用出处。如果你的回答引用了某个本地文档，请在句尾（标点符号前）以 `[Doc X]` 的格式作为文献引用标志（例如：“...根据[Doc 1]所示...”，其中 X 代表文档的序号），不要使用任何其他格式的标记，且不要自己捏造不存在的文档序号。
3. 语气约束：回答要专业、精炼、富有逻辑，避免啰嗦。
4. 语言约束：使用简体中文回答。
</generation_rules>
```

---

## 📌 技术栈清单
*   **UI 框架**：Jetpack Compose (ConstraintLayout, HorizontalPager, LazyVerticalGrid)
*   **异步流**：Kotlin Coroutines & StateFlow / SharedFlow
*   **本地数据库**：Room Database (FTS5 全文虚拟表)
*   **图像加载**：Coil 3.x
*   **OCR 文本识别**：Google ML Kit Text Recognition (离线版)
*   **网络通信**：OkHttp 4.x & Okio
*   **定时闹钟**：Android AlarmManager & BroadcastReceiver (双重守护精确闹钟)
