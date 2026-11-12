package com.example.myapplication.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_movements",
    indices = [Index("batchId"), Index("itemId"), Index("movementDate")]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val movementId: Long = 0,
    val movementDate: Long,       // التاريخ والوقت
    val movementType: String,     // رصيد افتتاحي، بيع، شراء
    val batchId: String?,         // يقبل الفراغ للحركات اليتيمة
    val itemId: String?,          // يقبل الفراغ
    val quantity: Double,         // الكمية
    val reference: String?,       // سبب/مرجع (رقم فاتورة أو نص)
    val note: String?             // ملاحظة
)