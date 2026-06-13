package com.example.privatecalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Month

class HolidayProviderTest {

    @Test
    fun testEaster2024() {
        // En 2024 el Domingo de Resurrección fue el 31 de marzo
        val year = 2024
        val expectedEaster = LocalDate.of(year, Month.MARCH, 31)
        
        val holidays = HolidayProvider.getSpanishHolidays(year)
        
        val easter = holidays.find { it.name == "Domingo de Resurrección" }
        assertEquals(expectedEaster, easter?.date)
        
        val goodFriday = holidays.find { it.name == "Viernes Santo" }
        assertEquals(expectedEaster.minusDays(2), goodFriday?.date)
    }

    @Test
    fun testEaster2025() {
        // En 2025 el Domingo de Resurrección será el 20 de abril
        val year = 2025
        val expectedEaster = LocalDate.of(year, Month.APRIL, 20)
        
        val holidays = HolidayProvider.getSpanishHolidays(year)
        
        val easter = holidays.find { it.name == "Domingo de Resurrección" }
        assertEquals(expectedEaster, easter?.date)
    }

    @Test
    fun testFixedHolidaysSpain() {
        val year = 2024
        val holidays = HolidayProvider.getSpanishHolidays(year)
        
        assertTrue(holidays.any { it.name == "Año Nuevo" && it.date == LocalDate.of(year, 1, 1) })
        assertTrue(holidays.any { it.name == "Fiesta Nacional de España" && it.date == LocalDate.of(year, 10, 12) })
        assertTrue(holidays.any { it.name == "Natividad del Señor" && it.date == LocalDate.of(year, 12, 25) })
    }

    @Test
    fun testUSHolidaysMemorialDay() {
        // Memorial Day is last Monday of May
        // In 2024, May 31 is Friday. Last Monday is May 27.
        val year = 2024
        val expectedMemorialDay = LocalDate.of(year, Month.MAY, 27)
        
        val holidays = HolidayProvider.getUSHolidays(year)
        
        val memorialDay = holidays.find { it.name == "Memorial Day" }
        assertEquals(expectedMemorialDay, memorialDay?.date)
    }
    @Test
    fun testSpainIncludesFamilyObservances() {
        val year = 2024
        val holidays = HolidayProvider.getSpanishHolidays(year)

        assertTrue(holidays.any { it.name.contains("Día del Padre") && it.date == LocalDate.of(year, Month.MARCH, 19) })
        assertTrue(holidays.any { it.name == "Día de la Madre" && it.date == LocalDate.of(year, Month.MAY, 5) })
    }

    @Test
    fun testSupportedLatinAmericanCountriesIncludeFathersDay() {
        val year = 2024

        assertTrue(HolidayProvider.getColombianHolidays(year).any { it.name == "Día del Padre" && it.date == LocalDate.of(year, Month.JUNE, 16) })
        assertTrue(HolidayProvider.getNicaraguanHolidays(year).any { it.name == "Día del Padre" && it.date == LocalDate.of(year, Month.JUNE, 23) })
        assertTrue(HolidayProvider.getSalvadoranHolidays(year).any { it.name == "Día del Padre" && it.date == LocalDate.of(year, Month.JUNE, 17) })
    }

    @Test
    fun testEuropeanCountriesIncludeLocalFamilyDates() {
        val year = 2024

        assertTrue(HolidayProvider.getGermanHolidays(year).any { it.name == "Día del Padre" && it.date == LocalDate.of(year, Month.MAY, 9) })
        assertTrue(HolidayProvider.getBritishHolidays(year).any { it.name == "Día de la Madre" && it.date == LocalDate.of(year, Month.MARCH, 10) })
        assertTrue(HolidayProvider.getPortugueseHolidays(year).any { it.name == "Día del Padre" && it.date == LocalDate.of(year, Month.MARCH, 19) })
    }

    @Test
    fun testFallbackProvidersIncludeOfficialYearRoundHolidays() {
        val year = 2024

        assertTrue(HolidayProvider.getColombianHolidays(year).any { it.name == "Año Nuevo" && it.date == LocalDate.of(year, Month.JANUARY, 1) })
        assertTrue(HolidayProvider.getColombianHolidays(year).any { it.name == "Viernes Santo" && it.date == LocalDate.of(year, Month.MARCH, 29) })
        assertTrue(HolidayProvider.getColombianHolidays(year).any { it.name == "Navidad" && it.date == LocalDate.of(year, Month.DECEMBER, 25) })
        assertTrue(HolidayProvider.getBrazilianHolidays(year).any { it.name == "Confraternização Universal" && it.date == LocalDate.of(year, Month.JANUARY, 1) })
        assertTrue(HolidayProvider.getBrazilianHolidays(year).any { it.name == "Sexta-feira Santa" && it.date == LocalDate.of(year, Month.MARCH, 29) })
        assertTrue(HolidayProvider.getFrenchHolidays(year).any { it.name == "Lunes de Pascua" && it.date == LocalDate.of(year, Month.APRIL, 1) })
        assertTrue(HolidayProvider.getCanadianHolidays(year).any { it.name == "Christmas Day" && it.date == LocalDate.of(year, Month.DECEMBER, 25) })
    }

    @Test
    fun testSpanishHolidaysIncludeCommonAndRegionalFestivities() {
        val year = 2024
        val holidays = HolidayProvider.getSpanishHolidays(year)

        assertTrue(holidays.any { it.name == "Lunes de Pascua" && it.date == LocalDate.of(year, Month.APRIL, 1) })
        assertTrue(holidays.any { it.name == "Corpus Christi" && it.date == LocalDate.of(year, Month.MAY, 30) })
        assertTrue(holidays.any { it.name == "Día de Andalucía" && it.date == LocalDate.of(year, Month.FEBRUARY, 28) })
        assertTrue(holidays.any { it.name == "Día de la Comunidad de Madrid" && it.date == LocalDate.of(year, Month.MAY, 2) })
        assertTrue(holidays.any { it.name == "Diada Nacional de Catalunya" && it.date == LocalDate.of(year, Month.SEPTEMBER, 11) })
        assertTrue(holidays.any { it.name == "Día de la Comunitat Valenciana" && it.date == LocalDate.of(year, Month.OCTOBER, 9) })
        assertTrue(holidays.any { it.name == "Nochebuena" && it.date == LocalDate.of(year, Month.DECEMBER, 24) })
        assertTrue(holidays.any { it.name == "San Esteban" && it.date == LocalDate.of(year, Month.DECEMBER, 26) })
    }

}
