package com.example.myapplication.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales_history",
    foreignKeys = [
        ForeignKey(
            entity = BatchEntity::class,
            parentColumns = ["batchId"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.SET_NULL // مهم جداً: لحماية السطور اليتيمة
        )
    ],
    indices = [
        Index("batchId"),
        Index("saleDate"),
        Index("invoiceRef"),
        Index("customerName") // 👈 هذا هو السطر الوحيد الذي يجب أن تضيفه
    ]
)

data class SaleHistoryEntity(
    @PrimaryKey(autoGenerate = true) val saleId: Long = 0, // نترك قاعدة البيانات تولد رقماً جديداً
    val saleDate: Long,              // التاريخ والوقت
    val batchId: String?,            // معرف الدفعة (يقبل الفراغ للبيانات اليتيمة)
    val legacyName: String?,         // اسم الدواء القديم
    val quantity: Double,            // الكمية
    val unitPrice: Double,           // سعر المبيع المسجل
    val totalAmount: Double,         // إجمالي المبيع
    val profit: Double?,             // الربح المسجل
    val offerProfit: Double?,        // ربح العرض
    val month: Long?,                // الشهر
    val invoiceRef: String?,          // ملاحظة الاستيراد أو رقم الفاتورة
    val customerName: String? = null,
    val prescriptionImagePath: String? = null,
    /** المبلغ الأصلي قبل تعديل إجمالي الفاتورة؛ NULL للسجلات السابقة لـ Room 7. */
    val listAmount: Double? = null,
)