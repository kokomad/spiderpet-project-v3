package com.khader.spiderpet

import android.content.Context

/** Developer: خضر أبو صالحه (khader abo salha) - mankhader@gmail.com */
object Prefs {
    const val DEFAULT_MODEL = "gemini-3.8-flash"
    const val DEV_NAME_AR = "خضر أبو صالحه"
    const val DEV_NAME_EN = "khader abo salha"
    const val DEV_EMAIL = "mankhader@gmail.com"

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("spider_prefs", Context.MODE_PRIVATE)

    fun apiKey(c: Context): String = sp(c).getString("api_key", "") ?: ""
    fun setApiKey(c: Context, v: String) = sp(c).edit().putString("api_key", v.trim()).apply()

    fun model(c: Context): String {
        val m = (sp(c).getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL).trim().removePrefix("models/")
        return if (m.isEmpty()) DEFAULT_MODEL else m
    }

    fun setModel(c: Context, v: String) = sp(c).edit().putString("model", v.trim()).apply()

    fun aiOn(c: Context): Boolean = sp(c).getBoolean("ai_on", true)
    fun setAiOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("ai_on", v).apply()

    fun sizeDp(c: Context): Int = sp(c).getInt("size_dp", 56)
    fun setSizeDp(c: Context, v: Int) = sp(c).edit().putInt("size_dp", v).apply()

    fun speed(c: Context): Int = sp(c).getInt("speed", 5)
    fun setSpeed(c: Context, v: Int) = sp(c).edit().putInt("speed", v).apply()

    /** 0 = top-left, 1 = top-right, 2 = bottom-left, 3 = bottom-right */
    fun corner(c: Context): Int = sp(c).getInt("corner", 1)
    fun setCorner(c: Context, v: Int) = sp(c).edit().putInt("corner", v).apply()

    fun homeOnly(c: Context): Boolean = sp(c).getBoolean("home_only", true)
    fun setHomeOnly(c: Context, v: Boolean) = sp(c).edit().putBoolean("home_only", v).apply()

    fun webOn(c: Context): Boolean = sp(c).getBoolean("web_on", true)
    fun setWebOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("web_on", v).apply()

    fun openApps(c: Context): Boolean = sp(c).getBoolean("open_apps", false)
    fun setOpenApps(c: Context, v: Boolean) = sp(c).edit().putBoolean("open_apps", v).apply()

    fun aiStatus(c: Context): String = sp(c).getString("ai_status", "") ?: ""
    fun setAiStatus(c: Context, v: String) = sp(c).edit().putString("ai_status", v).apply()

    /** Minimum seconds between Gemini requests (saves the free-tier quota). */
    fun aiInterval(c: Context): Int = sp(c).getInt("ai_interval", 60)
    fun setAiInterval(c: Context, v: Int) = sp(c).edit().putInt("ai_interval", v).apply()

    /** Epoch millis until which Gemini must not be called (after HTTP 429). */
    fun aiBlockedUntil(c: Context): Long = sp(c).getLong("ai_blocked_until", 0L)
    fun setAiBlockedUntil(c: Context, v: Long) = sp(c).edit().putLong("ai_blocked_until", v).apply()
}
