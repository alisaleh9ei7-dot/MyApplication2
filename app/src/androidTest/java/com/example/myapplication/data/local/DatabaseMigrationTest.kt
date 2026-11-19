package com.example.myapplication.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.local.entity.CompanyEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private lateinit var context: Context
    private val dbName = AppDatabase.DB_NAME

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        AppDatabase.closeAndReset()
        context.deleteDatabase(dbName)
        copyDatabaseFromTestAssets()
    }

    @After
    fun tearDown() {
        AppDatabase.closeAndReset()
        context.deleteDatabase(dbName)
    }

    @Test
    fun v6DatabaseMigratesToV7WithoutLosingSalesAndCompaniesArePopulated() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        val sqlite = db.openHelper.writableDatabase

        assertNotNull(sqlite)
        sqlite.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
        }

        // ملف الاختبار المرفق هو قاعدة v6 المعروفة: 6 مبيعات ومجموعها 743.
        sqlite.query(
            "SELECT COUNT(*), COALESCE(SUM(totalAmount), 0), " +
                    "COALESCE(SUM(quantity), 0), " +
                    "SUM(CASE WHEN listAmount IS NULL THEN 1 ELSE 0 END) " +
                    "FROM sales_history"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(6, cursor.getInt(0))
            assertEquals(743.0, cursor.getDouble(1), 0.000001)
            assertEquals(7.0, cursor.getDouble(2), 0.000001)
            assertEquals(6, cursor.getInt(3))
        }

        val companies: List<CompanyEntity> = withTimeout(5_000) {
            var list = db.companyDao().getAllCompanies().first()
            while (list.isEmpty()) {
                delay(200)
                list = db.companyDao().getAllCompanies().first()
            }
            list
        }
        assertTrue("يجب تعبئة جدول الشركات", companies.isNotEmpty())
    }

    @Test
    fun migration6To7PreservesExistingRowsAndAddsNullableListAmount() {
        val testDbName = "room-migration-6-7-test"
        context.deleteDatabase(testDbName)

        migrationHelper.createDatabase(testDbName, 6).apply {
            execSQL(
                "INSERT INTO sales_history " +
                        "(saleDate, quantity, unitPrice, totalAmount) " +
                        "VALUES (123456789, 2.0, 15.0, 30.0)"
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            testDbName,
            7,
            true,
            AppDatabase.MIGRATION_6_7
        )

        migrated.query(
            "SELECT saleDate, quantity, unitPrice, totalAmount, listAmount " +
                    "FROM sales_history WHERE saleDate = 123456789"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2.0, cursor.getDouble(1), 0.000001)
            assertEquals(15.0, cursor.getDouble(2), 0.000001)
            assertEquals(30.0, cursor.getDouble(3), 0.000001)
            assertTrue("listAmount يجب أن يكون NULL للسجل القديم", cursor.isNull(4))
        }
        migrated.close()
        context.deleteDatabase(testDbName)
    }

    // ملف قاعدة v6 التجريبي موجود في androidTest/assets/databases.
    private fun copyDatabaseFromTestAssets() {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        testAssets.open("databases/$dbName").use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }

        try {
            testAssets.open("databases/$dbName-wal").use { input ->
                java.io.File(dbFile.path + "-wal").outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: FileNotFoundException) {
            // لا مشكلة إن لم يوجد ملف WAL.
        }
    }
}
