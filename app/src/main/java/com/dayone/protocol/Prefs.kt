package com.dayone.protocol

import android.content.Context

object Prefs {
    private const val NAME = "reminders_prefs"

    // ---------- تفعيل/توقيت التذكيرات ----------

    fun isEnabled(context: Context, id: Int): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean("enabled_$id", true)

    fun setEnabled(context: Context, id: Int, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled_$id", enabled)
            .apply()
    }

    fun getHour(context: Context, id: Int, default: Int): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt("hour_$id", default)

    fun getMinute(context: Context, id: Int, default: Int): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt("minute_$id", default)

    fun setTime(context: Context, id: Int, hour: Int, minute: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("hour_$id", hour)
            .putInt("minute_$id", minute)
            .apply()
    }

    // ---------- الرؤية المضادة / الأولية (تُعرض في الـ Widget) ----------

    fun getVisionAnti(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("vision_anti", "") ?: ""

    fun setVisionAnti(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("vision_anti", value).apply()
    }

    fun getVisionInitial(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("vision_initial", "") ?: ""

    fun setVisionInitial(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("vision_initial", value).apply()
    }

    // ---------- إنجاز يومي لكل تذكير (لحساب الـ Streak) ----------

    fun markDone(context: Context, id: Int, dateKey: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("done_${id}_$dateKey", true).apply()
    }

    fun isDone(context: Context, id: Int, dateKey: String): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean("done_${id}_$dateKey", false)

    // ---------- المراجعة الأسبوعية ----------

    fun getWeeklyReviewEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean("weekly_enabled", true)

    fun setWeeklyReviewEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("weekly_enabled", enabled).apply()
    }

    fun getWeeklyHour(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt("weekly_hour", 18)

    fun getWeeklyMinute(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt("weekly_minute", 0)

    fun setWeeklyTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("weekly_hour", hour)
            .putInt("weekly_minute", minute)
            .apply()
    }

    fun saveWeeklyReviewAnswer(context: Context, dateKey: String, text: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("weekly_review_$dateKey", text).apply()
    }

    /** يرجّع (تاريخ آخر مراجعة, نصها) أو null لو مفيش */
    fun getLastWeeklyReviewAnswer(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val keys = prefs.all.keys.filter { it.startsWith("weekly_review_") }
        val latestKey = keys.maxOrNull() ?: return null
        val date = latestKey.removePrefix("weekly_review_")
        val text = prefs.getString(latestKey, "") ?: ""
        return date to text
    }

    // ---------------- دفتر العمل (رابط خارجي) ----------------

    fun getJournalUrl(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("journal_url", "") ?: ""

    fun setJournalUrl(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("journal_url", value).apply()
    }

    // ---------------- وضع الطوارئ ----------------

    fun getEnemy(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("enemy", "") ?: ""

    fun setEnemy(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("enemy", value).apply()
    }

    fun logEmergency(context: Context, text: String) {
        val key = "emergency_${System.currentTimeMillis()}"
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(key, text).apply()
    }

    // ---------------- عبارة الهوية العشوائية ----------------

    fun getIdentity(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("identity_text", "") ?: ""

    fun setIdentity(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("identity_text", value).apply()
    }

    fun getIdentityEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean("identity_enabled", true)

    fun setIdentityEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("identity_enabled", enabled).apply()
    }

    // ---------------- سؤال الحماية الذاتية (كل أسبوعين) ----------------

    fun getProtectionEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean("protection_enabled", true)

    fun setProtectionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("protection_enabled", enabled).apply()
    }

    fun getProtectionHour(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt("protection_hour", 19)

    fun getProtectionMinute(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt("protection_minute", 0)

    fun setProtectionTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("protection_hour", hour)
            .putInt("protection_minute", minute)
            .apply()
    }

    fun saveProtectionAnswer(context: Context, dateKey: String, text: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("protection_review_$dateKey", text).apply()
    }

    fun getLastProtectionAnswer(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val keys = prefs.all.keys.filter { it.startsWith("protection_review_") }
        val latestKey = keys.maxOrNull() ?: return null
        val date = latestKey.removePrefix("protection_review_")
        val text = prefs.getString(latestKey, "") ?: ""
        return date to text
    }

    // ---------------- خطة اللعبة: هدف السنة / مشروع الشهر ----------------

    fun getYearGoal(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("year_goal", "") ?: ""

    fun setYearGoal(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("year_goal", value).apply()
    }

    fun getMonthProject(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString("month_project", "") ?: ""

    fun setMonthProject(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString("month_project", value).apply()
    }

    fun getMonthlyCheckEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean("monthly_check_enabled", true)

    fun setMonthlyCheckEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("monthly_check_enabled", enabled).apply()
    }

    fun getMonthlyCheckHour(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt("monthly_check_hour", 9)

    fun getMonthlyCheckMinute(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt("monthly_check_minute", 0)

    fun setMonthlyCheckTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("monthly_check_hour", hour)
            .putInt("monthly_check_minute", minute)
            .apply()
    }

    // ---------------- تجديد الرؤية المضادة شهريًا ----------------

    fun getAntiVisionRefreshEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean("anti_refresh_enabled", true)

    fun setAntiVisionRefreshEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("anti_refresh_enabled", enabled).apply()
    }

    fun getAntiVisionRefreshHour(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt("anti_refresh_hour", 20)

    fun getAntiVisionRefreshMinute(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt("anti_refresh_minute", 0)

    fun setAntiVisionRefreshTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("anti_refresh_hour", hour)
            .putInt("anti_refresh_minute", minute)
            .apply()
    }

    fun logAntiVisionRefresh(context: Context, text: String) {
        val key = "anti_refresh_log_${System.currentTimeMillis()}"
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(key, text).apply()
    }

    // ---------------- نظام الليفلات ----------------

    fun getLastCelebratedLevel(context: Context): Int =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt("last_celebrated_level", 0)

    fun setLastCelebratedLevel(context: Context, level: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putInt("last_celebrated_level", level).apply()
    }

    // ---------------- تتبّع الموعد القادم للتذكيرات الدورية (منع الانزلاق) ----------------

    fun getNextTrigger(context: Context, key: String): Long =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getLong("next_trigger_$key", 0L)

    fun setNextTrigger(context: Context, key: String, value: Long) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putLong("next_trigger_$key", value).apply()
    }
}
