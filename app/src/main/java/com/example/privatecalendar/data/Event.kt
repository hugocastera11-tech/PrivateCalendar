package com.example.privatecalendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class RecurrenceType {
    NONE, DAILY, WEEKLY, MONTHLY, YEARLY
}

@Entity(tableName = "event_categories")
data class EventCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: EncryptedString,
    val color: Long
)

data class EncryptedString(val value: String, val isAlreadyDecrypted: Boolean = true) {
    val text: String by lazy {
        if (isAlreadyDecrypted) value 
        else com.example.privatecalendar.utils.CryptoManager.decrypt(value)
    }
}

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: LocalDate,
    val time: LocalTime?,
    val title: EncryptedString,
    val description: EncryptedString,
    val location: EncryptedString? = null,
    val isAllDay: Boolean = false,
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val categoryId: Int? = null,
    val attachments: List<String> = emptyList(),
    val externalId: String? = null
)

fun isEventOnDate(event: Event, date: LocalDate): Boolean {
    if (event.date == date) return true
    if (event.date.isAfter(date)) return false
    
    return when (event.recurrence) {
        RecurrenceType.DAILY -> true
        RecurrenceType.WEEKLY -> event.date.dayOfWeek == date.dayOfWeek
        RecurrenceType.MONTHLY -> {
            val originalDay = event.date.dayOfMonth
            val isLastDayOfMonth = date.dayOfMonth == date.lengthOfMonth()
            if (originalDay > date.lengthOfMonth()) {
                isLastDayOfMonth
            } else {
                date.dayOfMonth == originalDay
            }
        }
        RecurrenceType.YEARLY -> {
            val originalDay = event.date.dayOfMonth
            val originalMonth = event.date.month
            if (date.month == originalMonth) {
                if (originalDay > date.lengthOfMonth()) {
                    date.dayOfMonth == date.lengthOfMonth()
                } else {
                    date.dayOfMonth == originalDay
                }
            } else false
        }
        RecurrenceType.NONE -> false
    }
}

fun findNextOccurrence(event: Event): Pair<LocalDate, LocalTime?>? {
    val now = LocalDateTime.now()
    
    // Si el evento original es en el futuro, esa es la primera ocurrencia
    val firstOccurrence = if (event.time != null) LocalDateTime.of(event.date, event.time) else LocalDateTime.of(event.date, LocalTime.MAX)
    if (firstOccurrence.isAfter(now)) return event.date to event.time

    // Si no es recurrente y ya pasó, no hay más
    if (event.recurrence == RecurrenceType.NONE) return null

    // Buscar la siguiente ocurrencia a partir de hoy
    val searchStart = LocalDate.now()
    var candidate = searchStart

    // Lógica para encontrar la siguiente fecha válida según el tipo de recurrencia
    return when (event.recurrence) {
        RecurrenceType.DAILY -> {
            // Si hoy ya pasó la hora del evento, es mañana. Si no, es hoy.
            val todayEventTime = if (event.time != null) LocalDateTime.of(candidate, event.time) else LocalDateTime.of(candidate, LocalTime.MAX)
            if (todayEventTime.isAfter(now)) candidate to event.time else candidate.plusDays(1) to event.time
        }
        RecurrenceType.WEEKLY -> {
            var date = candidate
            // Buscar en los próximos 7 días
            repeat(8) {
                val dateTime = if (event.time != null) LocalDateTime.of(date, event.time) else LocalDateTime.of(date, LocalTime.MAX)
                if (date.dayOfWeek == event.date.dayOfWeek && dateTime.isAfter(now)) {
                    return date to event.time
                }
                date = date.plusDays(1)
            }
            null
        }
        RecurrenceType.MONTHLY -> {
            var date = candidate.withDayOfMonth(minOf(event.date.dayOfMonth, candidate.lengthOfMonth()))
            val dateTime = if (event.time != null) LocalDateTime.of(date, event.time) else LocalDateTime.of(date, LocalTime.MAX)
            
            if (dateTime.isAfter(now)) {
                date to event.time
            } else {
                val nextMonth = date.plusMonths(1)
                nextMonth.withDayOfMonth(minOf(event.date.dayOfMonth, nextMonth.lengthOfMonth())) to event.time
            }
        }
        RecurrenceType.YEARLY -> {
            var date = candidate.withMonth(event.date.monthValue).withDayOfMonth(event.date.dayOfMonth)
            val dateTime = if (event.time != null) LocalDateTime.of(date, event.time) else LocalDateTime.of(date, LocalTime.MAX)
            
            if (dateTime.isAfter(now)) {
                date to event.time
            } else {
                date.plusYears(1) to event.time
            }
        }
        RecurrenceType.NONE -> null
    }
}
