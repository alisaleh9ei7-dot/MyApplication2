package com.example.myapplication.data.v8.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_invoices",
    foreignKeys = [ForeignKey(
        entity = SupplierEntity::class,
        parentColumns = ["supplierId"], childColumns = ["supplierId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [
        Index(value = ["supplierId"]),
        Index(value = ["requestUuid"], unique = true),   // يمنع تكرار التأكيد
        Index(value = ["invoiceDate"])
    ]
)
data class PurchaseInvoiceEntity(
    @PrimaryKey(autoGenerate = true) val purchaseId: Long = 0,
    val supplierId: Long,
    val supplierInvoiceNo: String? = null,
    val status: PurchaseStatus = PurchaseStatus.DRAFT,
    val invoiceDate: Long,
    val generalDiscount: Long = 0,
    val netTotal: Long = 0,
    val requestUuid: String,
    val createdAt: Long,
    val confirmedAt: Long? = null
)

/** كل بند مؤكد ينشئ دفعة واحدة مستقلة (لا دمج دفعات). */
@Entity(
    tableName = "purchase_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseInvoiceEntity::class,
            parentColumns = ["purchaseId"], childColumns = ["purchaseId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["itemId"], childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["purchaseId"]), Index(value = ["itemId"])]
)
data class PurchaseLineEntity(
    @PrimaryKey(autoGenerate = true) val lineId: Long = 0,
    val purchaseId: Long,
    val itemId: Long,
    /** معامل الوحدات الأساسية في العلبة وقت إدراج بند الشراء؛ ثابت تاريخياً. */
    val baseUnitsPerBox: Int,
    val paidQty: Long,                  // بالوحدة الأساسية: شريط أو علبة للصنف غير القابل للتقسيم
    val freeQty: Long = 0,              // بالوحدة الأساسية نفسها
    val lineTotal: Long,                // قبل الخصم العام
    val lineDiscount: Long = 0,
    val expiryDate: Long? = null,       // epoch-day لآخر يوم من شهر الانتهاء (انظر BatchEntity)
    val costIsEstimated: Boolean = false
)
