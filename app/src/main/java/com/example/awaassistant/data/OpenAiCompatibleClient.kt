package com.example.awaassistant.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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
        val apiKey = SettingsManager.getApiKey(context)
        val baseUrl = SettingsManager.getBaseUrl(context)
        val model = SettingsManager.getModelName(context)

        if (apiKey.isEmpty()) {
            Log.e(TAG, "API Key is empty, skipping AI analysis.")
            return@withContext null
        }

        val currentTimeString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val promptContent = """
            你是一个个人 AI 助手。请分析以下屏幕文本或拍照笔记内容，并整理成 JSON 格式返回。
            当前系统时间是: $currentTimeString
            
            【整理要求】
            1. 规范语言：如果输入中包含错别字，请予以纠正；理顺句子排版，以易读的 Markdown 列表形式输出在 "summary" 中。所有输出内容必须使用规范的简体中文。
            2. 提炼标题：生成一个简短切题的标题（10字以内），放在 "title" 中。
            3. 提取标签：生成 1 到 3 个描述性标签，放在 "tags" 数组中。
            4. 提取提醒：如果内容中明确包含可作为待办提醒的事项与时间，请将其提取到 "reminders" 列表中。时间格式必须是 YYYY-MM-DD HH:mm:ss。如无明确提醒时间，请将 "reminders" 保持为空列表，严禁胡乱猜测或胡编乱造时间。
            5. 输出格式：直接输出纯 JSON 字符串，严禁包含任何 Markdown 格式包裹（如 ```json 等），保持输出极其简洁以提高处理速度。
            
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
            put("max_tokens", 1000)
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

        // 构建上下文
        val contextBuilder = StringBuilder()
        if (retrievedRecords.isNotEmpty()) {
            contextBuilder.append("以下是本地检索到的相关笔记和屏幕截取记录，请优先根据这些内容回答用户的问题：\n\n")
            retrievedRecords.forEachIndexed { index, record ->
                contextBuilder.append("【文档 ${index + 1}】\n")
                contextBuilder.append("标题: ${record.title}\n")
                contextBuilder.append("摘要: ${record.summary}\n")
                contextBuilder.append("详细原文: ${record.rawContent}\n")
                contextBuilder.append("标签: ${record.tags}\n")
                contextBuilder.append("----------------------\n\n")
            }
        } else {
            contextBuilder.append("没有在本地检索到相关的笔记记录。你可以使用你自身具备的知识库回答用户，但请明确说明该信息非本地存储记录。\n")
        }

        val systemPrompt = """
            你是一个个人智能 AI 助手。你可以帮用户检索、回忆和总结他们记录过的信息。
            
            ${contextBuilder}
            
            回答要求：
            1. 如果问题可以通过上述本地检索记录回答，请严格基于检索记录给出准确且精简的答复。
            2. 回答中如果引用了某个本地文档，可以提及文档的标题，以便用户了解来源。
            3. 请使用中文回答。
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
            val summary = json.optString("summary", "")

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
                    val rem = remindersArray.getJSONObject(i)
                    val remTitle = rem.optString("title", "")
                    val remTime = rem.optString("timeString", "")
                    if (remTitle.isNotEmpty() && remTime.isNotEmpty()) {
                        remindersList.add(ReminderSuggestion(remTitle, remTime))
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
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return text.substring(startIndex, endIndex + 1)
        }
        return text
    }
}
