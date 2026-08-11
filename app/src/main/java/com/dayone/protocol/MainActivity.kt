package com.dayone.protocol

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private lateinit var streakText: TextView
    private lateinit var progressText: TextView
    private lateinit var lastReviewText: TextView
    private lateinit var lastProtectionText: TextView

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

        buildHeader(container)
        buildEmergencyCard(container)
        buildStatusCard(container)
        buildVisionEditor(container)
        buildJournalCard(container)
        buildWeeklyReviewCard(container)
        buildProtectionCard(container)
        buildGamePlanCard(container)
        buildRemindersSection(container)

        AlarmScheduler.rescheduleAllEnabled(this)

        handleIntentExtras(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent?.getBooleanExtra("openWeeklyReview", false) == true) {
            showWeeklyReviewDialog()
        }
        if (intent?.getBooleanExtra("openProtectionReview", false) == true) {
            showProtectionDialog()
        }
        if (intent?.getBooleanExtra("openMonthlyCheck", false) == true) {
            showMonthlyCheckDialog()
        }
        if (intent?.getBooleanExtra("openAntiVisionRefresh", false) == true) {
            showAntiVisionRefreshDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------------- Header ----------------

    private fun buildHeader(container: LinearLayout) {
        val header = TextView(this).apply {
            text = "بروتوكول اليوم الواحد"
            textSize = 22f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            gravity = Gravity.END
            setPadding(0, 0, 0, 10)
        }
        container.addView(header)

        val sub = TextView(this).apply {
            text = "فعّل أو عطّل أي تذكير، واضغط \"تم ✓\" في الإشعار لما تنجزه"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            gravity = Gravity.END
            setPadding(0, 0, 0, 24)
        }
        container.addView(sub)
    }

    // ---------------- Status card (streak + today progress) ----------------

    private fun buildStatusCard(container: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, dp(16))
            layoutParams = params
        }

        streakText = TextView(this).apply {
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        progressText = TextView(this).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            gravity = Gravity.END
            setPadding(0, dp(6), 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        col.addView(streakText)
        col.addView(progressText)
        card.addView(col)
        container.addView(card)
    }

    private fun refreshStatus() {
        val streak = StreakCalculator.calculateStreak(this)
        val (done, total) = StreakCalculator.todayProgress(this)
        val level = streak / 7

        streakText.text = if (streak > 0) "🔥 $streak يوم متتالي  ·  🏅 المستوى $level" else "🔥 ابدأ سلسلتك النهاردة"
        progressText.text = "اليوم: $done من $total تذكيرات مُنجزة"

        Prefs.getLastWeeklyReviewAnswer(this)?.let { (_, text) ->
            if (::lastReviewText.isInitialized && text.isNotBlank()) {
                lastReviewText.text = "آخر مراجعة: $text"
            }
        }
        Prefs.getLastProtectionAnswer(this)?.let { (_, text) ->
            if (::lastProtectionText.isInitialized && text.isNotBlank()) {
                lastProtectionText.text = "آخر إجابة: $text"
            }
        }

        if (level > 0 && level > Prefs.getLastCelebratedLevel(this)) {
            Prefs.setLastCelebratedLevel(this, level)
            AlertDialog.Builder(this)
                .setTitle("🏅 وصلت المستوى $level")
                .setMessage("$streak يوم التزام متواصل. ده وقت كويس تزوّد حاجة واحدة بسيطة لخطتك لو حابب — مش أكتر، عشان التحدي يفضل متوازن مع مهارتك.")
                .setPositiveButton("تمام", null)
                .show()
        }
    }

    // ---------------- وضع الطوارئ ----------------

    private fun buildEmergencyCard(container: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, dp(16))
            layoutParams = params
        }

        card.addView(sectionTitle("🚨 وضع الطوارئ"))
        card.addView(hint("دوس هنا في اللحظة اللي حاسس فيها إنك برجع للنمط القديم — مش بعد ما ترجع."))

        val enemyLabel = fieldLabel("العدو الحقيقي (مش الظروف، النمط الداخلي)")
        val enemyInput = EditText(this).apply {
            setText(Prefs.getEnemy(this@MainActivity))
            textSize = 14f
            gravity = Gravity.END
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            minLines = 1
        }
        val saveEnemyBtn = TextView(this).apply {
            text = "حفظ"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(6))
            setOnClickListener {
                Prefs.setEnemy(this@MainActivity, enemyInput.text.toString().trim())
                Toast.makeText(this@MainActivity, "اتحفظ", Toast.LENGTH_SHORT).show()
            }
        }

        card.addView(enemyLabel)
        card.addView(enemyInput)
        card.addView(saveEnemyBtn)

        val emergencyBtn = TextView(this).apply {
            text = "🚨 دخلت في نمط الهروب — واجه نفسك دلوقتي"
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.background))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.danger))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = dp(14)
            layoutParams = params
            setOnClickListener { triggerEmergency() }
        }
        card.addView(emergencyBtn)

        container.addView(card)
    }

    private fun triggerEmergency() {
        postEmergencyNotification()
        showEmergencyDialog()
    }

    private fun postEmergencyNotification() {
        val channelId = "emergency_mode"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "وضع الطوارئ", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val enemy = Prefs.getEnemy(this).ifBlank { "الخوف من ثمن التغيير" }
        val anti = Prefs.getVisionAnti(this).ifBlank { "الحياة اللي رافض تصير إليها" }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 777, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("قف. واجه العدو الحقيقي: $enemy")
            .setContentText("بتتحرك ناحية: $anti")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("العدو الحقيقي: $enemy\nاللي بترفض تصير إليه: $anti\nهل أتحرّك ناحية الحياة اللي أكرهها أم التي أريدها؟")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (canPost) {
            NotificationManagerCompat.from(this).notify(888, notification)
        }
    }

    private fun showEmergencyDialog() {
        val enemy = Prefs.getEnemy(this).ifBlank { "لسه معرّفوش — عرّفه تحت" }
        val anti = Prefs.getVisionAnti(this).ifBlank { "لسه مكتوبتش رؤيتك المضادة" }
        val vision = Prefs.getVisionInitial(this).ifBlank { "لسه مكتوبتش رؤيتك الأولية" }

        val message = "العدو الحقيقي: $enemy\n\n" +
            "اللي بترفض تصير إليه: $anti\n\n" +
            "اللي بتبنيه: $vision\n\n" +
            "هل أتحرّك دلوقتي ناحية الحياة اللي أكرهها أم اللي أريدها؟"

        val input = EditText(this).apply {
            gravity = Gravity.END
            hint = "اكتب سطر واحد بصدق: إيه اللي بتعمله دلوقتي فعليًا؟"
        }

        AlertDialog.Builder(this)
            .setTitle("قف قبل ما تكمل")
            .setMessage(message)
            .setView(input)
            .setPositiveButton("سجّل واستمر") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    Prefs.logEmergency(this, text)
                    Toast.makeText(this, "اتسجل. رجّعنا لأصغر خطوة ممكنة دلوقتي.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    // ---------------- دفتر العمل ----------------

    private fun buildJournalCard(container: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, dp(16))
            layoutParams = params
        }

        card.addView(sectionTitle("📓 دفتر العمل"))
        card.addView(hint("حط رابط دفتر العمل بتاعك (لو رفعته على درايف أو أي مكان)، وهتقدر تفتحه بضغطة واحدة."))

        val urlInput = EditText(this).apply {
            setText(Prefs.getJournalUrl(this@MainActivity))
            textSize = 14f
            gravity = Gravity.END
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            hint = "https://..."
            minLines = 1
        }
        card.addView(urlInput)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }

        val saveBtn = TextView(this).apply {
            text = "حفظ الرابط"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                Prefs.setJournalUrl(this@MainActivity, urlInput.text.toString().trim())
                Toast.makeText(this@MainActivity, "اتحفظ الرابط", Toast.LENGTH_SHORT).show()
            }
        }

        val openBtn = TextView(this).apply {
            text = "افتح دفتر العمل"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val url = Prefs.getJournalUrl(this@MainActivity).ifBlank { urlInput.text.toString().trim() }
                if (url.isBlank()) {
                    Toast.makeText(this@MainActivity, "حط الرابط الأول واحفظه", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val fixedUrl = if (!url.startsWith("http")) "https://$url" else url
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fixedUrl)))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, "مفيش تطبيق يقدر يفتح الرابط ده", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        row.addView(saveBtn)
        row.addView(openBtn)
        card.addView(row)

        container.addView(card)
    }

    // ---------------- Vision editor (يغذي الـ Widget) ----------------

    private fun buildVisionEditor(container: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, dp(16))
            layoutParams = params
        }

        card.addView(sectionTitle("🖼️ رؤيتك على الـ Widget"))
        card.addView(hint("هتظهر جملتين دول على شاشتك الرئيسية لو ضفت الـ Widget. اكتبهم زي ما وصلت لهم في الدفتر (سؤال 26 و27)."))

        val antiLabel = fieldLabel("ما ترفض أن تصير إليه (رؤيتك المضادة)")
        val antiInput = EditText(this).apply {
            setText(Prefs.getVisionAnti(this@MainActivity))
            textSize = 14f
            gravity = Gravity.END
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            minLines = 2
        }

        val visionLabel = fieldLabel("ما تبنيه (رؤيتك الأولية)")
        val visionInput = EditText(this).apply {
            setText(Prefs.getVisionInitial(this@MainActivity))
            textSize = 14f
            gravity = Gravity.END
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            minLines = 2
        }

        val identityLabel = fieldLabel("عبارة هويتك («أنا من النوع الذي...»)")
        val identityInput = EditText(this).apply {
            setText(Prefs.getIdentity(this@MainActivity))
            textSize = 14f
            gravity = Gravity.END
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            minLines = 1
        }

        val saveBtn = TextView(this).apply {
            text = "حفظ وتحديث الـ Widget"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            setOnClickListener {
                Prefs.setVisionAnti(this@MainActivity, antiInput.text.toString().trim())
                Prefs.setVisionInitial(this@MainActivity, visionInput.text.toString().trim())
                Prefs.setIdentity(this@MainActivity, identityInput.text.toString().trim())
                VisionWidgetProvider.updateAll(this@MainActivity)
                Toast.makeText(this@MainActivity, "اتحفظ وتحدّث الـ Widget", Toast.LENGTH_SHORT).show()
            }
        }

        card.addView(antiLabel)
        card.addView(antiInput)
        card.addView(spacer())
        card.addView(visionLabel)
        card.addView(visionInput)
        card.addView(spacer())
        card.addView(identityLabel)
        card.addView(identityInput)
        card.addView(saveBtn)

        val identityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        val identitySwitchLabel = TextView(this).apply {
            text = "إشعار عشوائي بعبارة هويتك خلال اليوم"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val identitySwitch = Switch(this).apply {
            isChecked = Prefs.getIdentityEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setIdentityEnabled(this@MainActivity, isChecked)
                if (isChecked) {
                    AlarmScheduler.scheduleIdentityReminder(this@MainActivity)
                } else {
                    AlarmScheduler.cancelIdentityReminder(this@MainActivity)
                }
            }
        }
        identityRow.addView(identitySwitch)
        identityRow.addView(identitySwitchLabel)
        card.addView(identityRow)

        val refreshRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        val refreshLabel = TextView(this).apply {
            text = "تجديد الرؤية المضادة كل شهر (يوم 15)"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val refreshSwitch = Switch(this).apply {
            isChecked = Prefs.getAntiVisionRefreshEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setAntiVisionRefreshEnabled(this@MainActivity, isChecked)
                if (isChecked) {
                    AlarmScheduler.scheduleAntiVisionRefresh(
                        this@MainActivity,
                        Prefs.getAntiVisionRefreshHour(this@MainActivity),
                        Prefs.getAntiVisionRefreshMinute(this@MainActivity)
                    )
                } else {
                    AlarmScheduler.cancelAntiVisionRefresh(this@MainActivity)
                }
            }
        }
        refreshRow.addView(refreshSwitch)
        refreshRow.addView(refreshLabel)
        card.addView(refreshRow)

        val refreshNowBtn = TextView(this).apply {
            text = "جدّد رؤيتك المضادة دلوقتي"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
            setOnClickListener { showAntiVisionRefreshDialog() }
        }
        card.addView(refreshNowBtn)

        container.addView(card)
    }

    // ---------------- Weekly review ----------------

    private fun buildWeeklyReviewCard(container: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, dp(16))
            layoutParams = params
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "📅 المراجعة الأسبوعية (كل جمعة)"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val switch = Switch(this).apply {
            isChecked = Prefs.getWeeklyReviewEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setWeeklyReviewEnabled(this@MainActivity, isChecked)
                if (isChecked) {
                    AlarmScheduler.scheduleWeeklyReview(
                        this@MainActivity,
                        Prefs.getWeeklyHour(this@MainActivity),
                        Prefs.getWeeklyMinute(this@MainActivity)
                    )
                } else {
                    AlarmScheduler.cancelWeeklyReview(this@MainActivity)
                }
            }
        }

        topRow.addView(switch)
        topRow.addView(title)
        card.addView(topRow)

        card.addView(hint("الأسبوع ده قرّبك من رؤيتك المنشودة ولا المضادة؟"))

        val hour = Prefs.getWeeklyHour(this)
        val minute = Prefs.getWeeklyMinute(this)
        val timeText = TextView(this).apply {
            text = String.format("الوقت: %02d:%02d", hour, minute)
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.END
            setPadding(0, dp(8), 0, dp(4))
            setOnClickListener {
                val h = Prefs.getWeeklyHour(this@MainActivity)
                val m = Prefs.getWeeklyMinute(this@MainActivity)
                TimePickerDialog(this@MainActivity, { _, newH, newM ->
                    Prefs.setWeeklyTime(this@MainActivity, newH, newM)
                    text = String.format("الوقت: %02d:%02d", newH, newM)
                    if (Prefs.getWeeklyReviewEnabled(this@MainActivity)) {
                        AlarmScheduler.scheduleWeeklyReview(this@MainActivity, newH, newM)
                    }
                }, h, m, true).show()
            }
        }
        card.addView(timeText)

        lastReviewText = TextView(this).apply {
            val last = Prefs.getLastWeeklyReviewAnswer(this@MainActivity)
            text = if (last != null && last.second.isNotBlank()) "آخر مراجعة: ${last.second}" else "لسه مفيش مراجعة مسجّلة"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            gravity = Gravity.END
            setPadding(0, dp(10), 0, dp(10))
        }
        card.addView(lastReviewText)

        val openBtn = TextView(this).apply {
            text = "اكتب مراجعة الأسبوع دلوقتي"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.CENTER
            setOnClickListener { showWeeklyReviewDialog() }
        }
        card.addView(openBtn)

        container.addView(card)
    }

    private fun showWeeklyReviewDialog() {
        val input = EditText(this).apply {
            gravity = Gravity.END
            hint = "مثلاً: قرّبت من رؤيتي المنشودة بس لسه بسوّف في المذاكرة"
        }
        AlertDialog.Builder(this)
            .setTitle("الأسبوع ده قرّبك من رؤيتك المنشودة ولا المضادة؟")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    Prefs.saveWeeklyReviewAnswer(this, StreakCalculator.dateKey(0), text)
                    lastReviewText.text = "آخر مراجعة: $text"
                    Toast.makeText(this, "اتسجلت", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لاحقًا", null)
            .show()
    }

    // ---------------- سؤال الحماية الذاتية (كل أسبوعين) ----------------

    private fun buildProtectionCard(container: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, dp(16))
            layoutParams = params
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "🛡️ سؤال الحماية الذاتية (كل أسبوعين)"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val switch = Switch(this).apply {
            isChecked = Prefs.getProtectionEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setProtectionEnabled(this@MainActivity, isChecked)
                if (isChecked) {
                    AlarmScheduler.scheduleProtectionReview(
                        this@MainActivity,
                        Prefs.getProtectionHour(this@MainActivity),
                        Prefs.getProtectionMinute(this@MainActivity)
                    )
                } else {
                    AlarmScheduler.cancelProtectionReview(this@MainActivity)
                }
            }
        }
        topRow.addView(switch)
        topRow.addView(title)
        card.addView(topRow)

        card.addView(hint("سلوكك الحالي بيحميك من إيه تحديدًا؟ وكام تكلفتك الحماية دي؟"))

        val hour = Prefs.getProtectionHour(this)
        val minute = Prefs.getProtectionMinute(this)
        val timeText = TextView(this).apply {
            text = String.format("الوقت: %02d:%02d", hour, minute)
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.END
            setPadding(0, dp(8), 0, dp(4))
            setOnClickListener {
                val h = Prefs.getProtectionHour(this@MainActivity)
                val m = Prefs.getProtectionMinute(this@MainActivity)
                TimePickerDialog(this@MainActivity, { _, newH, newM ->
                    Prefs.setProtectionTime(this@MainActivity, newH, newM)
                    text = String.format("الوقت: %02d:%02d", newH, newM)
                    if (Prefs.getProtectionEnabled(this@MainActivity)) {
                        AlarmScheduler.scheduleProtectionReview(this@MainActivity, newH, newM)
                    }
                }, h, m, true).show()
            }
        }
        card.addView(timeText)

        lastProtectionText = TextView(this).apply {
            val last = Prefs.getLastProtectionAnswer(this@MainActivity)
            text = if (last != null && last.second.isNotBlank()) "آخر إجابة: ${last.second}" else "لسه مفيش إجابة مسجّلة"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            gravity = Gravity.END
            setPadding(0, dp(10), 0, dp(10))
        }
        card.addView(lastProtectionText)

        val openBtn = TextView(this).apply {
            text = "جاوب دلوقتي"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.CENTER
            setOnClickListener { showProtectionDialog() }
        }
        card.addView(openBtn)

        container.addView(card)
    }

    private fun showProtectionDialog() {
        val input = EditText(this).apply {
            gravity = Gravity.END
            hint = "مثلاً: بحمي نفسي من ثمن التغيير، وبتكلفني علاقتي وقربي من ربنا"
        }
        AlertDialog.Builder(this)
            .setTitle("سلوكك الحالي بيحميك من إيه؟ وكام تكلفتك؟")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    Prefs.saveProtectionAnswer(this, StreakCalculator.dateKey(0), text)
                    if (::lastProtectionText.isInitialized) {
                        lastProtectionText.text = "آخر إجابة: $text"
                    }
                    Toast.makeText(this, "اتسجلت", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لاحقًا", null)
            .show()
    }

    // ---------------- خطة اللعبة: هدف السنة ومشروع الشهر ----------------

    private fun buildGamePlanCard(container: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, dp(16))
            layoutParams = params
        }

        card.addView(sectionTitle("🎮 خطة اللعبة"))
        card.addView(hint("عدسة السنة والشهر — عشان التذكيرات اليومية تفضل مربوطة بصورة أكبر."))

        val yearLabel = fieldLabel("هدف السنة")
        val yearInput = EditText(this).apply {
            setText(Prefs.getYearGoal(this@MainActivity))
            textSize = 14f
            gravity = Gravity.END
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            minLines = 1
        }

        val monthLabel = fieldLabel("مشروع الشهر")
        val monthInput = EditText(this).apply {
            setText(Prefs.getMonthProject(this@MainActivity))
            textSize = 14f
            gravity = Gravity.END
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
            minLines = 1
        }

        val saveBtn = TextView(this).apply {
            text = "حفظ"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
            setOnClickListener {
                Prefs.setYearGoal(this@MainActivity, yearInput.text.toString().trim())
                Prefs.setMonthProject(this@MainActivity, monthInput.text.toString().trim())
                Toast.makeText(this@MainActivity, "اتحفظ", Toast.LENGTH_SHORT).show()
            }
        }

        card.addView(yearLabel)
        card.addView(yearInput)
        card.addView(spacer())
        card.addView(monthLabel)
        card.addView(monthInput)
        card.addView(saveBtn)

        val monthlyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        val monthlyLabel = TextView(this).apply {
            text = "تذكير شهري: هل الشهر ده لسه بيقرّبك من هدف السنة؟"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val monthlySwitch = Switch(this).apply {
            isChecked = Prefs.getMonthlyCheckEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                Prefs.setMonthlyCheckEnabled(this@MainActivity, isChecked)
                if (isChecked) {
                    AlarmScheduler.scheduleMonthlyCheck(
                        this@MainActivity,
                        Prefs.getMonthlyCheckHour(this@MainActivity),
                        Prefs.getMonthlyCheckMinute(this@MainActivity)
                    )
                } else {
                    AlarmScheduler.cancelMonthlyCheck(this@MainActivity)
                }
            }
        }
        monthlyRow.addView(monthlySwitch)
        monthlyRow.addView(monthlyLabel)
        card.addView(monthlyRow)

        container.addView(card)
    }

    private fun showMonthlyCheckDialog() {
        val year = Prefs.getYearGoal(this).ifBlank { "لسه مكتوبش" }
        val month = Prefs.getMonthProject(this).ifBlank { "لسه مكتوبش" }
        AlertDialog.Builder(this)
            .setTitle("شهر جديد — راجع خطة اللعبة")
            .setMessage("هدف السنة: $year\n\nمشروع الشهر: $month\n\nهل مشروع الشهر ده لسه بيقرّبك من هدف السنة؟ لو محتاج تغيّره، انزل لبطاقة \"خطة اللعبة\" وعدّله.")
            .setPositiveButton("تمام", null)
            .show()
    }

    private fun showAntiVisionRefreshDialog() {
        val input = EditText(this).apply {
            gravity = Gravity.END
            hint = "أين تستيقظ؟ بماذا تشعر؟ من حولك؟ ماذا تفعل من 9 لـ6؟ بماذا تشعر بعد 10 مساءً؟"
        }
        AlertDialog.Builder(this)
            .setTitle("لو استمريت كده 10 سنين كمان — يوم كامل بالتفصيل")
            .setMessage("اكتب يوم عادي كامل زي ما هيبقى لو مفيش تغيير. الهدف إن الصورة تفضل حيّة مش مجرد جملة اتحفظت.")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    Prefs.logAntiVisionRefresh(this, text)
                    Toast.makeText(this, "اتسجل. لو حابب لخّصه في جملة وحدّث بيها الـ Widget.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("لاحقًا", null)
            .show()
    }

    // ---------------- Reminders list ----------------

    private fun buildRemindersSection(container: LinearLayout) {
        container.addView(sectionTitle("⏰ التذكيرات اليومية"))
        Reminders.defaults.forEach { item ->
            container.addView(buildReminderRow(item))
        }
    }

    private fun buildReminderRow(item: ReminderItem): View {
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
                refreshStatus()
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

    // ---------------- Small UI helpers ----------------

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_main))
        gravity = Gravity.END
        setPadding(0, dp(6), 0, dp(14))
    }

    private fun fieldLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
        gravity = Gravity.END
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun hint(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
        gravity = Gravity.END
        setPadding(0, 0, 0, dp(6))
    }

    private fun spacer(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4))
    }

    // ---------------- Permissions ----------------

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
