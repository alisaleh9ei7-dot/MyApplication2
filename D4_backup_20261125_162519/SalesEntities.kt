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
    val displayNo: String,              // INV-YYYYMM-NNNNNN
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

/** عدّاد الفواتير الشهري؛ يُحدَّث داخل نفس معاملة التأكيد. yearMonth مثل 202611. */
@Entity(tableName = "invoice_counters")
data class InvoiceCounterEntity(
    @PrimaryKey val yearMonth: Int,
    val lastNumber: Long
)

object DisplayNo {
    /** INV-202611-000042؛ XXXX غير مفروض حتى يُعرَّف. */
    fun format(yearMonth: Int, number: Long): String {
        require(number in 1L..999_999L) { "Monthly invoice number must be between 1 and 999999" }
        return "INV-" + yearMonth.toString() + "-" + number.toString().padStart(6, '0')
    }
}
