package com.example.myapplication.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private lateinit var context: Context

    private val dbName = AppDatabase.DB_NAME
    private val migrationTestDbName = "migration_test_v6_to_v7"

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        AppDatabase.closeAndReset()
        context.deleteDatabase(dbName)
        context.deleteDatabase(migrationTestDbName)
        copyDatabaseFromTestAssets()
    }

    @After
    fun tearDown() {
        AppDatabase.closeAndReset()
        context.deleteDatabase(dbName)
        context.deleteDatabase(migrationTestDbName)
    }

    @Test
    fun v6DatabaseOpensAndCompaniesArePopulated() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        assertNotNull(db.openHelper.writableDatabase)

        val companies: List<CompanyEntity> = withTimeout(5_000) {
            var list = db.companyDao().getAllCompanies().first()
            while (list.isEmpty()) {
                delay(200)
                list = db.companyDao().getAllCompanies().first()
            }
            list
        }

        assertTrue(
            "يجب تعبئة جدول الشركات",
            companies.isNotEmpty()
        )
    }

    @Test
    fun migration6To7PreservesExistingRowsAndAddsNullableListAmount() {
        var migratedDb: SupportSQLiteDatabase? = null

        try {
            migrationTestHelper.createDatabase(
                migrationTestDbName,
                6
            ).apply {
                execSQL(
                    """
                    INSERT INTO sales_history
                    (saleId, saleDate, quantity, unitPrice, totalAmount)
                    VALUES (1001, 1700000000000, 2.0, 61.725, 123.45)
                    """.trimIndent()
                )
                close()
            }

            migratedDb = migrationTestHelper.runMigrationsAndValidate(
                migrationTestDbName,
                7,
                true,
                AppDatabase.MIGRATION_6_7
            )

            migratedDb.query(
                """
                SELECT saleId, totalAmount, listAmount
                FROM sales_history
                WHERE saleId = 1001
                """.trimIndent()
            ).use { cursor ->
                assertTrue(
                    "يجب الاحتفاظ بسجل البيع القديم",
                    cursor.moveToFirst()
                )

                assertEquals(
                    1001,
                    cursor.getInt(cursor.getColumnIndexOrThrow("saleId"))
                )

                assertEquals(
                    123.45,
                    cursor.getDouble(cursor.getColumnIndexOrThrow("totalAmount")),
                    0.001
                )

                assertTrue(
                    "يجب أن تكون listAmount بقيمة NULL للسجلات القديمة",
                    cursor.isNull(cursor.getColumnIndexOrThrow("listAmount"))
                )
            }

            migratedDb.query("PRAGMA table_info(sales_history)").use { cursor ->
                var columnFound = false

                while (cursor.moveToNext()) {
                    val columnName = cursor.getString(
                        cursor.getColumnIndexOrThrow("name")
                    )

                    if (columnName == "listAmount") {
                        columnFound = true

                        val notNull = cursor.getInt(
                            cursor.getColumnIndexOrThrow("notnull")
                        )

                        assertEquals(
                            "يجب أن يسمح العمود بقيمة NULL",
                            0,
                            notNull
                        )
                    }
                }

                assertTrue(
                    "يجب وجود عمود listAmount",
                    columnFound
                )
            }
        } finally {
            migratedDb?.close()
            context.deleteDatabase(migrationTestDbName)
        }
    }

    private fun copyDatabaseFromTestAssets() {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        testAssets.open("databases/$dbName").use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }

        try {
            testAssets.open("databases/$dbName-wal").use { input ->
                File(dbFile.path + "-wal").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: FileNotFoundException) {
            // لا مشكلة إن لم يوجد ملف WAL
        }
    }
}
