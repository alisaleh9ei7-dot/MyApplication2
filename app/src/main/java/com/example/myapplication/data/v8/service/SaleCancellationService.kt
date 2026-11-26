package com.example.myapplication.data.v8.service

import androidx.room.withTransaction
import com.example.myapplication.core.AppClock
import com.example.myapplication.core.SystemAppClock
import com.example.myapplication.data.v8.db.PharmacyDatabaseV8
import com.example.myapplication.data.v8.entity.MovementType
import com.example.myapplication.data.v8.entity.SaleStatus
import com.example.myapplication.data.v8.entity.StockMovementEntity

/** نتيجة إلغاء فاتورة بيع مؤكدة. الرفض لا يترك أي تغيير جزئي. */
sealed interface SaleCancellationResult {
    data class Cancelled(val saleId: Long, val reversalCount: Int) : SaleCancellationResult
    data object InvoiceNotFound : SaleCancellationResult
    data object NotConfirmed : SaleCancellationResult
    data object AlreadyCancelled : SaleCancellationResult
    data object InvalidSaleLedger : SaleCancellationResult
    data object InvalidCancellationTime : SaleCancellationResult
}

/**
 * يلغي فاتورة CONFIRMED داخل معاملة واحدة:
 * يستعيد أرصدة الدفعات، ويضيف حركة عكسية لكل SALE، ثم يغيّر حالة الفاتورة أخيراً.
 * لا يعدّل أو يحذف أي حركة أصلية.
 */
class SaleCancellationService(
    private val database: PharmacyDatabaseV8,
    private val clock: AppClock = SystemAppClock
) {
    suspend fun cancelConfirmedSale(
        saleId: Long,
        cancelReason: String? = null
    ): SaleCancellationResult {
        return database.withTransaction {
            val salesDao = database.salesDao()
            val batchDao = database.batchDao()
            val movementDao = database.stockMovementDao()

            val invoice = salesDao.getInvoice(saleId)
                ?: return@withTransaction SaleCancellationResult.InvoiceNotFound
            when (invoice.status) {
                SaleStatus.PENDING -> return@withTransaction SaleCancellationResult.NotConfirmed
                SaleStatus.CANCELLED -> return@withTransaction SaleCancellationResult.AlreadyCancelled
                SaleStatus.CONFIRMED -> Unit
            }

            val now = clock.nowMillis()
            val confirmedAt = invoice.confirmedAt
            if (now < 0L || confirmedAt == null || now < confirmedAt) {
                return@withTransaction SaleCancellationResult.InvalidCancellationTime
            }

            val saleMovements = movementDao.forSaleId(saleId)
                .filter { it.type == MovementType.SALE }
            val existingReversals = movementDao.forSaleId(saleId)
                .filter { it.type == MovementType.SALE_CANCEL }
            val allocations = salesDao.allocationsFor(saleId)

            // لا نقبل فاتورة مؤكدة بلا دفتر بيع، أو دفترًا ناقص/مكرر الربط.
            if (saleMovements.isEmpty() || existingReversals.isNotEmpty() ||
                saleMovements.size != allocations.size
            ) return@withTransaction SaleCancellationResult.InvalidSaleLedger

            val allocationsById = allocations.associateBy { it.allocId }
            if (allocationsById.size != allocations.size) {
                return@withTransaction SaleCancellationResult.InvalidSaleLedger
            }

            val restorePlan = saleMovements.map { movement ->
                val allocId = movement.allocId
                    ?: return@withTransaction SaleCancellationResult.InvalidSaleLedger
                val allocation = allocationsById[allocId]
                    ?: return@withTransaction SaleCancellationResult.InvalidSaleLedger
                val batchId = movement.batchId
                    ?: return@withTransaction SaleCancellationResult.InvalidSaleLedger

                if (movement.saleId != invoice.saleId ||
                    movement.invoiceRef != invoice.displayNo ||
                    movement.reversesMovementId != null ||
                    movement.qtyDelta >= 0L || movement.qtyDelta == Long.MIN_VALUE ||
                    movement.costDelta > 0L || movement.costDelta == Long.MIN_VALUE ||
                    allocation.batchId != batchId ||
                    allocation.qtyBase <= 0L ||
                    allocation.qtyBase != -movement.qtyDelta ||
                    allocation.costFrozen < 0L ||
                    allocation.costFrozen != -movement.costDelta
                ) return@withTransaction SaleCancellationResult.InvalidSaleLedger

                val batch = batchDao.getById(batchId)
                    ?: return@withTransaction SaleCancellationResult.InvalidSaleLedger
                if (batch.itemId != movement.itemId) {
                    return@withTransaction SaleCancellationResult.InvalidSaleLedger
                }
                RestoreEntry(movement, allocation.qtyBase, allocation.costFrozen)
            }

            // كل تخصيص يجب أن يقابله SALE واحد بالضبط.
            if (restorePlan.mapNotNull { it.movement.allocId }.toSet() != allocationsById.keys) {
                return@withTransaction SaleCancellationResult.InvalidSaleLedger
            }

            for (entry in restorePlan) {
                val original = entry.movement
                val batchId = original.batchId!!
                if (batchDao.restore(batchId, entry.qty, entry.cost) != 1) {
                    error("PHARMACY_V8:E_SALE_CANCEL_BATCH_RESTORE_FAILED")
                }
                movementDao.insert(
                    StockMovementEntity(
                        movementDate = now,
                        type = MovementType.SALE_CANCEL,
                        batchId = batchId,
                        itemId = original.itemId,
                        qtyDelta = entry.qty,
                        costDelta = entry.cost,
                        invoiceRef = invoice.displayNo,
                        saleId = invoice.saleId,
                        allocId = null,
                        reversesMovementId = original.movementId,
                        reason = cancelReason
                    )
                )
            }

            // هذا آخر تغيير: Trigger قاعدة البيانات يتحقق من اكتمال كل العكوسات.
            if (salesDao.markCancelled(saleId, now, cancelReason) != 1) {
                error("PHARMACY_V8:E_SALE_CANCEL_STATE_UPDATE_FAILED")
            }
            SaleCancellationResult.Cancelled(saleId, restorePlan.size)
        }
    }

    private data class RestoreEntry(
        val movement: StockMovementEntity,
        val qty: Long,
        val cost: Long
    )
}
