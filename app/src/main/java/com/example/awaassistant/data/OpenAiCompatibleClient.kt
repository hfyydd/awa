package com.example.awaassistant.data

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AnalysisResult(
    val title: String,
    val summary: String,
    val tags: List<String>,
    val reminders: List<ReminderSuggestion>
)

data class ReminderSuggestion(
    val title: String,
    val timeString: String // 格式: "YYYY-MM-DD HH:mm:ss"
)

object OpenAiCompatibleClient {
    private const val TAG = "OpenAiClient"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * 调用大模型分析屏幕文字或笔记 OCR 文本
     */
    suspend fun analyzeText(context: Context, rawText: String): AnalysisResult? = withContext(Dispatchers.IO) {
        try {
            val apiKey = SettingsManager.getApiKey(context)
            val baseUrl = SettingsManager.getBaseUrl(context)
            val model = SettingsManager.getModelName(context)

            if (apiKey.isEmpty()) {
                Log.e(TAG, "API Key is empty, skipping AI analysis.")
                return@withContext null
            }

            val currentTimeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val promptContent = """
                你是一个个人 AI 助手。请分析以下屏幕文本或拍照笔记内容，并整理成符合以下 JSON Schema 格式的 JSON 字符串返回。
                当前系统时间是: $currentTimeString
                
                【JSON 格式要求】
                {
                  "title": "简短切题的标题（10字以内，比如'接姥姥姥爷'）",
                  "summary": "以易读的 Markdown 列表形式整理的文本摘要（纠正错别字，理顺句子排版，必须是规范的简体中文，比如'- 下午六点去接姥姥姥爷'）",
                  "tags": ["标签1", "标签2"], // 1 到 3 个描述性标签
                  "reminders": [ // 待办提醒列表，如无明确提醒时间，请保持为空列表 []。严禁胡乱编造时间。
                    {
                      "title": "提醒的内容（15字以内）",
                      "timeString": "格式必须是 YYYY-MM-DD HH:mm:ss（例如 '2026-05-22 18:00:00'）"
                    }
                  ]
                }

                【整理要求】
                1. 规范语言：如果输入中包含错别字，请予以纠正；理顺句子排版。
                2. 提取提醒：如无明确提醒时间，请将 "reminders" 保持为空列表，严禁胡乱猜测或胡编乱造时间。
                3. 输出格式：直接输出纯 JSON 字符串，严禁包含任何 Markdown 格式包裹（如 ```json 等），保持输出极其简洁以提高处理速度。
                
                【输入内容】
                $rawText
            """.trimIndent()

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", promptContent)
                })
            }

            val payload = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.1)
                put("max_tokens", 16384)
            }

            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val requestUrl = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

            val request = Request.Builder()
                .url(requestUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed: ${response.code} ${response.message}")
                    return@withContext null
                }

                val responseBody = response.body?.string() ?: return@withContext null
                Log.d(TAG, "Response: $responseBody")

                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val content = choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    return@withContext parseAnalysisResult(content)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in analyzeText", e)
        }

        return@withContext null
    }

    private fun getVisionApiConfig(context: Context): Triple<String, String, String> {
        val useDedicated = SettingsManager.isDedicatedVisionEnabled(context)
        val visionKey = SettingsManager.getVisionApiKey(context)
        val visionUrl = SettingsManager.getVisionBaseUrl(context)
        val visionModel = SettingsManager.getVisionModelName(context)

        return if (useDedicated && visionKey.isNotEmpty() && visionUrl.isNotEmpty() && visionModel.isNotEmpty()) {
            Triple(visionKey, visionUrl, visionModel)
        } else {
            Triple(
                SettingsManager.getApiKey(context),
                SettingsManager.getBaseUrl(context),
                SettingsManager.getModelName(context)
            )
        }
    }

    /**
     * 拍照分析卡路里 (结合离线图像标签与 OCR 的本地识别 + 文本大模型分析模式)
     */
    suspend fun analyzeCalorieImage(
        context: Context,
        imageFile: File,
        ocrText: String,
        imageLabels: List<String>
    ): AnalysisResult? = withContext(Dispatchers.IO) {
        val apiKey = SettingsManager.getApiKey(context)
        val baseUrl = SettingsManager.getBaseUrl(context)
        val model = SettingsManager.getModelName(context)

        if (apiKey.isEmpty()) {
            Log.e(TAG, "API Key is empty, skipping Calorie analysis.")
            return@withContext null
        }

        val currentTimeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val labelsString = if (imageLabels.isNotEmpty()) imageLabels.joinToString(", ") else "未识别到具体食物特征"

        val promptContent = """
            你是一个专业的营养师和健康管理助手。
            请根据以下由手机本地离线图像识别模型和 OCR 文本检测出的结果，分析该食物（或餐食），估算其总热量、蛋白质、脂肪、碳水化合物的含量，并提供一些健康点评或饮食建议。
            当前系统时间是: $currentTimeString
            
            【手机本地离线图像分类结果】
            $labelsString
            
            【提取到的图片内 OCR 文字】
            ${ocrText.ifEmpty { "（未检测到明显文字）" }}
            
            请将整理好的结果以符合以下 JSON Schema 格式的 JSON 字符串返回：
            
            【JSON 格式要求】
            {
              "title": "估算出来的食物名称（10字以内，比如'红烧肉盖饭'，如果无法分析，则写'健康记录'）",
              "summary": "【识别出的食物/菜品】\\n- [结合图像识别标签及文字描述菜品]\\n\\n【食材明细估算】\\n- 🍕 食材1 (约50g)：100 kcal\\n- 🥦 食材2 (约100g)：50 kcal\\n\\n【卡路里/营养成分估算】\\n- 总热量：约 150 kcal\\n- 蛋白质：约 5g\\n- 脂肪：约 2g\\n- 碳水化合物：约 20g\\n\\n【健康点评与建议】\\n- [专业营养建议与点评]",
              "tags": ["卡路里", "健康记录", "食物类别标签"], // 2 到 3 个描述性标签
              "reminders": [] // 保持为空列表 []
            }

            【整理要求】
            1. 必须先识别后估算：必须结合图像识别标签（如 Pizza, Salad, Rice 等）与 OCR 文本判断食物，先在【识别出的食物/菜品】区块中明确描述识别结果，这样更能让用户信服。
            2. 估算要合理：根据食物类别和常见分量进行科学的热量及三大营养素估算。蛋白质、脂肪、碳水化合物必须以“约 XXg”或类似的数字g格式输出，以便正则提取。
            3. 结构规范：summary 字段必须是一个纯文本字符串（使用 \\n 进行换行，绝对不要输出为 JSON 对象），必须依次且完整地包含这四个主标题：`【识别出的食物/菜品】`、`【食材明细估算】`、`【卡路里/营养成分估算】`、`【健康点评与建议】`。
            4. 食材细分要求：必须在【食材明细估算】中，将识别出的主要食材和配料以带Emoji和单项卡路里的格式按行拆解（如 `- 🍚 米饭 (约150g)：174 kcal`）。
            5. 输出格式：直接输出纯 JSON 字符串，严禁包含任何 Markdown 格式包裹（如 ```json 等），保持输出极其简洁。
        """.trimIndent()

        try {
            Log.d(TAG, "Attempting text-only calorie analysis with offline image labels...")

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", promptContent)
                })
            }

            val payload = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.3)
                put("max_tokens", 16384)
            }

            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val requestUrl = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

            val request = Request.Builder()
                .url(requestUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        Log.d(TAG, "Text-only Calorie Response: $responseBody")
                        val jsonResponse = JSONObject(responseBody)
                        val choices = jsonResponse.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val content = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            val result = parseAnalysisResult(content)
                            if (result != null) {
                                return@withContext result
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Text-only calorie request failed with code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text-only calorie analysis failed: ${e.message}", e)
        }

        return@withContext null
    }

    /**
     * 拍照配菜谱 (支持 Vision 多模态及 OCR 降级逻辑)
     */
    suspend fun analyzeRecipeImage(context: Context, imageFile: File, ocrText: String): AnalysisResult? = withContext(Dispatchers.IO) {
        val (apiKey, baseUrl, model) = getVisionApiConfig(context)

        if (apiKey.isEmpty()) {
            Log.e(TAG, "API Key is empty, skipping Recipe analysis.")
            return@withContext null
        }

        val currentTimeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val promptContent = """
            你是一个优秀的家庭主厨和营养配餐专家。
            【最核心指令】你必须先仔细观察并识别出图片中包含什么具体的食材、配料（或者从OCR文本中识别出来的食材），然后再去搭配/设计家常菜谱。
            当前系统时间是: $currentTimeString
            
            请将整理好的结果以符合以下 JSON Schema 格式 of JSON 字符串返回：
            
            【JSON 格式要求】
            {
              "title": "推荐的菜谱名称（10字以内，比如'家常青椒炒肉'）",
              "summary": "【识别出的食材】\\n- [食材描述及新鲜程度]\\n\\n【食材明细估算】\\n- 🥩 食材1 (约100g)：150 kcal\\n- 🌶️ 食材2 (约50g)：20 kcal\\n\\n【推荐搭配菜谱】\\n- 🍳 推荐菜名：做法步骤描述...\\n\\n【健康点评与烹饪建议】\\n- [专业营养建议、火候建议与点评]",
              "tags": ["配菜谱", "家常食谱", "美食"], // 2 到 3 个描述性标签
              "reminders": [] // 保持为空列表 []
            }

            【整理要求】
            1. 必须先识别后推荐：必须先在【识别出的食材】区块中明确列出您在图片中看到了哪些蔬菜、肉类或配料。
            2. 结构规范：summary 字段必须是一个纯文本字符串（使用 \\n 进行换行，绝对不要输出为 JSON 对象），必须依次且完整地包含这四个主标题：`【识别出的食材】`、`【食材明细估算】`、`【推荐搭配菜谱】`、`【健康点评与烹饪建议】`。
            3. 输出格式：直接输出纯 JSON 字符串，严禁包含任何 Markdown 格式包裹（如 ```json 等）。
        """.trimIndent()

        // 1. 尝试多模态请求 (Vision)
        try {
            Log.d(TAG, "Attempting multimodal recipe analysis...")
            val bytes = compressImageFile(imageFile)
            val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val userContent = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", promptContent)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$base64Image")
                    })
                })
            }

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            }

            val payload = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.3)
                put("max_tokens", 16384)
                if (model.contains("glm-4.6v")) {
                    put("thinking", JSONObject().apply {
                        put("type", "disabled")
                    })
                }
            }

            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val requestUrl = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

            val request = Request.Builder()
                .url(requestUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        Log.d(TAG, "Multimodal Recipe Response: $responseBody")
                        val jsonResponse = JSONObject(responseBody)
                        val choices = jsonResponse.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val content = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            val result = parseAnalysisResult(content)
                            if (result != null) {
                                return@withContext result
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "Multimodal recipe request failed with code: ${response.code}. Falling back to text OCR...")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Multimodal recipe analysis failed: ${e.message}. Falling back to text OCR...", e)
        }

        // 2. 降级为文本请求 (OCR)
        try {
            Log.d(TAG, "Falling back to text-only OCR recipe analysis...")
            val fallbackPromptContent = """
                你是一个优秀的家庭主厨和营养配餐专家。由于多模态识别暂不可用，请根据以下从食材/厨房照片中提取出的 OCR 文字，识别食材，并推荐适合的家常菜谱。
                当前系统时间是: $currentTimeString
                
                请将整理好的结果以符合以下 JSON Schema 格式的 JSON 字符串返回：
                
                【JSON 格式要求】
                {
                  "title": "推荐的菜谱名称（10字以内，比如'青椒炒土豆丝'）",
                  "summary": "【识别出的食材】\\n- [食材描述及分量]\\n\\n【食材明细估算】\\n- 🥔 食材1 (约100g)：50 kcal\\n\\n【推荐搭配菜谱】\\n- 🍳 推荐菜名：做法步骤描述...\\n\\n【健康点评与烹饪建议】\\n- [烹饪小贴士和营养搭配建议]",
                  "tags": ["配菜谱", "家常食谱"], 
                  "reminders": []
                }

                【提取的 OCR 文字】
                $ocrText

                【整理要求】
                1. 结构规范：summary 字段必须是一个纯文本字符串（使用 \\n 进行换行，绝对不要输出为 JSON 对象），必须依次且完整地包含这四个主标题：`【识别出的食材】`、`【食材明细估算】`、`【推荐搭配菜谱】`、`【健康点评与烹饪建议】`。
                2. 输出格式：直接输出纯 JSON 字符串，严禁包含任何 Markdown 格式包裹（如 ```json 等）。
            """.trimIndent()

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", fallbackPromptContent)
                })
            }

            val payload = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.3)
                put("max_tokens", 16384)
            }

            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val requestUrl = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

            val request = Request.Builder()
                .url(requestUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        Log.d(TAG, "OCR Recipe Response: $responseBody")
                        val jsonResponse = JSONObject(responseBody)
                        val choices = jsonResponse.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val content = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            return@withContext parseAnalysisResult(content)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR recipe analysis failed too", e)
        }

        return@withContext null
    }

    /**
     * 基于本地检索内容的 RAG 问答
     */
    private fun getFriendlyDateTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val oneDay = 24 * 60 * 60 * 1000L
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val timePart = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        return when {
            diff < 0 -> sdf.format(Date(timestamp))
            diff < 5 * 60 * 1000L -> "刚刚"
            diff < 60 * 60 * 1000L -> "${diff / (60 * 1000L)}分钟前"
            diff < 24 * 60 * 60 * 1000L -> {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
                val recordStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
                if (todayStr == recordStr) {
                    "今天 $timePart"
                } else {
                    "昨天 $timePart"
                }
            }
            diff < 2 * oneDay -> {
                val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now - oneDay))
                val recordStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
                if (yesterdayStr == recordStr) {
                    "昨天 $timePart"
                } else {
                    "前天 $timePart"
                }
            }
            diff < 3 * oneDay -> "前天 $timePart"
            else -> sdf.format(Date(timestamp))
        }
    }

    /**
     * 基于本地检索内容的 RAG 问答
     */
    suspend fun chatWithContext(
        context: Context,
        query: String,
        retrievedRecords: List<CaptureRecord>,
        chatHistory: List<Pair<String, String>> // First: role ("user"/"assistant"), Second: message
    ): String = withContext(Dispatchers.IO) {
        val apiKey = SettingsManager.getApiKey(context)
        val baseUrl = SettingsManager.getBaseUrl(context)
        val model = SettingsManager.getModelName(context)

        if (apiKey.isEmpty()) {
            return@withContext "请先去设置页面配置您的 API Key。"
        }

        val currentTimeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // 构建上下文
        val contextBuilder = StringBuilder()
        if (retrievedRecords.isNotEmpty()) {
            contextBuilder.append("<retrieved_context>\n")
            contextBuilder.append("以下是本地检索到的相关笔记和屏幕截取记录，按照相关度排序（请优先根据这些内容，并结合记录的时间戳回答用户的问题）：\n\n")
            retrievedRecords.forEachIndexed { index, record ->
                val typeName = when (record.sourceType) {
                    "SCREENSHOT" -> "屏幕截图"
                    "PHOTO" -> "手拍笔记"
                    "CALORIE" -> "健康卡路里记录"
                    else -> "纯文本记录"
                }
                val timeStr = getFriendlyDateTime(record.timestamp)
                contextBuilder.append("【文档 ${index + 1}】\n")
                contextBuilder.append("记录时间: $timeStr\n")
                contextBuilder.append("记录类型: $typeName\n")
                contextBuilder.append("标题: ${record.title}\n")
                contextBuilder.append("摘要: ${record.summary}\n")
                contextBuilder.append("详细原文: ${record.rawContent}\n")
                contextBuilder.append("标签: ${record.tags}\n")
                contextBuilder.append("----------------------\n\n")
            }
            contextBuilder.append("</retrieved_context>")
        } else {
            contextBuilder.append("<retrieved_context>\n没有在本地检索到相关的笔记或截图记录。\n</retrieved_context>")
        }

        val systemPrompt = """
            <system_role>
            你是一个部署在用户手机端的个人智能 AI 助手，名为 Awa。你能够基于用户的屏幕截图和拍下的工作笔记（即下方提供的本地上下文数据）帮用户做回忆和检索。
            </system_role>
            
            <current_environment>
            当前系统时间: $currentTimeString
            当前活跃页面: 智能对话页
            </current_environment>
            
            $contextBuilder
            
            <generation_rules>
            1. 事实性约束：有本地文档时优先根据文档回答。如果检索到的文档无法回答该问题，请明确告知用户：“在本地笔记中未检索到相关内容，基于我自身知识推测...”。
            2. 引用约束：回答必须引用出处。如果你的回答引用了某个本地文档，请在句尾（标点符号前）以 `[Doc X]` 的格式作为文献引用标志（例如：“...根据[Doc 1]所示...”，其中 X 代表文档的序号，如 1 代表文档 1），不要使用任何其他格式的标记，且不要自己捏造不存在的文档序号。
            3. 语气约束：回答要专业、精炼、富有逻辑，避免啰嗦。
            4. 语言约束：使用简体中文回答。
            </generation_rules>
        """.trimIndent()

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            // 添加历史记录
            chatHistory.forEach { (role, message) ->
                put(JSONObject().apply {
                    put("role", role)
                    put("content", message)
                })
            }
            // 添加当前提问
            put(JSONObject().apply {
                put("role", "user")
                put("content", query)
            })
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.7)
        }

        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val requestUrl = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

        val request = Request.Builder()
            .url(requestUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "大模型请求失败，错误代码: ${response.code}"
                }

                val responseBody = response.body?.string() ?: return@withContext "收到空回复"
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    return@withContext choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            }
        } catch (e: Exception) {
            return@withContext "大模型连接出错: ${e.message}"
        }

        return@withContext "大模型未返回任何内容"
    }

    /**
     * 基于本地检索内容的 RAG 问答（流式返回）
     */
    fun chatWithContextStream(
        context: Context,
        query: String,
        retrievedRecords: List<CaptureRecord>,
        chatHistory: List<Pair<String, String>>
    ): Flow<String> = flow {
        val apiKey = SettingsManager.getApiKey(context)
        val baseUrl = SettingsManager.getBaseUrl(context)
        val model = SettingsManager.getModelName(context)

        if (apiKey.isEmpty()) {
            emit("请先去设置页面配置您的 API Key。")
            return@flow
        }

        val currentTimeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // 构建上下文
        val contextBuilder = StringBuilder()
        if (retrievedRecords.isNotEmpty()) {
            contextBuilder.append("<retrieved_context>\n")
            contextBuilder.append("以下是本地检索到的相关笔记和屏幕截取记录，按照相关度排序（请优先根据这些内容，并结合记录的时间戳回答用户的问题）：\n\n")
            retrievedRecords.forEachIndexed { index, record ->
                val typeName = when (record.sourceType) {
                    "SCREENSHOT" -> "屏幕截图"
                    "PHOTO" -> "手拍笔记"
                    "CALORIE" -> "健康卡路里记录"
                    else -> "纯文本记录"
                }
                val timeStr = getFriendlyDateTime(record.timestamp)
                contextBuilder.append("【文档 ${index + 1}】\n")
                contextBuilder.append("记录时间: $timeStr\n")
                contextBuilder.append("记录类型: $typeName\n")
                contextBuilder.append("标题: ${record.title}\n")
                contextBuilder.append("摘要: ${record.summary}\n")
                contextBuilder.append("详细原文: ${record.rawContent}\n")
                contextBuilder.append("标签: ${record.tags}\n")
                contextBuilder.append("----------------------\n\n")
            }
            contextBuilder.append("</retrieved_context>")
        } else {
            contextBuilder.append("<retrieved_context>\n没有在本地检索到相关的笔记或截图记录。\n</retrieved_context>")
        }

        val systemPrompt = """
            <system_role>
            你是一个部署在用户手机端的个人智能 AI 助手，名为 Awa。你能够基于用户的屏幕截图 and 拍下的工作笔记（即下方提供的本地上下文数据）帮用户做回忆 and 检索。
            </system_role>
            
            <current_environment>
            当前系统时间: $currentTimeString
            当前活跃页面: 智能对话页
            </current_environment>
            
            $contextBuilder
            
            <generation_rules>
            1. 事实性约束：有本地文档时优先根据文档回答。如果检索到的文档无法回答该问题，请明确告知用户：“在本地笔记中未检索到相关内容，基于我自身知识推测...”。
            2. 引用约束：回答必须引用出处。如果你的回答引用了某个本地文档，请在句尾（标点符号前）以 `[Doc X]` 的格式作为文献引用标志（例如：“...根据[Doc 1]所示...”，其中 X 代表文档的序号，如 1 代表文档 1），不要使用任何其他格式的标记，且不要自己捏造不存在的文档序号。
            3. 语气约束：回答要专业、精炼、富有逻辑，避免啰嗦。
            4. 语言约束：使用简体中文回答。
            </generation_rules>
        """.trimIndent()

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            // 添加历史记录
            chatHistory.forEach { (role, message) ->
                put(JSONObject().apply {
                    put("role", role)
                    put("content", message)
                })
            }
            // 添加当前提问
            put(JSONObject().apply {
                put("role", "user")
                put("content", query)
            })
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.7)
            put("stream", true)
        }

        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val requestUrl = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

        val request = Request.Builder()
            .url(requestUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit("大模型请求失败，错误代码: ${response.code}")
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit("收到空回复")
                return@flow
            }

            body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data:")) {
                        val data = line.substring(5).trim()
                        if (data == "[DONE]") {
                            break
                        }
                        if (data.isNotEmpty()) {
                            try {
                                val json = JSONObject(data)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    if (delta != null && delta.has("content")) {
                                        val chunk = delta.getString("content")
                                        emit(chunk)
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignored
                            }
                        }
                    }
                }
            }
            response.close()
        } catch (e: Exception) {
            emit("\n[大模型连接出错: ${e.message}]")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 测试大模型连接
     */
    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (apiKey.trim().isEmpty()) {
            return@withContext Pair(false, "API Key 不能为空")
        }
        if (baseUrl.trim().isEmpty()) {
            return@withContext Pair(false, "Base URL 不能为空")
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", "请用最简短的中文回复，例如'连接成功'")
            })
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 50)
            put("temperature", 0.5)
            if (model.contains("glm-4.6v")) {
                put("thinking", JSONObject().apply {
                    put("type", "disabled")
                })
            }
        }

        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val requestUrl = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

        val request = Request.Builder()
            .url(requestUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(responseBody).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "HTTP ${response.code}: ${response.message}"
                    }
                    return@withContext Pair(false, errMsg)
                }

                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val content = choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    return@withContext Pair(true, content.trim())
                }
                return@withContext Pair(false, "未收到模型回复")
            }
        } catch (e: Exception) {
            return@withContext Pair(false, "连接失败: ${e.message}")
        }
    }

    private fun parseAnalysisResult(rawContent: String): AnalysisResult? {
        try {
            // 提取可能的 JSON 部分，防止模型返回包含 markdown 包装
            val jsonString = extractJson(rawContent)
            val json = JSONObject(jsonString)

            val title = json.optString("title", "未命名记录")
            
            val summary: String
            val summaryObj = json.optJSONObject("summary")
            if (summaryObj != null) {
                val sb = StringBuilder()
                val sections = listOf("【识别出的食物/菜品】", "【食材明细估算】", "【卡路里/营养成分估算】", "【健康点评与建议】")
                var appendedAny = false
                for (section in sections) {
                    if (summaryObj.has(section)) {
                        sb.append(section).append("\n").append(summaryObj.optString(section)).append("\n\n")
                        appendedAny = true
                    }
                }
                if (!appendedAny) {
                    val keys = summaryObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        sb.append(key).append("\n").append(summaryObj.optString(key)).append("\n\n")
                    }
                }
                summary = sb.toString().trim()
            } else {
                summary = json.optString("summary", "")
            }

            val tagsList = mutableListOf<String>()
            val tagsArray = json.optJSONArray("tags")
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    tagsList.add(tagsArray.getString(i))
                }
            }

            val remindersList = mutableListOf<ReminderSuggestion>()
            val remindersArray = json.optJSONArray("reminders")
            if (remindersArray != null) {
                for (i in 0 until remindersArray.length()) {
                    try {
                        val element = remindersArray.get(i)
                        if (element is JSONObject) {
                            val remTitle = element.optString("title", "")
                            val remTime = element.optString("timeString", "")
                            if (remTitle.isNotEmpty() && remTime.isNotEmpty()) {
                                remindersList.add(ReminderSuggestion(remTitle, remTime))
                            }
                        } else if (element is String) {
                            if (element.trim().isNotEmpty()) {
                                remindersList.add(ReminderSuggestion(title, element.trim()))
                            }
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Failed to parse single reminder item at index $i", ex)
                    }
                }
            }

            return AnalysisResult(title, summary, tagsList, remindersList)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI JSON response: $rawContent", e)
            return null
        }
    }

    private fun extractJson(text: String): String {
        var cleanedText = text
        // Remove <think>...</think> blocks (including open-ended or truncated ones)
        while (cleanedText.contains("<think>")) {
            val start = cleanedText.indexOf("<think>")
            val end = cleanedText.indexOf("</think>")
            if (end != -1 && end > start) {
                cleanedText = cleanedText.substring(0, start) + cleanedText.substring(end + 8)
            } else {
                cleanedText = cleanedText.substring(0, start)
                break
            }
        }

        val startIndex = cleanedText.indexOf('{')
        val endIndex = cleanedText.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return cleanedText.substring(startIndex, endIndex + 1)
        }
        return cleanedText
    }

    private fun compressImageFile(imageFile: File, maxDimension: Int = 1024, quality: Int = 80): ByteArray {
        try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, options)
            
            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            
            var sampleSize = 1
            while (srcWidth / sampleSize > maxDimension || srcHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            
            val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions) ?: return imageFile.readBytes()
            
            val currentWidth = bitmap.width
            val currentHeight = bitmap.height
            val scale = Math.min(maxDimension.toFloat() / currentWidth, maxDimension.toFloat() / currentHeight)
            
            val finalBitmap = if (scale < 1.0f) {
                val newWidth = (currentWidth * scale).toInt()
                val newHeight = (currentHeight * scale).toInt()
                android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }
            
            val outputStream = java.io.ByteArrayOutputStream()
            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, outputStream)
            val resultBytes = outputStream.toByteArray()
            
            if (finalBitmap != bitmap) {
                finalBitmap.recycle()
            }
            bitmap.recycle()
            
            Log.d(TAG, "Compressed image from ${imageFile.length() / 1024} KB to ${resultBytes.size / 1024} KB")
            return resultBytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress image, using original bytes", e)
            return imageFile.readBytes()
        }
    }
}
