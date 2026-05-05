package com.alaimtiaz.calendaralarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class EventAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "calendar_alarm_channel"
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

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CalendarAlarm::ReceiverWakeLock")
        wl.acquire(10_000L)

        try {
            ensureChannel(context)

            val overlayIntent = Intent(context, AlarmOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                         Intent.FLAG_ACTIVITY_CLEAR_TOP or
                         Intent.FLAG_ACTIVITY_SINGLE_TOP)
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

            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(if (isTask) "✅ مهمة" else "📅 تقويم")
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setFullScreenIntent(fullScreenPi, true)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()

            // ⭐ لا نضع FLAG_INSISTENT — يخلي الصوت يشتغل مرة واحدة فقط

            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID_BASE + eventId.toInt(), notification)
        } finally {
            if (wl.isHeld) wl.release()
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        NotificationChannel(CHANNEL_ID, "منبّه التقويم", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تنبيهات شاشة كاملة لأحداث التقويم"
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }.also { nm.createNotificationChannel(it) }
    }
}
