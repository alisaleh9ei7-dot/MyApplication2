package com.example.myapplication.data.v8.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "suppliers", indices = [Index(value = ["name"], unique = true)])
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val supplierId: Long = 0,
    val name: String,
    val phone: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long
)

/**
 * حركات حساب المورد اليدوية فقط. فواتير الشراء والمرتجعات تُقرأ من مستنداتها.
 * رصيد المورد = OPENING + فواتير الشراء المؤكدة − PAYMENT − ائتمان مرتجعات المورد + RECEIPT.
 */
@Entity(
    tableName = "supplier_entries",
    foreignKeys = [ForeignKey(
        entity = SupplierEntity::class,
        parentColumns = ["supplierId"], childColumns = ["supplierId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index(value = ["supplierId"]), Index(value = ["entryDate"])]
)
data class SupplierEntryEntity(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0,
    val supplierId: Long,
    val type: SupplierEntryType,
    val amount: Long,
    val entryDate: Long,
    val status: RecordStatus = RecordStatus.ACTIVE,
    val reference: String? = null,
    val note: String? = null
)
