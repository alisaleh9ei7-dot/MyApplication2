package com.example.myapplication.data.v8.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.data.v8.entity.SupplierEntity
import com.example.myapplication.data.v8.entity.SupplierEntryEntity
import com.example.myapplication.data.v8.entity.SupplierEntryType
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Insert suspend fun insert(supplier: SupplierEntity): Long
    @Update suspend fun update(supplier: SupplierEntity): Int

    @Query("SELECT * FROM suppliers WHERE supplierId = :id")
    suspend fun getById(id: Long): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<SupplierEntity>>

    @Query("UPDATE suppliers SET isActive = :active WHERE supplierId = :id")
    suspend fun setActive(id: Long, active: Boolean): Int

    @Insert suspend fun insertEntry(entry: SupplierEntryEntity): Long

    /** إلغاء منضبط بدل الحذف. */
    @Query("UPDATE supplier_entries SET status = 'CANCELLED' WHERE entryId = :entryId AND status = 'ACTIVE'")
    suspend fun cancelEntry(entryId: Long): Int

    @Query("SELECT * FROM supplier_entries WHERE supplierId = :supplierId ORDER BY entryDate DESC, entryId DESC")
    suspend fun entriesFor(supplierId: Long): List<SupplierEntryEntity>

    @Query(
        """SELECT COALESCE(SUM(amount), 0) FROM supplier_entries
           WHERE supplierId = :supplierId AND type = :type AND status = 'ACTIVE'"""
    )
    suspend fun entriesTotal(supplierId: Long, type: SupplierEntryType): Long

    /** مجموع فواتير الشراء المؤكدة للمورد (جزء من رصيده). */
    @Query(
        """SELECT COALESCE(SUM(netTotal), 0) FROM purchase_invoices
           WHERE supplierId = :supplierId AND status = 'CONFIRMED'"""
    )
    suspend fun confirmedPurchasesTotal(supplierId: Long): Long
}
