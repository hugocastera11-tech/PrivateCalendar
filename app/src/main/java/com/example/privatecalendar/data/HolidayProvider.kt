package com.example.privatecalendar.data

import java.time.LocalDate
import java.time.Month
import java.util.Locale

data class Holiday(val date: LocalDate, val name: String)

object HolidayProvider {
    val SUPPORTED_COUNTRIES = mapOf(
        "ES" to "España",
        "MX" to "México",
        "AR" to "Argentina",
        "US" to "EE.UU."
    )

    fun getHolidaysForYear(year: Int, countryCode: String): List<Holiday> {
        return when (countryCode.uppercase()) {
            "ES" -> getSpanishHolidays(year)
            "MX" -> getMexicanHolidays(year)
            "AR" -> getArgentineHolidays(year)
            "US" -> getUSHoliday(year)
            else -> getSpanishHolidays(year)
        }
    }

    private fun getSpanishHolidays(year: Int): List<Holiday> {
        return listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Año Nuevo"),
            Holiday(LocalDate.of(year, Month.JANUARY, 6), "Epifanía del Señor"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Fiesta del Trabajo"),
            Holiday(LocalDate.of(year, Month.AUGUST, 15), "Asunción de la Virgen"),
            Holiday(LocalDate.of(year, Month.OCTOBER, 12), "Fiesta Nacional de España"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 1), "Todos los Santos"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 6), "Día de la Constitución"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 8), "Inmaculada Concepción"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Natividad del Señor")
        )
    }

    private fun getMexicanHolidays(year: Int): List<Holiday> {
        return listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Año Nuevo"),
            Holiday(LocalDate.of(year, Month.FEBRUARY, 5), "Día de la Constitución"),
            Holiday(LocalDate.of(year, Month.MARCH, 21), "Natalicio de Benito Juárez"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Día del Trabajo"),
            Holiday(LocalDate.of(year, Month.SEPTEMBER, 16), "Día de la Independencia"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 20), "Día de la Revolución"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Navidad")
        )
    }

    private fun getArgentineHolidays(year: Int): List<Holiday> {
        return listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Año Nuevo"),
            Holiday(LocalDate.of(year, Month.MARCH, 24), "Día de la Memoria"),
            Holiday(LocalDate.of(year, Month.APRIL, 2), "Día del Veterano"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Día del Trabajador"),
            Holiday(LocalDate.of(year, Month.MAY, 25), "Revolución de Mayo"),
            Holiday(LocalDate.of(year, Month.JULY, 9), "Día de la Independencia"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 8), "Inmaculada Concepción"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Navidad")
        )
    }

    private fun getUSHoliday(year: Int): List<Holiday> {
        return listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "New Year's Day"),
            Holiday(LocalDate.of(year, Month.JULY, 4), "Independence Day"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 11), "Veterans Day"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Christmas Day")
        )
    }
}
