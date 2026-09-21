package com.amaxonia.pos.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CajaDateParserTest {
    private val today = LocalDate.of(2026, 9, 18)

    @Test
    fun `parseLocalDate correctly parses various date and datetime formats`() {
        assertEquals(LocalDate.of(2026, 9, 17), CajaDateParser.parseLocalDate("2026-09-17 14:30:00"))
        assertEquals(LocalDate.of(2026, 9, 17), CajaDateParser.parseLocalDate("17/09/2026 14:30:00"))
        assertEquals(LocalDate.of(2026, 9, 17), CajaDateParser.parseLocalDate("2026-09-17T14:30:00"))
        assertEquals(LocalDate.of(2026, 9, 17), CajaDateParser.parseLocalDate("2026-09-17"))
        assertEquals(LocalDate.of(2026, 9, 17), CajaDateParser.parseLocalDate("17/09/2026"))
        assertNull(CajaDateParser.parseLocalDate(null))
        assertNull(CajaDateParser.parseLocalDate(""))
        assertNull(CajaDateParser.parseLocalDate("invalid-date"))
    }

    @Test
    fun `formatDisplayDate formats to dd-MM-yyyy`() {
        assertEquals("17/09/2026", CajaDateParser.formatDisplayDate("2026-09-17 08:00:00"))
        assertEquals("18/09/2026", CajaDateParser.formatDisplayDate("2026-09-18"))
        assertEquals("15/05/2025", CajaDateParser.formatDisplayDate("15/05/2025 10:11:12"))
        assertEquals("", CajaDateParser.formatDisplayDate(null))
    }

    @Test
    fun `isFromPreviousDay returns true when opening date is before today`() {
        assertTrue(CajaDateParser.isFromPreviousDay("2026-09-17 23:59:59", today))
        assertTrue(CajaDateParser.isFromPreviousDay("2026-09-01", today))
        assertTrue(CajaDateParser.isFromPreviousDay("10/08/2026 09:00:00", today))
    }

    @Test
    fun `isFromPreviousDay returns false when opening date is today or future`() {
        assertFalse(CajaDateParser.isFromPreviousDay("2026-09-18 00:00:00", today))
        assertFalse(CajaDateParser.isFromPreviousDay("2026-09-18 10:30:00", today))
        assertFalse(CajaDateParser.isFromPreviousDay("2026-09-19 08:00:00", today))
        assertFalse(CajaDateParser.isFromPreviousDay(null, today))
        assertFalse(CajaDateParser.isFromPreviousDay("", today))
    }
}
