package com.alaimtiaz.calendaralarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.CalendarContract
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AlarmOverlayActivity — الشاشة المنبثقة عند المنبه.
 *
 * ━━━ الإصلاح الأخير ━━━
 * المنبه الحقيقي → الصوت من القناة فقط (لا MediaPlayer)
 * الاختبار اليدوي → الصوت من MediaPlayer (مرة وحدة)
 *
 * النتيجة: صوت واحد فقط في كل الحالات.
 */
class AlarmOverlayActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EVENT_ID    = "event_id"
        const val EXTRA_TITLE       = "event_title"
        const val EXTRA_DESCRIPTION = "event_description"
        const val EXTRA_LOCATION    = "event_location"
        const val EXTRA_IS_TASK     = "event_is_task"
        const val EXTRA_START_TIME  = "event_start_time"
        const val EXTRA_NOTIF_KEY   = "notif_key"
        private const val TEST_ALARM_ID = 99999L
        private const val TEST_TASK_ID  = 99998L
        private val SNOOZE_LABELS  = listOf("تأجيل متقدم ▾","ساعتان","4 ساعات","8 ساعات","يوم كامل","أسبوع")
        private val SNOOZE_MINUTES = listOf(0L, 120L, 240L, 480L, 1440L, 10080L)
    }

    private var mediaPlayer: MediaPlayer? = null
    private var screenWakeLock: PowerManager.WakeLock? = null
    private val clockHandler = Handler(Looper.getMainLooper())
    private var eventId = -1L
    private var title = ""
    private var desc = ""
    private var location = ""
    private var isTask = false
    private var startTime = 0L
    private var notifKey: String? = null

    private val clockRunnable = object : Runnable {
        override fun run() {
            findViewById<TextView>(R.id.tvClock)?.text =
                SimpleDateFormat("hh:mm\naa", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyWindowFlags()
        acquireScreenWakeLock()
        setContentView(R.layout.activity_alarm_overlay)
        loadExtras(intent)
        setupUI()
        // الصوت فقط في حالة الاختبار اليدوي
        // المنبه الحقيقي → الصوت من القناة
        if (isTestAlarm()) {
            playAlarmSound()
        }
    }

    private fun isTestAlarm(): Boolean = eventId == TEST_ALARM_ID || eventId == TEST_TASK_ID

    private fun applyWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    @Suppress("DEPRECATION")
    private fun acquireScreenWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            screenWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "CalendarAlarm::OverlayWakeLock"
            ).apply { acquire(60_000L) }
        } catch (_: Exception) {}
    }

    private fun releaseScreenWakeLock() {
        try {
            screenWakeLock?.let { if (it.isHeld) it.release() }
            screenWakeLock = null
        } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadExtras(intent)
        setupUI()
        stopSound()
        if (isTestAlarm()) playAlarmSound()
    }

    private fun loadExtras(intent: Intent) {
        eventId   = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        title     = intent.getStringExtra(EXTRA_TITLE)       ?: ""
        desc      = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        location  = intent.getStringExtra(EXTRA_LOCATION)    ?: ""
        isTask    = intent.getBooleanExtra(EXTRA_IS_TASK, false)
        startTime = intent.getLongExtra(EXTRA_START_TIME, 0L)
        notifKey  = intent.getStringExtra(EXTRA_NOTIF_KEY)
    }

    private fun setupUI() {
        clockHandler.removeCallbacksAndMessages(null)
        clockHandler.post(clockRunnable)

        val tvType = findViewById<TextView>(R.id.tvEventType)
        val topBar = findViewById<TextView>(R.id.viewTopBar)
        if (isTask) {
            tvType.text = "✅  مهمة"
            tvType.setBackgroundColor(getColor(R.color.task_green))
            topBar?.setBackgroundColor(getColor(R.color.task_green))
        } else {
            tvType.text = "📅  تقويم"
            tvType.setBackgroundColor(getColor(R.color.accent))
            topBar?.setBackgroundColor(getColor(R.color.accent))
        }

        findViewById<TextView>(R.id.tvEventTitle).text = title
        val detail = listOf(location, desc).filter { it.isNotBlank() }.joinToString("\n")
        val tvDetail = findViewById<TextView>(R.id.tvEventDetail)
        tvDetail.text = detail
        tvDetail.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE

        findViewById<Button>(R.id.btnSnooze5).setOnClickListener  { snoozeAndFinish(5L) }
        findViewById<Button>(R.id.btnSnooze10).setOnClickListener { snoozeAndFinish(10L) }
        findViewById<Button>(R.id.btnSnooze30).setOnClickListener { snoozeAndFinish(30L) }

        val spinner = findViewById<Spinner>(R.id.spinnerSnooze)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, SNOOZE_LABELS)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        findViewById<Button>(R.id.btnSnoozeAdvanced).setOnClickListener {
            val pos = spinner.selectedItemPosition
            if (pos > 0) snoozeAndFinish(SNOOZE_MINUTES[pos])
        }
        findViewById<Button>(R.id.btnEdit).setOnClickListener { openInCalendar() }
        findViewById<Button>(R.id.btnDismiss).setOnClickListener { dismissAndFinish() }
    }

    private fun snoozeAndFinish(minutes: Long) {
        AlarmScheduler.scheduleSnooze(this, eventId, title, desc, location, isTask, startTime, minutes)
        cancelNotif()
        stopEverythingAndFinish()
    }

    private fun dismissAndFinish() {
        if (eventId >= 0) {
            PendingAlarmsStore.dismiss(this, eventId)
            AlarmScheduler.cancelAlarm(this, eventId)
        }
        cancelNotif()
        stopEverythingAndFinish()
    }

    private fun openInCalendar() {
        try {
            val uri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, eventId.toString())
            startActivity(Intent(Intent.ACTION_VIEW).apply { data = uri })
        } catch (_: Exception) {
            try {
                packageManager.getLaunchIntentForPackage("com.google.android.calendar")?.let {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(it)
                }
            } catch (_: Exception) {}
        }
    }

    private fun cancelNotif() {
        try {
            getSystemService(NotificationManager::class.java)
                .cancel(EventAlarmReceiver.NOTIF_ID_BASE + eventId.toInt())
        } catch (_: Exception) {}
        notifKey?.let {
            try { TasksNotificationListener.instance?.cancelNotification(it) } catch (_: Exception) {}
        }
    }

    /**
     * تشغيل النغمة (للاختبار اليدوي فقط) — مرة واحدة فقط.
     */
    private fun playAlarmSound() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val soundUri = prefs.getString(MainActivity.KEY_SOUND_URI, null)?.let { Uri.parse(it) }
            ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmOverlayActivity, soundUri)
                isLooping = false
                prepare()
                start()
            }
        } catch (_: Exception) {}
    }

    private fun stopSound() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun stopEverythingAndFinish() {
        stopSound()
        clockHandler.removeCallbacksAndMessages(null)
        releaseScreenWakeLock()
        finish()
    }

    override fun onDestroy() {
        stopSound()
        clockHandler.removeCallbacksAndMessages(null)
        releaseScreenWakeLock()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}
}
