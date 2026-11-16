package com.example.myapplication.data.local.dao


import androidx.room.Dao
import androidx.room.Query
import com.example.myapplication.data.local.entity.SaleHistoryEntity

@Dao
interface DashboardDao {
    // مبيعات وربح اليوم
    @Query("SELECT SUM(totalAmount) FROM sales_history WHERE saleDate BETWEEN :start AND :end")
    suspend fun getSalesBetween(start: Long, end: Long): Double?

    @Query("SELECT SUM(profit) FROM sales_history WHERE saleDate BETWEEN :start AND :end")
    suspend fun getProfitBetween(start: Long, end: Long): Double?

    // قيمة المخزون الإجمالية (كمية * تكلفة الوحدة) للدفعات غير المنتهية
    @Query("SELECT SUM(currentQty * unitCost) FROM batches WHERE currentQty > 0 AND expiryDate > :currentTime")
    suspend fun getTotalInventoryValue(currentTime: Long): Double?
    // إجمالي مصاريف الشهر
    @Query("SELECT SUM(amount) FROM expenses WHERE month = :monthKey")
    suspend fun getExpensesForMonth(monthKey: Long): Double?

    // الأصناف منخفضة المخزون (أكبر من صفر وأقل من أو يساوي حد الطلب)
    @Query("SELECT COUNT(*) FROM items WHERE currentStock > 0 AND currentStock <= reorderLevel")
    suspend fun getLowStockCount(): Int

    // الدفعات القريبة من الانتهاء (خلال 90 يوماً)
    @Query("SELECT COUNT(*) FROM batches WHERE currentQty > 0 AND expiryDate >= :today AND expiryDate <= :futureDate")
    suspend fun getExpiringSoonCount(today: Long, futureDate: Long): Int

    // ربح التصفية للشهر
    @Query("SELECT SUM(offerProfit) FROM sales_history WHERE saleDate BETWEEN :start AND :end")
    suspend fun getOfferProfitBetween(start: Long, end: Long): Double?

    // جلب مبيعات آخر 7 أيام للرسم البياني
    @Query("SELECT * FROM sales_history WHERE saleDate >= :startDate ORDER BY saleDate ASC")
    suspend fun getSalesSince(startDate: Long): List<SaleHistoryEntity>

    // جلب أحدث 5 عمليات بيع
    @Query("SELECT * FROM sales_history ORDER BY saleDate DESC LIMIT 5")
    suspend fun getRecentSales(): List<SaleHistoryEntity>
}