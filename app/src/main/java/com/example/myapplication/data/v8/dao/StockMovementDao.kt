package com.example.myapplication.data.v8.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.myapplication.data.v8.entity.StockMovementEntity

/** إضافة واستعلام فقط. لا @Update ولا @Delete عن قصد؛ والـ trigger يمنعهما في القاعدة. */
@Dao
interface StockMovementDao {
    @Insert suspend fun insert(movement: StockMovementEntity): Long
    @Insert suspend fun insertAll(movements: List<StockMovementEntity>): List<Long>

    @Query("SELECT * FROM stock_movements WHERE itemId = :itemId ORDER BY movementDate DESC, movementId DESC")
    suspend fun forItem(itemId: Long): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE batchId = :batchId ORDER BY movementDate, movementId")
    suspend fun forBatch(batchId: Long): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE invoiceRef = :displayNo ORDER BY movementId")
    suspend fun forInvoiceRef(displayNo: String): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE saleId = :saleId ORDER BY movementId")
    suspend fun forSaleId(saleId: Long): List<StockMovementEntity>

    /** فترة نصف مفتوحة [start, end). */
    @Query(
        """SELECT COALESCE(SUM(costDelta), 0) FROM stock_movements
           WHERE type = :type AND movementDate >= :start AND movementDate < :end"""
    )
    suspend fun costSumByType(type: String, start: Long, end: Long): Long

    /** آخر تاريخ حركة؛ يُستخدم في كشف رجوع ساعة الجهاز (ClockGuard). */
    @Query("SELECT MAX(movementDate) FROM stock_movements")
    suspend fun lastMovementDate(): Long?
}
