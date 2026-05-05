package com.alaimtiaz.calendaralarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class EventAlarmReceiver : BroadcastReceiver() {

    companion object {
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

            val contentPi = PendingIntent.getActivity(
                context, eventId.toInt() + 100_000, overlayIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = Notification.Builder(context, CalendarAlarmApplication.CHANNEL_ID_ALARM_V5)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(if (isTask) "✅ مهمة" else "📅 تقويم")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setFullScreenIntent(fullScreenPi, true)
                .setContentIntent(contentPi)
                .setAutoCancel(false)
                .setOngoing(true)
                .setShowWhen(true)
                .setWhen(startTime)
                .build()

            val nm = context.getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID_BASE + eventId.toInt(), notification)
        } finally {
            if (wl.isHeld) wl.release()
        }
    }
}
