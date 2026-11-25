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

    @Test
    fun historicalSnapshotsDoNotBlockFactorChangeWhenAllStockIsZero() = runBlocking {
        val itemId = database.itemDao().insert(
            ItemEntity(tradeName = "Historical item", stripsPerBox = 10, createdAt = 1L)
        )
        val supplierId = database.supplierDao().insert(
            SupplierEntity(name = "Supplier", createdAt = 1L)
        )
        val purchaseId = database.purchaseDao().insertInvoice(
            PurchaseInvoiceEntity(
                supplierId = supplierId,
                invoiceDate = 1L,
                requestUuid = "factor-history-purchase",
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
                qtyRemaining = 0L,
                costRemaining = 0L,
                receivedAt = 2L
            )
        )

        assertEquals(
            ChangeStripsPerBoxResult.CHANGED,
            service.changeStripsPerBox(itemId, 12)
        )
        val changes = database.itemDao().changesFor(itemId)
        assertEquals(1, changes.size)
        assertEquals("10", changes.single().oldValue)
        assertEquals("12", changes.single().newValue)
        assertEquals(12, database.itemDao().getById(itemId)?.stripsPerBox)
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
        expectConstraint {
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
        assertEquals(
            ChangeStripsPerBoxResult.CHANGED,
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

    private suspend fun expectConstraint(block: suspend () -> Unit) {
        var thrown = false
        try {
            block()
        } catch (_: SQLiteConstraintException) {
            thrown = true
        }
        assertTrue("Expected SQLiteConstraintException", thrown)
    }
}
