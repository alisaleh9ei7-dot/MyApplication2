package com.example.myapplication.data.v8.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * الصنف. الرصيد غير مخزَّن (يُحسب من الدفعات).
 * المبالغ Long بمقياس Money.SCALE. الحقول النصية الخمسة للهوية غير قابلة لـ null
 * (قيمتها الافتراضية "") حتى يعمل فهرس منع التكرار؛ SQLite يعتبر كل NULL مختلفاً.
 */
@Entity(
    tableName = "items",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["tradeName"]),
        Index(value = ["tradeName", "activeIngredient", "strength", "dosageForm", "company"], unique = true)
    ]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val itemId: Long = 0,
    val tradeName: String,
    val activeIngredient: String = "",
    val strength: String = "",
    val dosageForm: String = "",
    val company: String = "",
    val category: String = "",          // therapeuticClass القديم (D2)
    val location: String? = null,
    val barcode: String? = null,        // يُطبَّع (trim) قبل الحفظ؛ null = بلا باركود
    val requiresPrescription: Boolean = false,
    /** null للصنف غير القابل للتقسيم؛ وإلا عدد الشرائط في العلبة ويجب أن يكون >= 2. */
    val stripsPerBox: Int? = null,
    val boxSalePrice: Long? = null,     // D1؛ null = لا يمكن إضافته لفاتورة
    val reorderLevel: Long = 0,
    val isActive: Boolean = true,
    val createdAt: Long
)

/** سجل تغييرات stripsPerBox التاريخي؛ تكتبه ItemService داخل معاملة التعديل ويُعامل كسجل append-only. */
@Entity(
    tableName = "item_changes",
    foreignKeys = [ForeignKey(
        entity = ItemEntity::class,
        parentColumns = ["itemId"], childColumns = ["itemId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index(value = ["itemId"])]
)
data class ItemChangeEntity(
    @PrimaryKey(autoGenerate = true) val changeId: Long = 0,
    val itemId: Long,
    val changedAt: Long,
    val fieldName: String,
    val oldValue: String?,
    val newValue: String?,
    val reason: String? = null
)
