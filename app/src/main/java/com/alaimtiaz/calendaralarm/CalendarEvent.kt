package com.alaimtiaz.calendaralarm

data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String = "",
    val location: String = "",
    val startTime: Long,
    val calendarId: Long,
    val calendarName: String,
    val isTask: Boolean = false
)
