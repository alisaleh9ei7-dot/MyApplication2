package com.example.myapplication.data.v8.service

import androidx.room.withTransaction
import com.example.myapplication.core.AppClock
import com.example.myapplication.core.SystemAppClock
import com.example.myapplication.data.v8.db.PharmacyDatabaseV8
import com.example.myapplication.data.v8.entity.BatchEntity
import com.example.myapplication.data.v8.entity.MovementType
import com.example.myapplication.data.v8.entity.SaleAllocationEntity
import com.example.myapplication.data.v8.entity.SaleStatus
import com.example.myapplication.data.v8.entity.SalesInvoiceEntity
import com.example.myapplication.data.v8.entity.SalesLineEntity
import com.example.myapplication.data.v8.entity.StockMovementEntity
import com.example.myapplication.data.v8.entity.UnitType
import java.math.BigInteger
import java.time.Instant

/** Result of confirming a pending sale. Rejections never leave partial stock or ledger changes. */
sealed interface SaleConfirmationResult {
    data class Confirmed(val saleId: Long, val allocationCount: Int) : SaleConfirmationResult
    data object InvoiceNotFound : SaleConfirmationResult
    data object AlreadyConfirmed : SaleConfirmationResult
    data object AlreadyCancelled : SaleConfirmationResult
    data object EmptyInvoice : SaleConfirmationResult
    data object InvalidInvoiceTotals : SaleConfirmationResult
    data object InvalidSaleLine : SaleConfirmationResult
    data class ItemUnavailable(val itemId: Long) : SaleConfirmationResult
    data class ItemFactorMismatch(val itemId: Long) : SaleConfirmationResult
    data class BatchFactorMismatch(val itemId: Long, val batchId: Long) : SaleConfirmationResult
    data class UnpricedStock(val itemId: Long, val requestedBaseQty: Long) : SaleConfirmationResult
    data class InsufficientStock(
        val itemId: Long,
        val requestedBaseQty: Long,
        val availableBaseQty: Long
    ) : SaleConfirmationResult
}

/**
 * Confirms an existing PENDING invoice and posts all its stock effects atomically.
 *
 * SalesLineEntity is the authoritative frozen selling-price snapshot. A batch with a null
 * salePrice is not eligible for allocation; non-null batch prices are not re-applied here.
 * The cart/line creation flow is responsible for freezing the applicable unit price.
 */
class SaleService(
    private val database: PharmacyDatabaseV8,
    private val clock: AppClock = SystemAppClock
) {
    suspend fun confirmPendingSale(saleId: Long): SaleConfirmationResult {
        return try {
            database.withTransaction {
                val salesDao = database.salesDao()
                val batchDao = database.batchDao()
                val itemDao = database.itemDao()
                val movementDao = database.stockMovementDao()

                val invoice = salesDao.getInvoice(saleId)
                    ?: return@withTransaction SaleConfirmationResult.InvoiceNotFound
                when (invoice.status) {
                    SaleStatus.CONFIRMED -> return@withTransaction SaleConfirmationResult.AlreadyConfirmed
                    SaleStatus.CANCELLED -> return@withTransaction SaleConfirmationResult.AlreadyCancelled
                    SaleStatus.PENDING -> Unit
                }

                val lines = salesDao.linesFor(saleId).sortedBy { it.lineId }
                if (lines.isEmpty()) return@withTransaction SaleConfirmationResult.EmptyInvoice

                val totalsValid = validateInvoiceTotals(invoice, lines)
                if (!totalsValid) return@withTransaction SaleConfirmationResult.InvalidInvoiceTotals

                val now = clock.nowMillis()
                if (now < 0L) return@withTransaction SaleConfirmationResult.InvalidSaleLine
                val todayEpochDay = Instant.ofEpochMilli(now)
                    .atZone(clock.timeZone().toZoneId())
                    .toLocalDate()
                    .toEpochDay()

                // Build the complete allocation plan before writing anything. Virtual balances
                // account for multiple lines that consume the same batch in this invoice.
                val virtualBalances = mutableMapOf<Long, VirtualBatchBalance>()
                val planned = mutableListOf<PlannedAllocation>()

                for (line in lines) {
                    if (!isValidLine(line)) {
                        return@withTransaction SaleConfirmationResult.InvalidSaleLine
                    }

                    val item = itemDao.getById(line.itemId)
                        ?: return@withTransaction SaleConfirmationResult.ItemUnavailable(line.itemId)
                    if (!item.isActive) {
                        return@withTransaction SaleConfirmationResult.ItemUnavailable(line.itemId)
                    }
                    val currentFactor = item.stripsPerBox ?: 1
                    if (currentFactor != line.stripsPerBoxFrozen) {
                        return@withTransaction SaleConfirmationResult.ItemFactorMismatch(line.itemId)
                    }

                    val eligibleBatches = batchDao.sellableFefo(line.itemId, todayEpochDay)
                    if (eligibleBatches.any { it.baseUnitsPerBox != line.stripsPerBoxFrozen }) {
                        val mismatched = eligibleBatches.first { it.baseUnitsPerBox != line.stripsPerBoxFrozen }
                        return@withTransaction SaleConfirmationResult.BatchFactorMismatch(
                            line.itemId,
                            mismatched.batchId
                        )
                    }

                    val pricedBatches = eligibleBatches.filter { it.salePrice != null }
                    val pricedAvailable = sumAvailable(pricedBatches, virtualBalances)
                    if (pricedAvailable < line.baseQty) {
                        val allAvailable = sumAvailable(eligibleBatches, virtualBalances)
                        return@withTransaction if (allAvailable >= line.baseQty) {
                            SaleConfirmationResult.UnpricedStock(line.itemId, line.baseQty)
                        } else {
                            SaleConfirmationResult.InsufficientStock(
                                line.itemId,
                                line.baseQty,
                                allAvailable
                            )
                        }
                    }

                    var remaining = line.baseQty
                    val lineTakes = mutableListOf<LineTake>()
                    for (batch in pricedBatches) {
                        if (remaining == 0L) break
                        val balance = virtualBalances.getOrPut(batch.batchId) {
                            VirtualBatchBalance(batch.qtyRemaining, batch.costRemaining)
                        }
                        if (balance.qtyRemaining <= 0L) continue
                        val takeQty = minOf(remaining, balance.qtyRemaining)
                        val costOut = proportionalCostOut(
                            balance.costRemaining,
                            balance.qtyRemaining,
                            takeQty
                        )
                        lineTakes += LineTake(batch, takeQty, costOut)
                        balance.qtyRemaining -= takeQty
                        balance.costRemaining -= costOut
                        remaining -= takeQty
                    }
                    check(remaining == 0L) { "Sale allocation planning mismatch" }

                    val revenueShares = allocateRevenue(line.lineTotal, line.baseQty, lineTakes)
                    lineTakes.forEachIndexed { index, take ->
                        planned += PlannedAllocation(
                            lineId = line.lineId,
                            itemId = line.itemId,
                            batch = take.batch,
                            qtyBase = take.qty,
                            revenueFrozen = revenueShares[index],
                            costFrozen = take.cost
                        )
                    }
                }

                // Persist allocation, batch balance and immutable movement together.
                planned.forEach { allocation ->
                    val updated = batchDao.deductIfAvailable(
                        allocation.batch.batchId,
                        allocation.qtyBase,
                        allocation.costFrozen
                    )
                    if (updated != 1) {
                        throw SaleConfirmationAbort(
                            SaleConfirmationResult.InsufficientStock(
                                allocation.itemId,
                                allocation.qtyBase,
                                0L
                            )
                        )
                    }

                    val allocId = salesDao.insertAllocation(
                        SaleAllocationEntity(
                            lineId = allocation.lineId,
                            batchId = allocation.batch.batchId,
                            qtyBase = allocation.qtyBase,
                            revenueFrozen = allocation.revenueFrozen,
                            costFrozen = allocation.costFrozen
                        )
                    )
                    movementDao.insert(
                        StockMovementEntity(
                            movementDate = now,
                            type = MovementType.SALE,
                            batchId = allocation.batch.batchId,
                            itemId = allocation.itemId,
                            qtyDelta = -allocation.qtyBase,
                            costDelta = -allocation.costFrozen,
                            invoiceRef = invoice.displayNo,
                            saleId = invoice.saleId,
                            allocId = allocId
                        )
                    )
                }

                if (salesDao.confirmPending(saleId, now) != 1) {
                    throw SaleConfirmationAbort(SaleConfirmationResult.AlreadyConfirmed)
                }
                SaleConfirmationResult.Confirmed(saleId, planned.size)
            }
        } catch (abort: SaleConfirmationAbort) {
            abort.result
        }
    }

    private fun validateInvoiceTotals(
        invoice: SalesInvoiceEntity,
        lines: List<SalesLineEntity>
    ): Boolean {
        if (invoice.grossTotal < 0L || invoice.discountTotal < 0L || invoice.netTotal < 0L) return false
        var gross = BigInteger.ZERO
        var discount = BigInteger.ZERO
        var net = BigInteger.ZERO
        for (line in lines) {
            if (!isValidLine(line)) return false
            gross = gross.add(BigInteger.valueOf(line.unitPrice).multiply(BigInteger.valueOf(line.qty)))
            discount = discount.add(BigInteger.valueOf(line.lineDiscount))
            net = net.add(BigInteger.valueOf(line.lineTotal))
        }
        return gross == BigInteger.valueOf(invoice.grossTotal) &&
            discount == BigInteger.valueOf(invoice.discountTotal) &&
            net == BigInteger.valueOf(invoice.netTotal)
    }

    private fun isValidLine(line: SalesLineEntity): Boolean {
        if (line.qty <= 0L || line.baseQty <= 0L || line.unitPrice < 0L ||
            line.lineDiscount < 0L || line.lineTotal < 0L || line.stripsPerBoxFrozen < 1
        ) return false
        val gross = BigInteger.valueOf(line.unitPrice).multiply(BigInteger.valueOf(line.qty))
        val discount = BigInteger.valueOf(line.lineDiscount)
        val net = gross.subtract(discount)
        if (net.signum() < 0 || net != BigInteger.valueOf(line.lineTotal)) return false
        return when (line.unitType) {
            UnitType.BOX -> BigInteger.valueOf(line.qty)
                .multiply(BigInteger.valueOf(line.stripsPerBoxFrozen.toLong())) == BigInteger.valueOf(line.baseQty)
            UnitType.STRIP -> line.stripsPerBoxFrozen >= 2 && line.baseQty == line.qty
        }
    }

    private fun sumAvailable(
        batches: List<BatchEntity>,
        balances: Map<Long, VirtualBatchBalance>
    ): Long {
        val total = batches.fold(BigInteger.ZERO) { acc, batch ->
            val qty = balances[batch.batchId]?.qtyRemaining ?: batch.qtyRemaining
            acc.add(BigInteger.valueOf(qty.coerceAtLeast(0L)))
        }
        return total.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact()
    }

    private fun proportionalCostOut(costRemaining: Long, qtyRemaining: Long, takeQty: Long): Long {
        require(costRemaining >= 0L && qtyRemaining > 0L && takeQty in 1..qtyRemaining)
        if (takeQty == qtyRemaining) return costRemaining
        return BigInteger.valueOf(costRemaining)
            .multiply(BigInteger.valueOf(takeQty))
            .divide(BigInteger.valueOf(qtyRemaining))
            .longValueExact()
    }

    /** Largest-remainder split; ties follow FEFO order to keep the result deterministic. */
    private fun allocateRevenue(total: Long, totalQty: Long, takes: List<LineTake>): List<Long> {
        require(total >= 0L && totalQty > 0L && takes.isNotEmpty())
        data class Share(val index: Int, val floor: Long, val remainder: BigInteger)

        val denominator = BigInteger.valueOf(totalQty)
        val shares = takes.mapIndexed { index, take ->
            val division = BigInteger.valueOf(total)
                .multiply(BigInteger.valueOf(take.qty))
                .divideAndRemainder(denominator)
            Share(index, division[0].longValueExact(), division[1])
        }
        val result = shares.map { it.floor }.toMutableList()
        val assigned = result.fold(BigInteger.ZERO) { acc, value -> acc.add(BigInteger.valueOf(value)) }
        val remaining = BigInteger.valueOf(total).subtract(assigned).intValueExact()
        shares.sortedWith(
            compareByDescending<Share> { it.remainder }.thenBy { it.index }
        ).take(remaining).forEach { share ->
            result[share.index] = Math.addExact(result[share.index], 1L)
        }
        return result
    }

    private data class VirtualBatchBalance(var qtyRemaining: Long, var costRemaining: Long)
    private data class LineTake(val batch: BatchEntity, val qty: Long, val cost: Long)
    private data class PlannedAllocation(
        val lineId: Long,
        val itemId: Long,
        val batch: BatchEntity,
        val qtyBase: Long,
        val revenueFrozen: Long,
        val costFrozen: Long
    )

    private class SaleConfirmationAbort(val result: SaleConfirmationResult) :
        RuntimeException(null, null, false, false)
}
