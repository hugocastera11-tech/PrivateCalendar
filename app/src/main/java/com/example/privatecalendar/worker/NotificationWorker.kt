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
import com.example.privatecalendar.widget.CalendarWidget

class NotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: "Evento próximo"
        val description = inputData.getString("description") ?: "Tienes un evento pronto"
        val notificationId = inputData.getInt("notificationId", 0)

        showNotification(title, description, notificationId)
        
        // Sincronizar el widget al disparar una notificación
        // Esto asegura que si el evento ya pasó o el día cambió, el widget se refresque.
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
            0, 
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
