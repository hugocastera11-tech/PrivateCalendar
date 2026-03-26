package com.example.privatecalendar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

data class Holiday(val date: LocalDate, val name: String)

object HolidayProvider {
    val SUPPORTED_COUNTRIES = mapOf(
        "ES" to "España",
        "MX" to "México",
        "AR" to "Argentina",
        "US" to "EE.UU."
    )

    fun getHolidaysForYear(year: Int, countryCode: String): List<Holiday> {
        val holidays = when (countryCode.uppercase()) {
            "ES" -> getSpanishHolidays(year)
            "MX" -> getMexicanHolidays(year)
            "AR" -> getArgentineHolidays(year)
            "US" -> getUSHolidays(year)
            else -> getSpanishHolidays(year)
        }

        return holidays.sortedBy { it.date }
    }

    private fun getSpanishHolidays(year: Int): List<Holiday> {
        val easterSunday = calculateEasterSunday(year)
        return listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Año Nuevo"),
            Holiday(LocalDate.of(year, Month.JANUARY, 6), "Epifanía del Señor"),
            Holiday(easterSunday.minusDays(2), "Viernes Santo"),
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
            Holiday(nthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 1), "Día de la Constitución"),
            Holiday(nthWeekdayOfMonth(year, Month.MARCH, DayOfWeek.MONDAY, 3), "Natalicio de Benito Juárez"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Día del Trabajo"),
            Holiday(LocalDate.of(year, Month.SEPTEMBER, 16), "Día de la Independencia"),
            Holiday(nthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.MONDAY, 3), "Día de la Revolución"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Navidad")
        )
    }

    private fun getArgentineHolidays(year: Int): List<Holiday> {
        val easterSunday = calculateEasterSunday(year)
        return listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "Año Nuevo"),
            Holiday(easterSunday.minusDays(48), "Carnaval (Lunes)"),
            Holiday(easterSunday.minusDays(47), "Carnaval (Martes)"),
            Holiday(LocalDate.of(year, Month.MARCH, 24), "Día de la Memoria"),
            Holiday(LocalDate.of(year, Month.APRIL, 2), "Día del Veterano y de los Caídos en Malvinas"),
            Holiday(easterSunday.minusDays(2), "Viernes Santo"),
            Holiday(LocalDate.of(year, Month.MAY, 1), "Día del Trabajador"),
            Holiday(LocalDate.of(year, Month.MAY, 25), "Revolución de Mayo"),
            Holiday(LocalDate.of(year, Month.JUNE, 20), "Paso a la Inmortalidad de Manuel Belgrano"),
            Holiday(LocalDate.of(year, Month.JULY, 9), "Día de la Independencia"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 8), "Inmaculada Concepción"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Navidad")
        )
    }

    private fun getUSHolidays(year: Int): List<Holiday> {
        return listOf(
            Holiday(LocalDate.of(year, Month.JANUARY, 1), "New Year's Day"),
            Holiday(nthWeekdayOfMonth(year, Month.JANUARY, DayOfWeek.MONDAY, 3), "Martin Luther King Jr. Day"),
            Holiday(nthWeekdayOfMonth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3), "Washington's Birthday"),
            Holiday(lastWeekdayOfMonth(year, Month.MAY, DayOfWeek.MONDAY), "Memorial Day"),
            Holiday(LocalDate.of(year, Month.JUNE, 19), "Juneteenth National Independence Day"),
            Holiday(LocalDate.of(year, Month.JULY, 4), "Independence Day"),
            Holiday(nthWeekdayOfMonth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1), "Labor Day"),
            Holiday(nthWeekdayOfMonth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2), "Columbus Day"),
            Holiday(LocalDate.of(year, Month.NOVEMBER, 11), "Veterans Day"),
            Holiday(nthWeekdayOfMonth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4), "Thanksgiving Day"),
            Holiday(LocalDate.of(year, Month.DECEMBER, 25), "Christmas Day")
        )
    }

    private fun nthWeekdayOfMonth(
        year: Int,
        month: Month,
        dayOfWeek: DayOfWeek,
        occurrence: Int
    ): LocalDate {
        require(occurrence >= 1) { "occurrence must be >= 1" }

        var date = LocalDate.of(year, month, 1)
        while (date.dayOfWeek != dayOfWeek) {
            date = date.plusDays(1)
        }

        return date.plusWeeks((occurrence - 1).toLong())
    }

    private fun lastWeekdayOfMonth(year: Int, month: Month, dayOfWeek: DayOfWeek): LocalDate {
        var date = LocalDate.of(year, month, 1).withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth())
        while (date.dayOfWeek != dayOfWeek) {
            date = date.minusDays(1)
        }
        return date
    }

    // Algoritmo de Meeus/Jones/Butcher para calendario gregoriano.
    private fun calculateEasterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1

        return LocalDate.of(year, month, day)
    }
}
