package com.example.myapplication.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.myapplication.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    // دالة إنشاء ملف النسخة الاحتياطية (ZIP)
    suspend fun createBackup(context: Context): Uri? = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath("my_pharmacy_db")
            if (!dbFile.exists()) {
                return@withContext null
            }

            // 1. إجبار قاعدة البيانات على حفظ البيانات في الملف الرئيسي وإغلاقها
            val db = AppDatabase.getDatabase(context)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            db.close()

            // 2. ننتظر قليلاً لكي يتأكد النظام من إغلاق الملف
            kotlinx.coroutines.delay(500)

            // 3. الآن ننسخ الملف (بعد التأكد من حفظه)
            val backupFile = File(context.cacheDir, "Pharmacy_Backup_${System.currentTimeMillis()}.zip")
            val zipOutputStream = ZipOutputStream(FileOutputStream(backupFile))

            val fis = FileInputStream(dbFile)
            zipOutputStream.putNextEntry(ZipEntry("my_pharmacy_db"))
            fis.copyTo(zipOutputStream)
            zipOutputStream.closeEntry()
            fis.close()
            zipOutputStream.close()

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // دالة استعادة النسخة الاحتياطية
    suspend fun restoreBackup(context: Context, backupUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. نسخ الملف إلى ذاكرة التطبيق المؤقتة
            val tempFile = File(context.cacheDir, "temp_restore.zip")
            context.contentResolver.openInputStream(backupUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempFile.exists()) return@withContext false

            val dbFile = context.getDatabasePath("my_pharmacy_db")
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            // 2. إغلاق قاعدة البيانات الحالية
            AppDatabase.getDatabase(context).close()

            // 3. حذف الملفات المؤقتة القديمة (مهم جداً)
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()
            if (dbFile.exists()) dbFile.delete()

            // 4. فك الضغط واستبدال قاعدة البيانات
            val inputStream = FileInputStream(tempFile)
            val zipInputStream = ZipInputStream(inputStream)

            var entry: ZipEntry? = zipInputStream.nextEntry
            var foundDb = false
            while (entry != null) {
                if (entry.name == "my_pharmacy_db") {
                    foundDb = true
                    val outputStream = FileOutputStream(dbFile)
                    zipInputStream.copyTo(outputStream)
                    outputStream.close()
                    break
                }
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()
            inputStream.close()
            tempFile.delete()

            foundDb
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}