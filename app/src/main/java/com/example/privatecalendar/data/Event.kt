package com.example.privatecalendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

enum class RecurrenceType {
    NONE, DAILY, WEEKLY, MONTHLY, YEARLY
}

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: LocalDate,
    val time: LocalTime?,
    val title: String,
    val description: String,
    val isAllDay: Boolean = false,
    val recurrence: RecurrenceType = RecurrenceType.NONE
)

fun isEventOnDate(event: Event, date: LocalDate): Boolean {
    if (event.date == date) return true
    if (event.date.isAfter(date)) return false
    
    return when (event.recurrence) {
        RecurrenceType.DAILY -> true
        RecurrenceType.WEEKLY -> event.date.dayOfWeek == date.dayOfWeek
        RecurrenceType.MONTHLY -> event.date.dayOfMonth == date.dayOfMonth
        RecurrenceType.YEARLY -> event.date.month == date.month && event.date.dayOfMonth == date.dayOfMonth
        RecurrenceType.NONE -> false
    }
}
