package com.example.myapplication.data.local.dao

import androidx.room.*
import com.example.myapplication.data.local.entity.BatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches WHERE itemId = :itemId AND currentQty > 0 ORDER BY expiryDate ASC")
    fun getAvailableBatchesForItem(itemId: String): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE itemId = :itemId AND currentQty > 0 ORDER BY expiryDate ASC")
    suspend fun getBatchesForItemSync(itemId: String): List<BatchEntity>

    @Query("SELECT * FROM batches WHERE batchId = :batchId")
    suspend fun getBatchById(batchId: String): BatchEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) // 👈 بدلاً من REPLACE
    suspend fun insertBatch(batch: BatchEntity)

    @Query("UPDATE batches SET currentQty = currentQty - :qty WHERE batchId = :batchId")
    suspend fun deductBatchQuantity(batchId: String, qty: Double)

    @Query("UPDATE batches SET currentQty = currentQty + :qty WHERE batchId = :batchId")
    suspend fun restoreBatchQuantity(batchId: String, qty: Double)

    @Query("SELECT * FROM batches WHERE itemId = :itemId AND expiryDate = :expiryDate AND purchasePrice = :purchasePrice LIMIT 1")
    suspend fun getMatchingBatch(itemId: String, expiryDate: Long, purchasePrice: Double): BatchEntity?

    @Query("SELECT SUM(currentQty) FROM batches WHERE itemId = :itemId")
    suspend fun getTotalBatchQuantity(itemId: String): Double?

    @Query("UPDATE batches SET salePrice = :newPrice WHERE itemId = :itemId")
    suspend fun updateSellingPriceForItem(itemId: String, newPrice: Double)

    @Query("SELECT * FROM batches WHERE itemId = :itemId AND currentQty > 0 AND expiryDate > :now ORDER BY expiryDate ASC")
    suspend fun getSellableBatchesSync(itemId: String, now: Long): List<BatchEntity>

    @Query("UPDATE batches SET currentQty = currentQty - :qty WHERE batchId = :batchId AND currentQty >= :qty")
    suspend fun deductIfAvailable(batchId: String, qty: Double): Int

    @Update
    suspend fun updateBatch(batch: BatchEntity)
}