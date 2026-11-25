package com.example.myapplication.data.v8

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.v8.db.PharmacyDatabaseV8
import com.example.myapplication.data.v8.error.SaleError
import com.example.myapplication.data.v8.error.V8DatabaseErrorParser
import com.example.myapplication.data.v8.error.V8ErrorCode
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V8DatabaseErrorParserTest {
    private lateinit var database: PharmacyDatabaseV8

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PharmacyDatabaseV8::class.java)
            .addCallback(PharmacyDatabaseV8.TRIGGERS_CALLBACK)
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun parsesActualAndroidSqliteTriggerExceptionAndPreservesRawMessage() {
        val sql = database.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE d6_error_probe (id INTEGER PRIMARY KEY)")
        sql.execSQL("""CREATE TRIGGER d6_error_probe_trigger BEFORE INSERT ON d6_error_probe
            BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_INSUFFICIENT_STOCK'); END""")

        val thrown = try {
            sql.execSQL("INSERT INTO d6_error_probe(id) VALUES (1)")
            throw AssertionError("Expected SQLite trigger constraint")
        } catch (e: SQLiteConstraintException) {
            e
        }

        val parsed = V8DatabaseErrorParser.parse(thrown)
        assertTrue(parsed is SaleError.Known)
        parsed as SaleError.Known
        assertEquals(V8ErrorCode.INSUFFICIENT_STOCK, parsed.code)
        assertTrue(parsed.rawMessage.contains("PHARMACY_V8:E_INSUFFICIENT_STOCK"))
        assertSame(thrown, parsed.cause)
    }

    @Test
    fun unknownSqliteMessageIsNotGuessed() {
        val thrown = SQLiteConstraintException("constraint failed: unrelated message")
        val parsed = V8DatabaseErrorParser.parse(thrown)
        assertTrue(parsed is SaleError.Unknown)
        assertTrue((parsed as SaleError.Unknown).rawMessage.contains("unrelated message"))
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverConvertedToSaleError() {
        V8DatabaseErrorParser.parse(CancellationException("cancelled"))
    }
}
