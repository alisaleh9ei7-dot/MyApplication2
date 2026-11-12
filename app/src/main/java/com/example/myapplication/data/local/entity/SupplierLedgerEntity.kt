package com.example.myapplication.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "supplier_ledger",
    indices = [Index("supplierName"), Index("invoiceNo")]
)
data class SupplierLedgerEntity(
    @PrimaryKey(autoGenerate = true) val opId: Long = 0,
    val opDate: Long,
    val supplierName: String,
    val orderValue: Double,
    val invoiceNo: String?,
    val paidAmount: Double,
    val debt: Double,
    val notes: String?
)