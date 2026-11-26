package com.example.myapplication.data.v8

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.v8.db.PharmacyDatabaseV8
import com.example.myapplication.data.v8.entity.BatchEntity
import com.example.myapplication.data.v8.entity.BatchSource
import com.example.myapplication.data.v8.entity.ItemEntity
import com.example.myapplication.data.v8.entity.MovementType
import com.example.myapplication.data.v8.entity.PurchaseInvoiceEntity
import com.example.myapplication.data.v8.entity.PurchaseLineEntity
import com.example.myapplication.data.v8.entity.SalesInvoiceEntity
import com.example.myapplication.data.v8.entity.SalesLineEntity
import com.example.myapplication.data.v8.entity.StockMovementEntity
import com.example.myapplication.data.v8.entity.SupplierEntity
import com.example.myapplication.data.v8.entity.UnitType
import com.example.myapplication.data.v8.service.ChangeStripsPerBoxResult
import com.example.myapplication.data.v8.service.ItemService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemD2IntegrityTest {
    private lateinit var context: Context
    private lateinit var database: PharmacyDatabaseV8
    private lateinit var service: ItemService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PharmacyDatabaseV8::class.java
        ).addCallback(PharmacyDatabaseV8.TRIGGERS_CALLBACK).build()
        service = ItemService(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---- Factor lock by history (V9 fix 1) -------------------------------------------------

    private suspend fun newItem(name: String): Long = database.itemDao().insert(
        ItemEntity(tradeName = name, stripsPerBox = 10, createdAt = 1L)
    )

    /** Purchase invoice + one line for the item; no batch is created. */
    private suspend fun addPurchaseLine(itemId: Long, tag: String): Long {
        val supplierId = database.supplierDao().insert(
            SupplierEntity(name = "Supplier $tag", createdAt = 1L)
        )
        val purchaseId = database.purchaseDao().insertInvoice(
            PurchaseInvoiceEntity(
                supplierId = supplierId,
                invoiceDate = 1L,
                requestUuid = "factor-lock-$tag",
                createdAt = 1L
            )
        )
        return database.purchaseDao().insertLine(
            PurchaseLineEntity(
                purchaseId = purchaseId,
                itemId = itemId,
                baseUnitsPerBox = 10,
                paidQty = 10L,
                lineTotal = 100L
            )
        )
    }

    /**
     * The factor must be refused at every layer, with no side effects:
     * Service result, DAO update count, SQLite trigger (direct SQL), unchanged factor,
     * and no item_changes row.
     */
    private suspend fun assertFactorLockedByHistory(itemId: Long) {
        assertEquals(
            ChangeStripsPerBoxResult.LOCKED_BY_HISTORY,
            service.changeStripsPerBox(itemId, 12)
        )
        assertEquals(0, database.itemDao().updateStripsPerBoxWhenNoHistory(itemId, 12))
        expectConstraint("PHARMACY_V8:E_ITEM_FACTOR_LOCKED_BY_HISTORY") {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE items SET stripsPerBox = 12 WHERE itemId = ?",
                arrayOf<Any?>(itemId)
            )
        }
        assertEquals(10, database.itemDao().getById(itemId)?.stripsPerBox)
        assertTrue(database.itemDao().changesFor(itemId).isEmpty())
    }

    @Test
    fun newItemWithoutHistoryCanChangeFactor() = runBlocking {
        val itemId = newItem("Brand new item")

        assertEquals(ChangeStripsPerBoxResult.CHANGED, service.changeStripsPerBox(itemId, 12))
        assertEquals(12, database.itemDao().getById(itemId)?.stripsPerBox)
        val first = database.itemDao().changesFor(itemId).single()
        assertEquals("10", first.oldValue)
        assertEquals("12", first.newValue)

        // Still no history, so a second change is allowed too.
        assertEquals(ChangeStripsPerBoxResult.CHANGED, service.changeStripsPerBox(itemId, 15))
        assertEquals(15, database.itemDao().getById(itemId)?.stripsPerBox)
        assertEquals(2, database.itemDao().changesFor(itemId).size)
    }

    @Test
    fun zeroQuantityBatchLocksFactorByHistory() = runBlocking {
        val itemId = newItem("Zero batch item")
        // OPENING batch with zero remaining quantity and no purchase line or movement,
        // so the batch itself is the only historical record.
        database.batchDao().insert(
            BatchEntity(
                itemId = itemId,
                baseUnitsPerBox = 10,
                source = BatchSource.OPENING,
                qtyReceived = 10L,
                qtyRemaining = 0L,
                costRemaining = 0L,
                receivedAt = 2L
            )
        )
        assertFactorLockedByHistory(itemId)
    }

    @Test
    fun purchaseLineLocksFactorByHistoryEvenWithZeroStock() = runBlocking {
        val itemId = newItem("Purchased item")
        addPurchaseLine(itemId, "purchase-only")
        assertFactorLockedByHistory(itemId)
    }

    @Test
    fun purchaseBatchWithZeroStockLocksFactorByHistory() = runBlocking {
        val itemId = newItem("Sold-out purchased item")
        val purchaseLineId = addPurchaseLine(itemId, "sold-out")
        database.batchDao().insert(
            BatchEntity(
                itemId = itemId,
                baseUnitsPerBox = 10,
                source = BatchSource.PURCHASE,
                purchaseLineId = purchaseLineId,
                qtyReceived = 10L,
                qtyRemaining = 0L,
                costRemaining = 0L,
                receivedAt = 2L
            )
        )
        assertFactorLockedByHistory(itemId)
    }

    @Test
    fun salesLineLocksFactorByHistory() = runBlocking {
        val itemId = newItem("Sold item")
        val saleId = database.salesDao().insertInvoice(
            SalesInvoiceEntity(
                displayNo = "INV-202611-000002",
                grossTotal = 200L,
                netTotal = 200L,
                cartUuid = "factor-lock-sale"
            )
        )
        database.salesDao().insertLine(
            SalesLineEntity(
                saleId = saleId,
                itemId = itemId,
                unitType = UnitType.BOX,
                qty = 2L,
                stripsPerBoxFrozen = 10,
                baseQty = 20L,
                unitPrice = 100L,
                lineTotal = 200L
            )
        )
        assertFactorLockedByHistory(itemId)
    }

    @Test
    fun stockMovementLocksFactorByHistory() = runBlocking {
        val itemId = newItem("Movement item")
        // PURCHASE movement without a batch: the movement is the only historical record.
        database.stockMovementDao().insert(
            StockMovementEntity(
                movementDate = 2L,
                type = MovementType.PURCHASE,
                batchId = null,
                itemId = itemId,
                qtyDelta = 10L,
                costDelta = 100L
            )
        )
        assertFactorLockedByHistory(itemId)
    }

    @Test
    fun rejectedHistoricalChangeIsNotRetriedIntoAuditLog() = runBlocking {
        val itemId = newItem("Audit item")
        addPurchaseLine(itemId, "audit")
        repeat(2) {
            assertEquals(
                ChangeStripsPerBoxResult.LOCKED_BY_HISTORY,
                service.changeStripsPerBox(itemId, 12)
            )
        }
        assertTrue(database.itemDao().changesFor(itemId).isEmpty())
        assertEquals(10, database.itemDao().getById(itemId)?.stripsPerBox)
        // Same value is still reported as UNCHANGED, not as a lock error.
        assertEquals(ChangeStripsPerBoxResult.UNCHANGED, service.changeStripsPerBox(itemId, 10))
    }

    @Test
    fun nonZeroStockBlocksFactorChangeAtServiceAndSqliteLevels() = runBlocking {
        val itemId = database.itemDao().insert(
            ItemEntity(tradeName = "Stocked item", stripsPerBox = 10, createdAt = 1L)
        )
        val supplierId = database.supplierDao().insert(
            SupplierEntity(name = "Stock supplier", createdAt = 1L)
        )
        val purchaseId = database.purchaseDao().insertInvoice(
            PurchaseInvoiceEntity(
                supplierId = supplierId,
                invoiceDate = 1L,
                requestUuid = "factor-stock-purchase",
                createdAt = 1L
            )
        )
        val purchaseLineId = database.purchaseDao().insertLine(
            PurchaseLineEntity(
                purchaseId = purchaseId,
                itemId = itemId,
                baseUnitsPerBox = 10,
                paidQty = 10L,
                lineTotal = 100L
            )
        )
        database.batchDao().insert(
            BatchEntity(
                itemId = itemId,
                baseUnitsPerBox = 10,
                source = BatchSource.PURCHASE,
                purchaseLineId = purchaseLineId,
                qtyReceived = 10L,
                qtyRemaining = 5L,
                costRemaining = 50L,
                receivedAt = 2L
            )
        )

        assertEquals(
            ChangeStripsPerBoxResult.LOCKED_BY_STOCK,
            service.changeStripsPerBox(itemId, 12)
        )
        expectConstraint("PHARMACY_V8:E_ITEM_FACTOR_LOCKED_BY_STOCK") {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE items SET stripsPerBox = 12 WHERE itemId = ?",
                arrayOf<Any?>(itemId)
            )
        }
        assertEquals(10, database.itemDao().getById(itemId)?.stripsPerBox)
        assertTrue(database.itemDao().changesFor(itemId).isEmpty())
    }

    @Test
    fun salesLineStoresAndValidatesFrozenBaseQuantity() = runBlocking {
        val itemId = database.itemDao().insert(
            ItemEntity(tradeName = "Sale item", stripsPerBox = 10, createdAt = 1L)
        )
        val saleId = database.salesDao().insertInvoice(
            SalesInvoiceEntity(
                displayNo = "INV-202611-000001",
                grossTotal = 200L,
                netTotal = 200L,
                cartUuid = "frozen-sale-line"
            )
        )
        database.salesDao().insertLine(
            SalesLineEntity(
                saleId = saleId,
                itemId = itemId,
                unitType = UnitType.BOX,
                qty = 2L,
                stripsPerBoxFrozen = 10,
                baseQty = 20L,
                unitPrice = 100L,
                lineTotal = 200L
            )
        )
        expectConstraint {
            database.openHelper.writableDatabase.execSQL(
                """INSERT INTO sales_lines
                   (saleId, itemId, unitType, qty, stripsPerBoxFrozen, baseQty,
                    unitPrice, lineDiscount, lineTotal)
                   VALUES (?, ?, 'BOX', 2, 10, 19, 100, 0, 200)""",
                arrayOf<Any?>(saleId, itemId)
            )
        }
        // A sales line now exists, so the factor is permanently locked (V9 fix 1).
        assertEquals(
            ChangeStripsPerBoxResult.LOCKED_BY_HISTORY,
            service.changeStripsPerBox(itemId, 12)
        )
    }

    @Test
    fun openingMovementMustBePositiveUniqueAndUnlinked() = runBlocking {
        val itemId = database.itemDao().insert(
            ItemEntity(tradeName = "Opening item", stripsPerBox = 10, createdAt = 1L)
        )
        val batchId = database.batchDao().insert(
            BatchEntity(
                itemId = itemId,
                baseUnitsPerBox = 10,
                source = BatchSource.OPENING,
                qtyReceived = 10L,
                qtyRemaining = 10L,
                costRemaining = 100L,
                costIsEstimated = true,
                receivedAt = 2L
            )
        )
        database.stockMovementDao().insert(
            StockMovementEntity(
                movementDate = 2L,
                type = MovementType.OPENING,
                batchId = batchId,
                itemId = itemId,
                qtyDelta = 10L,
                costDelta = 100L
            )
        )
        expectConstraint {
            database.stockMovementDao().insert(
                StockMovementEntity(
                    movementDate = 3L,
                    type = MovementType.OPENING,
                    batchId = batchId,
                    itemId = itemId,
                    qtyDelta = 1L
                )
            )
        }
        expectConstraint {
            database.stockMovementDao().insert(
                StockMovementEntity(
                    movementDate = 3L,
                    type = MovementType.SUPPLIER_RETURN_DIFFERENCE,
                    batchId = batchId,
                    itemId = itemId,
                    qtyDelta = 1L
                )
            )
        }
        expectConstraint {
            database.stockMovementDao().insert(
                StockMovementEntity(
                    movementDate = 3L,
                    type = MovementType.OPENING,
                    batchId = batchId,
                    itemId = itemId,
                    qtyDelta = -1L
                )
            )
        }
    }

    @Test
    fun invalidFactorsAndUnsupportedBatchSourcesAreRejected() = runBlocking {
        val itemId = database.itemDao().insert(
            ItemEntity(tradeName = "Validation item", createdAt = 1L)
        )
        assertEquals(
            ChangeStripsPerBoxResult.INVALID_VALUE,
            service.changeStripsPerBox(itemId, 1)
        )
        expectConstraint {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE items SET stripsPerBox = 1 WHERE itemId = ?",
                arrayOf<Any?>(itemId)
            )
        }
        expectConstraint {
            database.openHelper.writableDatabase.execSQL(
                """INSERT INTO batches
                   (itemId, baseUnitsPerBox, source, qtyReceived, qtyRemaining,
                    qtySupplierReturned, costRemaining, costIsEstimated, receivedAt, isSellable)
                   VALUES (?, 1, 'STOCK_IN', 1, 1, 0, 1, 0, 1, 1)""",
                arrayOf<Any?>(itemId)
            )
        }
    }

    private suspend fun expectConstraint(
        expectedToken: String? = null,
        block: suspend () -> Unit
    ) {
        var thrown = false
        try {
            block()
        } catch (e: SQLiteConstraintException) {
            thrown = true
            if (expectedToken != null) {
                assertTrue(
                    "Expected $expectedToken but was: ${e.message}",
                    e.message?.contains(expectedToken) == true
                )
            }
        }
        assertTrue("Expected SQLiteConstraintException", thrown)
    }
}
