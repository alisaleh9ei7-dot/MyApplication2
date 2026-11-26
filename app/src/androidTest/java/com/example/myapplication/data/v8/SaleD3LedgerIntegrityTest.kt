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
import com.example.myapplication.data.v8.entity.SaleAllocationEntity
import com.example.myapplication.data.v8.entity.SalesInvoiceEntity
import com.example.myapplication.data.v8.entity.SalesLineEntity
import com.example.myapplication.data.v8.entity.StockMovementEntity
import com.example.myapplication.data.v8.entity.UnitType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleD3LedgerIntegrityTest {
    private lateinit var database: PharmacyDatabaseV8

    private data class SaleFixture(
        val itemId: Long,
        val batchId: Long,
        val saleId: Long,
        val lineId: Long,
        val allocId: Long,
        val displayNo: String
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PharmacyDatabaseV8::class.java)
            .addCallback(PharmacyDatabaseV8.TRIGGERS_CALLBACK)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun expectConstraint(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: SQLiteConstraintException) {
            rejected = true
        }
        assertTrue("Expected D3 ledger trigger to reject invalid data", rejected)
    }

    private suspend fun createItem(name: String): Long = database.itemDao().insert(
        ItemEntity(tradeName = name, createdAt = 100L)
    )

    private suspend fun createBatch(itemId: Long, suffix: Int = 1): Long {
        val batchId = database.batchDao().insert(
            BatchEntity(
                itemId = itemId,
                baseUnitsPerBox = 1,
                source = BatchSource.OPENING,
                qtyReceived = 20L,
                qtyRemaining = 20L,
                costRemaining = 2_000L,
                receivedAt = 100L + suffix
            )
        )
        database.stockMovementDao().insert(
            StockMovementEntity(
                movementDate = 100L + suffix,
                type = MovementType.OPENING,
                batchId = batchId,
                itemId = itemId,
                qtyDelta = 20L,
                costDelta = 2_000L
            )
        )
        return batchId
    }

    private suspend fun createFixture(number: Int = 1): SaleFixture {
        val itemId = createItem("D3 item $number")
        val batchId = createBatch(itemId, number)
        val displayNo = "INV-202611-${number.toString().padStart(6, '0')}-D3A0"
        val saleId = database.salesDao().insertInvoice(
            SalesInvoiceEntity(
                displayNo = displayNo,
                grossTotal = 100L,
                netTotal = 100L,
                cartUuid = "d3-cart-$number"
            )
        )
        val lineId = database.salesDao().insertLine(
            SalesLineEntity(
                saleId = saleId,
                itemId = itemId,
                unitType = UnitType.BOX,
                qty = 10L,
                stripsPerBoxFrozen = 1,
                baseQty = 10L,
                unitPrice = 10L,
                lineTotal = 100L
            )
        )
        val allocId = database.salesDao().insertAllocation(
            SaleAllocationEntity(
                lineId = lineId,
                batchId = batchId,
                qtyBase = 10L,
                revenueFrozen = 100L,
                costFrozen = 1_000L
            )
        )
        return SaleFixture(itemId, batchId, saleId, lineId, allocId, displayNo)
    }

    private suspend fun insertMatchingSaleMovement(f: SaleFixture) {
        database.stockMovementDao().insert(
            StockMovementEntity(
                movementDate = 200L,
                type = MovementType.SALE,
                batchId = f.batchId,
                itemId = f.itemId,
                qtyDelta = -10L,
                costDelta = -1_000L,
                invoiceRef = f.displayNo,
                saleId = f.saleId,
                allocId = f.allocId
            )
        )
    }

    @Test
    fun allocationMustReferenceBatchOfSameItemAndMatchingFrozenFactor() = runBlocking {
        val itemId = createItem("D3 allocation item")
        val otherItem = createItem("D3 different batch item")
        val otherBatch = createBatch(otherItem, 2)
        val saleId = database.salesDao().insertInvoice(
            SalesInvoiceEntity(
                displayNo = "INV-202611-000001-D3B0",
                grossTotal = 100L,
                netTotal = 100L,
                cartUuid = "d3-item-mismatch-cart"
            )
        )
        val lineId = database.salesDao().insertLine(
            SalesLineEntity(
                saleId = saleId, itemId = itemId, unitType = UnitType.BOX, qty = 1L,
                stripsPerBoxFrozen = 1, baseQty = 1L, unitPrice = 100L, lineTotal = 100L
            )
        )
        expectConstraint {
            runBlocking {
                database.salesDao().insertAllocation(
                    SaleAllocationEntity(
                        lineId = lineId, batchId = otherBatch, qtyBase = 1L,
                        revenueFrozen = 100L, costFrozen = 100L
                    )
                )
            }
        }
    }

    @Test
    fun allocationRejectsNonPositiveQuantityAndNegativeAmounts() = runBlocking {
        val f = createFixture(3)
        expectConstraint {
            runBlocking {
                database.salesDao().insertAllocation(
                    SaleAllocationEntity(lineId = f.lineId, batchId = f.batchId, qtyBase = 0L, revenueFrozen = 0L, costFrozen = 0L)
                )
            }
        }
        expectConstraint {
            runBlocking {
                database.salesDao().insertAllocation(
                    SaleAllocationEntity(lineId = f.lineId, batchId = f.batchId, qtyBase = 1L, revenueFrozen = -1L, costFrozen = 0L)
                )
            }
        }
        expectConstraint {
            runBlocking {
                database.salesDao().insertAllocation(
                    SaleAllocationEntity(lineId = f.lineId, batchId = f.batchId, qtyBase = 1L, revenueFrozen = 1L, costFrozen = -1L)
                )
            }
        }
    }

    @Test
    fun allocationCannotExceedLineQuantityOrRevenue() = runBlocking {
        val f = createFixture(4)
        expectConstraint {
            runBlocking {
                database.salesDao().insertAllocation(
                    SaleAllocationEntity(lineId = f.lineId, batchId = f.batchId, qtyBase = 1L, revenueFrozen = 1L, costFrozen = 1L)
                )
            }
        }
        // The existing allocation already covers the full base quantity and line revenue.
        assertEquals(1, database.salesDao().allocationsFor(f.saleId).size)
    }

    @Test
    fun saleMovementRequiresAllLinksAndRejectsNullReferences() = runBlocking {
        val f = createFixture(5)
        expectConstraint {
            runBlocking {
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        movementDate = 300L,
                        type = MovementType.SALE,
                        itemId = f.itemId,
                        qtyDelta = -1L,
                        costDelta = 0L
                    )
                )
            }
        }
    }

    @Test
    fun saleMovementMustMatchInvoiceAllocationBatchQuantityAndCost() = runBlocking {
        val f = createFixture(6)
        expectConstraint {
            runBlocking {
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        movementDate = 300L,
                        type = MovementType.SALE,
                        batchId = f.batchId,
                        itemId = f.itemId,
                        qtyDelta = -9L,
                        costDelta = -1_000L,
                        invoiceRef = f.displayNo,
                        saleId = f.saleId,
                        allocId = f.allocId
                    )
                )
            }
        }
        expectConstraint {
            runBlocking {
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        movementDate = 301L,
                        type = MovementType.SALE,
                        batchId = f.batchId,
                        itemId = f.itemId,
                        qtyDelta = -10L,
                        costDelta = -1_000L,
                        invoiceRef = "INV-202611-999999-D3A0",
                        saleId = f.saleId,
                        allocId = f.allocId
                    )
                )
            }
        }
        // Cost must equal the frozen allocation cost.
        expectConstraint {
            runBlocking {
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        movementDate = 302L,
                        type = MovementType.SALE,
                        batchId = f.batchId,
                        itemId = f.itemId,
                        qtyDelta = -10L,
                        costDelta = -999L,
                        invoiceRef = f.displayNo,
                        saleId = f.saleId,
                        allocId = f.allocId
                    )
                )
            }
        }

        // The movement batch must match the allocation batch.
        val otherBatchId = createBatch(f.itemId, suffix = 61)
        expectConstraint {
            runBlocking {
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        movementDate = 303L,
                        type = MovementType.SALE,
                        batchId = otherBatchId,
                        itemId = f.itemId,
                        qtyDelta = -10L,
                        costDelta = -1_000L,
                        invoiceRef = f.displayNo,
                        saleId = f.saleId,
                        allocId = f.allocId
                    )
                )
            }
        }

        // The sale ID must match the invoice reference.
        val otherSale = createFixture(62)
        expectConstraint {
            runBlocking {
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        movementDate = 304L,
                        type = MovementType.SALE,
                        batchId = f.batchId,
                        itemId = f.itemId,
                        qtyDelta = -10L,
                        costDelta = -1_000L,
                        invoiceRef = f.displayNo,
                        saleId = otherSale.saleId,
                        allocId = f.allocId
                    )
                )
            }
        }
    }

    @Test
    fun validSaleMovementAllowsConfirmationAndConfirmedAllocationIsImmutable() = runBlocking {
        val f = createFixture(7)
        insertMatchingSaleMovement(f)
        assertEquals(1, database.salesDao().confirmPending(f.saleId, 400L))
        expectConstraint {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE sale_allocations SET revenueFrozen = 99 WHERE allocId = ?",
                arrayOf<Any?>(f.allocId)
            )
        }
    }

    @Test
    fun nonSaleMovementCannotCarrySaleAllocationReference() = runBlocking {
        val f = createFixture(8)
        expectConstraint {
            runBlocking {
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        movementDate = 500L,
                        type = MovementType.PURCHASE,
                        batchId = f.batchId,
                        itemId = f.itemId,
                        qtyDelta = 1L,
                        costDelta = 100L,
                        allocId = f.allocId
                    )
                )
            }
        }
    }
}
