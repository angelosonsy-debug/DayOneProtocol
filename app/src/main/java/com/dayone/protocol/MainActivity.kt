package com.dayone.protocol

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        checkExactAlarmPermission()

        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 70, 40, 70)
        }
        scroll.addView(container)
        setContentView(scroll)

        val header = TextView(this).apply {
            text = "بروتوكول اليوم الواحد"
            textSize = 22f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            gravity = Gravity.END
            setPadding(0, 0, 0, 10)
        }
        container.addView(header)

        val sub = TextView(this).apply {
            text = "فعّل أو عطّل أي تذكير، واضغط على الوقت عشان تغيّره"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            gravity = Gravity.END
            setPadding(0, 0, 0, 30)
        }
        container.addView(sub)

        Reminders.defaults.forEach { item ->
            container.addView(buildReminderRow(item))
        }

        AlarmScheduler.rescheduleAllEnabled(this)
    }

    private fun buildReminderRow(item: ReminderItem): View {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, dp(16))
            layoutParams = params
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            text = item.title
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val enabled = Prefs.isEnabled(this, item.id)
        val switch = Switch(this).apply {
            isChecked = enabled
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setEnabled(this@MainActivity, item.id, isChecked)
                if (isChecked) {
                    val h = Prefs.getHour(this@MainActivity, item.id, item.defaultHour)
                    val m = Prefs.getMinute(this@MainActivity, item.id, item.defaultMinute)
                    AlarmScheduler.scheduleReminder(this@MainActivity, item.id, h, m)
                } else {
                    AlarmScheduler.cancelReminder(this@MainActivity, item.id)
                }
            }
        }

        topRow.addView(switch)
        topRow.addView(titleText)
        row.addView(topRow)

        val msgText = TextView(this).apply {
            text = item.message
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            gravity = Gravity.END
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(msgText)

        val hour = Prefs.getHour(this, item.id, item.defaultHour)
        val minute = Prefs.getMinute(this, item.id, item.defaultMinute)
        val timeText = TextView(this).apply {
            text = String.format("%02d:%02d", hour, minute)
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.END
            setOnClickListener {
                val currentHour = Prefs.getHour(this@MainActivity, item.id, item.defaultHour)
                val currentMinute = Prefs.getMinute(this@MainActivity, item.id, item.defaultMinute)
                TimePickerDialog(
                    this@MainActivity,
                    { _, h, m ->
                        Prefs.setTime(this@MainActivity, item.id, h, m)
                        text = String.format("%02d:%02d", h, m)
                        if (Prefs.isEnabled(this@MainActivity, item.id)) {
                            AlarmScheduler.scheduleReminder(this@MainActivity, item.id, h, m)
                        }
                    },
                    currentHour, currentMinute, true
                ).show()
            }
        }
        row.addView(timeText)

        return row
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(
                    this,
                    "فعّل صلاحية 'تنبيهات دقيقة' عشان المواعيد تشتغل بدقة",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}
