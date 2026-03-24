package com.example.privatecalendar

import android.app.Application
import androidx.work.*
import com.example.privatecalendar.worker.WidgetUpdateWorker
import java.util.concurrent.TimeUnit

class CalendarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        schedulePeriodicWidgetUpdate()
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
