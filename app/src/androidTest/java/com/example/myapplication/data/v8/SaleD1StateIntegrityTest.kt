package com.example.myapplication.data.v8

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.v8.db.PharmacyDatabaseV8
import com.example.myapplication.data.v8.entity.SaleStatus
import com.example.myapplication.data.v8.entity.SalesInvoiceEntity
import com.example.myapplication.data.v8.entity.ItemEntity
import com.example.myapplication.data.v8.entity.SalesLineEntity
import com.example.myapplication.data.v8.entity.UnitType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleD1StateIntegrityTest {
    private lateinit var database: PharmacyDatabaseV8

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PharmacyDatabaseV8::class.java
        ).addCallback(PharmacyDatabaseV8.TRIGGERS_CALLBACK).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun newInvoice(
        number: Int,
        total: Long = 100L,
        cartUuid: String = "d1-cart-$number"
    ): Long = database.salesDao().insertInvoice(
        SalesInvoiceEntity(
            displayNo = "INV-202611-${number.toString().padStart(6, '0')}-D1A0",
            grossTotal = total,
            netTotal = total,
            cartUuid = cartUuid
        )
    )

    private fun expectConstraint(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: SQLiteConstraintException) {
            rejected = true
        }
        assertTrue("Expected SQLite D1 state trigger to reject the operation", rejected)
    }

    @Test
    fun newInvoiceMustStartPendingWithoutTransitionTimestamps() = runBlocking {
        val saleId = newInvoice(1)
        val invoice = database.salesDao().getInvoice(saleId)
        assertEquals(SaleStatus.PENDING, invoice?.status)
        assertEquals(null, invoice?.confirmedAt)
        assertEquals(null, invoice?.cancelledAt)
        assertEquals(null, invoice?.cancelReason)
    }

    @Test
    fun databaseRejectsInvoiceInsertedDirectlyAsConfirmed() = runBlocking {
        expectConstraint {
            runBlocking {
                database.salesDao().insertInvoice(
                    SalesInvoiceEntity(
                        displayNo = "INV-202611-000002-D1A0",
                        status = SaleStatus.CONFIRMED,
                        confirmedAt = 200L,
                        grossTotal = 100L,
                        netTotal = 100L,
                        cartUuid = "d1-invalid-confirmed-insert"
                    )
                )
            }
        }
        assertEquals(null, database.salesDao().findByCartUuid("d1-invalid-confirmed-insert"))
    }

    @Test
    fun pendingInvoiceCanOnlyBeConfirmedWithTimestamp() = runBlocking {
        val saleId = newInvoice(3)
        expectConstraint { runBlocking { database.salesDao().confirmPending(saleId, 300L) } }
        val stillPending = database.salesDao().getInvoice(saleId)
        assertEquals(SaleStatus.PENDING, stillPending?.status)
        assertEquals(null, stillPending?.confirmedAt)
        assertEquals(null, stillPending?.cancelledAt)
    }

    @Test
    fun databaseRejectsInvalidTransitionsAndTimestampOnlyChanges() = runBlocking {
        val saleId = newInvoice(4)
        expectConstraint {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE sales_invoices SET status = 'CONFIRMED' WHERE saleId = ?",
                arrayOf<Any?>(saleId)
            )
        }
        expectConstraint {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE sales_invoices SET confirmedAt = 400 WHERE saleId = ?",
                arrayOf<Any?>(saleId)
            )
        }
        assertEquals(SaleStatus.PENDING, database.salesDao().getInvoice(saleId)?.status)

        expectConstraint { runBlocking { database.salesDao().confirmPending(saleId, 401L) } }
        assertEquals(SaleStatus.PENDING, database.salesDao().getInvoice(saleId)?.status)
        assertEquals(null, database.salesDao().getInvoice(saleId)?.confirmedAt)
    }

    @Test
    fun confirmedInvoiceCanBeCancelledOnceWithCancellationTimestamp() = runBlocking {
        val saleId = newInvoice(5)
        expectConstraint { runBlocking { database.salesDao().confirmPending(saleId, 500L) } }
        assertEquals(0, database.salesDao().markCancelled(saleId, 600L, "D1 test"))
        val pending = database.salesDao().getInvoice(saleId)
        assertEquals(SaleStatus.PENDING, pending?.status)
        assertEquals(null, pending?.confirmedAt)
        assertEquals(null, pending?.cancelledAt)
    }

    @Test
    fun databaseRejectsDirectConfirmationWhenLineHasNoAllocationsOrSaleMovement() = runBlocking {
        val itemId = database.itemDao().insert(ItemEntity(tradeName = "D1 line test", createdAt = 100L))
        val saleId = newInvoice(9)
        database.salesDao().insertLine(
            SalesLineEntity(
                saleId = saleId,
                itemId = itemId,
                unitType = UnitType.BOX,
                qty = 1L,
                stripsPerBoxFrozen = 1,
                baseQty = 1L,
                unitPrice = 100L,
                lineDiscount = 0L,
                lineTotal = 100L
            )
        )
        expectConstraint { runBlocking { database.salesDao().confirmPending(saleId, 950L) } }
        assertEquals(SaleStatus.PENDING, database.salesDao().getInvoice(saleId)?.status)
    }

    @Test
    fun reportsIncludeOnlyConfirmedInvoices() = runBlocking {
        val pendingId = newInvoice(6, total = 100L)
        val confirmedId = newInvoice(7, total = 200L)
        val cancelledId = newInvoice(8, total = 300L)

        expectConstraint { runBlocking { database.salesDao().confirmPending(confirmedId, 700L) } }
        expectConstraint { runBlocking { database.salesDao().confirmPending(cancelledId, 800L) } }

        assertEquals(0L, database.salesDao().netSales(0L, 1_000L))
        assertEquals(SaleStatus.PENDING, database.salesDao().getInvoice(pendingId)?.status)
        assertEquals(SaleStatus.PENDING, database.salesDao().getInvoice(confirmedId)?.status)
        assertEquals(SaleStatus.PENDING, database.salesDao().getInvoice(cancelledId)?.status)
    }
}
