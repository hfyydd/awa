package com.example.awaassistant.data

import android.content.Context

object SettingsManager {
    private const val PREFS_NAME = "awa_settings"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MODEL_NAME = "model_name"

    private const val KEY_FLOATING_BALL_ENABLED = "floating_ball_enabled"
    private const val KEY_VOLUME_SHORTCUT_ENABLED = "volume_shortcut_enabled"
    private const val KEY_AUTO_ANALYZE_SCREENSHOTS = "auto_analyze_screenshots"

    private const val KEY_USE_DEDICATED_VISION = "use_dedicated_vision"
    private const val KEY_VISION_API_KEY = "vision_api_key"
    private const val KEY_VISION_BASE_URL = "vision_base_url"
    private const val KEY_VISION_MODEL_NAME = "vision_model_name"
    private const val KEY_DEFAULT_HOMEPAGE = "default_homepage"

    fun getApiKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_API_KEY, value).apply()
    }

    fun getBaseUrl(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, "https://api.deepseek.com/v1") ?: "https://api.deepseek.com/v1"
    }

    fun setBaseUrl(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_BASE_URL, value).apply()
    }

    fun getModelName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODEL_NAME, "deepseek-chat") ?: "deepseek-chat"
    }

    fun setModelName(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODEL_NAME, value).apply()
    }

    fun isFloatingBallEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FLOATING_BALL_ENABLED, true)
    }

    fun setFloatingBallEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FLOATING_BALL_ENABLED, value).apply()
    }

    fun isVolumeShortcutEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VOLUME_SHORTCUT_ENABLED, true)
    }

    fun setVolumeShortcutEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VOLUME_SHORTCUT_ENABLED, value).apply()
    }

    fun isAutoAnalyzeScreenshotsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_ANALYZE_SCREENSHOTS, false)
    }

    fun setAutoAnalyzeScreenshotsEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_ANALYZE_SCREENSHOTS, value).apply()
    }

    fun isDedicatedVisionEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_DEDICATED_VISION, true)
    }

    fun setDedicatedVisionEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USE_DEDICATED_VISION, value).apply()
    }

    fun getVisionApiKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VISION_API_KEY, "e32db79a6da248f1a0e3bfa5dad03d0e.7G3RT6rxLvgcdWWw") ?: "e32db79a6da248f1a0e3bfa5dad03d0e.7G3RT6rxLvgcdWWw"
    }

    fun setVisionApiKey(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_VISION_API_KEY, value).apply()
    }

    fun getVisionBaseUrl(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VISION_BASE_URL, "https://open.bigmodel.cn/api/paas/v4") ?: "https://open.bigmodel.cn/api/paas/v4"
    }

    fun setVisionBaseUrl(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_VISION_BASE_URL, value).apply()
    }

    fun getVisionModelName(context: Context): String {
        val model = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VISION_MODEL_NAME, "glm-4v-flash") ?: "glm-4v-flash"
        if (model == "glm-4.6v-flash") {
            setVisionModelName(context, "glm-4v-flash")
            return "glm-4v-flash"
        }
        return model
    }

    fun setVisionModelName(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_VISION_MODEL_NAME, value).apply()
    }

    fun getDefaultHomepage(context: Context): Int {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_HOMEPAGE, "CHAT") ?: "CHAT"
        return if (value == "DASHBOARD") 1 else 0
    }

    fun getDefaultHomepageString(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_HOMEPAGE, "CHAT") ?: "CHAT"
    }

    fun setDefaultHomepage(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEFAULT_HOMEPAGE, value).apply()
    }
}
