package com.example.awaassistant.ui.chat

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Send
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.awaassistant.data.CaptureRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Represents markdown parsed content blocks
sealed interface ChatContentBlock {
    data class ThinkBlock(val content: String, val isFinished: Boolean) : ChatContentBlock
    data class CodeBlock(val language: String, val code: String) : ChatContentBlock
    data class TextBlock(val content: String) : ChatContentBlock
}

/**
 * Fast stateful markdown-like parser to extract fenced code blocks, text blocks,
 * and think blocks.
 */
fun parseMarkdown(text: String): List<ChatContentBlock> {
    val blocks = mutableListOf<ChatContentBlock>()
    val thinkOpenTag = "<think>"
    val thinkCloseTag = "</think>"
    var currentIndex = 0

    while (currentIndex < text.length) {
        val nextOpen = text.indexOf(thinkOpenTag, currentIndex)
        if (nextOpen == -1) {
            val rest = text.substring(currentIndex)
            if (rest.isNotEmpty()) {
                blocks.addAll(parseMarkdownSegment(rest))
            }
            break
        }

        if (nextOpen > currentIndex) {
            val before = text.substring(currentIndex, nextOpen)
            blocks.addAll(parseMarkdownSegment(before))
        }

        val nextClose = text.indexOf(thinkCloseTag, nextOpen + thinkOpenTag.length)
        if (nextClose == -1) {
            val thinkContent = text.substring(nextOpen + thinkOpenTag.length)
            if (thinkContent.isNotEmpty()) {
                blocks.add(ChatContentBlock.ThinkBlock(thinkContent, isFinished = false))
            }
            break
        } else {
            val thinkContent = text.substring(nextOpen + thinkOpenTag.length, nextClose)
            if (thinkContent.isNotEmpty()) {
                blocks.add(ChatContentBlock.ThinkBlock(thinkContent, isFinished = true))
            }
            currentIndex = nextClose + thinkCloseTag.length
        }
    }
    return blocks
}

private fun parseMarkdownSegment(segment: String): List<ChatContentBlock> {
    val blocks = mutableListOf<ChatContentBlock>()
    val parts = segment.split("```")
    for (i in parts.indices) {
        val part = parts[i]
        if (i % 2 == 1) {
            // Code block segment
            val lines = part.split("\n", limit = 2)
            if (lines.size >= 2) {
                val lang = lines[0].trim()
                val code = lines[1].trimEnd()
                blocks.add(ChatContentBlock.CodeBlock(lang, code))
            } else {
                blocks.add(ChatContentBlock.CodeBlock("", part.trim()))
            }
        } else {
            // Normal text block
            if (part.isNotEmpty()) {
                blocks.add(ChatContentBlock.TextBlock(part))
            }
        }
    }
    return blocks
}

/**
 * Formats inline bold text, inline code backticks, and [Doc X] reference tags using SpanStyle
 */
fun buildAnnotatedStringWithInlineStyles(
    text: String,
    retrievedRecords: List<CaptureRecord> = emptyList()
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White))
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFF8A65),
                                background = Color(0x22FFFFFF)
                            )
                        )
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append("`")
                        i += 1
                    }
                }
                text.startsWith("[Doc ", i) -> {
                    val end = text.indexOf("]", i + 5)
                    if (end != -1) {
                        val numStr = text.substring(i + 5, end).trim()
                        val docIndex = numStr.toIntOrNull()?.minus(1)
                        if (docIndex != null && docIndex >= 0 && docIndex < retrievedRecords.size) {
                            val record = retrievedRecords[docIndex]
                            pushStringAnnotation(tag = "DOC_CLICK", annotation = record.id.toString())
                            pushStyle(
                                SpanStyle(
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            )
                            append(" 🔗 ${record.title} ")
                            pop()
                            pop()
                            i = end + 1
                        } else {
                            // Fallback if index out of bounds or invalid
                            pushStyle(SpanStyle(color = Color(0xFF00E676), fontWeight = FontWeight.Bold))
                            append(text.substring(i, end + 1))
                            pop()
                            i = end + 1
                        }
                    } else {
                        append("[Doc ")
                        i += 5
                    }
                }
                else -> {
                    append(text[i])
                    i += 1
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    // Auto-scroll to bottom on list update
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Infinite cosmic background breathing transition
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic")
    val cosmicProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("智能知识库对话", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "清空历史", tint = Color.LightGray)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0C1B))
                )
            }
        },
        containerColor = Color.Transparent, // Managed by custom background
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A0714), Color(0xFF120D22), Color(0xFF07040A))
                    )
                )
        ) {
            // Cosmic dust ambient glow canvas
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val sinVal = kotlin.math.sin((cosmicProgress * Math.PI / 180f)).toFloat()
                val pulsate = 1f + 0.15f * sinVal

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1F8E2DE2), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.2f),
                        radius = width * 0.7f * pulsate
                    ),
                    center = androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.2f),
                    radius = width * 0.7f * pulsate
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x164A00E0), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(width * 0.1f, height * 0.8f),
                        radius = width * 0.8f * (2f - pulsate)
                    ),
                    center = androidx.compose.ui.geometry.Offset(width * 0.1f, height * 0.8f),
                    radius = width * 0.8f * (2f - pulsate)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Conversation Area / Onboarding empty state
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OnboardingView(
                            onSelectPreset = { prompt ->
                                viewModel.sendMessage(context, prompt)
                                inputText = ""
                            }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp) // Leave top padding for the clear button
                        ) {
                            items(messages) { message ->
                                MessageBubble(
                                    message = message,
                                    onNavigateToDetail = onNavigateToDetail
                                )
                            }

                            if (isLoading) {
                                item {
                                    ThinkingBubble()
                                }
                            }
                        }

                        // Localized Glassmorphic Clear Button
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x33000000))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                                .clickable { viewModel.clearHistory() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClearAll,
                                    contentDescription = "清空对话",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "清空对话",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Floating Input Bar Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    InputCapsule(
                        text = inputText,
                        onTextChange = { inputText = it },
                        onSend = {
                            if (inputText.trim().isNotEmpty()) {
                                viewModel.sendMessage(context, inputText)
                                inputText = ""
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Premium onboarding empty state view with quick suggestion grids
 */
@Composable
fun OnboardingView(
    onSelectPreset: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Glowing brand logo image
        Image(
            painter = painterResource(id = com.example.awaassistant.R.drawable.ic_launcher_premium),
            contentDescription = "Awa Logo",
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Awa AI 智能助手",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = "随时提问有关本地知识、屏幕内容和手写笔记的信息",
                fontSize = 11.sp,
                color = Color.LightGray
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid cells for quick suggestions
        val suggestions = listOf(
            Pair("🔍 查找截图回忆", "帮我搜一下我刚才截图里的关键信息是什么？"),
            Pair("📝 总结工作便签", "总结我昨晚拍下的工作手记/会议备忘内容"),
            Pair("🥗 分析食物卡路里", "帮我看看我最近一次吃的食物有多少卡路里"),
            Pair("🍳 智能食材配菜谱", "根据我最新拍的食材照片，推荐几道健康菜谱")
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = false
        ) {
            items(suggestions) { pair ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF)),
                    border = BorderStroke(1.dp, Color(0x10FFFFFF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp) // Fixed height to align all items perfectly!
                        .clickable { onSelectPreset(pair.second) }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = pair.first,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = pair.second,
                            fontSize = 9.sp,
                            color = Color.Gray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Message Bubble representing ChatMessages
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    onNavigateToDetail: (Long) -> Unit
) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (isUser) {
            // User message bubble with gradient and asymmetric corners
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 4.dp
                ),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
                        ),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 4.dp
                        )
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            // AI message bubble with glassmorphic cards and local Markdown parser
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 16.dp
                ),
                colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF)),
                border = BorderStroke(1.dp, Color(0x12FFFFFF)),
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val parsedBlocks = remember(message.content) { parseMarkdown(message.content) }
                    parsedBlocks.forEach { block ->
                        when (block) {
                            is ChatContentBlock.ThinkBlock -> {
                                RenderThinkBlock(
                                    content = block.content,
                                    isFinished = block.isFinished,
                                    sources = message.sources,
                                    onNavigateToDetail = onNavigateToDetail
                                )
                            }
                            is ChatContentBlock.TextBlock -> {
                                RenderTextBlock(
                                    text = block.content,
                                    sources = message.sources,
                                    onNavigateToDetail = onNavigateToDetail
                                )
                            }
                            is ChatContentBlock.CodeBlock -> {
                                RenderCodeBlock(lang = block.language, code = block.code)
                            }
                        }
                    }
                }
            }

            // Sources block (retrieved context documents)
            if (message.sources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF8E2DE2), RoundedCornerShape(3.dp))
                    )
                    Text(
                        "参考本地知识：",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(message.sources) { record ->
                        SourceChip(record = record, onClick = { onNavigateToDetail(record.id) })
                    }
                }
            }
        }
    }
}

/**
 * Text renderer that supports inline formatting and bullet lists
 */
@Composable
fun RenderThinkBlock(
    content: String,
    isFinished: Boolean,
    sources: List<CaptureRecord> = emptyList(),
    onNavigateToDetail: (Long) -> Unit = {}
) {
    var isExpanded by remember(isFinished) { mutableStateOf(!isFinished) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF)),
        border = BorderStroke(1.dp, Color(0x12FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🧠",
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (isFinished) "思考过程" else "正在思考...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFinished) Color.LightGray else Color(0xFF8E2DE2)
                    )
                }

                Text(
                    text = if (isExpanded) "收起" else "展开",
                    fontSize = 11.sp,
                    color = Color(0xFF8E2DE2),
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    RenderTextBlock(
                        text = content,
                        color = Color(0xFF9E9BAC),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        sources = sources,
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
            }
        }
    }
}

/**
 * Text renderer that supports inline formatting, citation links, and bullet lists
 */
@Composable
fun RenderTextBlock(
    text: String,
    color: Color = Color(0xFFE2E0EB),
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 20.sp,
    sources: List<CaptureRecord> = emptyList(),
    onNavigateToDetail: (Long) -> Unit = {}
) {
    val lines = text.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                val cleanLine = line.trim().substring(2)
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("•", color = Color(0xFF8E2DE2), fontWeight = FontWeight.Bold, fontSize = fontSize)
                    val annotatedString = buildAnnotatedStringWithInlineStyles(cleanLine, sources)
                    ClickableText(
                        text = annotatedString,
                        style = LocalTextStyle.current.copy(
                            color = color,
                            fontSize = fontSize,
                            lineHeight = lineHeight
                        ),
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "DOC_CLICK", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    annotation.item.toLongOrNull()?.let { docId ->
                                        onNavigateToDetail(docId)
                                    }
                                }
                        }
                    )
                }
            } else {
                val annotatedString = buildAnnotatedStringWithInlineStyles(line, sources)
                ClickableText(
                    text = annotatedString,
                    style = LocalTextStyle.current.copy(
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight
                    ),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "DOC_CLICK", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                annotation.item.toLongOrNull()?.let { docId ->
                                    onNavigateToDetail(docId)
                                }
                            }
                    }
                )
            }
        }
    }
}

/**
 * Custom code block layout with dynamic Copy button
 */
@Composable
fun RenderCodeBlock(lang: String, code: String) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF07050F)),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF100D1C))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lang.ifEmpty { "code" }.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("awa_code", code)
                            clipboard.setPrimaryClip(clip)
                            isCopied = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isCopied) {
                        Text("已复制", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                    } else {
                        Text("复制", fontSize = 10.sp, color = Color.LightGray)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFDCD8E8),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Clickable referenced capture chip with image previews
 */
@Composable
fun SourceChip(
    record: CaptureRecord,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF)),
        border = BorderStroke(1.dp, Color(0x12FFFFFF)),
        modifier = Modifier
            .width(160.dp)
            .height(48.dp)
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (record.imagePath != null) {
                AsyncImage(
                    model = record.imagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x22FFFFFF))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x12FFFFFF), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF8E2DE2),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val typeText = when (record.sourceType) {
                    "SCREENSHOT" -> "屏幕截图"
                    "PHOTO" -> "手拍笔记"
                    "CALORIE" -> "卡路里记录"
                    "RECIPE" -> "智能食谱"
                    else -> "纯文本记录"
                }
                Text(
                    text = typeText,
                    fontSize = 9.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

/**
 * Premium floating input capsule layout with dynamic Send triggers
 */
@Composable
fun InputCapsule(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xE5150E28),
                shape = RoundedCornerShape(30.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0x20FFFFFF),
                shape = RoundedCornerShape(30.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left Accessory Plus Button
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(Color(0x0EFFFFFF), RoundedCornerShape(19.dp))
                .clickable { /* Attachments action */ },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "添加",
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }

        // Inner TextField with custom decoration
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            maxLines = 4,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = "输入问题，如“我昨晚手写的备忘...”",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Dynamic Send FAB with transition scaling
        val buttonVisible = text.trim().isNotEmpty()
        val scale by animateFloatAsState(
            targetValue = if (buttonVisible) 1f else 0.8f,
            label = "sendScale"
        )
        val opacity by animateFloatAsState(
            targetValue = if (buttonVisible) 1f else 0.5f,
            label = "sendAlpha"
        )

        Box(
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = opacity
                )
                .background(
                    Brush.linearGradient(
                        colors = if (buttonVisible) {
                            listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
                        } else {
                            listOf(Color(0x338E2DE2), Color(0x334A00E0))
                        }
                    ),
                    shape = RoundedCornerShape(19.dp)
                )
                .clickable(enabled = buttonVisible) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Send,
                contentDescription = "发送",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Thinking indicator containing 3 bouncing particles
 */
@Composable
fun ThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")

    @Composable
    fun animateDotOffset(delay: Int): Float {
        val floatState by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -8f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, delayMillis = delay, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "thinkingDot"
        )
        return floatState
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF)),
            border = BorderStroke(1.dp, Color(0x12FFFFFF)),
            modifier = Modifier.width(76.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dot1 = animateDotOffset(0)
                val dot2 = animateDotOffset(150)
                val dot3 = animateDotOffset(300)

                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .graphicsLayer(translationY = dot1)
                        .background(Color(0xFF8E2DE2), RoundedCornerShape(2.5.dp))
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .graphicsLayer(translationY = dot2)
                        .background(Color(0xFF7A22D2), RoundedCornerShape(2.5.dp))
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .graphicsLayer(translationY = dot3)
                        .background(Color(0xFF4A00E0), RoundedCornerShape(2.5.dp))
                )
            }
        }
    }
}
