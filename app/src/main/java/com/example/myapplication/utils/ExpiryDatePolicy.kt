package com.example.myapplication.utils

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Converts a local calendar date into the UTC-midnight date key used by Material DatePicker.
 * Expiry dates are date-only values, not timestamps within a day.
 */
object ExpiryDatePolicy {
    fun todayDateKey(
        nowMillis: Long = System.currentTimeMillis(),
        localTimeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val localDate = Calendar.getInstance(localTimeZone, Locale.US).apply {
            timeInMillis = nowMillis
        }

        return Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(
                localDate.get(Calendar.YEAR),
                localDate.get(Calendar.MONTH),
                localDate.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun addDays(dateKey: Long, days: Int): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            timeInMillis = dateKey
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
    }
}