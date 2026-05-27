package com.example.privatecalendar.data

import android.content.Context
import android.provider.CalendarContract
import com.example.privatecalendar.utils.NotificationHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object GoogleCalendarImporter {

    suspend fun importGoogleEventsSuspend(
        context: Context,
        eventDao: EventDao,
        leadTimeMinutes: Int,
        allDayHour: Int = 9,
        allDayDayBefore: Boolean = false
    ): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var count = 0
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION
        )

        // Traer eventos desde hace 7 días hasta 1 año en el futuro para no saturar
        val startMillis = System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 7)
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DELETED} = 0"
        val selectionArgs = arrayOf(startMillis.toString())

        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val descIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val tzIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE)
                val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                if (idIdx < 0 || titleIdx < 0 || startIdx < 0 || allDayIdx < 0) {
                    android.util.Log.e("GoogleCalendarImporter", "Calendar provider does not expose required columns")
                    return@use
                }

                while (cursor.moveToNext()) {
                    try {
                        val gId = cursor.getString(idIdx)
                        val title = cursor.getString(titleIdx) ?: continue
                        val description = cursor.getString(descIdx) ?: ""
                        val location = cursor.getString(locIdx)
                        val dtStart = cursor.getLong(startIdx)
                        val isAllDay = cursor.getInt(allDayIdx) == 1
                        val timezone = cursor.getString(tzIdx) ?: ZoneId.systemDefault().id

                        val zoneId = try { ZoneId.of(timezone) } catch (e: Exception) { ZoneId.systemDefault() }
                        val dateTime = Instant.ofEpochMilli(dtStart).atZone(zoneId)
                        
                        val date = dateTime.toLocalDate()
                        val time = if (isAllDay) null else dateTime.toLocalTime()

                        // Solo procesar si el evento no es muy antiguo
                        if (date.isBefore(LocalDate.now().minusDays(1))) continue

                        val existingEvent = eventDao.getEventByExternalId(gId)
                        
                        if (existingEvent == null) {
                            val event = Event(
                                title = EncryptedString(title),
                                description = EncryptedString(description),
                                location = location?.let { EncryptedString(it) },
                                date = date,
                                time = time,
                                isAllDay = isAllDay,
                                externalId = gId
                            )
                            val id = eventDao.insertEvent(event)
                            
                            // Programar notificación solo si el evento es futuro o hoy
                            if (!date.isBefore(LocalDate.now())) {
                                try {
                                    NotificationHelper.scheduleNotification(
                                        context, date, time, title, id.toInt(), leadTimeMinutes, isAllDay, 
                                        allDayHour, allDayDayBefore, recurrence = RecurrenceType.NONE
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("GoogleCalendarImporter", "Error scheduling notification", e)
                                }
                            }
                            count++
                        } else {
                            // Si ya existe, actualizamos
                            val updatedEvent = existingEvent.copy(
                                title = EncryptedString(title),
                                description = EncryptedString(description),
                                location = location?.let { EncryptedString(it) },
                                date = date,
                                time = time,
                                isAllDay = isAllDay
                            )
                            eventDao.updateEvent(updatedEvent)
                            
                            if (!date.isBefore(LocalDate.now())) {
                                try {
                                    NotificationHelper.scheduleNotification(
                                        context, date, time, title, updatedEvent.id, leadTimeMinutes, isAllDay, 
                                        allDayHour, allDayDayBefore, recurrence = RecurrenceType.NONE
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("GoogleCalendarImporter", "Error updating notification", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GoogleCalendarImporter", "Error processing individual event", e)
                    }
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("GoogleCalendarImporter", "Permission denied for CalendarProvider", e)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("GoogleCalendarImporter", "General error during import", e)
        }
        count
    }
}
