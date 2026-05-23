package com.example.awaassistant.util

data class NutrientInfo(
    val calories: Int,
    val protein: Float,
    val fat: Float,
    val carbs: Float
)

object NutrientParser {
    fun parseNutrients(summary: String): NutrientInfo? {
        try {
            val calorieMatch = Regex("(?:总热量|热量).*?(\\d+(?:\\.\\d+)?)\\s*(?:kcal|千卡|大卡|卡)", RegexOption.IGNORE_CASE).find(summary)
            val proteinMatch = Regex("蛋白质.*?(\\d+(?:\\.\\d+)?)\\s*(?:g|克)", RegexOption.IGNORE_CASE).find(summary)
            val fatMatch = Regex("脂肪.*?(\\d+(?:\\.\\d+)?)\\s*(?:g|克)", RegexOption.IGNORE_CASE).find(summary)
            val carbsMatch = Regex("(?:碳水化合物|碳水).*?(\\d+(?:\\.\\d+)?)\\s*(?:g|克)", RegexOption.IGNORE_CASE).find(summary)

            val calories = calorieMatch?.groupValues?.get(1)?.toFloatOrNull()?.toInt() ?: 0
            val protein = proteinMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val fat = fatMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val carbs = carbsMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

            if (calories == 0 && protein == 0f && fat == 0f && carbs == 0f) {
                return null
            }
            return NutrientInfo(calories, protein, fat, carbs)
        } catch (e: Exception) {
            return null
        }
    }
}
