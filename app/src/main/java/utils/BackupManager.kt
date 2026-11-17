package com.example.myapplication.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

    // دالة إنشاء النسخة الاحتياطية (تشمل القاعدة + الصور)
    suspend fun createBackup(context: Context): Uri? = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath("my_pharmacy_db")
            if (!dbFile.exists()) return@withContext null

            // 1. إجبار قاعدة البيانات على حفظ البيانات
            val db = AppDatabase.getDatabase(context)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()

            val backupFileName = "Pharmacy_Backup_${System.currentTimeMillis()}.zip"
            val tempBackupFile = File(context.cacheDir, backupFileName)
            val zipOutputStream = ZipOutputStream(FileOutputStream(tempBackupFile))

            // 2. إضافة قاعدة البيانات
            val fis = FileInputStream(dbFile)
            zipOutputStream.putNextEntry(ZipEntry("my_pharmacy_db"))
            fis.copyTo(zipOutputStream)
            zipOutputStream.closeEntry()
            fis.close()

            // 3. إضافة صور الوصفات
            val prescriptionsDir = File(context.filesDir, "prescriptions")
            if (prescriptionsDir.exists()) {
                prescriptionsDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val fileFis = FileInputStream(file)
                        zipOutputStream.putNextEntry(ZipEntry("prescriptions/${file.name}"))
                        fileFis.copyTo(zipOutputStream)
                        zipOutputStream.closeEntry()
                        fileFis.close()
                    }
                }
            }
            zipOutputStream.close()

            // 4. الحفظ في مجلد التنزيلات
            val savedUri = saveToDownloads(context, tempBackupFile, backupFileName)
            tempBackupFile.delete()

            savedUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // دالة استعادة النسخة الاحتياطية (آمنة - لا تحذف القديم حتى تتأكد من الجديد)
    suspend fun restoreBackup(context: Context, backupUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, "temp_restore.zip")
            context.contentResolver.openInputStream(backupUri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            if (!tempFile.exists()) return@withContext false

            // 1. التحقق من محتويات الملف أولاً (هل يحتوي على قاعدة البيانات؟)
            var hasValidDb = false
            val checkStream = ZipInputStream(FileInputStream(tempFile))
            var entry = checkStream.nextEntry
            while (entry != null) {
                if (entry.name == "my_pharmacy_db") {
                    hasValidDb = true
                    break
                }
                entry = checkStream.nextEntry
            }
            checkStream.close()

            // إذا لم نجد قاعدة البيانات في الملف، نوقف العملية فوراً
            if (!hasValidDb) {
                tempFile.delete()
                return@withContext false
            }

            // 2. الآن فقط، نغلق القاعدة ونأخذ نسخة احتياطية للملف القديم
            val dbFile = context.getDatabasePath("my_pharmacy_db")
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            val backupDbFile = File(dbFile.path + ".bak")

            AppDatabase.getDatabase(context).close()

            // إعادة تسمية الملف القديم إلى .bak بدلاً من حذفه
            if (dbFile.exists()) {
                if (backupDbFile.exists()) backupDbFile.delete()
                dbFile.renameTo(backupDbFile)
            }
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // 3. فك الضغط
            val inputStream = FileInputStream(tempFile)
            val zipInputStream = ZipInputStream(inputStream)
            val prescriptionsDir = File(context.filesDir, "prescriptions")
            if (!prescriptionsDir.exists()) prescriptionsDir.mkdirs()

            var entry2 = zipInputStream.nextEntry
            var foundDb = false
            while (entry2 != null) {
                when {
                    entry2.name == "my_pharmacy_db" -> {
                        foundDb = true
                        val outputStream = FileOutputStream(dbFile)
                        zipInputStream.copyTo(outputStream)
                        outputStream.close()
                    }
                    entry2.name.startsWith("prescriptions/") -> {
                        val fileName = entry2.name.substringAfter("prescriptions/")
                        if (fileName.isNotEmpty()) {
                            val file = File(prescriptionsDir, fileName)
                            val outputStream = FileOutputStream(file)
                            zipInputStream.copyTo(outputStream)
                            outputStream.close()
                        }
                    }
                }
                zipInputStream.closeEntry()
                entry2 = zipInputStream.nextEntry
            }
            zipInputStream.close()
            inputStream.close()
            tempFile.delete()

            // 4. إذا نجحت العملية، نحذف ملف الـ .bak
            if (foundDb && backupDbFile.exists()) {
                backupDbFile.delete()
            }

            foundDb
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // دالة مساعدة للحفظ في التنزيلات (تدعم كل إصدارات أندرويد)
    private fun saveToDownloads(context: Context, file: File, fileName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(it, values, null, null)
            }
            uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, fileName)
            file.copyTo(destFile, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
        }
    }
}