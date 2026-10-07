package com.amaxonia.erp.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CajaDateParserTest {

    @Test
    fun parseLocalDate_withStandardDateTimeFormat() {
        val parsed = CajaDateParser.parseLocalDate("2026-10-04 15:30:00")
        assertNotNull(parsed)
        assertEquals(LocalDate.of(2026, 10, 4), parsed)
    }

    @Test
    fun parseLocalDate_withSlashDateTimeFormat() {
        val parsed = CajaDateParser.parseLocalDate("04/10/2026 15:30:00")
        assertNotNull(parsed)
        assertEquals(LocalDate.of(2026, 10, 4), parsed)
    }

    @Test
    fun parseLocalDate_withIsoFormat() {
        val parsed = CajaDateParser.parseLocalDate("2026-10-04T15:30:00")
        assertNotNull(parsed)
        assertEquals(LocalDate.of(2026, 10, 4), parsed)
    }

    @Test
    fun parseLocalDate_withInvalidStringReturnsNull() {
        assertNull(CajaDateParser.parseLocalDate(null))
        assertNull(CajaDateParser.parseLocalDate(""))
        assertNull(CajaDateParser.parseLocalDate("not-a-date"))
    }

    @Test
    fun formatDisplayDate_formatsCorrectly() {
        val formatted = CajaDateParser.formatDisplayDate("2026-10-04 09:15:00")
        assertEquals("04/10/2026", formatted)
    }

    @Test
    fun isFromPreviousDay_detectsYesterday() {
        val today = LocalDate.of(2026, 10, 5)
        val yesterdayRaw = "2026-10-04 22:00:00"

        assertTrue(CajaDateParser.isFromPreviousDay(yesterdayRaw, today))
    }

    @Test
    fun isFromPreviousDay_detectsTodayAsFalse() {
        val today = LocalDate.of(2026, 10, 5)
        val todayRaw = "2026-10-05 08:00:00"

        assertFalse(CajaDateParser.isFromPreviousDay(todayRaw, today))
    }

    @Test
    fun isFromPreviousDay_detectsTomorrowAsFalse() {
        val today = LocalDate.of(2026, 10, 5)
        val tomorrowRaw = "2026-10-06 08:00:00"

        assertFalse(CajaDateParser.isFromPreviousDay(tomorrowRaw, today))
    }

    @Test
    fun isFromPreviousDay_withNullReturnsFalse() {
        assertFalse(CajaDateParser.isFromPreviousDay(null))
    }

    @Test
    fun isFromPreviousDay_calendarDayRollover_strictlyEvaluatesCalendarDay() {
        val today = LocalDate.of(2026, 10, 7)
        // Opened yesterday late at night (23:55), checked today in early morning (00:05):
        // Calendar day comparison must evaluate to true because it is a new day!
        val lateYesterday = "2026-10-06 23:55:00"
        assertTrue(CajaDateParser.isFromPreviousDay(lateYesterday, today))

        // Opened today early in the morning:
        val earlyToday = "2026-10-07 00:05:00"
        assertFalse(CajaDateParser.isFromPreviousDay(earlyToday, today))
    }

    @Test
    fun parseLocalDate_withIsoOffsetAndFractionalSeconds() {
        val parsedUtc = CajaDateParser.parseLocalDate("2026-10-06T23:55:00.123Z")
        assertNotNull(parsedUtc)
        assertEquals(LocalDate.of(2026, 10, 6), parsedUtc)

        val parsedOffset = CajaDateParser.parseLocalDate("2026-10-06T18:00:00-05:00")
        assertNotNull(parsedOffset)
        assertEquals(LocalDate.of(2026, 10, 6), parsedOffset)

        val parsedSqlFractional = CajaDateParser.parseLocalDate("2026-10-06 14:30:00.123456")
        assertNotNull(parsedSqlFractional)
        assertEquals(LocalDate.of(2026, 10, 6), parsedSqlFractional)
    }
}
