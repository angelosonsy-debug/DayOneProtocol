package com.dayone.protocol

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {

    private fun buildPendingIntent(context: Context, item: ReminderItem): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", item.id)
            putExtra("title", item.title)
            putExtra("message", item.message)
        }
        return PendingIntent.getBroadcast(
            context,
            item.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun nextTrigger(hour: Int, minute: Int): Calendar {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar
    }

    fun scheduleReminder(context: Context, id: Int, hour: Int, minute: Int) {
        val item = Reminders.defaults.first { it.id == id }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, item)
        val calendar = nextTrigger(hour, minute)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, id: Int) {
        val item = Reminders.defaults.first { it.id == id }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, item))
    }

    fun rescheduleAllEnabled(context: Context) {
        Reminders.defaults.forEach { item ->
            if (Prefs.isEnabled(context, item.id)) {
                val hour = Prefs.getHour(context, item.id, item.defaultHour)
                val minute = Prefs.getMinute(context, item.id, item.defaultMinute)
                scheduleReminder(context, item.id, hour, minute)
            }
        }
        if (Prefs.getWeeklyReviewEnabled(context)) {
            scheduleWeeklyReview(
                context,
                Prefs.getWeeklyHour(context),
                Prefs.getWeeklyMinute(context)
            )
        }
        if (Prefs.getIdentityEnabled(context)) {
            scheduleIdentityReminder(context)
        }
        if (Prefs.getProtectionEnabled(context)) {
            ensureProtectionScheduled(context)
        }
        if (Prefs.getMonthlyCheckEnabled(context)) {
            scheduleMonthlyCheck(
                context,
                Prefs.getMonthlyCheckHour(context),
                Prefs.getMonthlyCheckMinute(context)
            )
        }
        if (Prefs.getAntiVisionRefreshEnabled(context)) {
            scheduleAntiVisionRefresh(
                context,
                Prefs.getAntiVisionRefreshHour(context),
                Prefs.getAntiVisionRefreshMinute(context)
            )
        }
        if (Prefs.getRandomQuestionEnabled(context)) {
            scheduleRandomQuestion(
                context,
                Prefs.getRandomQuestionHour(context),
                Prefs.getRandomQuestionMinute(context)
            )
        }
    }

    // ---------- المراجعة الأسبوعية (كل يوم جمعة) ----------

    private const val WEEKLY_REQUEST_CODE = 9999

    private fun weeklyPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WeeklyReviewReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, WEEKLY_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleWeeklyReview(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = weeklyPendingIntent(context)

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = Calendar.getInstance()
        val daysUntilFriday = (Calendar.FRIDAY - target.get(Calendar.DAY_OF_WEEK) + 7) % 7
        target.add(Calendar.DAY_OF_YEAR, daysUntilFriday)
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 7)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
        }
    }

    fun cancelWeeklyReview(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(weeklyPendingIntent(context))
    }

    // ---------- مساعد عام: جدولة آمنة مع fallback ----------

    private fun scheduleSafely(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    // ---------- عبارة الهوية العشوائية (وقت عشوائي كل يوم) ----------

    private const val IDENTITY_REQUEST_CODE = 9998

    private fun identityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, IdentityReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, IDENTITY_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** بيختار وقت عشوائي بين 9ص و9م - لليوم لو لسه ماجاش، وإلا لبكرة */
    fun scheduleIdentityReminder(context: Context) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, (9..21).random())
            set(Calendar.MINUTE, (0..59).random())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        scheduleSafely(context, target.timeInMillis, identityPendingIntent(context))
    }

    /** يُستخدم بعد إطلاق الإشعار - يجدول وقت عشوائي جديد بكرة تحديدًا */
    fun scheduleIdentityReminderNextDay(context: Context) {
        val target = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, (9..21).random())
            set(Calendar.MINUTE, (0..59).random())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        scheduleSafely(context, target.timeInMillis, identityPendingIntent(context))
    }

    fun cancelIdentityReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(identityPendingIntent(context))
    }

    // ---------- سؤال الحماية الذاتية (كل 14 يوم) ----------

    private const val PROTECTION_REQUEST_CODE = 9997

    private fun protectionPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ProtectionReviewReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, PROTECTION_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleProtectionReview(context: Context, hour: Int, minute: Int) {
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 14)
        }
        Prefs.setNextTrigger(context, "protection", target.timeInMillis)
        scheduleSafely(context, target.timeInMillis, protectionPendingIntent(context))
    }

    /** بتستخدم من onCreate/Boot: لو فيه موعد محفوظ لسه في المستقبل بتحافظ عليه، وإلا بتحسب موعد جديد */
    fun ensureProtectionScheduled(context: Context) {
        val stored = Prefs.getNextTrigger(context, "protection")
        if (stored > System.currentTimeMillis()) {
            scheduleSafely(context, stored, protectionPendingIntent(context))
        } else {
            scheduleProtectionReview(
                context,
                Prefs.getProtectionHour(context),
                Prefs.getProtectionMinute(context)
            )
        }
    }

    fun cancelProtectionReview(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(protectionPendingIntent(context))
    }

    // ---------- خطة اللعبة: مراجعة شهرية (أول كل شهر) ----------

    private const val MONTHLY_REQUEST_CODE = 9996

    private fun monthlyPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MonthlyCheckReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, MONTHLY_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleMonthlyCheck(context: Context, hour: Int, minute: Int) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) {
            target.add(Calendar.MONTH, 1)
        }
        scheduleSafely(context, target.timeInMillis, monthlyPendingIntent(context))
    }

    fun cancelMonthlyCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(monthlyPendingIntent(context))
    }

    // ---------- تجديد الرؤية المضادة (يوم 15 كل شهر) ----------

    private const val ANTI_REFRESH_REQUEST_CODE = 9995

    private fun antiRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AntiVisionRefreshReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, ANTI_REFRESH_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleAntiVisionRefresh(context: Context, hour: Int, minute: Int) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) {
            target.add(Calendar.MONTH, 1)
        }
        scheduleSafely(context, target.timeInMillis, antiRefreshPendingIntent(context))
    }

    fun cancelAntiVisionRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(antiRefreshPendingIntent(context))
    }

    // ---------- سؤال أسبوعي عشوائي من الدفتر الكامل (كل يوم أربعاء) ----------

    private const val RANDOM_Q_REQUEST_CODE = 9994

    private fun randomQuestionPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RandomQuestionReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, RANDOM_Q_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleRandomQuestion(context: Context, hour: Int, minute: Int) {
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = Calendar.getInstance()
        val daysUntilWednesday = (Calendar.WEDNESDAY - target.get(Calendar.DAY_OF_WEEK) + 7) % 7
        target.add(Calendar.DAY_OF_YEAR, daysUntilWednesday)
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 7)
        }
        scheduleSafely(context, target.timeInMillis, randomQuestionPendingIntent(context))
    }

    fun cancelRandomQuestion(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(randomQuestionPendingIntent(context))
    }
}
