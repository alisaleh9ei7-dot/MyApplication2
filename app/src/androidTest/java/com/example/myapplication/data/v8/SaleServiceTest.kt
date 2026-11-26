package com.example.myapplication.data.v8

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.core.FixedAppClock
import com.example.myapplication.data.v8.db.PharmacyDatabaseV8
import com.example.myapplication.data.v8.entity.BatchEntity
import com.example.myapplication.data.v8.entity.BatchSource
import com.example.myapplication.data.v8.entity.ItemEntity
import com.example.myapplication.data.v8.entity.MovementType
import com.example.myapplication.data.v8.entity.SaleStatus
import com.example.myapplication.data.v8.entity.SalesInvoiceEntity
import com.example.myapplication.data.v8.entity.SalesLineEntity
import com.example.myapplication.data.v8.entity.StockMovementEntity
import com.example.myapplication.data.v8.entity.UnitType
import com.example.myapplication.data.v8.service.SaleConfirmationResult
import com.example.myapplication.data.v8.service.SaleCancellationResult
import com.example.myapplication.data.v8.service.SaleCancellationService
import com.example.myapplication.data.v8.service.SaleService
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleServiceTest {
    private lateinit var database: PharmacyDatabaseV8
    private lateinit var service: SaleService
    private val now = Instant.parse("2026-11-26T10:00:00Z").toEpochMilli()
    private val today = LocalDate.of(2026, 11, 26).toEpochDay()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PharmacyDatabaseV8::class.java
        ).addCallback(PharmacyDatabaseV8.TRIGGERS_CALLBACK).build()
        service = SaleService(database, FixedAppClock(now, TimeZone.getTimeZone("UTC")))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun confirmationPostsAllocationMovementAndBatchDeductionAtomically() = runBlocking {
        val itemId = addItem()
        val batchId = addOpeningBatch(itemId, qty = 10L, cost = 1_000L)
        val saleId = addInvoiceAndLine(
            number = 1,
            itemId = itemId,
            qty = 3L,
            unitPrice = 100L,
            lineDiscount = 0L,
            lineTotal = 300L
        )

        val result = service.confirmPendingSale(saleId)

        assertEquals(SaleConfirmationResult.Confirmed(saleId, 1), result)
        val invoice = database.salesDao().getInvoice(saleId)
        assertEquals(SaleStatus.CONFIRMED, invoice?.status)
        assertEquals(now, invoice?.confirmedAt)

        val allocation = database.salesDao().allocationsFor(saleId).single()
        assertEquals(batchId, allocation.batchId)
        assertEquals(3L, allocation.qtyBase)
        assertEquals(300L, allocation.revenueFrozen)
        assertEquals(300L, allocation.costFrozen)

        val batch = database.batchDao().getById(batchId)
        assertEquals(7L, batch?.qtyRemaining)
        assertEquals(700L, batch?.costRemaining)

        val movement = database.stockMovementDao().forSaleId(saleId).single()
        assertEquals(MovementType.SALE, movement.type)
        assertEquals(-3L, movement.qtyDelta)
        assertEquals(-300L, movement.costDelta)
        assertEquals(invoice?.displayNo, movement.invoiceRef)
        assertEquals(allocation.allocId, movement.allocId)
    }

    @Test
    fun allocationUsesFefoAndLargestRemainderForRevenue() = runBlocking {
        val itemId = addItem()
        val firstBatch = addOpeningBatch(
            itemId, qty = 1L, cost = 100L,
            expiryDate = today + 1, receivedAt = now - 2_000L
        )
        val secondBatch = addOpeningBatch(
            itemId, qty = 3L, cost = 600L,
            expiryDate = today + 2, receivedAt = now - 1_000L
        )
        val saleId = addInvoiceAndLine(
            number = 2,
            itemId = itemId,
            qty = 3L,
            unitPrice = 34L,
            lineDiscount = 2L,
            lineTotal = 100L
        )

        val result = service.confirmPendingSale(saleId)

        assertEquals(SaleConfirmationResult.Confirmed(saleId, 2), result)
        val allocations = database.salesDao().allocationsFor(saleId)
        assertEquals(listOf(firstBatch, secondBatch), allocations.map { it.batchId })
        assertEquals(listOf(1L, 2L), allocations.map { it.qtyBase })
        assertEquals(listOf(33L, 67L), allocations.map { it.revenueFrozen })
        assertEquals(listOf(100L, 400L), allocations.map { it.costFrozen })
        assertEquals(0L, database.batchDao().getById(firstBatch)?.qtyRemaining)
        assertEquals(1L, database.batchDao().getById(secondBatch)?.qtyRemaining)
        assertEquals(200L, database.batchDao().getById(secondBatch)?.costRemaining)
    }

    @Test
    fun insufficientStockLeavesInvoiceBatchesAndLedgerUnchanged() = runBlocking {
        val itemId = addItem()
        val batchId = addOpeningBatch(itemId, qty = 1L, cost = 100L)
        val saleId = addInvoiceAndLine(
            number = 3,
            itemId = itemId,
            qty = 2L,
            unitPrice = 50L,
            lineDiscount = 0L,
            lineTotal = 100L
        )

        val result = service.confirmPendingSale(saleId)

        assertEquals(
            SaleConfirmationResult.InsufficientStock(itemId, 2L, 1L),
            result
        )
        assertEquals(SaleStatus.PENDING, database.salesDao().getInvoice(saleId)?.status)
        assertTrue(database.salesDao().allocationsFor(saleId).isEmpty())
        assertTrue(database.stockMovementDao().forSaleId(saleId).isEmpty())
        assertEquals(1L, database.batchDao().getById(batchId)?.qtyRemaining)
        assertEquals(100L, database.batchDao().getById(batchId)?.costRemaining)
    }

    @Test
    fun unpricedStockCannotBeUsedToConfirmSale() = runBlocking {
        val itemId = addItem()
        val batchId = addOpeningBatch(itemId, qty = 5L, cost = 500L, salePrice = null)
        val saleId = addInvoiceAndLine(
            number = 4,
            itemId = itemId,
            qty = 2L,
            unitPrice = 25L,
            lineDiscount = 0L,
            lineTotal = 50L
        )

        val result = service.confirmPendingSale(saleId)

        assertEquals(SaleConfirmationResult.UnpricedStock(itemId, 2L), result)
        assertEquals(SaleStatus.PENDING, database.salesDao().getInvoice(saleId)?.status)
        assertTrue(database.salesDao().allocationsFor(saleId).isEmpty())
        assertEquals(5L, database.batchDao().getById(batchId)?.qtyRemaining)
    }

    @Test
    fun retryingAnAlreadyConfirmedSaleDoesNotDuplicateMovements() = runBlocking {
        val itemId = addItem()
        addOpeningBatch(itemId, qty = 5L, cost = 500L)
        val saleId = addInvoiceAndLine(
            number = 5,
            itemId = itemId,
            qty = 2L,
            unitPrice = 25L,
            lineDiscount = 0L,
            lineTotal = 50L
        )

        assertEquals(SaleConfirmationResult.Confirmed(saleId, 1), service.confirmPendingSale(saleId))
        assertEquals(SaleConfirmationResult.AlreadyConfirmed, service.confirmPendingSale(saleId))
        assertEquals(1, database.salesDao().allocationsFor(saleId).size)
        assertEquals(1, database.stockMovementDao().forSaleId(saleId).size)
    }

    @Test
    fun databaseFailureAfterFirstLineRollsBackTheWholeConfirmation() = runBlocking {
        val firstItemId = addItem("Test item one")
        val secondItemId = addItem("Test item two")
        val firstBatchId = addOpeningBatch(firstItemId, qty = 5L, cost = 500L)
        val secondBatchId = addOpeningBatch(secondItemId, qty = 5L, cost = 500L)
        val saleId = database.salesDao().insertInvoice(
            SalesInvoiceEntity(
                displayNo = displayNo(7),
                grossTotal = 100L,
                discountTotal = 0L,
                netTotal = 100L,
                cartUuid = "sale-service-cart-atomic-rollback"
            )
        )
        database.salesDao().insertLine(
            SalesLineEntity(
                saleId = saleId, itemId = firstItemId, unitType = UnitType.BOX,
                qty = 1L, stripsPerBoxFrozen = 1, baseQty = 1L,
                unitPrice = 50L, lineDiscount = 0L, lineTotal = 50L
            )
        )
        database.salesDao().insertLine(
            SalesLineEntity(
                saleId = saleId, itemId = secondItemId, unitType = UnitType.BOX,
                qty = 1L, stripsPerBoxFrozen = 1, baseQty = 1L,
                unitPrice = 50L, lineDiscount = 0L, lineTotal = 50L
            )
        )
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER test_abort_second_sale_movement
               BEFORE INSERT ON stock_movements
               WHEN NEW.type = 'SALE' AND NEW.itemId = $secondItemId
               BEGIN SELECT RAISE(ABORT, 'TEST:FORCED_SECOND_LINE_FAILURE'); END"""
        )

        var failed = false
        try {
            service.confirmPendingSale(saleId)
        } catch (_: SQLiteConstraintException) {
            failed = true
        }

        assertTrue("The injected database constraint should abort confirmation", failed)
        assertEquals(SaleStatus.PENDING, database.salesDao().getInvoice(saleId)?.status)
        assertTrue(database.salesDao().allocationsFor(saleId).isEmpty())
        assertTrue(database.stockMovementDao().forSaleId(saleId).isEmpty())
        assertEquals(5L, database.batchDao().getById(firstBatchId)?.qtyRemaining)
        assertEquals(500L, database.batchDao().getById(firstBatchId)?.costRemaining)
        assertEquals(5L, database.batchDao().getById(secondBatchId)?.qtyRemaining)
        assertEquals(500L, database.batchDao().getById(secondBatchId)?.costRemaining)
    }

    @Test
    fun invoiceTotalMismatchIsRejectedBeforeAnyStockMutation() = runBlocking {
        val itemId = addItem()
        val batchId = addOpeningBatch(itemId, qty = 5L, cost = 500L)
        val saleId = database.salesDao().insertInvoice(
            SalesInvoiceEntity(
                displayNo = displayNo(6),
                grossTotal = 999L,
                discountTotal = 0L,
                netTotal = 999L,
                cartUuid = "sale-service-cart-6"
            )
        )
        database.salesDao().insertLine(
            SalesLineEntity(
                saleId = saleId,
                itemId = itemId,
                unitType = UnitType.BOX,
                qty = 2L,
                stripsPerBoxFrozen = 1,
                baseQty = 2L,
                unitPrice = 25L,
                lineDiscount = 0L,
                lineTotal = 50L
            )
        )

        assertEquals(SaleConfirmationResult.InvalidInvoiceTotals, service.confirmPendingSale(saleId))
        assertEquals(SaleStatus.PENDING, database.salesDao().getInvoice(saleId)?.status)
        assertTrue(database.salesDao().allocationsFor(saleId).isEmpty())
        assertEquals(5L, database.batchDao().getById(batchId)?.qtyRemaining)
    }


    @Test
    fun cancellationRestoresBatchAndAppendsExactInverseMovement() = runBlocking {
        val itemId = addItem()
        val batchId = addOpeningBatch(itemId, qty = 10L, cost = 1_000L)
        val saleId = addInvoiceAndLine(
            number = 30, itemId = itemId, qty = 3L,
            unitPrice = 100L, lineDiscount = 0L, lineTotal = 300L
        )
        assertEquals(SaleConfirmationResult.Confirmed(saleId, 1), service.confirmPendingSale(saleId))

        val result = SaleCancellationService(
            database, FixedAppClock(now + 5_000L, TimeZone.getTimeZone("UTC"))
        ).cancelConfirmedSale(saleId, "Customer returned purchase")

        assertEquals(SaleCancellationResult.Cancelled(saleId, 1), result)
        val invoice = database.salesDao().getInvoice(saleId)
        assertEquals(SaleStatus.CANCELLED, invoice?.status)
        assertEquals(now, invoice?.confirmedAt)
        assertEquals(now + 5_000L, invoice?.cancelledAt)
        assertEquals("Customer returned purchase", invoice?.cancelReason)
        assertEquals(10L, database.batchDao().getById(batchId)?.qtyRemaining)
        assertEquals(1_000L, database.batchDao().getById(batchId)?.costRemaining)

        val movements = database.stockMovementDao().forSaleId(saleId)
        val sale = movements.single { it.type == MovementType.SALE }
        val reversal = movements.single { it.type == MovementType.SALE_CANCEL }
        assertEquals(sale.movementId, reversal.reversesMovementId)
        assertEquals(sale.batchId, reversal.batchId)
        assertEquals(sale.itemId, reversal.itemId)
        assertEquals(-sale.qtyDelta, reversal.qtyDelta)
        assertEquals(-sale.costDelta, reversal.costDelta)
        assertEquals(sale.invoiceRef, reversal.invoiceRef)
        assertEquals(sale.saleId, reversal.saleId)
        assertEquals(null, reversal.allocId)
        assertEquals("Customer returned purchase", reversal.reason)
    }

    @Test
    fun cancellationOfPendingInvoiceIsRejectedWithoutMutation() = runBlocking {
        val itemId = addItem()
        val batchId = addOpeningBatch(itemId, qty = 5L, cost = 500L)
        val saleId = addInvoiceAndLine(
            number = 31, itemId = itemId, qty = 2L,
            unitPrice = 50L, lineDiscount = 0L, lineTotal = 100L
        )
        val result = SaleCancellationService(database).cancelConfirmedSale(saleId)
        assertEquals(SaleCancellationResult.NotConfirmed, result)
        assertEquals(SaleStatus.PENDING, database.salesDao().getInvoice(saleId)?.status)
        assertEquals(5L, database.batchDao().getById(batchId)?.qtyRemaining)
        assertTrue(database.stockMovementDao().forSaleId(saleId).isEmpty())
    }

    @Test
    fun failureDuringSecondReversalRollsBackAllRestoresAndReversalRows() = runBlocking {
        val itemId = addItem()
        val firstBatch = addOpeningBatch(
            itemId, qty = 1L, cost = 100L,
            expiryDate = today + 1, receivedAt = now - 2_000L
        )
        val secondBatch = addOpeningBatch(
            itemId, qty = 3L, cost = 600L,
            expiryDate = today + 2, receivedAt = now - 1_000L
        )
        val saleId = addInvoiceAndLine(
            number = 32, itemId = itemId, qty = 3L,
            unitPrice = 100L, lineDiscount = 0L, lineTotal = 300L
        )
        assertEquals(SaleConfirmationResult.Confirmed(saleId, 2), service.confirmPendingSale(saleId))
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER test_abort_second_sale_reversal
               BEFORE INSERT ON stock_movements
               WHEN NEW.type = 'SALE_CANCEL' AND NEW.batchId = $secondBatch
               BEGIN SELECT RAISE(ABORT, 'TEST:FORCED_SECOND_REVERSAL_FAILURE'); END"""
        )

        var failed = false
        try {
            SaleCancellationService(database).cancelConfirmedSale(saleId, "forced failure")
        } catch (_: SQLiteConstraintException) {
            failed = true
        }
        assertTrue("The injected constraint should abort cancellation", failed)
        assertEquals(SaleStatus.CONFIRMED, database.salesDao().getInvoice(saleId)?.status)
        assertEquals(0L, database.batchDao().getById(firstBatch)?.qtyRemaining)
        assertEquals(1L, database.batchDao().getById(secondBatch)?.qtyRemaining)
        assertEquals(0L, database.batchDao().getById(firstBatch)?.costRemaining)
        assertEquals(200L, database.batchDao().getById(secondBatch)?.costRemaining)
        val movements = database.stockMovementDao().forSaleId(saleId)
        assertEquals(2, movements.count { it.type == MovementType.SALE })
        assertEquals(0, movements.count { it.type == MovementType.SALE_CANCEL })
    }

    @Test
    fun retryingCancellationDoesNotRestoreStockTwice() = runBlocking {
        val itemId = addItem()
        val batchId = addOpeningBatch(itemId, qty = 5L, cost = 500L)
        val saleId = addInvoiceAndLine(
            number = 33, itemId = itemId, qty = 2L,
            unitPrice = 50L, lineDiscount = 0L, lineTotal = 100L
        )
        assertEquals(SaleConfirmationResult.Confirmed(saleId, 1), service.confirmPendingSale(saleId))
        val cancellation = SaleCancellationService(database)
        assertEquals(SaleCancellationResult.Cancelled(saleId, 1), cancellation.cancelConfirmedSale(saleId))
        assertEquals(SaleCancellationResult.AlreadyCancelled, cancellation.cancelConfirmedSale(saleId))
        assertEquals(5L, database.batchDao().getById(batchId)?.qtyRemaining)
        assertEquals(500L, database.batchDao().getById(batchId)?.costRemaining)
        assertEquals(1, database.stockMovementDao().forSaleId(saleId).count { it.type == MovementType.SALE_CANCEL })
    }

    private suspend fun addItem(
        tradeName: String = "Test item",
        stripsPerBox: Int? = null
    ): Long = database.itemDao().insert(
            ItemEntity(
                tradeName = tradeName,
                stripsPerBox = stripsPerBox,
                createdAt = now
            )
        )

    private suspend fun addOpeningBatch(
        itemId: Long,
        qty: Long,
        cost: Long,
        expiryDate: Long? = today + 30,
        receivedAt: Long = now - 10_000L,
        salePrice: Long? = 1_000L
    ): Long {
        val factor = database.itemDao().getById(itemId)?.stripsPerBox ?: 1
        val batchId = database.batchDao().insert(
            BatchEntity(
                itemId = itemId,
                baseUnitsPerBox = factor,
                source = BatchSource.OPENING,
                qtyReceived = qty,
                qtyRemaining = qty,
                costRemaining = cost,
                salePrice = salePrice,
                expiryDate = expiryDate,
                receivedAt = receivedAt
            )
        )
        database.stockMovementDao().insert(
            StockMovementEntity(
                movementDate = receivedAt,
                type = MovementType.OPENING,
                batchId = batchId,
                itemId = itemId,
                qtyDelta = qty,
                costDelta = cost
            )
        )
        return batchId
    }

    private suspend fun addInvoiceAndLine(
        number: Int,
        itemId: Long,
        qty: Long,
        unitPrice: Long,
        lineDiscount: Long,
        lineTotal: Long
    ): Long {
        val gross = unitPrice * qty
        val saleId = database.salesDao().insertInvoice(
            SalesInvoiceEntity(
                displayNo = displayNo(number),
                grossTotal = gross,
                discountTotal = lineDiscount,
                netTotal = lineTotal,
                cartUuid = "sale-service-cart-$number"
            )
        )
        database.salesDao().insertLine(
            SalesLineEntity(
                saleId = saleId,
                itemId = itemId,
                unitType = UnitType.BOX,
                qty = qty,
                stripsPerBoxFrozen = 1,
                baseQty = qty,
                unitPrice = unitPrice,
                lineDiscount = lineDiscount,
                lineTotal = lineTotal
            )
        )
        return saleId
    }

    private fun displayNo(number: Int): String =
        "INV-202611-${number.toString().padStart(6, '0')}-D1T0"
}
