package com.example.myapplication.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "batches",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["itemId"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE // إذا حُذف الصنف، تُحذف دفعاته
        )
    ],
    indices = [Index("itemId"), Index("expiryDate")] // تسريع البحث
)

data class BatchEntity(
    @PrimaryKey val batchId: String, // معرف الدفعة ITEM-0001|202802|001
    val itemId: String,              // معرف الصنف (يربط بجدول الأصناف)
    val drugName: String?,           // اسم الدواء
    val dosageForm: String?,         // الشكل الصيدلاني
    val company: String?,            // الشركة
    val originalOffer: String?,      // العرض الأصلي (2+1)
    val currentQty: Double,          // الكمية الحالية
    val expiryDate: Long,            // تاريخ الصلاحية (نخزنه كـ timestamp)
    val purchasePrice: Double,       // سعر الشراء
    val paidUnits: Double,           // الوحدات المدفوعة
    val freeUnits: Double,           // الوحدات المجانية
    val totalUnits: Double,          // إجمالي الوحدات
    val unitCost: Double,            // سعر التكلفة الفعلي للوحدة
    val salePrice: Double,           // سعر المبيع
    val clearancePrice: Double?,     // سعر التصفية
    val legacyBatchId: String?,      // معرف الدفعة القديم
    val dataStatus: String?          // حالة البيانات (مستوردة، جديدة...)
)