package com.alaimtiaz.calendaralarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.provider.CalendarContract

/**
 * CalendarObserverService — يراقب تغييرات Google Calendar في الخلفية
 * ويعيد جدولة المنبهات تلقائياً عند إضافة/تعديل/حذف الأحداث.
 */
class CalendarObserverService : Service() {

    companion object {
        // استخدم قناة Observer من Application
        val CHANNEL_ID get() = CalendarAlarmApplication.CHANNEL_ID_OBSERVER
        const val NOTIF_ID = 2001

        fun start(context: Context) {
            try {
                val intent = Intent(context, CalendarObserverService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    context.startForegroundService(intent)
                else
                    context.startService(intent)
            } catch (e: Exception) {
                // لو ما قدر يشتغل — جدول المنبهات مباشرة
                AlarmScheduler.rescheduleAllUpcoming(context)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val notification = buildNotification()
            startForeground(NOTIF_ID, notification)
            startObserving()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun startObserving() {
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    try { AlarmScheduler.rescheduleAllUpcoming(this@CalendarObserverService) }
                    catch (_: Exception) {}
                }, 2000)
            }
        }
        try {
            contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI, true, observer!!)
            AlarmScheduler.rescheduleAllUpcoming(this)
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { observer?.let { contentResolver.unregisterContentObserver(it) } } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("منبّه التقويم")
            .setContentText("يراقب مواعيدك")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }
}
