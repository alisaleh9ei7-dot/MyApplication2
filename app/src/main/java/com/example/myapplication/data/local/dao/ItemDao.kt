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

    @Insert(onConflict = OnConflictStrategy.IGNORE) // 👈 بدلاً من REPLACE
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

    // جلب الأصناف التي لديها دفعات منتهية الصلاحية (currentQty > 0)
    @Query("""
    SELECT DISTINCT i.* FROM items i 
    INNER JOIN batches b ON i.itemId = b.itemId 
    WHERE b.currentQty > 0 AND b.expiryDate < :today
""")
    suspend fun getItemsWithExpiredBatches(today: Long): List<ItemEntity>

    // جلب الأصناف التي لديها دفعات تنتهي خلال الفترة القادمة
    @Query("""
    SELECT DISTINCT i.* FROM items i 
    INNER JOIN batches b ON i.itemId = b.itemId 
    WHERE b.currentQty > 0 AND b.expiryDate BETWEEN :today AND :futureDate
""")
    suspend fun getItemsWithExpiringBatches(today: Long, futureDate: Long): List<ItemEntity>
}