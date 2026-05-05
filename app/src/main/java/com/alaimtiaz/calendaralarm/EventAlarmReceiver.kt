package com.alaimtiaz.calendaralarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * EventAlarmReceiver — يستقبل trigger من AlarmManager عند وقت الحدث.
 *
 * مسار التنفيذ الصحيح على Android 14+ / One UI 8:
 * ────────────────────────────────────────────────
 * 1. AlarmManager (setAlarmClock) → broadcast هنا
 * 2. هذا الـ Receiver يبني Notification بـ FullScreenIntent
 * 3. النظام (وليس التطبيق) يقرر إطلاق AlarmOverlayActivity
 *    لأن:
 *    - القناة بـ USAGE_ALARM (تُصنّف "alarm" حقيقي)
 *    - Notification بـ CATEGORY_ALARM + PRIORITY_MAX
 *    - FullScreenIntent مع PendingIntent.getActivity
 *    - canUseFullScreenIntent مفعّل من Settings
 *
 * هذا النهج يحترم قيود Android 14/15/16 ولا يحاول
 * استدعاء startActivity من background (الذي محجوب).
 */
class EventAlarmReceiver : BroadcastReceiver() {

    companion object {
        // استخدم قناة v3 الجديدة (بـ USAGE_ALARM)
        val CHANNEL_ID get() = CalendarAlarmApplication.CHANNEL_ID_ALARM_V3
        const val NOTIF_ID_BASE = 9000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId     = intent.getLongExtra(AlarmScheduler.EXTRA_EVENT_ID, -1L)
        val title       = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE)       ?: return
        val description = intent.getStringExtra(AlarmScheduler.EXTRA_DESCRIPTION) ?: ""
        val location    = intent.getStringExtra(AlarmScheduler.EXTRA_LOCATION)    ?: ""
        val isTask      = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_TASK,    false)
        val startTime   = intent.getLongExtra(AlarmScheduler.EXTRA_START_TIME,    0L)
        if (eventId < 0 || title.isBlank()) return
        if (PendingAlarmsStore.isDismissed(context, eventId)) return

        // PARTIAL_WAKE_LOCK يكفي — يبقي المعالج صاحياً حتى يكتمل onReceive
        // (إيقاظ الشاشة مهمة النظام عبر FSI، ليس هذا الـ wakelock)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CalendarAlarm::ReceiverWakeLock"
        )
        wl.acquire(10_000L)

        try {
            // PendingIntent للـ AlarmOverlayActivity (يطلقه النظام تلقائياً عبر FSI)
            val overlayIntent = Intent(context, AlarmOverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
                putExtra(AlarmOverlayActivity.EXTRA_EVENT_ID,    eventId)
                putExtra(AlarmOverlayActivity.EXTRA_TITLE,       title)
                putExtra(AlarmOverlayActivity.EXTRA_DESCRIPTION, description)
                putExtra(AlarmOverlayActivity.EXTRA_LOCATION,    location)
                putExtra(AlarmOverlayActivity.EXTRA_IS_TASK,     isTask)
                putExtra(AlarmOverlayActivity.EXTRA_START_TIME,  startTime)
            }

            val fullScreenPi = PendingIntent.getActivity(
                context, eventId.toInt(), overlayIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // PendingIntent للضغط على الإشعار (نفس الـ overlay)
            val contentPi = PendingIntent.getActivity(
                context, eventId.toInt() + 100_000, overlayIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // بناء Notification بكل الـ flags الحرجة لـ FSI ليطلق Activity
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(if (isTask) "✅ مهمة" else "📅 تقويم")
                .setCategory(Notification.CATEGORY_ALARM)        // ← CATEGORY ALARM
                .setVisibility(Notification.VISIBILITY_PUBLIC)   // ← يظهر على lock screen
                .setPriority(Notification.PRIORITY_MAX)          // ← أعلى أولوية
                .setFullScreenIntent(fullScreenPi, true)         // ← المفتاح: true = highPriority
                .setContentIntent(contentPi)
                .setAutoCancel(false)
                .setOngoing(true)
                .setShowWhen(true)
                .setWhen(startTime)
                .build()

            // FLAG_INSISTENT = الصوت يستمر حتى يتفاعل المستخدم
            notification.flags = notification.flags or Notification.FLAG_INSISTENT

            val nm = context.getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID_BASE + eventId.toInt(), notification)
        } finally {
            if (wl.isHeld) wl.release()
        }
    }
}
