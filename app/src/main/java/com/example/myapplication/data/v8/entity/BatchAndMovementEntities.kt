package com.example.myapplication.data.v8.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * الدفعة. receivedAt وحقول أوقات الحركات Long بالملّي ثانية.
 * expiryDate هو epoch-day لآخر يوم من شهر الانتهاء (عدد الأيام منذ 1970-01-01)؛ null = بلا صلاحية.
 * الدفعة صالحة إذا expiryDate >= todayEpochDay (صالحة طوال يوم الانتهاء).
 * isSellable=0 لدفعات الحجر (مرتجع عميل) فلا تدخل البيع ولا FEFO.
 */
@Entity(
    tableName = "batches",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["itemId"], childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PurchaseLineEntity::class,
            parentColumns = ["lineId"], childColumns = ["purchaseLineId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = BatchEntity::class,
            parentColumns = ["batchId"], childColumns = ["parentBatchId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["itemId", "isSellable", "expiryDate"]),
        Index(value = ["purchaseLineId"], unique = true),   // دفعة واحدة لكل بند شراء
        Index(value = ["parentBatchId"])
    ]
)
data class BatchEntity(
    @PrimaryKey(autoGenerate = true) val batchId: Long = 0,
    val itemId: Long,
    /** معامل الوحدات الأساسية في العلبة وقت إنشاء الدفعة؛ ثابت تاريخياً. */
    val baseUnitsPerBox: Int,
    val source: BatchSource,
    val purchaseLineId: Long? = null,
    val parentBatchId: Long? = null,
    val qtyReceived: Long,
    val qtyRemaining: Long,
    val qtySupplierReturned: Long = 0,
    val costRemaining: Long,
    val costIsEstimated: Boolean = false,
    /** سعر العلبة لهذه الدفعة؛ null يعني أن الدفعة غير مسعّرة ولا تُباع حتى تحديد السعر. */
    val salePrice: Long? = null,
    /** آخر يوم من شهر الصلاحية بصيغة epoch-day، وليس milliseconds. */
    val expiryDate: Long? = null,
    val receivedAt: Long,
    val isSellable: Boolean = true
)

/** دفتر الحركات: للإضافة فقط (يمنع التعديل والحذف trigger في PharmacyDatabaseV8). */
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["itemId"], childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = BatchEntity::class,
            parentColumns = ["batchId"], childColumns = ["batchId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SalesInvoiceEntity::class,
            parentColumns = ["saleId"], childColumns = ["saleId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SaleAllocationEntity::class,
            parentColumns = ["allocId"], childColumns = ["allocId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = StockMovementEntity::class,
            parentColumns = ["movementId"], childColumns = ["reversesMovementId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["itemId"]),
        // يغطي تجميع دفتر الدفعة ويحل محل فهرس batchId المنفرد وفق D8.
        Index(value = ["batchId", "qtyDelta", "costDelta"]),
        Index(value = ["movementDate"]),
        Index(value = ["type", "movementDate"]),
        Index(value = ["invoiceRef"]),
        Index(value = ["saleId"]),
        Index(value = ["allocId"], unique = true),
        Index(value = ["reversesMovementId"], unique = true)
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val movementId: Long = 0,
    val movementDate: Long,
    val type: MovementType,
    val batchId: Long? = null,
    val itemId: Long,
    val qtyDelta: Long,
    val costDelta: Long = 0,
    /** رقم الفاتورة المعروض؛ الربط الداخلي المنفصل عبر saleId. */
    val invoiceRef: String? = null,
    val saleId: Long? = null,
    /** تخصيص البيع الذي أنشأ هذه الحركة؛ فريد لكل تخصيص. */
    val allocId: Long? = null,
    /** الحركة الأصلية التي تعكسها حركة الإلغاء؛ فريدة لمنع العكس المكرر. */
    val reversesMovementId: Long? = null,
    val reason: String? = null,
    val note: String? = null
)
