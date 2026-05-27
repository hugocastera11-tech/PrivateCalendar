package com.example.privatecalendar.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.privatecalendar.data.AppDatabase
import com.example.privatecalendar.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimezoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val settings = SettingsManager(context)
                    
                    val events = db.eventDao().getAllEventsSync()
                    val lead = settings.notificationLeadTime.first()
                    val adHour = settings.allDayNotificationHour.first()
                    val adDayBefore = settings.allDayNotificationDayBefore.first()
                    
                    events.forEach { event ->
                        NotificationHelper.scheduleNotification(
                            context, event.date, event.time, event.title.text, event.id, lead, event.isAllDay, adHour, adDayBefore, event.recurrence
                        )
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
