package com.alaimtiaz.calendaralarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.CalendarContract
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AlarmOverlayActivity — الشاشة المنبثقة عند المنبه.
 *
 * هذي الـ Activity تنطلق بأحد طريقتين:
 * 1. تلقائياً من النظام عبر FullScreenIntent عند وصول الإشعار
 *    (هذا الطريق الصحيح على Android 14+ و One UI 8)
 * 2. يدوياً من زر الاختبار في MainActivity
 *
 * ━━━ Window Flags الحرجة ━━━
 * - setShowWhenLocked + setTurnScreenOn = توقظ الشاشة وتعرض على lock screen
 * - requestDismissKeyguard = تخفي keyguard إذا الجهاز غير محمي
 *   (إذا محمي بـ pattern/PIN، لا يمكن تجاوزه — يجب على المستخدم الفتح)
 * - WakeLock SCREEN_BRIGHT = طبقة احتياطية لإيقاظ الشاشة فوراً
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
        private val SNOOZE_LABELS  = listOf("تأجيل متقدم ▾","ساعتان","4 ساعات","8 ساعات","يوم كامل","أسبوع")
        private val SNOOZE_MINUTES = listOf(0L, 120L, 240L, 480L, 1440L, 10080L)
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
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

        // ━━━ Window Flags ━━━ تستدعى قبل setContentView
        applyWindowFlags()

        // ━━━ WakeLock احتياطي لإيقاظ الشاشة ━━━
        acquireScreenWakeLock()

        setContentView(R.layout.activity_alarm_overlay)
        loadExtras(intent)
        setupUI()
        playAlarmSound()
        startVibration()
    }

    /**
     * تطبيق window flags الحرجة لإيقاظ الشاشة وعرضها على lock screen.
     */
    private fun applyWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // طلب إخفاء keyguard (يعمل فقط إذا الجهاز غير محمي بـ PIN/pattern)
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        }

        // FLAG_KEEP_SCREEN_ON = الشاشة تبقى مضاءة طوال عرض الـ Activity
        // FLAG_DISMISS_KEYGUARD + FLAG_SHOW_WHEN_LOCKED + FLAG_TURN_SCREEN_ON =
        //   احتياط للنسخ القديمة (deprecated على O_MR1+ لكن مازالت تعمل)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    /**
     * WakeLock احتياطي لإيقاظ الشاشة (طبقة دفاع إضافية).
     * SCREEN_BRIGHT_WAKE_LOCK يضيء الشاشة (deprecated لكنه فعّال على Samsung).
     */
    @Suppress("DEPRECATION")
    private fun acquireScreenWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            screenWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "CalendarAlarm::OverlayWakeLock"
            ).apply { acquire(60_000L) /* 60 ثانية حد أقصى */ }
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
        playAlarmSound()
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
                isLooping = true   // يستمر الصوت حتى يضغط المستخدم
                prepare()
                start()
            }
            // رفع volume للـ alarm stream ضماناً
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
        } catch (_: Exception) {}
    }

    private fun stopSound() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else
                @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator

            val pattern = longArrayOf(0, 700, 300, 700, 300, 700)
            val effect = VibrationEffect.createWaveform(pattern, 0) // 0 = repeat من البداية
            vibrator?.vibrate(effect)
        } catch (_: Exception) {}
    }

    private fun stopEverythingAndFinish() {
        stopSound()
        try { vibrator?.cancel() } catch (_: Exception) {}
        clockHandler.removeCallbacksAndMessages(null)
        releaseScreenWakeLock()
        finish()
    }

    override fun onDestroy() {
        stopSound()
        try { vibrator?.cancel() } catch (_: Exception) {}
        clockHandler.removeCallbacksAndMessages(null)
        releaseScreenWakeLock()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* لا نسمح بالخروج إلا من الأزرار */ }
}
