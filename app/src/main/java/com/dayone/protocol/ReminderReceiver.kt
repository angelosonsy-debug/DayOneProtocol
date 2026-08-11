package com.dayone.protocol

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val item = Reminders.defaults.firstOrNull { it.id == id } ?: return

        showNotification(context, item)

        // reschedule the same reminder for tomorrow, same time, if still enabled
        if (Prefs.isEnabled(context, id)) {
            val hour = Prefs.getHour(context, id, item.defaultHour)
            val minute = Prefs.getMinute(context, id, item.defaultMinute)
            AlarmScheduler.scheduleReminder(context, id, hour, minute)
        }
    }

    private fun showNotification(context: Context, item: ReminderItem) {
        val channelId = "day_one_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تذكيرات بروتوكول اليوم الواحد",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        // التذكير الصباحي (id=1) والمسائي (id=10) بيدوّروا سؤال حي من الدفتر بدل رسالة ثابتة
        val displayMessage = when (item.id) {
            1 -> JournalQuestions.morningQuestionForToday()
            10 -> JournalQuestions.eveningQuestionForToday()
            else -> item.message
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context, item.id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, MarkDoneReceiver::class.java).apply {
            putExtra("id", item.id)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context, item.id + 10000, doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(item.title)
            .setContentText(displayMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "تم ✓", donePendingIntent)
            .setAutoCancel(true)
            .build()

        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (canPost) {
            NotificationManagerCompat.from(context).notify(item.id, notification)
        }
    }
}
