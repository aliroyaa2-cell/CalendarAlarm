package com.alaimtiaz.calendaralarm

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import java.util.Locale

object CalendarRepository {

    private val EVENT_PROJECTION = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.CALENDAR_ID,
        CalendarContract.Events.DELETED
    )

    private val CALENDAR_PROJECTION = arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.ACCOUNT_TYPE
    )

    private fun loadCalendars(context: Context): Map<Long, Pair<String, Boolean>> {
        val map = mutableMapOf<Long, Pair<String, Boolean>>()
        try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
                CALENDAR_PROJECTION, null, null, null)?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val name = it.getString(1) ?: ""
                    val type = it.getString(2) ?: ""
                    val isTask = type.contains("tasks", true) || name.contains("tasks", true) || name.contains("مهام", true)
                    map[id] = Pair(name, isTask)
                }
            }
        } catch (_: Exception) {}
        return map
    }

    fun getUpcomingEvents(context: Context, limit: Int = 50): List<CalendarEvent> {
        val calendars = loadCalendars(context)
        val now = System.currentTimeMillis()
        val events = mutableListOf<CalendarEvent>()
        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, EVENT_PROJECTION,
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DELETED} = 0",
                arrayOf(now.toString()),
                "${CalendarContract.Events.DTSTART} ASC LIMIT $limit"
            )?.use { while (it.moveToNext()) { cursorToEvent(it, calendars)?.let { e -> events.add(e) } } }
        } catch (_: Exception) {}
        return events
    }

    fun getPastEvents(context: Context, limit: Int = 50): List<CalendarEvent> {
        val calendars = loadCalendars(context)
        val now = System.currentTimeMillis()
        val events = mutableListOf<CalendarEvent>()
        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, EVENT_PROJECTION,
                "${CalendarContract.Events.DTSTART} < ? AND ${CalendarContract.Events.DELETED} = 0",
                arrayOf(now.toString()),
                "${CalendarContract.Events.DTSTART} DESC LIMIT $limit"
            )?.use { while (it.moveToNext()) { cursorToEvent(it, calendars)?.let { e -> events.add(e) } } }
        } catch (_: Exception) {}
        return events
    }

    fun searchEvents(context: Context, query: String): List<CalendarEvent> {
        if (query.isBlank()) return emptyList()
        val calendars = loadCalendars(context)
        val q = query.trim().lowercase(Locale.getDefault())
        val events = mutableListOf<CalendarEvent>()
        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, EVENT_PROJECTION,
                "${CalendarContract.Events.DELETED} = 0", null,
                "${CalendarContract.Events.DTSTART} DESC LIMIT 200"
            )?.use {
                while (it.moveToNext()) {
                    val e = cursorToEvent(it, calendars) ?: continue
                    if (e.title.lowercase(Locale.getDefault()).contains(q) ||
                        e.description.lowercase(Locale.getDefault()).contains(q) ||
                        e.location.lowercase(Locale.getDefault()).contains(q)) events.add(e)
                }
            }
        } catch (_: Exception) {}
        return events
    }

    private fun cursorToEvent(cursor: Cursor, calendars: Map<Long, Pair<String, Boolean>>): CalendarEvent? {
        return try {
            val title = cursor.getString(1) ?: return null
            if (title.isBlank()) return null
            val calId = cursor.getLong(5)
            val calInfo = calendars[calId]
            CalendarEvent(
                id = cursor.getLong(0),
                title = title.trim(),
                description = cursor.getString(2)?.trim() ?: "",
                location = cursor.getString(3)?.trim() ?: "",
                startTime = cursor.getLong(4),
                calendarId = calId,
                calendarName = calInfo?.first ?: "",
                isTask = calInfo?.second ?: false
            )
        } catch (_: Exception) { null }
    }
}
