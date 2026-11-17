package com.example.myapplication.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
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
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

object BackupManager {

    private const val DB_ENTRY = AppDatabase.DB_NAME
    private const val PRESCRIPTIONS_PREFIX = PrescriptionStorage.DIR_NAME + "/"
    private const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024 // سقف حجم الاستعادة (ضد Zip Bomb)

    // أقل إصدار مسموح باستعادته. اخفضه فقط بعد كتابة Migrations للإصدارات الأقدم.
    private const val MIN_SUPPORTED_DB_VERSION = AppDatabase.DB_VERSION

    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    // ───────────────────────── إنشاء النسخة ─────────────────────────
    suspend fun createBackup(context: Context): Uri? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        var tempBackupFile: File? = null
        try {
            val dbFile = appContext.getDatabasePath(AppDatabase.DB_NAME)
            if (!dbFile.exists()) return@withContext null

            // إجبار WAL على الكتابة داخل ملف القاعدة
            AppDatabase.getDatabase(appContext).openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }

            val backupFileName = "Pharmacy_Backup_${System.currentTimeMillis()}.zip"
            val tmp = File(appContext.cacheDir, backupFileName)
            tempBackupFile = tmp

            ZipOutputStream(FileOutputStream(tmp).buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(DB_ENTRY))
                FileInputStream(dbFile).use { it.copyTo(zip) }
                zip.closeEntry()

                val prescriptionsDir = File(appContext.filesDir, PrescriptionStorage.DIR_NAME)
                prescriptionsDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        zip.putNextEntry(ZipEntry(PRESCRIPTIONS_PREFIX + file.name))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }

            saveToDownloads(appContext, tmp, backupFileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            tempBackupFile?.delete()
        }
    }

    // ───────────────────────── الاستعادة ─────────────────────────
    /**
     * تعيد true عند النجاح. بعد النجاح يجب إعادة تشغيل التطبيق (استدعِ restartApp)
     * لأن الـ DAOs الموجودة في الـ ViewModels كانت مرتبطة بالقاعدة القديمة المغلقة.
     */
    suspend fun restoreBackup(context: Context, backupUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val tempZip = File(appContext.cacheDir, "temp_restore.zip")
        val staging = File(appContext.cacheDir, "restore_staging")
        val dbFile = appContext.getDatabasePath(AppDatabase.DB_NAME)
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        val bakFile = File(dbFile.path + ".bak")

        var swapped = false
        var committed = false

        try {
            tempZip.delete()
            staging.deleteRecursively()
            staging.mkdirs()

            // 1. نسخ الملف المختار إلى مكان مؤقت
            val input = appContext.contentResolver.openInputStream(backupUri)
                ?: return@withContext false
            input.use { i -> FileOutputStream(tempZip).use { o -> i.copyTo(o) } }

            // 2. فك الضغط إلى مجلد مؤقت مع حماية Zip Slip وحد الحجم
            val stagedDb = File(staging, "db.sqlite")
            val stagedPrescriptions = File(staging, PrescriptionStorage.DIR_NAME).apply { mkdirs() }
            var foundDb = false
            var total = 0L

            ZipInputStream(FileInputStream(tempZip).buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (isUnsafeEntryName(name)) {
                        throw SecurityException("Unsafe zip entry: $name")
                    }
                    if (!entry.isDirectory) {
                        when {
                            name == DB_ENTRY -> {
                                total += copyLimited(zis, stagedDb, MAX_TOTAL_BYTES - total)
                                foundDb = true
                            }
                            name.startsWith(PRESCRIPTIONS_PREFIX) -> {
                                val fileName = name.removePrefix(PRESCRIPTIONS_PREFIX)
                                val target = safeChild(stagedPrescriptions, fileName)
                                    ?: throw SecurityException("Unsafe zip entry: $name")
                                total += copyLimited(zis, target, MAX_TOTAL_BYTES - total)
                            }
                            // أي ملف آخر يُتجاهل
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 3. التحقق من القاعدة المُستخرجة قبل لمس القاعدة الحالية
            if (!foundDb) return@withContext false
            if (!hasSqliteHeader(stagedDb)) return@withContext false
            if (!isCompatibleDatabase(stagedDb)) return@withContext false

            // 4. نقل الصور (إضافة/استبدال بنفس الاسم فقط، لا يحذف شيئاً)
            val prescriptionsDir = PrescriptionStorage.dir(appContext)
            stagedPrescriptions.listFiles()?.forEach { f ->
                if (f.isFile) f.copyTo(File(prescriptionsDir, f.name), overwrite = true)
            }

            // 5. حفظ القديمة كـ .bak ثم وضع الجديدة
            AppDatabase.getDatabase(appContext).openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
            AppDatabase.closeAndReset()

            if (bakFile.exists()) bakFile.delete()
            if (dbFile.exists() && !dbFile.renameTo(bakFile)) return@withContext false
            swapped = true

            walFile.delete()
            shmFile.delete()
            stagedDb.copyTo(dbFile, overwrite = true)

            // 6. التحقق عبر Room نفسه (يفحص المخطط والسلامة)
            val verified = try {
                AppDatabase.getDatabase(appContext).openHelper.writableDatabase
                    .query("PRAGMA quick_check")
                    .use { c -> c.moveToFirst() && c.getString(0) == "ok" }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }

            if (!verified) {
                rollbackDatabase(dbFile, walFile, shmFile, bakFile)
                swapped = false
                return@withContext false
            }

            committed = true
            bakFile.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (swapped && !committed) {
                try {
                    rollbackDatabase(dbFile, walFile, shmFile, bakFile)
                } catch (re: Exception) {
                    re.printStackTrace()
                }
            }
            false
        } finally {
            tempZip.delete()
            staging.deleteRecursively()
        }
    }

    /** يعيد تشغيل التطبيق بعد استعادة ناجحة. */
    fun restartApp(context: Context) {
        val appContext = context.applicationContext
        val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        if (intent != null) appContext.startActivity(intent)
        exitProcess(0)
    }

    // ───────────────────────── دوال مساعدة ─────────────────────────
    private fun rollbackDatabase(dbFile: File, walFile: File, shmFile: File, bakFile: File) {
        AppDatabase.closeAndReset()
        dbFile.delete()
        walFile.delete()
        shmFile.delete()
        if (bakFile.exists()) bakFile.renameTo(dbFile)
    }

    private fun isUnsafeEntryName(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("/") || name.startsWith("\\") || name.contains('\u0000')) {
            return true
        }
        return name.replace('\\', '/').split('/').any { it == ".." }
    }

    /** يعيد ملفاً مسطحاً داخل baseDir فقط، أو null إن كان الاسم خطراً. */
    private fun safeChild(baseDir: File, fileName: String): File? {
        if (fileName.isEmpty() || fileName != File(fileName).name) return null
        val base = baseDir.canonicalFile
        val target = File(base, fileName).canonicalFile
        return if (target.path.startsWith(base.path + File.separator)) target else null
    }

    private fun copyLimited(input: InputStream, dest: File, remaining: Long): Long {
        if (remaining <= 0) throw IOException("Backup too large")
        var written = 0L
        FileOutputStream(dest).use { out ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                written += n
                if (written > remaining) throw IOException("Backup too large")
                out.write(buffer, 0, n)
            }
        }
        return written
    }

    private fun hasSqliteHeader(file: File): Boolean = try {
        FileInputStream(file).use { fis ->
            val header = ByteArray(SQLITE_MAGIC.size)
            var read = 0
            while (read < header.size) {
                val n = fis.read(header, read, header.size - read)
                if (n < 0) break
                read += n
            }
            read == header.size && header.contentEquals(SQLITE_MAGIC)
        }
    } catch (e: Exception) {
        false
    }

    /** يفتح النسخة المؤقتة ويفحص السلامة والإصدار. */
    private fun isCompatibleDatabase(file: File): Boolean = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val intact = db.rawQuery("PRAGMA quick_check", null)
                .use { c -> c.moveToFirst() && c.getString(0) == "ok" }
            val version = db.version
            intact && version in MIN_SUPPORTED_DB_VERSION..AppDatabase.DB_VERSION
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

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
