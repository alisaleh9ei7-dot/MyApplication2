package com.example.myapplication.data.v8

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.v8.db.PharmacyDatabaseV8
import com.example.myapplication.data.v8.entity.BatchEntity
import com.example.myapplication.data.v8.entity.BatchSource
import com.example.myapplication.data.v8.entity.ItemEntity
import com.example.myapplication.data.v8.entity.MovementType
import com.example.myapplication.data.v8.entity.StockMovementEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchLedgerBalanceIntegrityTest {

    private lateinit var database: PharmacyDatabaseV8

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PharmacyDatabaseV8::class.java
        )
            .addCallback(PharmacyDatabaseV8.TRIGGERS_CALLBACK)
            .build()

        database.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createConsistentBatch(): Long {
        val itemId = database.itemDao().insert(
            ItemEntity(
                tradeName = "D7 ledger test item",
                createdAt = 100L
            )
        )

        val batchId = database.batchDao().insert(
            BatchEntity(
                itemId = itemId,
                baseUnitsPerBox = 1,
                source = BatchSource.OPENING,
                qtyReceived = 20L,
                qtyRemaining = 20L,
                costRemaining = 2_000L,
                receivedAt = 100L
            )
        )

        database.stockMovementDao().insert(
            StockMovementEntity(
                movementDate = 100L,
                type = MovementType.OPENING,
                batchId = batchId,
                itemId = itemId,
                qtyDelta = 20L,
                costDelta = 2_000L
            )
        )

        return batchId
    }

    private fun assertRejectedWithoutRepair(
        batchId: Long,
        column: String,
        corruptedValue: Long
    ) {
        val sqlite = database.openHelper.writableDatabase

        sqlite.execSQL(
            "UPDATE batches SET $column = ? WHERE batchId = ?",
            arrayOf(corruptedValue, batchId)
        )

        var rejected = false

        try {
            PharmacyDatabaseV8.TRIGGERS_CALLBACK.onOpen(sqlite)
        } catch (error: IllegalStateException) {
            rejected = error.message?.contains(
                "V8 data integrity verification failed"
            ) == true
        }

        assertTrue(
            "D7 must reject a mismatch in $column",
            rejected
        )

        sqlite.query(
            "SELECT $column FROM batches WHERE batchId = ?",
            arrayOf(batchId)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(corruptedValue, cursor.getLong(0))
        }
    }

    @Test
    fun rejectsQuantityBalanceMismatchWithoutRepair() = runBlocking {
        val batchId = createConsistentBatch()

        assertRejectedWithoutRepair(
            batchId = batchId,
            column = "qtyRemaining",
            corruptedValue = 19L
        )
    }

    @Test
    fun rejectsCostBalanceMismatchWithoutRepair() = runBlocking {
        val batchId = createConsistentBatch()

        assertRejectedWithoutRepair(
            batchId = batchId,
            column = "costRemaining",
            corruptedValue = 1_999L
        )
    }
}