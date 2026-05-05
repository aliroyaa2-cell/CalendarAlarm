package com.alaimtiaz.calendaralarm

import android.content.Context
import org.json.JSONArray

object PendingAlarmsStore {
    private const val PREFS = "PendingAlarmsStore"
    private const val KEY_DISMISSED = "dismissed_ids"

    fun dismiss(context: Context, eventId: Long) {
        val set = getDismissedSet(context).toMutableSet()
        set.add(eventId)
        val arr = JSONArray(); set.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DISMISSED, arr.toString()).apply()
    }

    fun isDismissed(context: Context, eventId: Long) = getDismissedSet(context).contains(eventId)

    private fun getDismissedSet(context: Context): Set<Long> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DISMISSED, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getLong(it) }.toSet()
        } catch (e: Exception) { emptySet() }
    }
}
