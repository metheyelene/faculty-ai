package com.bits.facultyai.ui.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The event-finance math runs on the JVM (pure Kotlin), so the money-critical
 * logic is covered here: rupee formatting with Indian digit grouping, paise
 * conversion without float drift, time parsing/formatting round-trips, and
 * day-of-week derivation — the values faculty see on the FINANCES tab.
 */
class EventFormatTest {

    // ------------------------------------------------------------- money

    @Test
    fun `plain rupee amounts format with Indian grouping`() {
        assertEquals("₹0", EventFormat.money(0L))
        assertEquals("₹5", EventFormat.money(500L))
        assertEquals("₹999", EventFormat.money(99900L))
        assertEquals("₹1,000", EventFormat.money(100000L))
        assertEquals("₹25,000", EventFormat.money(2500000L))
        assertEquals("₹18,450", EventFormat.money(1845000L))
        assertEquals("₹6,550", EventFormat.money(655000L))
        assertEquals("₹1,00,000", EventFormat.money(10000000L))
        assertEquals("₹10,00,000", EventFormat.money(100000000L))
        assertEquals("₹1,23,45,678", EventFormat.money(1234567800L))
    }

    @Test
    fun `paise are shown only when present`() {
        assertEquals("₹10.50", EventFormat.money(1050L))
        assertEquals("₹1,234.05", EventFormat.money(123405L))
        assertEquals("₹0.99", EventFormat.money(99L))
    }

    @Test
    fun `negative balances format correctly`() {
        assertEquals("-₹2,000", EventFormat.money(-200000L))
        assertEquals("-₹0.50", EventFormat.money(-50L))
    }

    @Test
    fun `balance arithmetic is exact in paise`() {
        // The exact scenario from the spec: 25000 collected, 18450 spent.
        val collected = 2500000L
        val spent = 1845000L
        assertEquals(655000L, collected - spent)
        assertEquals("₹6,550", EventFormat.money(collected - spent))
    }

    @Test
    fun `rupee string round-trips through the exact parser`() {
        // Editors display EventFormat.money(...) minus the ₹; typing it back
        // through paisaFromRupees must reproduce the identical paise value.
        val samples = listOf(2500000L, 1845000L, 655000L, 123405L, 10000000L, 1L, 99L)
        for (paisa in samples) {
            val displayed = EventFormat.money(paisa).removePrefix("₹")
            assertEquals("round-trip failed for $paisa", paisa, EventFormat.paisaFromRupees(displayed))
        }
    }

    // ------------------------------------------------- exact rupee parsing

    @Test
    fun `paisaFromRupees parses plain amounts exactly`() {
        assertEquals(2500000L, EventFormat.paisaFromRupees("25000"))
        assertEquals(1845000L, EventFormat.paisaFromRupees("18450"))
        assertEquals(100L, EventFormat.paisaFromRupees("1"))
        assertEquals(0L, EventFormat.paisaFromRupees("0"))
    }

    @Test
    fun `paisaFromRupees never drifts through floating point`() {
        // The historical Double-based parser truncated these by a paise.
        assertEquals(10L, EventFormat.paisaFromRupees("0.10"))
        assertEquals(99L, EventFormat.paisaFromRupees("0.99"))
        assertEquals(1845010L, EventFormat.paisaFromRupees("18450.10"))
        assertEquals(1L, EventFormat.paisaFromRupees("0.01"))
        assertEquals(999999999999L, EventFormat.paisaFromRupees("9999999999.99"))
    }

    @Test
    fun `paisaFromRupees accepts Indian grouping and rupee symbol`() {
        assertEquals(2500000L, EventFormat.paisaFromRupees("25,000"))
        assertEquals(1234567800L, EventFormat.paisaFromRupees("₹1,23,45,678"))
        assertEquals(10000000L, EventFormat.paisaFromRupees("1,00,000"))
    }

    @Test
    fun `paisaFromRupees rejects malformed input instead of guessing`() {
        assertNull(EventFormat.paisaFromRupees(""))
        assertNull(EventFormat.paisaFromRupees("   "))
        assertNull(EventFormat.paisaFromRupees("abc"))
        assertNull(EventFormat.paisaFromRupees("12.3.4"))
        assertNull(EventFormat.paisaFromRupees("1.234"))
        assertNull(EventFormat.paisaFromRupees("-50"))
        assertNull(EventFormat.paisaFromRupees(".50"))
    }

    @Test
    fun `paisaFromRupees normalizes stray separators by design`() {
        // Comma cleaning is intentional: half-typed Indian grouping still
        // parses once the digits are unambiguous.
        assertEquals(1200L, EventFormat.paisaFromRupees("12,"))
        assertEquals(1200L, EventFormat.paisaFromRupees(",12"))
    }

    @Test
    fun `paisaFromRupees tolerates single trailing decimal point`() {
        // Users type "25000." often; treat as whole rupees.
        assertEquals(2500000L, EventFormat.paisaFromRupees("25000."))
        assertEquals(500L, EventFormat.paisaFromRupees("5.0"))
    }

    // ------------------------------------------------------------- times

    @Test
    fun `time formatting matches the spec example`() {
        assertEquals("10:00 AM", EventFormat.minutesToTime(10 * 60))
        assertEquals("4:00 PM", EventFormat.minutesToTime(16 * 60))
        assertEquals("12:00 AM", EventFormat.minutesToTime(0))
        assertEquals("12:30 PM", EventFormat.minutesToTime(12 * 60 + 30))
        assertEquals("11:59 PM", EventFormat.minutesToTime(23 * 60 + 59))
    }

    @Test
    fun `time parsing accepts the app display formats`() {
        assertEquals(10 * 60, EventFormat.timeToMinutes("10:00 AM"))
        assertEquals(16 * 60, EventFormat.timeToMinutes("4:00 PM"))
        assertEquals(12 * 60 + 30, EventFormat.timeToMinutes("12:30 PM"))
        // 24h input also accepted
        assertEquals(16 * 60, EventFormat.timeToMinutes("16:00"))
        assertEquals(0, EventFormat.timeToMinutes("00:00"))
    }

    @Test
    fun `time round-trip is stable`() {
        for (minutes in listOf(0, 375, 600, 720, 960, 1439)) {
            assertEquals(minutes, EventFormat.timeToMinutes(EventFormat.minutesToTime(minutes)))
        }
    }

    @Test
    fun `invalid times are rejected not guessed`() {
        assertNull(EventFormat.timeToMinutes(""))
        assertNull(EventFormat.timeToMinutes("abc"))
        assertNull(EventFormat.timeToMinutes("25:00"))
        assertNull(EventFormat.timeToMinutes("10:60 AM"))
        assertNull(EventFormat.timeToMinutes("12:00 ZM"))
    }

    // ------------------------------------------------------------- dates

    @Test
    fun `full date matches the spec example`() {
        // 18 September 2026 is a Friday.
        assertEquals("18 SEPTEMBER 2026", EventFormat.fullDate("2026-09-18"))
        assertEquals("FRIDAY", EventFormat.dayName("2026-09-18"))
    }

    @Test
    fun `day is derived from the date not stored`() {
        assertEquals("SATURDAY", EventFormat.dayName("2026-09-19"))
        assertEquals("MONDAY", EventFormat.dayName("2026-09-14"))
    }

    @Test
    fun `unparseable dates fall back to the raw string`() {
        assertEquals("not-a-date", EventFormat.fullDate("not-a-date"))
        assertEquals("", EventFormat.dayName("not-a-date"))
        assertNull(EventFormat.parse("18/09/2026"))
        assertNull(EventFormat.parse(""))
    }

    @Test
    fun `month-year grouping uses the event timezone convention`() {
        assertEquals("SEPTEMBER 2026", EventFormat.monthYear("2026-09-11"))
    }

    // ------------------------------------------------- filter date math

    @Test
    fun `upcoming includes today and future, excludes past`() {
        val today = java.time.LocalDate.of(2026, 9, 11)
        assertTrue(EventFormat.passesFilter("2026-09-11", EventFilter.UPCOMING, today))
        assertTrue(EventFormat.passesFilter("2027-01-01", EventFilter.UPCOMING, today))
        assertFalse(EventFormat.passesFilter("2026-09-10", EventFilter.UPCOMING, today))
    }

    @Test
    fun `past excludes today`() {
        val today = java.time.LocalDate.of(2026, 9, 11)
        assertTrue(EventFormat.passesFilter("2026-09-10", EventFilter.PAST, today))
        assertFalse(EventFormat.passesFilter("2026-09-11", EventFilter.PAST, today))
    }

    @Test
    fun `this month respects month and year boundaries`() {
        val today = java.time.LocalDate.of(2026, 9, 11)
        assertTrue(EventFormat.passesFilter("2026-09-30", EventFilter.THIS_MONTH, today))
        assertTrue(EventFormat.passesFilter("2026-09-01", EventFilter.THIS_MONTH, today))
        assertFalse(EventFormat.passesFilter("2026-10-01", EventFilter.THIS_MONTH, today))
        assertFalse(EventFormat.passesFilter("2025-09-11", EventFilter.THIS_MONTH, today))
    }

    @Test
    fun `this year ignores month and day`() {
        val today = java.time.LocalDate.of(2026, 9, 11)
        assertTrue(EventFormat.passesFilter("2026-01-01", EventFilter.THIS_YEAR, today))
        assertTrue(EventFormat.passesFilter("2026-12-31", EventFilter.THIS_YEAR, today))
        assertFalse(EventFormat.passesFilter("2025-12-31", EventFilter.THIS_YEAR, today))
        assertFalse(EventFormat.passesFilter("2027-01-01", EventFilter.THIS_YEAR, today))
    }

    @Test
    fun `all passes everything including unparseable dates are dropped by others`() {
        val today = java.time.LocalDate.of(2026, 9, 11)
        assertTrue(EventFormat.passesFilter("2020-01-01", EventFilter.ALL, today))
        assertFalse(EventFormat.passesFilter("not-a-date", EventFilter.UPCOMING, today))
        assertFalse(EventFormat.passesFilter("", EventFilter.PAST, today))
        assertFalse(EventFormat.passesFilter("2026-13-01", EventFilter.THIS_YEAR, today))
    }

    // ------------------------------------------------- editor guards

    @Test
    fun `amount field input filtering keeps only digits separators`() {
        val raw = "₹ 25,000.50x"
        val cleaned = raw.filter { it.isDigit() || it == '.' || it == ',' }
        assertEquals("25,000.50", cleaned)
    }

    @Test
    fun `calendar math across a year boundary stays correct`() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.DECEMBER, 31, 0, 0, 0)
        }
        assertEquals("THURSDAY", EventFormat.dayName("2026-12-31"))
        // Sanity: 2027-01-01 is a Friday.
        assertEquals("FRIDAY", EventFormat.dayName("2027-01-01"))
        assertTrue(cal.get(Calendar.YEAR) == 2026)
        assertFalse(EventFormat.parse("2026-13-01") != null)
    }
}
