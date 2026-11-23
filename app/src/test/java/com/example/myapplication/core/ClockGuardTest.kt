package com.example.myapplication.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockGuardTest {
    private val last = 1_000_000_000L

    @Test fun okWhenNoLastKnownTime() {
        assertEquals(ClockCheck.Ok, ClockGuard.check(5L, null))
    }

    @Test fun okWhenTimeMovesForward() {
        assertEquals(ClockCheck.Ok, ClockGuard.check(last + 1000L, last))
    }

    @Test fun okWhenSlightlyBehindWithinTolerance() {
        assertEquals(ClockCheck.Ok, ClockGuard.check(last - 60_000L, last))
    }

    @Test fun detectsRollbackBeyondTolerance() {
        val now = last - 10 * 60 * 1000L
        assertEquals(ClockCheck.RolledBack(last, now), ClockGuard.check(now, last))
    }

    @Test fun toleranceBoundaryIsInclusive() {
        val tolerance = ClockGuard.DEFAULT_TOLERANCE_MILLIS
        assertEquals(ClockCheck.Ok, ClockGuard.check(last - tolerance, last))
        assertEquals(
            ClockCheck.RolledBack(last, last - tolerance - 1L),
            ClockGuard.check(last - tolerance - 1L, last)
        )
    }

    @Test fun customAndZeroToleranceWork() {
        assertEquals(ClockCheck.Ok, ClockGuard.check(90L, 100L, 10L))
        assertEquals(ClockCheck.RolledBack(100L, 89L), ClockGuard.check(89L, 100L, 10L))
        assertEquals(ClockCheck.Ok, ClockGuard.check(100L, 100L, 0L))
        assertEquals(ClockCheck.RolledBack(100L, 99L), ClockGuard.check(99L, 100L, 0L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeTolerance() {
        ClockGuard.check(1L, 1L, -1L)
    }

    @Test fun handlesExtremeLongValuesWithoutOverflow() {
        assertEquals(ClockCheck.Ok, ClockGuard.check(Long.MAX_VALUE - 10L, 5L))
        assertEquals(
            ClockCheck.RolledBack(Long.MAX_VALUE, Long.MIN_VALUE),
            ClockGuard.check(Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)
        )
        assertEquals(ClockCheck.Ok, ClockGuard.check(0L, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(
            ClockCheck.RolledBack(Long.MAX_VALUE, -1L),
            ClockGuard.check(-1L, Long.MAX_VALUE, Long.MAX_VALUE)
        )
        assertEquals(
            ClockCheck.RolledBack(0L, Long.MIN_VALUE),
            ClockGuard.check(Long.MIN_VALUE, 0L, 1L)
        )
    }

    @Test fun lastKnownNeverDecreasesAcrossRepeatedSmallRollbacks() {
        var lastKnown: Long? = 1_000_000_000L
        var now = lastKnown!!
        var allowed = 0
        repeat(12) {
            now -= 240_000L
            if (ClockGuard.check(now, lastKnown) == ClockCheck.Ok) {
                allowed++
                lastKnown = ClockGuard.nextLastKnown(now, lastKnown)
            }
        }
        assertEquals(1, allowed)
        assertEquals(1_000_000_000L, lastKnown)
    }

    @Test fun fixedClockReturnsSetTime() {
        val clock = FixedAppClock(42L)
        assertEquals(42L, clock.nowMillis())
        clock.millis = 99L
        assertEquals(99L, clock.nowMillis())
    }

    @Test fun lastKnownNeverDecreases() {
        assertEquals(200L, ClockGuard.nextLastKnown(100L, 200L))
        assertEquals(300L, ClockGuard.nextLastKnown(300L, 200L))
        assertEquals(100L, ClockGuard.nextLastKnown(100L, null))
    }
}
