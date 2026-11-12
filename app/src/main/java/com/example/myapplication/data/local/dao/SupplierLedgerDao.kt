package com.example.myapplication.data.local.dao

import androidx.room.*
import com.example.myapplication.data.local.entity.SupplierLedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierLedgerDao {
    @Query("SELECT * FROM supplier_ledger ORDER BY opDate DESC")
    fun getAllLedger(): Flow<List<SupplierLedgerEntity>>

    @Insert
    suspend fun insertRecord(record: SupplierLedgerEntity)

    // دوال إدارة الموردين الجديدة
    @Query("SELECT COUNT(*) FROM supplier_ledger WHERE supplierName = :name AND orderValue > 0")
    suspend fun getInvoiceCount(name: String): Int

    @Query("DELETE FROM supplier_ledger WHERE supplierName = :name")
    suspend fun deleteSupplierRecords(name: String)

    @Query("UPDATE supplier_ledger SET supplierName = :newName, notes = :newNotes WHERE supplierName = :oldName")
    suspend fun updateSupplier(oldName: String, newName: String, newNotes: String)

    @Query("SELECT * FROM supplier_ledger WHERE supplierName = :name ORDER BY opDate DESC")
    fun getInvoicesBySupplier(name: String): Flow<List<SupplierLedgerEntity>>
}