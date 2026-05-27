package com.example.privatecalendar.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.privatecalendar.MainActivity
import com.example.privatecalendar.data.AppDatabase
import com.example.privatecalendar.worker.NotificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val title = intent.getStringExtra("title") ?: "Evento"
        val description = intent.getStringExtra("description") ?: ""
        val notificationId = intent.getIntExtra("notificationId", 0)
        val occurrenceId = intent.getStringExtra("occurrenceId") ?: "legacy_$notificationId"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. VERIFICACIÓN DE EXISTENCIA: ¿El evento sigue en la DB?
                val db = AppDatabase.getDatabase(context)
                val event = db.eventDao().getEventById(notificationId)
                
                if (event != null) {
                    // 2. REGISTRO DE AVISO: Guardar que ya hemos avisado esta ocurrencia específica
                    context.getSharedPreferences("notif_history", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("shown_$occurrenceId", true)
                        .putLong("last_shown_$notificationId", System.currentTimeMillis())
                        .apply()

                    showNotification(context, title, description, notificationId)

                    // 3. REPROGRAMACIÓN: Agendar la siguiente ocurrencia si es recurrente
                    val data = Data.Builder()
                        .putInt("notificationId", notificationId)
                        .putBoolean("skipNotification", true)
                        .build()

                    val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                        .setInputData(data)
                        .build()

                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "reschedule_$notificationId",
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, title: String, description: String, id: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            id, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
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
