package com.example.privatecalendar.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import com.example.privatecalendar.R
import com.example.privatecalendar.data.RecurrenceType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object NotificationHelper {
    private const val ACTION_SHOW_NOTIFICATION = "com.example.privatecalendar.ACTION_SHOW_NOTIFICATION"

    fun scheduleNotification(
        context: Context,
        date: LocalDate,
        time: LocalTime?,
        title: String,
        id: Int,
        leadTimeMinutes: Int,
        isAllDay: Boolean,
        allDayHour: Int = 9,
        allDayDayBefore: Boolean = false,
        recurrence: RecurrenceType = RecurrenceType.NONE
    ) {
        val now = LocalDateTime.now()
        
        fun calculateNotificationTime(eventDate: LocalDate): LocalDateTime {
            return if (isAllDay) {
                val baseDate = if (allDayDayBefore) eventDate.minusDays(1) else eventDate
                LocalDateTime.of(baseDate, LocalTime.of(allDayHour, 0))
            } else {
                LocalDateTime.of(eventDate, time ?: LocalTime.of(12, 0)).minusMinutes(leadTimeMinutes.toLong())
            }
        }

        fun getEventStartTime(eventDate: LocalDate): LocalDateTime {
            return if (isAllDay) LocalDateTime.of(eventDate, LocalTime.MIDNIGHT)
            else LocalDateTime.of(eventDate, time ?: LocalTime.of(12, 0))
        }

        var targetEventDate = date
        var notificationTime = calculateNotificationTime(targetEventDate)

        // Si es recurrente y la notificación ya pasó Y el evento ya terminó, buscamos la siguiente
        if (recurrence != RecurrenceType.NONE) {
            val currentOccurrenceStartTime = getEventStartTime(targetEventDate)
            val eventEndTime = if (isAllDay) currentOccurrenceStartTime.plusDays(1) else currentOccurrenceStartTime.plusHours(1)
            
            if (now.isAfter(eventEndTime)) {
                var nextDate = targetEventDate
                var count = 0
                while (count < 100) {
                    count++
                    nextDate = when (recurrence) {
                        RecurrenceType.DAILY -> nextDate.plusDays(1)
                        RecurrenceType.WEEKLY -> nextDate.plusWeeks(1)
                        RecurrenceType.MONTHLY -> {
                            val candidate = nextDate.plusMonths(1)
                            candidate.withDayOfMonth(minOf(date.dayOfMonth, candidate.lengthOfMonth()))
                        }
                        RecurrenceType.YEARLY -> {
                            val candidate = nextDate.plusYears(1)
                            candidate.withDayOfMonth(minOf(date.dayOfMonth, candidate.lengthOfMonth()))
                        }
                        else -> nextDate
                    }
                    val nextNotificationTime = calculateNotificationTime(nextDate)
                    val nextEventStartTime = getEventStartTime(nextDate)
                    val nextEventEndTime = if (isAllDay) nextEventStartTime.plusDays(1) else nextEventStartTime.plusHours(1)
                    
                    if (nextNotificationTime.isAfter(now) || now.isBefore(nextEventEndTime)) {
                        targetEventDate = nextDate
                        notificationTime = nextNotificationTime
                        break
                    }
                }
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val eventStartTime = getEventStartTime(targetEventDate)
        val occurrenceId = "${id}_${eventStartTime.atZone(ZoneId.systemDefault()).toEpochSecond()}"

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_SHOW_NOTIFICATION
            putExtra("title", title)
            putExtra("notificationId", id)
            putExtra("occurrenceId", occurrenceId)
            
            val description = if (isAllDay) {
                val isToday = eventStartTime.toLocalDate().isEqual(now.toLocalDate())
                if (allDayDayBefore && !isToday) context.getString(R.string.notification_tomorrow, title)
                else context.getString(R.string.notification_today, title)
            } else {
                val diffMinutes = java.time.Duration.between(now, eventStartTime).toMinutes()
                when {
                    diffMinutes <= 0 -> context.getString(R.string.notification_now, title)
                    diffMinutes < leadTimeMinutes -> context.getString(R.string.notification_imminent, diffMinutes.toInt(), title)
                    else -> context.getString(R.string.notification_lead, leadTimeMinutes, title)
                }
            }
            putExtra("description", description)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Evitar alarmas duplicadas al editar/reprogramar el mismo evento.
        alarmManager.cancel(pendingIntent)

        // CASO ESPECIAL: Si la hora de notificación ya pasó pero el evento NO ha terminado todavía
        if (now.isAfter(notificationTime)) {
            val eventEndTime = if (isAllDay) eventStartTime.plusDays(1) else eventStartTime.plusHours(1)
            
            // Solo avisamos si el evento no ha terminado y es MUY reciente (ej. empezó hace menos de 1h)
            // para evitar spam masivo al arrancar o cambiar configuración
            val isVeryRecent = now.isBefore(eventEndTime) && now.isBefore(notificationTime.plusMinutes(60))

            if (isVeryRecent) {
                // EVITAR REPETICIONES: Comprobar si ya se mostró esta ocurrencia específica
                val prefs = context.getSharedPreferences("notif_history", Context.MODE_PRIVATE)
                val alreadyShown = prefs.contains("shown_$occurrenceId")
                
                if (!alreadyShown) {
                    context.sendBroadcast(intent)
                }
            } else {
                alarmManager.cancel(pendingIntent)
            }
            return
        }

        val triggerAtMillis = notificationTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val canUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }
                if (canUseExact) {
                    // Precisión al minuto exacto incluso en Doze.
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            // Fallback final por si acaso
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelNotification(context: Context, id: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_SHOW_NOTIFICATION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, id, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
        // Limpiar también el historial al borrar el evento.
        // El formato real de guardado es shown_<eventId>_<occurrenceEpochSeconds>.
        val prefs = context.getSharedPreferences("notif_history", Context.MODE_PRIVATE)
        prefs.edit {
            prefs.all.keys
                .filter { it.startsWith("shown_${id}_") }
                .forEach(::remove)
        }
    }
}
