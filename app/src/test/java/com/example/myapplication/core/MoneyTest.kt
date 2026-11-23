package com.example.myapplication.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test fun parsesPlainNumbers() {
        assertEquals(12500L, Money.parse("12.5"))
        assertEquals(12000L, Money.parse("12"))
        assertEquals(12500L, Money.parse(" 12.5 "))
        assertEquals(1L, Money.parse("0.001"))
        assertEquals(1500L, Money.parse("1.5000"))
    }

    @Test fun parsesArabicIndicDigitsAndSeparators() {
        assertEquals(12500L, Money.parse("١٢٫٥"))
        assertEquals(12500L, Money.parse("۱۲.۵"))
        assertEquals(1250750L, Money.parse("1,250.75"))
        assertEquals(1250750L, Money.parse("1٬250٫75"))
    }

    @Test fun rejectsMalformedThousandsGroupsAndLeadingZeroGroups() {
        assertNull(Money.parse("1,5"))
        assertNull(Money.parse("0,500"))
        assertNull(Money.parse("00,500"))
        assertNull(Money.parse("012,345"))
        assertEquals(1500000L, Money.parse("1,500"))
        assertNull(Money.parse("1,,000"))
        assertNull(Money.parse(",123"))
        assertNull(Money.parse("1,2345"))
    }

    @Test fun normalizesUnicodeFormatCharactersBeforeTrimming() {
        assertEquals(12500L, Money.parse("\u200F 12.5"))
        assertEquals(12500L, Money.parse("12.5 \u200F"))
        assertEquals(12500L, Money.parse("\u202A12.5\u202C"))
        assertEquals(12500L, Money.parse("\u206612.5\u2069"))
        assertEquals(12500L, Money.parse("\u061C12.5"))
        assertEquals(12500L, Money.parse("\u200B12.5"))
        assertEquals(12500L, Money.parse("\uFEFF12.5"))
    }

    @Test fun rejectsUnsupportedSeparatorsAndNonAsciiDigits() {
        assertNull(Money.parse("12\u00A05"))
        assertNull(Money.parse("12\u202F5"))
        assertNull(Money.parse("12\u060C5"))
        assertNull(Money.parse("\u0967\u0968"))
    }

    @Test fun enforcesInputLengthBoundary() {
        assertEquals(0L, Money.parse("0".repeat(32)))
        assertNull(Money.parse("0".repeat(33)))
        assertNull(Money.parse("9".repeat(100_000)))
    }

    @Test fun rejectsInvalidInputInsteadOfReturningZero() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("12 5"))
        assertNull(Money.parse(".5"))
        assertNull(Money.parse("5."))
        assertNull(Money.parse("12.3456"))
        assertNull(Money.parse("0.0001"))
        assertNull(Money.parse("-5"))
    }

    @Test fun parseHonorsLongBoundary() {
        assertEquals(Long.MAX_VALUE, Money.parse("9223372036854775.807"))
        assertNull(Money.parse("9223372036854775.808"))
        assertNull(Money.parse("-9223372036854775.808", allowNegative = true))
    }

    @Test fun negativeOnlyWhenAllowed() {
        assertEquals(-5000L, Money.parse("-5", allowNegative = true))
        assertEquals(0L, Money.parse("-0", allowNegative = true))
    }

    @Test fun formatsForDisplay() {
        assertEquals("12.5", Money.format(12500L))
        assertEquals("12", Money.format(12000L))
        assertEquals("100", Money.format(100000L))
        assertEquals("0", Money.format(0L))
        assertEquals("0.001", Money.format(1L))
        assertEquals("-12.5", Money.format(-12500L))
        assertEquals("12.35", Money.formatFixed(12345L, 2))
        assertEquals("-12.35", Money.formatFixed(-12345L, 2))
        assertEquals("12", Money.formatFixed(12345L, 0))
        assertEquals("-9223372036854775.808", Money.format(Long.MIN_VALUE))
    }

    @Test fun parseThenFormatRoundTrips() {
        for (text in listOf("0", "1", "12.5", "1234.567", "999999.999")) {
            assertEquals(text, Money.format(Money.parse(text)!!))
        }
    }

    @Test fun ceilDivRoundsUpAndSupportsLargeValues() {
        assertEquals(4L, Money.ceilDiv(10L, 3L))
        assertEquals(3L, Money.ceilDiv(9L, 3L))
        assertEquals(0L, Money.ceilDiv(0L, 5L))
        assertEquals(4611686018427387904L, Money.ceilDiv(Long.MAX_VALUE, 2L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ceilDivRejectsZeroDenominator() {
        Money.ceilDiv(10L, 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun ceilDivRejectsNegativeNumerator() {
        Money.ceilDiv(-1L, 2L)
    }

    @Test fun mulDivHalfUpRoundsHalfUpAndAvoidsIntermediateOverflow() {
        assertEquals(3L, Money.mulDivHalfUp(10L, 1L, 3L))
        assertEquals(3L, Money.mulDivHalfUp(5L, 1L, 2L))
        assertEquals(4L, Money.mulDivHalfUp(7L, 1L, 2L))
        assertEquals(4611686018427387904L, Money.mulDivHalfUp(Long.MAX_VALUE, 2L, 4L))
    }

    @Test(expected = ArithmeticException::class)
    fun mulDivHalfUpRejectsFinalOverflow() {
        Money.mulDivHalfUp(Long.MAX_VALUE, 2L, 1L)
    }

    @Test(expected = ArithmeticException::class)
    fun mulDivHalfUpRejectsOverflowCausedByHalfUpRounding() {
        // Exact result is Long.MAX_VALUE + 0.5
        Money.mulDivHalfUp(4_294_967_295L, 4_294_967_297L, 2L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mulDivHalfUpRejectsNegativeInput() {
        Money.mulDivHalfUp(-1L, 1L, 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mulDivHalfUpRejectsZeroDenominator() {
        Money.mulDivHalfUp(1L, 1L, 0L)
    }
}
