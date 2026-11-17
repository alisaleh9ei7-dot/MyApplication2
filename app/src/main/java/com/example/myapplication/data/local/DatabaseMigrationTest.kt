package com.example.myapplication.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.local.entity.CompanyEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

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
        context.deleteDatabase(dbName) // يحذف -wal و -shm أيضاً
    }

    @Test
    fun v6DatabaseOpensAndCompaniesArePopulated() = runBlocking {
        // يفشل هنا إذا اختلف المخطط عن v6 أو نقص Migration (Room يرمي IllegalStateException)
        val db = AppDatabase.getDatabase(context)
        assertNotNull(db.openHelper.writableDatabase)

        // انتظار تعبئة الشركات (تعمل في الخلفية بعد البناء) بمهلة 5 ثوانٍ
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

    // ملفات androidTest/assets موجودة في APK الاختبار، لا في APK التطبيق
    private fun copyDatabaseFromTestAssets() {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        testAssets.open("databases/$dbName").use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }

        try {
            testAssets.open("databases/$dbName-wal").use { input ->
                File(dbFile.path + "-wal").outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: FileNotFoundException) {
            // لا مشكلة إن لم يوجد ملف WAL
        }
    }
}
