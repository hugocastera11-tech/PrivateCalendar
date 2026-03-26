package com.example.privatecalendar.data

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    private val datePickerFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timePickerFormatter = DateTimeFormatter.ISO_LOCAL_TIME
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.format(datePickerFormatter)
    }

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it, datePickerFormatter) }
    }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? {
        return value?.format(timePickerFormatter)
    }

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? {
        return value?.let { LocalTime.parse(it, timePickerFormatter) }
    }


    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        return value?.format(dateTimeFormatter)
    }

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it, dateTimeFormatter) }
    }

    @TypeConverter
    fun fromRecurrenceType(value: RecurrenceType): String {
        return value.name
    }

    @TypeConverter
    fun toRecurrenceType(value: String): RecurrenceType {
        return RecurrenceType.valueOf(value)
    }
}
