package com.example.myapplication.data.v8.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.myapplication.data.v8.entity.ItemChangeEntity
import com.example.myapplication.data.v8.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * todayEpochDay in queries is the current day's epoch-day from AppClock.
 * Stock is calculated from batches and is not stored on the item.
 */
@Dao
interface ItemDao {
    @Insert
    suspend fun insert(item: ItemEntity): Long

    /** Update catalog fields without replacing the complete item row. */
    @Query(
        """UPDATE items SET tradeName = :tradeName, activeIngredient = :activeIngredient,
           strength = :strength, dosageForm = :dosageForm, company = :company,
           category = :category, location = :location, barcode = :barcode,
           requiresPrescription = :requiresPrescription, boxSalePrice = :boxSalePrice,
           reorderLevel = :reorderLevel, isActive = :isActive
           WHERE itemId = :itemId"""
    )
    suspend fun updateCatalogFields(
        itemId: Long, tradeName: String, activeIngredient: String, strength: String,
        dosageForm: String, company: String, category: String, location: String?,
        barcode: String?, requiresPrescription: Boolean, boxSalePrice: Long?,
        reorderLevel: Long, isActive: Boolean
    ): Int

    /**
     * Change the conversion factor only when the item has never had any batch,
     * purchase line, sales line, or stock movement. Once one exists the factor is
     * permanently fixed, even if all remaining stock is zero.
     * The service must insert the corresponding ItemChangeEntity in the same transaction.
     */
    @Query(
        """UPDATE items SET stripsPerBox = :stripsPerBox
           WHERE itemId = :itemId
             AND (:stripsPerBox IS NULL OR :stripsPerBox >= 2)
             AND NOT EXISTS (SELECT 1 FROM batches b WHERE b.itemId = :itemId)
             AND NOT EXISTS (SELECT 1 FROM purchase_lines p WHERE p.itemId = :itemId)
             AND NOT EXISTS (SELECT 1 FROM sales_lines s WHERE s.itemId = :itemId)
             AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.itemId = :itemId)"""
    )
    suspend fun updateStripsPerBoxWhenNoHistory(itemId: Long, stripsPerBox: Int?): Int

    /** True when at least one batch of this item has a non-zero remaining quantity. */
    @Query("SELECT EXISTS(SELECT 1 FROM batches WHERE itemId = :itemId AND qtyRemaining <> 0)")
    suspend fun hasNonZeroBatchStock(itemId: Long): Boolean

    /**
     * True when the item has any record that gives its conversion factor a historical meaning:
     * a batch (even with zero quantity), purchase line, sales line, or stock movement.
     */
    @Query(
        """SELECT EXISTS(SELECT 1 FROM batches WHERE itemId = :itemId)
           OR EXISTS(SELECT 1 FROM purchase_lines WHERE itemId = :itemId)
           OR EXISTS(SELECT 1 FROM sales_lines WHERE itemId = :itemId)
           OR EXISTS(SELECT 1 FROM stock_movements WHERE itemId = :itemId)"""
    )
    suspend fun hasFactorHistory(itemId: Long): Boolean

    @Query("SELECT * FROM items WHERE itemId = :itemId")
    suspend fun getById(itemId: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ItemEntity?

    @Query("SELECT * FROM items WHERE isActive = 1 ORDER BY tradeName")
    fun observeActive(): Flow<List<ItemEntity>>

    /** Instant search by trade name, active ingredient, strength, company, or barcode. */
    @Query(
        """SELECT * FROM items WHERE isActive = 1 AND (
            tradeName LIKE '%' || :q || '%' OR activeIngredient LIKE '%' || :q || '%' OR
            strength LIKE '%' || :q || '%' OR company LIKE '%' || :q || '%' OR
            barcode LIKE '%' || :q || '%')
           ORDER BY tradeName LIMIT :limit"""
    )
    suspend fun search(q: String, limit: Int): List<ItemEntity>

    @Query("UPDATE items SET isActive = :active WHERE itemId = :itemId")
    suspend fun setActive(itemId: Long, active: Boolean): Int

    /** Whether the item has historical stock, purchase, or sale records. */
    @Query(
        """SELECT EXISTS(SELECT 1 FROM stock_movements WHERE itemId = :itemId)
           OR EXISTS(SELECT 1 FROM purchase_lines WHERE itemId = :itemId)
           OR EXISTS(SELECT 1 FROM sales_lines WHERE itemId = :itemId)"""
    )
    suspend fun hasHistory(itemId: Long): Boolean

    /** Delete only an item without history; the service checks this before calling. */
    @Query("DELETE FROM items WHERE itemId = :itemId")
    suspend fun deleteById(itemId: Long): Int

    /** Sellable, unexpired stock. */
    @Query(
        """SELECT COALESCE(SUM(qtyRemaining), 0) FROM batches
           WHERE itemId = :itemId AND isSellable = 1
             AND (expiryDate IS NULL OR expiryDate >= :todayEpochDay)"""
    )
    suspend fun sellableStock(itemId: Long, todayEpochDay: Long): Long

    /** Actual stock across all batches, including blocked and expired batches. */
    @Query("SELECT COALESCE(SUM(qtyRemaining), 0) FROM batches WHERE itemId = :itemId")
    suspend fun totalStock(itemId: Long): Long

    @Insert
    suspend fun insertChange(change: ItemChangeEntity): Long

    @Query("SELECT * FROM item_changes WHERE itemId = :itemId ORDER BY changedAt DESC, changeId DESC")
    suspend fun changesFor(itemId: Long): List<ItemChangeEntity>
}
