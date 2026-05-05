package com.alaimtiaz.calendaralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON",
                "com.htc.intent.action.QUICKBOOT_POWERON"
        )) {
            // أعد الجدولة بعد ثانيتين (ضماناً لاكتمال التهيئة)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    AlarmScheduler.rescheduleAllUpcoming(context)
                    CalendarObserverService.start(context)
                } catch (_: Exception) {}
            }, 2000L)
        }
    }
}
