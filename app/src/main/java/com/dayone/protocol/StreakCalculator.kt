package com.dayone.protocol

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object StreakCalculator {
    private val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)

    fun dateKey(daysAgo: Int = 0): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return fmt.format(cal.time)
    }

    /** عدد الأيام المتتالية (بدءًا من أمس رجوعًا) اللي كل التذكيرات المفعّلة اتعملت فيها بالكامل */
    fun calculateStreak(context: Context, maxDays: Int = 120): Int {
        val enabledIds = Reminders.defaults.filter { Prefs.isEnabled(context, it.id) }.map { it.id }
        if (enabledIds.isEmpty()) return 0
        var streak = 0
        for (daysAgo in 1..maxDays) {
            val key = dateKey(daysAgo)
            val allDone = enabledIds.all { Prefs.isDone(context, it, key) }
            if (allDone) streak++ else break
        }
        return streak
    }

    /** (المُنجز اليوم, الإجمالي المفعّل اليوم) */
    fun todayProgress(context: Context): Pair<Int, Int> {
        val enabledIds = Reminders.defaults.filter { Prefs.isEnabled(context, it.id) }.map { it.id }
        val key = dateKey(0)
        val done = enabledIds.count { Prefs.isDone(context, it, key) }
        return done to enabledIds.size
    }

    /** نسبة الإنجاز الفعلي آخر 7 أيام (0-100) - مقياس الأفعال الحقيقي مقابل الكلام */
    fun weeklyCompletionRate(context: Context): Int {
        val enabledIds = Reminders.defaults.filter { Prefs.isEnabled(context, it.id) }.map { it.id }
        if (enabledIds.isEmpty()) return 0
        var totalPossible = 0
        var totalDone = 0
        for (daysAgo in 0..6) {
            val key = dateKey(daysAgo)
            totalPossible += enabledIds.size
            totalDone += enabledIds.count { Prefs.isDone(context, it, key) }
        }
        return if (totalPossible == 0) 0 else (totalDone * 100 / totalPossible)
    }
}
