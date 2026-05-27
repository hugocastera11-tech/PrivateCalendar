package com.example.privatecalendar.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.privatecalendar.MainActivity
import com.example.privatecalendar.R
import com.example.privatecalendar.data.AppDatabase
import kotlinx.coroutines.flow.first

class QuickTasksReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val QUICK_TASKS_NOTIFICATION_ID = 1000000
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val pendingTasks = db.quickTaskDao().observePendingTasks().first()

        if (pendingTasks.isNotEmpty()) {
            showNotification()
        }

        return Result.success()
    }

    private fun showNotification() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            QUICK_TASKS_NOTIFICATION_ID, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(applicationContext, MainActivity.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(R.string.tasks_notification_title))
            .setContentText(applicationContext.getString(R.string.tasks_notification_desc))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(QUICK_TASKS_NOTIFICATION_ID, notification)
    }
}
