package com.alaimtiaz.calendaralarm

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings

class CalendarAlarmApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            cleanupOldChannels()
            createAlarmChannel()
            createObserverChannel()
        }
    }

    private fun cleanupOldChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        listOf(
            "calendar_alarm_channel",
            "calendar_alarm_channel_v2",
            "calendar_alarm_channel_v3",
            "calendar_alarm_channel_v4",
            "alarm_service_channel",
            "observer_channel"
        ).forEach { oldId ->
            try { nm.deleteNotificationChannel(oldId) } catch (_: Exception) {}
        }
    }

    /**
     * قناة v5: فيها صوت من نغمة المستخدم المختارة + بدون اهتزاز
     */
    private fun createAlarmChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID_ALARM_V5) != null) return

        // النغمة المختارة من المستخدم (أو الافتراضية)
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val soundUri: Uri = prefs.getString(MainActivity.KEY_SOUND_URI, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID_ALARM_V5,
            "منبّه التقويم",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تنبيهات شاشة كاملة لأحداث التقويم"
            setSound(soundUri, audioAttributes)  // ⭐ الصوت من القناة
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)               // ⭐ لا اهتزاز
            enableLights(true)
            lightColor = 0xFF5B6EF5.toInt()
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    private fun createObserverChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID_OBSERVER) != null) return
        NotificationChannel(
            CHANNEL_ID_OBSERVER,
            "منبّه التقويم - مراقبة في الخلفية",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "إشعار صامت لمراقبة التقويم"
            setShowBadge(false)
            setSound(null, null)
        }.also { nm.createNotificationChannel(it) }
    }

    fun recreateAlarmChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        try { nm.deleteNotificationChannel(CHANNEL_ID_ALARM_V5) } catch (_: Exception) {}
        createAlarmChannel()
    }

    companion object {
        const val CHANNEL_ID_ALARM_V5 = "calendar_alarm_channel_v5"
        const val CHANNEL_ID_OBSERVER = "observer_channel_v2"
    }
}
