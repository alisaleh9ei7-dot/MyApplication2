package com.example.myapplication.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.example.myapplication.data.local.entity.SaleHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleHistoryEntity): Long

    @Query("SELECT * FROM sales_history WHERE saleId = :id")
    suspend fun getSaleById(id: Long): SaleHistoryEntity?

    @Query("SELECT * FROM sales_history ORDER BY saleDate DESC")
    fun getPagedSalesHistory(): PagingSource<Int, SaleHistoryEntity>

    @Query("SELECT * FROM sales_history WHERE legacyName LIKE '%' || :searchQuery || '%' OR invoiceRef LIKE '%' || :searchQuery || '%' OR customerName LIKE '%' || :searchQuery || '%' ORDER BY saleDate DESC")
    fun getPagedSalesHistorySearch(searchQuery: String): PagingSource<Int, SaleHistoryEntity>

    @Query("DELETE FROM sales_history WHERE saleId = :id")
    suspend fun deleteSaleById(id: Long)

    @Query("DELETE FROM stock_movements WHERE batchId = :batchId AND reference = :invoiceRef")
    suspend fun deleteSaleMovement(batchId: String, invoiceRef: String?)

    // 👇 الدوال الجديدة الخاصة بالمرضى 👇
    @Query("SELECT DISTINCT customerName FROM sales_history WHERE customerName IS NOT NULL AND customerName != ''")
    fun getAllCustomerNames(): Flow<List<String>>

    @Query("""
        SELECT legacyName as medicineName, 
               MAX(saleDate) as lastPurchaseDate, 
               SUM(quantity) as totalQuantity 
        FROM sales_history 
        WHERE customerName = :customerName 
        GROUP BY legacyName 
        ORDER BY lastPurchaseDate DESC
    """)
    fun getPatientMedications(customerName: String): Flow<List<PatientMedicationSummary>>
}

// 👇 الكلاس خارج الـ interface 👇
data class PatientMedicationSummary(
    val medicineName: String,
    val lastPurchaseDate: Long,
    val totalQuantity: Double
)