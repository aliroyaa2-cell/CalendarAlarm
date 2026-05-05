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
 * ━━━ المسؤولية الوحيدة عن الصوت ━━━
 * هذه الـ Activity هي المصدر الوحيد للصوت عند التنبيه.
 * النغمة الافتراضية للقناة تم تعطيلها (setSound(null)) في ensureChannelMuted
 * عشان نتحكم 100% في الصوت من هنا.
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
        applyWindowFlags()
        acquireScreenWakeLock()
        setContentView(R.layout.activity_alarm_overlay)
        loadExtras(intent)
        setupUI()
        playAlarmSound()
        startVibration()
    }

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
            tvT
