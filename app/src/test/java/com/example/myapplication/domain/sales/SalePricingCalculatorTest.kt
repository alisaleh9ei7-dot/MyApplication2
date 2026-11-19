package com.example.myapplication.domain.sales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SalePricingCalculatorTest {

    @Test
    fun distributesDiscountProportionally() {
        val result = SalePricingCalculator.allocate(
            listOf(
                SalePricingLine("a", 10_000),
                SalePricingLine("b", 20_000)
            ),
            24_000
        ).associate { it.stableId to it.finalAmountCents }

        assertEquals(8_000L, result["a"])
        assertEquals(16_000L, result["b"])
    }

    @Test
    fun distributesIncreaseProportionally() {
        val result = SalePricingCalculator.allocate(
            listOf(
                SalePricingLine("a", 24_500),
                SalePricingLine("b", 24_500)
            ),
            50_000
        ).associate { it.stableId to it.finalAmountCents }

        assertEquals(25_000L, result["a"])
        assertEquals(25_000L, result["b"])
    }

    @Test
    fun largestRemainderDistributesOneCentExactly() {
        val result = SalePricingCalculator.allocate(
            listOf(
                SalePricingLine("a", 1),
                SalePricingLine("b", 1),
                SalePricingLine("c", 1)
            ),
            1
        ).associate { it.stableId to it.finalAmountCents }

        assertEquals(1L, result.values.sum())
        assertEquals(1L, result["a"])
        assertEquals(0L, result["b"])
        assertEquals(0L, result["c"])
    }

    @Test
    fun allocationDoesNotDependOnInputOrder() {
        val first = SalePricingCalculator.allocate(
            listOf(
                SalePricingLine("c", 7),
                SalePricingLine("a", 7),
                SalePricingLine("b", 7)
            ),
            2
        ).associate { it.stableId to it.finalAmountCents }

        val reordered = SalePricingCalculator.allocate(
            listOf(
                SalePricingLine("b", 7),
                SalePricingLine("c", 7),
                SalePricingLine("a", 7)
            ),
            2
        ).associate { it.stableId to it.finalAmountCents }

        assertEquals(first, reordered)
    }

    @Test
    fun zeroWeightLinesDoNotReceiveAllocation() {
        val result = SalePricingCalculator.allocate(
            listOf(
                SalePricingLine("free", 0),
                SalePricingLine("paid", 100)
            ),
            125
        ).associate { it.stableId to it.finalAmountCents }

        assertEquals(0L, result["free"])
        assertEquals(125L, result["paid"])
    }

    @Test
    fun allZeroLinesAllowOnlyZeroTotal() {
        val result = SalePricingCalculator.allocate(
            listOf(SalePricingLine("a", 0), SalePricingLine("b", 0)),
            0
        )

        assertEquals(listOf(0L, 0L), result.map { it.finalAmountCents })
        assertThrows(IllegalArgumentException::class.java) {
            SalePricingCalculator.allocate(
                listOf(SalePricingLine("a", 0), SalePricingLine("b", 0)),
                1
            )
        }
    }

    @Test
    fun zeroInvoiceTotalProducesZeroLines() {
        val result = SalePricingCalculator.allocate(
            listOf(SalePricingLine("a", 10_000), SalePricingLine("b", 5_000)),
            0
        )

        assertEquals(listOf(0L, 0L), result.map { it.finalAmountCents })
    }

    @Test
    fun rejectsNegativeAmountsAndTotals() {
        assertThrows(IllegalArgumentException::class.java) {
            SalePricingCalculator.allocate(listOf(SalePricingLine("a", -1)), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalePricingCalculator.allocate(listOf(SalePricingLine("a", 1)), -1)
        }
    }

    @Test
    fun rejectsEmptyLinesAndDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) {
            SalePricingCalculator.allocate(emptyList(), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SalePricingCalculator.allocate(
                listOf(SalePricingLine("same", 1), SalePricingLine("same", 2)),
                1
            )
        }
    }

    @Test
    fun supportsLargeValuesWithoutIntermediateLongOverflow() {
        val result = SalePricingCalculator.allocate(
            listOf(
                SalePricingLine("a", Long.MAX_VALUE),
                SalePricingLine("b", Long.MAX_VALUE)
            ),
            Long.MAX_VALUE
        )

        assertEquals(Long.MAX_VALUE, result.sumOf { it.finalAmountCents })
        assertEquals(Long.MAX_VALUE / 2 + 1, result[0].finalAmountCents)
        assertEquals(Long.MAX_VALUE / 2, result[1].finalAmountCents)
    }
}
