package com.example.myapplication.data.v8

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.v8.db.PharmacyDatabaseV8
import com.example.myapplication.data.v8.entity.DisplayNo
import com.example.myapplication.data.v8.entity.InvoiceCounterEntity
import com.example.myapplication.data.v8.entity.SalesInvoiceEntity
import com.example.myapplication.data.v8.service.InvoiceNumberAllocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class InvoiceNumberAllocatorTest {
    private lateinit var context: Context
    private lateinit var database: PharmacyDatabaseV8

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PharmacyDatabaseV8::class.java
        ).addCallback(PharmacyDatabaseV8.TRIGGERS_CALLBACK).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun displayNumberUsesExactApprovedFormatAndRejectsInvalidValues() {
        assertEquals("INV-202611-000042-A7K2", DisplayNo.format(202611, 42L, "A7K2"))
        assertTrue(DisplayNo.isValid("INV-202611-000042-A7K2"))
        assertFalse(DisplayNo.isValid("INV-202613-000042-A7K2"))
        assertFalse(DisplayNo.isValid("INV-202611-000000-A7K2"))
        assertFalse(DisplayNo.isValid("INV-202611-000042-a7K2"))
    }

    @Test
    fun usesThePhoneTimeZoneToDetermineInvoiceMonth() = runBlocking {
        val instant = utcMillis(2026, 12, 1, 0, 30, 0)
        val losAngeles = InvoiceNumberAllocator(
            database = database,
            nowMillis = { instant },
            deviceTimeZone = { TimeZone.getTimeZone("America/Los_Angeles") },
            suffixGenerator = { "AAAA" }
        )
        val tokyo = InvoiceNumberAllocator(
            database = database,
            nowMillis = { instant },
            deviceTimeZone = { TimeZone.getTimeZone("Asia/Tokyo") },
            suffixGenerator = { "BBBB" }
        )
        val beforeMidnight = losAngeles.createInvoice(10, 10, "la-cart")
        val afterMidnight = tokyo.createInvoice(20, 20, "tokyo-cart")
        assertTrue(beforeMidnight.displayNo.startsWith("INV-202611-"))
        assertTrue(afterMidnight.displayNo.startsWith("INV-202612-"))
    }

    @Test
    fun monthlyCounterStartsAtOneIncrementsAndResetsForNewMonth() = runBlocking {
        var now = utcMillis(2026, 11, 30, 23, 59, 0)
        val suffixes = listOf("AAAA", "BBBB", "CCCC").iterator()
        val allocator = InvoiceNumberAllocator(
            database = database,
            nowMillis = { now },
            deviceTimeZone = { TimeZone.getTimeZone("UTC") },
            suffixGenerator = { suffixes.next() }
        )
        val first = allocator.createInvoice(100, 100, "cart-nov-1")
        val second = allocator.createInvoice(200, 200, "cart-nov-2")
        assertEquals("INV-202611-000001-AAAA", first.displayNo)
        assertEquals("INV-202611-000002-BBBB", second.displayNo)

        now = utcMillis(2026, 12, 1, 0, 1, 0)
        val third = allocator.createInvoice(300, 300, "cart-dec-1")
        assertEquals("INV-202612-000001-CCCC", third.displayNo)
    }

    @Test
    fun retryWithSameCartUuidReturnsExistingInvoiceWithoutConsumingNumber() = runBlocking {
        val allocator = allocatorAt(utcMillis(2026, 11, 12, 12, 0, 0), listOf("AAAA", "BBBB"))
        val first = allocator.createInvoice(100, 100, "same-cart")
        val retry = allocator.createInvoice(100, 100, "same-cart")
        val next = allocator.createInvoice(200, 200, "next-cart")
        assertEquals(first.saleId, retry.saleId)
        assertEquals(first.displayNo, retry.displayNo)
        assertEquals("INV-202611-000002-BBBB", next.displayNo)
    }

    @Test
    fun suffixCollisionRegeneratesOnlySuffix() = runBlocking {
        database.salesDao().insertInvoice(
            SalesInvoiceEntity(
                displayNo = "INV-202611-000001-AAAA",
                grossTotal = 10,
                netTotal = 10,
                cartUuid = "existing-cart"
            )
        )
        val allocator = allocatorAt(utcMillis(2026, 11, 12, 12, 0, 0), listOf("AAAA", "BBBB"))
        val created = allocator.createInvoice(20, 20, "new-cart")
        assertEquals("INV-202611-000001-BBBB", created.displayNo)
        val cursor = database.openHelper.readableDatabase.query(
            "SELECT yearMonth, lastNumber FROM invoice_counters"
        )
        val rawCounterRows = cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add("${it.getInt(0)}:${it.getLong(1)}")
                }
            }
        }
        assertEquals("Raw SQLite counter rows", listOf("202611:1"), rawCounterRows)
        assertEquals(1L, database.salesDao().counterValue(202611))
        assertEquals("INV-202611-000001-BBBB", database.salesDao().findByCartUuid("new-cart")?.displayNo)
        assertEquals(2, database.salesDao().recent(10).size)
    }

    @Test
    fun failedInvoiceInsertRollsBackMonthlyCounter() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER test_fail_invoice_insert BEFORE INSERT ON sales_invoices
               WHEN NEW.cartUuid = 'forced-failure'
               BEGIN SELECT RAISE(ABORT, 'forced test failure'); END"""
        )
        val allocator = allocatorAt(utcMillis(2026, 11, 12, 12, 0, 0), listOf("AAAA"))
        var failed = false
        try {
            allocator.createInvoice(20, 20, "forced-failure")
        } catch (_: SQLiteConstraintException) {
            failed = true
        }
        assertTrue("Expected the test trigger to reject invoice insertion", failed)
        assertEquals(null, database.salesDao().counterValue(202611))
        assertEquals(0, database.salesDao().recent(10).size)
    }

    @Test
    fun confirmedCommittedInvoiceDoesNotReleaseItsNumber() = runBlocking {
        val allocator = allocatorAt(utcMillis(2026, 11, 12, 12, 0, 0), listOf("AAAA", "BBBB"))
        val first = allocator.createInvoice(20, 20, "cancelled-cart")
        assertEquals(1, database.salesDao().confirmPending(first.saleId, 1234L))

        val second = allocator.createInvoice(30, 30, "next-cart")
        assertEquals("INV-202611-000001-AAAA", first.displayNo)
        assertEquals("INV-202611-000002-BBBB", second.displayNo)
    }

    @Test
    fun counterCannotExceed999999() = runBlocking {
        database.salesDao().ensureCounter(InvoiceCounterEntity(202611, 999_999L))
        val allocator = allocatorAt(utcMillis(2026, 11, 12, 12, 0, 0), listOf("AAAA"))
        var failed = false
        try {
            allocator.createInvoice(20, 20, "overflow-cart")
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue("Expected monthly counter exhaustion", failed)
        assertEquals(999_999L, database.salesDao().counterValue(202611))
    }

    @Test
    fun concurrentCreationsReceiveDifferentSequentialNumbers() = runBlocking {
        val sequence = AtomicInteger(0)
        val suffixes = listOf("AAAA", "BBBB")
        val allocator = InvoiceNumberAllocator(
            database = database,
            nowMillis = { utcMillis(2026, 11, 12, 12, 0, 0) },
            deviceTimeZone = { TimeZone.getTimeZone("UTC") },
            suffixGenerator = { suffixes[sequence.getAndIncrement()] }
        )
        val results = withContext(Dispatchers.IO) {
            listOf(
                async { allocator.createInvoice(10, 10, "parallel-a") },
                async { allocator.createInvoice(20, 20, "parallel-b") }
            ).map { it.await() }
        }
        assertEquals(2, results.map { it.displayNo }.distinct().size)
        assertEquals(setOf(1L, 2L), results.map { it.displayNo.substring(11, 17).toLong() }.toSet())
    }

    private fun allocatorAt(now: Long, suffixes: List<String>): InvoiceNumberAllocator {
        val iterator = suffixes.iterator()
        return InvoiceNumberAllocator(
            database = database,
            nowMillis = { now },
            deviceTimeZone = { TimeZone.getTimeZone("UTC") },
            suffixGenerator = { iterator.next() }
        )
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        java.util.GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, hour, minute, second)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
}

