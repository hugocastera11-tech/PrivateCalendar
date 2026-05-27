package com.example.privatecalendar

import android.app.Application
import androidx.work.*
import com.example.privatecalendar.worker.WidgetUpdateWorker
import java.util.concurrent.TimeUnit

class CalendarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Limpiar posibles tareas antiguas de WorkManager de versiones anteriores
        // para evitar notificaciones fantasma
        WorkManager.getInstance(this).cancelAllWorkByTag("event_notification")

        schedulePeriodicWidgetUpdate()
        scheduleDailyQuickTasksReminder()
    }

    private fun scheduleDailyQuickTasksReminder() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        // Programar para las 8:30 AM cada día
        val periodicWorkRequest = PeriodicWorkRequestBuilder<com.example.privatecalendar.worker.QuickTasksReminderWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(calculateInitialDelayForMorning(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_tasks_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    private fun calculateInitialDelayForMorning(): Long {
        val now = java.time.LocalDateTime.now()
        var target = java.time.LocalDateTime.of(now.toLocalDate(), java.time.LocalTime.of(8, 30))
        if (now.isAfter(target)) {
            target = target.plusDays(1)
        }
        return java.time.Duration.between(now, target).toMillis()
    }

    private fun schedulePeriodicWidgetUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            4, TimeUnit.HOURS // Actualizar cada 4 horas es razonable para un calendario
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "periodic_widget_update",
            ExistingPeriodicWorkPolicy.KEEP, // Mantener si ya existe
            periodicWorkRequest
        )
    }
}
