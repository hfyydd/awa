package com.example.awaassistant.ui.main

import com.example.awaassistant.util.NutrientParser
import com.example.awaassistant.ui.detail.parseIngredientLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NutrientParserTest {

    @Test
    fun testParseNutrients() {
        val summary1 = """
            【识别出的食物/菜品】
            - 白米饭 (约100g)
            - 玉米 (半个)
            - 红烧肉 (约100g)
            - 素炒蔬菜 (约200g)
            - 豆腐炖蘑菇 (约150g)

            【食材明细估算】
            - 🌽 玉米 (半个)：100 kcal
            - 🍚 白米饭 (约100g)：110 kcal
            - 🐷 红烧肉 (约100g)：300 kcal
            - 🥦 素炒蔬菜 (约200g)：50 kcal
            - 🧀 豆腐炖蘑菇 (约150g)：90 kcal

            【卡路里/营养成分估算】
            - 总热量：650 kcal
            - 蛋白质：约 30g
            - 脂肪：约 30g
            - 碳水化合物：约 80g

            【健康点评与建议】
            - 这是一份均衡的中式餐点，包含了主食、蔬菜和肉类。
            - 可以适量增加蔬菜摄入量，减少油脂的使用。
        """.trimIndent()

        val info = NutrientParser.parseNutrients(summary1)
        assertNotNull("NutrientInfo should not be null", info)
        assertEquals(650, info?.calories)
        assertEquals(30f, info?.protein ?: 0f)
        assertEquals(30f, info?.fat ?: 0f)
        assertEquals(80f, info?.carbs ?: 0f)
    }

    @Test
    fun testParseIngredientLine() {
        val line1 = "- 🌽 玉米 (半个)：100 kcal"
        val item1 = parseIngredientLine(line1)
        println("parsed item1 emoji: '${item1?.emoji}' (len ${item1?.emoji?.length}), name: '${item1?.name}'")
        assertNotNull("IngredientItem 1 should not be null", item1)
        assertEquals("🌽", item1?.emoji)
        assertEquals("玉米", item1?.name)
        assertEquals("半个", item1?.weight)
        assertEquals("100", item1?.calories)

        val line2 = "- 🍚 白米饭 (约100g)：110 kcal"
        val item2 = parseIngredientLine(line2)
        println("parsed item2 emoji: '${item2?.emoji}' (len ${item2?.emoji?.length}), name: '${item2?.name}'")
        assertNotNull("IngredientItem 2 should not be null", item2)
        assertEquals("🍚", item2?.emoji)
        assertEquals("白米饭", item2?.name)
        assertEquals("约100g", item2?.weight)
        assertEquals("110", item2?.calories)
    }

    @Test
    fun testRegexBehavior() {
        val cleanLine = "🌽 玉米"
        val emojiRegex = Regex("^([\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\u2300-\\u23FF\\u2B50\\u2934\\u2B06])")
        val emojiMatch = emojiRegex.find(cleanLine)
        println("emojiMatch matched: '${emojiMatch?.value}' (len ${emojiMatch?.value?.length})")
    }
}
