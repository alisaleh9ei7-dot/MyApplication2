package com.example.myapplication.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.myapplication.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements ORDER BY movementDate DESC")
    fun getAllMovements(): Flow<List<StockMovementEntity>>

    @Insert
    suspend fun insertMovement(movement: StockMovementEntity)

    @Query("UPDATE stock_movements SET quantity = :newQty WHERE batchId = :batchId AND reference = :invoiceRef AND movementType = 'بيع'")
    suspend fun updateSaleMovementQuantity(batchId: String, invoiceRef: String?, newQty: Double)
}