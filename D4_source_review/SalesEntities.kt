package com.example.myapplication.data.v8.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales_invoices",
    indices = [
        Index(value = ["displayNo"], unique = true),
        Index(value = ["cartUuid"], unique = true),      // يمنع تكرار التأكيد
        Index(value = ["confirmedAt"])
    ]
)
data class SalesInvoiceEntity(
    @PrimaryKey(autoGenerate = true) val saleId: Long = 0,
    val displayNo: String,
    val status: SaleStatus = SaleStatus.PENDING,
    /** يظل null حتى تثبيت الفاتورة فعلياً. */
    val confirmedAt: Long? = null,
    val cancelledAt: Long? = null,
    /** سبب الإلغاء اختياري؛ ينسخه Trigger إلى حركة SALE_CANCEL. */
    val cancelReason: String? = null,
    val customerName: String? = null,
    val prescriptionImageName: String? = null,
    val grossTotal: Long,
    val discountTotal: Long = 0,
    val netTotal: Long,
    val cartUuid: String
)

@Entity(
    tableName = "sales_lines",
    foreignKeys = [
        ForeignKey(
            entity = SalesInvoiceEntity::class,
            parentColumns = ["saleId"], childColumns = ["saleId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["itemId"], childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["saleId"]), Index(value = ["itemId"])]
)
data class SalesLineEntity(
    @PrimaryKey(autoGenerate = true) val lineId: Long = 0,
    val saleId: Long,
    val itemId: Long,
    val unitType: UnitType,
    val qty: Long,                      // بوحدة البيع (علب أو شرائط)
    /** معامل التحويل المجمد: stripsPerBox للصنف القابل للتجزئة، وإلا 1. */
    val stripsPerBoxFrozen: Int,
    /** كمية البند بوحدة الصنف الأساسية، محسوبة ومجمدة وقت البيع. */
    val baseQty: Long,
    val unitPrice: Long,                // مجمّد وقت البيع
    val lineDiscount: Long = 0,
    val lineTotal: Long
)

/** تخصيص بند البيع على الدفعات (FEFO). الإيراد والتكلفة مجمّدان للتاريخ. */
@Entity(
    tableName = "sale_allocations",
    foreignKeys = [
        ForeignKey(
            entity = SalesLineEntity::class,
            parentColumns = ["lineId"], childColumns = ["lineId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = BatchEntity::class,
            parentColumns = ["batchId"], childColumns = ["batchId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["lineId"]), Index(value = ["batchId"])]
)
data class SaleAllocationEntity(
    @PrimaryKey(autoGenerate = true) val allocId: Long = 0,
    val lineId: Long,
    val batchId: Long,
    val qtyBase: Long,                  // بالوحدة الأساسية للصنف
    val revenueFrozen: Long,
    val costFrozen: Long
)

/** عدّاد الفواتير الشهري؛ يُحدَّث داخل نفس معاملة إنشاء الفاتورة. */
@Entity(tableName = "invoice_counters")
data class InvoiceCounterEntity(
    @PrimaryKey val yearMonth: Int,
    val lastNumber: Long
)

object DisplayNo {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /** الصيغة المعتمدة: INV-YYYYMM-NNNNNN-XXXX. */
    fun format(yearMonth: Int, number: Long, suffix: String): String {
        val year = yearMonth / 100
        val month = yearMonth % 100
        require(year in 2000..9999 && month in 1..12) { "Invalid invoice year/month" }
        require(number in 1L..999_999L) { "Monthly invoice number must be between 1 and 999999" }
        require(suffix.length == 4 && suffix.all { it in ALPHABET }) {
            "Invoice suffix must contain exactly four uppercase letters or digits"
        }
        return "INV-$yearMonth-${number.toString().padStart(6, '0')}-$suffix"
    }

    fun isValid(value: String): Boolean {
        if (value.length != 22 || !value.startsWith("INV-")) return false
        val parts = value.split('-')
        if (parts.size != 4 || parts[0] != "INV") return false
        val yearMonth = parts[1]
        val number = parts[2]
        val suffix = parts[3]
        if (yearMonth.length != 6 || yearMonth.any { it !in '0'..'9' }) return false
        if (number.length != 6 || number.any { it !in '0'..'9' }) return false
        val year = yearMonth.substring(0, 4).toIntOrNull() ?: return false
        val month = yearMonth.substring(4, 6).toIntOrNull() ?: return false
        val sequence = number.toLongOrNull() ?: return false
        return year in 2000..9999 && month in 1..12 && sequence in 1L..999_999L &&
            suffix.length == 4 && suffix.all { it in ALPHABET }
    }
}
