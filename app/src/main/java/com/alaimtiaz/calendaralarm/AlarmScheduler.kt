package com.alaimtiaz.calendaralarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AlarmScheduler {
    const val EXTRA_EVENT_ID    = "event_id"
    const val EXTRA_TITLE       = "event_title"
    const val EXTRA_DESCRIPTION = "event_description"
    const val EXTRA_LOCATION    = "event_location"
    const val EXTRA_IS_TASK     = "event_is_task"
    const val EXTRA_START_TIME  = "event_start_time"
    private const val TAG = "AlarmScheduler"

    fun scheduleAlarm(context: Context, event: CalendarEvent): Boolean {
        if (PendingAlarmsStore.isDismissed(context, event.id)) return false
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return false
        val pi = buildPendingIntent(context, event.id, event.title, event.description,
            event.location, event.isTask, event.startTime)
        val alarmInfo = AlarmManager.AlarmClockInfo(event.startTime, pi)
        return try {
            am.setAlarmClock(alarmInfo, pi)
            Log.d(TAG, "Scheduled: ${event.title} at ${event.startTime}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed: ${event.title}", e)
            false
        }
    }

    fun cancelAlarm(context: Context, eventId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, eventId.toInt(),
            Intent(context, EventAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) ?: return
        am.cancel(pi); pi.cancel()
    }

    fun scheduleSnooze(context: Context, eventId: Long, title: String, description: String,
                       location: String, isTask: Boolean, startTime: Long, snoozeMinutes: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return
        val triggerAt = System.currentTimeMillis() + snoozeMinutes * 60_000L
        val pi = buildPendingIntent(context, eventId + 1_000_000L, title, description,
            location, isTask, startTime)
        val alarmInfo = AlarmManager.AlarmClockInfo(triggerAt, pi)
        am.setAlarmClock(alarmInfo, pi)
    }

    fun rescheduleAllUpcoming(context: Context): Int {
        var scheduled = 0
        try {
            val events = CalendarRepository.getUpcomingEvents(context, 100)
            Log.i(TAG, "Found ${events.size} upcoming events")
            events.forEach { event ->
                if (event.startTime >= System.currentTimeMillis() &&
                    !PendingAlarmsStore.isDismissed(context, event.id)) {
                    if (scheduleAlarm(context, event)) scheduled++
                }
            }
            Log.i(TAG, "Scheduled $scheduled alarms")
        } catch (e: Exception) {
            Log.e(TAG, "rescheduleAllUpcoming failed", e)
        }
        return scheduled
    }

    private fun buildPendingIntent(context: Context, id: Long, title: String, description: String,
                                   location: String, isTask: Boolean, startTime: Long): PendingIntent {
        val intent = Intent(context, EventAlarmReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, id); putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DESCRIPTION, description); putExtra(EXTRA_LOCATION, location)
            putExtra(EXTRA_IS_TASK, isTask); putExtra(EXTRA_START_TIME, startTime)
        }
        return PendingIntent.getBroadcast(context, id.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
