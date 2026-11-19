package com.example.myapplication.domain.sales

import java.math.BigInteger

/**
 * A sale line's original (pre-adjustment) amount, expressed in the smallest
 * currency unit (for example, cents). [stableId] must be unique within a sale
 * and must not depend on the current list position.
 */
data class SalePricingLine(
    val stableId: String,
    val listAmountCents: Long
)

/** The final amount allocated to one sale line, in the smallest currency unit. */
data class SalePricingAllocation(
    val stableId: String,
    val listAmountCents: Long,
    val finalAmountCents: Long
)

/**
 * Distributes a manually entered invoice total proportionally across its lines.
 *
 * All inputs and outputs are integer minor currency units. The largest-remainder
 * method guarantees that allocations add up to [finalTotalCents] exactly.
 * Ties are resolved by larger original amount, then lexicographically by stableId,
 * so the result is independent of the order in which lines are supplied.
 *
 * A zero-weight line receives zero. If every line has zero weight, only a zero
 * invoice total is valid because there is no defensible proportional basis.
 */
object SalePricingCalculator {

    private val ZERO = BigInteger.ZERO

    fun allocate(
        lines: List<SalePricingLine>,
        finalTotalCents: Long
    ): List<SalePricingAllocation> {
        require(lines.isNotEmpty()) { "Sale must contain at least one line" }
        require(finalTotalCents >= 0L) { "Final total cannot be negative" }
        require(lines.all { it.stableId.isNotBlank() }) { "Line IDs cannot be blank" }
        require(lines.map { it.stableId }.toSet().size == lines.size) {
            "Line IDs must be unique within a sale"
        }
        require(lines.all { it.listAmountCents >= 0L }) {
            "Original line amounts cannot be negative"
        }

        val totalWeight = lines.fold(ZERO) { sum, line ->
            sum.add(BigInteger.valueOf(line.listAmountCents))
        }
        val target = BigInteger.valueOf(finalTotalCents)

        if (totalWeight == ZERO) {
            require(finalTotalCents == 0L) {
                "A positive invoice total cannot be allocated when all original lines are zero"
            }
            return lines.map {
                SalePricingAllocation(it.stableId, it.listAmountCents, 0L)
            }
        }

        data class Remainder(
            val inputIndex: Int,
            val stableId: String,
            val weight: Long,
            val remainder: BigInteger
        )

        val allocated = LongArray(lines.size)
        val remainders = ArrayList<Remainder>(lines.size)
        var allocatedTotal = ZERO

        lines.forEachIndexed { index, line ->
            val numerator = target.multiply(BigInteger.valueOf(line.listAmountCents))
            val division = numerator.divideAndRemainder(totalWeight)
            val base = division[0].longValueExact()
            allocated[index] = base
            allocatedTotal = allocatedTotal.add(division[0])
            remainders += Remainder(
                inputIndex = index,
                stableId = line.stableId,
                weight = line.listAmountCents,
                remainder = division[1]
            )
        }

        val centsLeft = target.subtract(allocatedTotal).intValueExact()
        val priority = remainders
            .asSequence()
            .filter { it.weight > 0L }
            .sortedWith(
                compareByDescending<Remainder> { it.remainder }
                    .thenByDescending { it.weight }
                    .thenBy { it.stableId }
            )
            .take(centsLeft)
            .toList()

        check(priority.size == centsLeft) {
            "Internal allocation error: unable to distribute remaining cents"
        }
        priority.forEach { allocated[it.inputIndex] = Math.addExact(allocated[it.inputIndex], 1L) }

        val result = lines.mapIndexed { index, line ->
            SalePricingAllocation(
                stableId = line.stableId,
                listAmountCents = line.listAmountCents,
                finalAmountCents = allocated[index]
            )
        }

        val verifiedTotal = result.fold(ZERO) { sum, allocation ->
            sum.add(BigInteger.valueOf(allocation.finalAmountCents))
        }
        check(verifiedTotal == target) { "Internal allocation error: allocated total mismatch" }
        check(result.all { it.finalAmountCents >= 0L }) {
            "Internal allocation error: negative line allocation"
        }

        return result
    }
}
