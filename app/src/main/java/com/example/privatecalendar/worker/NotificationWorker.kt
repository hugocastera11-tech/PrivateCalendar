package com.example.privatecalendar.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.privatecalendar.MainActivity
import com.example.privatecalendar.data.AppDatabase
import com.example.privatecalendar.data.RecurrenceType
import com.example.privatecalendar.data.SettingsManager
import com.example.privatecalendar.utils.NotificationHelper
import com.example.privatecalendar.widget.CalendarWidget
import kotlinx.coroutines.flow.first

class NotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: "Evento próximo"
        val description = inputData.getString("description") ?: "Tienes un evento pronto"
        val notificationId = inputData.getInt("notificationId", 0)
        val skipNotification = inputData.getBoolean("skipNotification", false)

        if (!skipNotification) {
            showNotification(title, description, notificationId)
        }
        
        // Reprogramar si es recurrente
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val event = db.eventDao().getEventById(notificationId)
            if (event != null && event.recurrence != RecurrenceType.NONE) {
                val settings = SettingsManager(applicationContext)
                val lead = settings.notificationLeadTime.first()
                val adHour = settings.allDayNotificationHour.first()
                val adDayBefore = settings.allDayNotificationDayBefore.first()
                
                NotificationHelper.scheduleNotification(
                    applicationContext, event.date, event.time, event.title.text, event.id, lead, event.isAllDay, adHour, adDayBefore, event.recurrence
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sincronizar el widget al disparar una notificación
        try {
            CalendarWidget().updateAll(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Result.success()
    }

    private fun showNotification(title: String, description: String, id: Int) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            id,
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(applicationContext, MainActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(id, notification)
    }
}
