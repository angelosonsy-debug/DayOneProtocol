package com.dayone.protocol

import android.content.Context

object Prefs {
    private const val NAME = "reminders_prefs"

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
}
