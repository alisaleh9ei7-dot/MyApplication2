package com.example.myapplication.utils

import android.content.Context
import java.io.File

/**
 * القاعدة تخزّن اسم الملف فقط (مثل: rx_1731800000.jpg)،
 * والملف الفعلي دائماً في: filesDir/prescriptions/
 */
object PrescriptionStorage {
    const val DIR_NAME = "prescriptions"

    fun dir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /** يحوّل أي مسار (مطلق قديم أو اسم فقط) إلى اسم الملف فقط. */
    fun toStoredName(path: String?): String? =
        path?.trim()
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotEmpty() }

    /** يعيد الملف الفعلي للعرض، أو null إن لم يوجد. يعمل مع المسارات القديمة المطلقة أيضاً. */
    fun resolve(context: Context, stored: String?): File? {
        val name = toStoredName(stored) ?: return null
        return File(dir(context), name).takeIf { it.exists() }
    }
}
