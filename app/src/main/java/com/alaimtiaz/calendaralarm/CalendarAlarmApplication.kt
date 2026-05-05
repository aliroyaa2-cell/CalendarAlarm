package com.alaimtiaz.calendaralarm

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Application class — يُنشأ مرة واحدة عند فتح التطبيق.
 *
 * المسؤولية الوحيدة: إنشاء NotificationChannel بشكل صحيح (USAGE_ALARM).
 * ─────────────────────────────────────────────────────────────────────
 * السبب الجذري للمشكلة في الإصدار السابق: القناة كانت تُنشأ بدون setSound
 * + AudioAttributes.USAGE_ALARM، فالنظام لم يصنّفها كـ alarm channel،
 * ولم يطلق Full Screen Intent ليوقظ الشاشة عندما الجوال مقفل.
 *
 * الحل: قناة جديدة بـ ID جديد (v3) مع setSound + USAGE_ALARM.
 * + حذف القنوات القديمة (v1, v2) لتنظيف Settings الجوال.
 */
class CalendarAlarmApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            cleanupOldChannels()
            createAlarmChannel()
            createObserverChannel()
        }
    }

    /**
     * حذف القنوات القديمة من الإصدارات السابقة.
     * هذا ضروري لأن القنوات لا تقبل التحديث بعد الإنشاء —
     * يجب حذفها وإعادة إنشائها بإعدادات صحيحة.
     */
    private fun cleanupOldChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        listOf(
            "calendar_alarm_channel",      // v1 (السابق — بدون sound)
            "calendar_alarm_channel_v2",   // v2 (احتياطي)
            "alarm_service_channel",       // قناة AlarmForegroundService القديم
            "observer_channel"             // قناة Observer القديمة
        ).forEach { oldId ->
            try { nm.deleteNotificationChannel(oldId) } catch (_: Exception) {}
        }
    }

    /**
     * إنشاء قناة المنبه الرئيسية (v3).
     *
     * المفتاح هنا: setSound() + AudioAttributes.USAGE_ALARM
     * ───────────────────────────────────────────────────────
     * هذا ما يجعل النظام يصنّف القناة كـ "alarm channel" حقيقي،
     * فيُسمح لـ Full Screen Intent بإيقاظ الشاشة من background
     * حتى على Android 14+ و Samsung One UI 8.
     */
    private fun createAlarmChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID_ALARM_V3) != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)            // ← الحاسم
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // نغمة المنبه (المخصصة من Settings أو الافتراضية)
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val soundUri: Uri = prefs.getString(MainActivity.KEY_SOUND_URI, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

        val channel = NotificationChannel(
            CHANNEL_ID_ALARM_V3,
            "منبّه التقويم",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تنبيهات شاشة كاملة لأحداث التقويم — توقظ الشاشة"
            setSound(soundUri, audioAttributes)             // ← الإصلاح الجوهري
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 300, 700, 300, 700)
            enableLights(true)
            lightColor = 0xFF5B6EF5.toInt()
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * قناة الـ Observer Service (إشعار خفيف للمراقبة).
     */
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

    /**
     * إعادة إنشاء قناة المنبه — تُستدعى عند تغيير النغمة.
     * (NotificationChannel لا تقبل التحديث بعد الإنشاء — يجب الحذف ثم الإنشاء)
     */
    fun recreateAlarmChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        try { nm.deleteNotificationChannel(CHANNEL_ID_ALARM_V3) } catch (_: Exception) {}
        createAlarmChannel()
    }

    companion object {
        // قناة المنبه الرئيسية — v3 (بـ USAGE_ALARM)
        const val CHANNEL_ID_ALARM_V3 = "calendar_alarm_channel_v3"
        // قناة Observer
        const val CHANNEL_ID_OBSERVER = "observer_channel_v2"
    }
}
