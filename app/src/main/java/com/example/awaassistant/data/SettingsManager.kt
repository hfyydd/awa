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
}
