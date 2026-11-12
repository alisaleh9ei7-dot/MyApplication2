package com.example.myapplication.data.local.dao

import androidx.room.*
import com.example.myapplication.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getAllItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE itemId = :id")
    suspend fun getItemById(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE name = :name LIMIT 1")
    suspend fun getItemByName(name: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemEntity>)

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("UPDATE items SET currentStock = currentStock - :qty WHERE itemId = :itemId")
    suspend fun deductItemStock(itemId: String, qty: Double)

    // دوال الحزمة الثالثة
    @Query("UPDATE items SET reorderLevel = :level WHERE itemId = :itemId")
    suspend fun updateReorderLevel(itemId: String, level: Double)

    // إصلاح الرصيد لصنف واحد
    @Query("UPDATE items SET currentStock = (SELECT COALESCE(SUM(currentQty), 0) FROM batches WHERE itemId = :itemId) WHERE itemId = :itemId")
    suspend fun repairSingleBalance(itemId: String)

    // إصلاح الرصيد لجميع الأصناف
    @Query("UPDATE items SET currentStock = (SELECT COALESCE(SUM(currentQty), 0) FROM batches WHERE itemId = items.itemId)")
    suspend fun repairAllBalances()

    // البحث بالباركود
    @Query("SELECT * FROM items WHERE barcode = :barcode LIMIT 1")
    suspend fun getItemByBarcode(barcode: String): ItemEntity?

    // 👇 دالة تحديث الباركود الجديدة 👇
    @Query("UPDATE items SET barcode = :barcode WHERE itemId = :itemId")
    suspend fun updateBarcode(itemId: String, barcode: String)
}