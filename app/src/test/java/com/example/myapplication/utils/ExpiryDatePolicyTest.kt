package com.example.myapplication.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ExpiryDatePolicyTest {

    @Test
    fun todayDateKeyUsesLocalCalendarDateAndUtcMidnight() {
        val zone = TimeZone.getTimeZone("America/Los_Angeles")
        val localMoment = Calendar.getInstance(zone, Locale.US).apply {
            clear()
            set(2026, Calendar.MARCH, 8, 23, 30, 0)
        }.timeInMillis

        val expected = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(2026, Calendar.MARCH, 8, 0, 0, 0)
        }.timeInMillis

        assertEquals(expected, ExpiryDatePolicy.todayDateKey(localMoment, zone))
    }

    @Test
    fun todayDateKeyChangesAtLocalMidnight() {
        val zone = TimeZone.getTimeZone("Europe/Amsterdam")
        val beforeMidnight = Calendar.getInstance(zone, Locale.US).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 20, 23, 59, 0)
        }.timeInMillis
        val afterMidnight = Calendar.getInstance(zone, Locale.US).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 21, 0, 1, 0)
        }.timeInMillis

        assertEquals(
            ExpiryDatePolicy.addDays(ExpiryDatePolicy.todayDateKey(beforeMidnight, zone), 1),
            ExpiryDatePolicy.todayDateKey(afterMidnight, zone)
        )
    }

    @Test
    fun addDaysUsesCalendarDatesAcrossDaylightSavingChanges() {
        val start = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(2026, Calendar.MARCH, 7, 0, 0, 0)
        }.timeInMillis

        val expected = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(2026, Calendar.MARCH, 8, 0, 0, 0)
        }.timeInMillis

        assertEquals(expected, ExpiryDatePolicy.addDays(start, 1))
    }
}