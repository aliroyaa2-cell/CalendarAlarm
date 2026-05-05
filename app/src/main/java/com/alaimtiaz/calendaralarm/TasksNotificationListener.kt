package com.alaimtiaz.calendaralarm
import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
class TasksNotificationListener : NotificationListenerService() {
    companion object {
        var instance: TasksNotificationListener? = null
        private val TASKS_PACKAGES = setOf("com.google.android.apps.tasks")
    }
    override fun onCreate() { super.onCreate(); instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in TASKS_PACKAGES) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE)?.trim() ?: return
        if (title.isBlank()) return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        startActivity(Intent(this, AlarmOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmOverlayActivity.EXTRA_EVENT_ID, sbn.id.toLong())
            putExtra(AlarmOverlayActivity.EXTRA_TITLE, title)
            putExtra(AlarmOverlayActivity.EXTRA_DESCRIPTION, text)
            putExtra(AlarmOverlayActivity.EXTRA_LOCATION, "")
            putExtra(AlarmOverlayActivity.EXTRA_IS_TASK, true)
            putExtra(AlarmOverlayActivity.EXTRA_START_TIME, System.currentTimeMillis())
            putExtra(AlarmOverlayActivity.EXTRA_NOTIF_KEY, sbn.key)
        })
    }
}
