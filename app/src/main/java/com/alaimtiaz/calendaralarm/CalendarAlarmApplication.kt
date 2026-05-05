package com.alaimtiaz.calendaralarm

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

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
            "alarm_service_channel",
            "observer_channel"
        ).forEach { oldId ->
            try { nm.deleteNotificationChannel(oldId) } catch (_: Exception) {}
        }
    }

    private fun createAlarmChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID_ALARM_V4) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID_ALARM_V4,
            "منبّه التقويم",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تنبيهات شاشة كاملة لأحداث التقويم"
            setSound(null, null)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
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
        try { nm.deleteNotificationChannel(CHANNEL_ID_ALARM_V4) } catch (_: Exception) {}
        createAlarmChannel()
    }

    companion object {
        const val CHANNEL_ID_ALARM_V4 = "calendar_alarm_channel_v4"
        const val CHANNEL_ID_OBSERVER = "observer_channel_v2"
    }
}
