package com.example.myapplication.data.v8.service

import androidx.room.withTransaction
import com.example.myapplication.data.v8.db.PharmacyDatabaseV8
import com.example.myapplication.data.v8.entity.DisplayNo
import com.example.myapplication.data.v8.entity.InvoiceCounterEntity
import com.example.myapplication.data.v8.entity.SalesInvoiceEntity
import java.security.SecureRandom
import java.util.Calendar
import java.util.TimeZone

/**
 * تخصيص رقم الفاتورة وإنشاء سجل PENDING في معاملة واحدة.
 * المنطقة الزمنية الافتراضية هي منطقة Android الحالية (TimeZone.getDefault()).
 */
class InvoiceNumberAllocator(
    private val database: PharmacyDatabaseV8,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val deviceTimeZone: () -> TimeZone = { TimeZone.getDefault() },
    private val suffixGenerator: () -> String = ::generateSecureSuffix
) {
    suspend fun createInvoice(
        grossTotal: Long,
        netTotal: Long,
        cartUuid: String,
        discountTotal: Long = 0,
        customerName: String? = null,
        prescriptionImageName: String? = null
    ): SalesInvoiceEntity = database.withTransaction {
        val dao = database.salesDao()

        // إعادة المحاولة لنفس السلة تعيد الفاتورة نفسها ولا تستهلك رقماً جديداً.
        dao.findByCartUuid(cartUuid)?.let { return@withTransaction it }

        // نلتقط الوقت والمنطقة مرة واحدة كي لا يتغير الشهر وسط عملية الإنشاء.
        val createdAt = nowMillis()
        val timeZone = deviceTimeZone()
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = createdAt }
        val yearMonth = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH) + 1

        dao.ensureCounter(InvoiceCounterEntity(yearMonth = yearMonth, lastNumber = 0L))
        check(dao.bumpCounter(yearMonth) == 1) {
            "Monthly invoice counter exhausted for $yearMonth"
        }
        val number = checkNotNull(dao.counterValue(yearMonth)) {
            "Monthly invoice counter disappeared for $yearMonth"
        }

        // التصادم يعيد توليد XXXX فقط؛ لا يستهلك رقماً شهرياً آخر.
        for (attempt in 0 until MAX_SUFFIX_ATTEMPTS) {
            val displayNo = DisplayNo.format(yearMonth, number, suffixGenerator())
            val invoice = SalesInvoiceEntity(
                displayNo = displayNo,
                grossTotal = grossTotal,
                discountTotal = discountTotal,
                netTotal = netTotal,
                cartUuid = cartUuid,
                customerName = customerName,
                prescriptionImageName = prescriptionImageName
            )
            // نتجنب رمي استثناء متوقع عند تصادم الرقم، لأننا نريد إبقاء
            // معاملة العدّاد سليمة أثناء إعادة توليد XXXX.
            if (dao.findByDisplayNo(displayNo) != null) continue

            val saleId = dao.tryInsertInvoice(invoice)
            if (saleId != -1L) {
                return@withTransaction invoice.copy(saleId = saleId)
            }

            // IGNORE قد يتجاهل أيضاً تعارض cartUuid؛ نعيد الفاتورة الموجودة
            // إن كانت إعادة محاولة لنفس السلة، ولا نخفي أي تعارض غير معروف.
            dao.findByCartUuid(cartUuid)?.let { return@withTransaction it }
            if (dao.findByDisplayNo(displayNo) != null) continue
            error("Invoice insert was ignored by an unexpected constraint")
        }
        error("Unable to allocate a unique invoice suffix after $MAX_SUFFIX_ATTEMPTS attempts")
    }

    private companion object {
        const val MAX_SUFFIX_ATTEMPTS = 16
        const val SUFFIX_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val secureRandom = SecureRandom()

        fun generateSecureSuffix(): String = buildString(4) {
            repeat(4) { append(SUFFIX_ALPHABET[secureRandom.nextInt(SUFFIX_ALPHABET.length)]) }
        }
    }
}
