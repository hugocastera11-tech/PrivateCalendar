package com.example.privatecalendar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EventRecurrenceTest {

    @Test
    fun testIsEventOnDateMonthlyEndAt31() {
        val jan31 = LocalDate.of(2024, 1, 31)
        val event = Event(
            id = 1,
            date = jan31,
            time = null,
            title = EncryptedString("Test"),
            description = EncryptedString("Test"),
            recurrence = RecurrenceType.MONTHLY
        )

        // Feb 29 (leap year 2024) should be true
        assertTrue("Feb 29 should be true for Jan 31 event", isEventOnDate(event, LocalDate.of(2024, 2, 29)))
        
        // Feb 28 should be false in 2024
        assertFalse("Feb 28 should be false for Jan 31 event in 2024", isEventOnDate(event, LocalDate.of(2024, 2, 28)))

        // March 31 should be true
        assertTrue("March 31 should be true for Jan 31 event", isEventOnDate(event, LocalDate.of(2024, 3, 31)))
        
        // April 30 should be true
        assertTrue("April 30 should be true for Jan 31 event", isEventOnDate(event, LocalDate.of(2024, 4, 30)))
    }

    @Test
    fun testIsEventOnDateYearlyFeb29() {
        val feb29 = LocalDate.of(2024, 2, 29)
        val event = Event(
            id = 1,
            date = feb29,
            time = null,
            title = EncryptedString("Test"),
            description = EncryptedString("Test"),
            recurrence = RecurrenceType.YEARLY
        )

        // Feb 28 in 2025 (not leap) should be true
        assertTrue("Feb 28 2025 should be true for Feb 29 2024 event", isEventOnDate(event, LocalDate.of(2025, 2, 28)))
        
        // Feb 29 2028 should be true
        assertTrue("Feb 29 2028 should be true for Feb 29 2024 event", isEventOnDate(event, LocalDate.of(2028, 2, 29)))
    }
}
