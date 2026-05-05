package com.alaimtiaz.calendaralarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {
    const val EXTRA_EVENT_ID    = "event_id"
    const val EXTRA_TITLE       = "event_title"
    const val EXTRA_DESCRIPTION = "event_description"
    const val EXTRA_LOCATION    = "event_location"
    const val EXTRA_IS_TASK     = "event_is_task"
    const val EXTRA_START_TIME  = "event_start_time"

    fun scheduleAlarm(context: Context, event: CalendarEvent) {
        if (PendingAlarmsStore.isDismissed(context, event.id)) return
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return
        val pi = buildPendingIntent(context, event.id, event.title, event.description, event.location, event.isTask, event.startTime)
        // setAlarmClock — يعامله Samsung كمنبه رسمي يوقظ الشاشة
        val alarmInfo = AlarmManager.AlarmClockInfo(event.startTime, pi)
        am.setAlarmClock(alarmInfo, pi)
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
        val pi = buildPendingIntent(context, eventId + 1_000_000L, title, description, location, isTask, startTime)
        // التأجيل أيضاً كمنبه رسمي
        val alarmInfo = AlarmManager.AlarmClockInfo(triggerAt, pi)
        am.setAlarmClock(alarmInfo, pi)
    }

    fun rescheduleAllUpcoming(context: Context) {
        CalendarRepository.getUpcomingEvents(context, 100).forEach { event ->
            if (event.startTime >= System.currentTimeMillis() && !PendingAlarmsStore.isDismissed(context, event.id))
                scheduleAlarm(context, event)
        }
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
