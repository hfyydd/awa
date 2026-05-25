package com.example.awaassistant.util

object TagHelper {
    /**
     * 正则匹配并提取文本中的所有 #标签
     * 支持子标签（如 #工作/周报），排除标点符号和空白
     */
    fun extractInlineTags(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        // 匹配 # 开头后面跟着字母、数字、下划线、斜杠或汉字，直到空格、逗号或其它换行标点
        val regex = Regex("#([^\\s#,\\n\\r\\t，。、；;！？]+)")
        return regex.findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    /**
     * 合并已有标签和文本中新提取的标签，去重后生成逗号分隔的字符串
     */
    fun mergeTags(existingTags: String, text: String): String {
        val currentTags = existingTags.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val newTags = extractInlineTags(text)
        
        // 合并去重并组合
        return (currentTags + newTags)
            .distinct()
            .joinToString(",")
    }
}
