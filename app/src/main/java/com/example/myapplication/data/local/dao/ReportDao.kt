package com.example.myapplication.data.local.dao

import androidx.room.Dao
import androidx.room.Query

// كلاس لتمثيل بيانات الدفعات القريبة من الانتهاء
data class NearExpiryReportItem(
    val name: String,
    val batchId: String,
    val qty: Double,
    val expiry: Long,
    val daysLeft: Int
)

// كلاس لتمثيل حركة الأصناف
data class MovementReportItem(
    val name: String,
    val qty: Double,
    val sales: Double,
    val profit: Double
)

// --- كلاس جديد لتقرير الأدوية المنتهية ---
data class ExpiredReportItem(
    val name: String,
    val batchId: String,
    val qty: Double,
    val expiry: Long,
    val daysExpired: Int
)

data class SupplierSummary(
    val supplierName: String,
    val totalPurchases: Double,
    val totalPaid: Double,
    val totalDebt: Double
)

@Dao
interface ReportDao {
    // تقرير الدفعات القريبة من الانتهاء (خلال 90 يوماً)
    @Query("""
        SELECT drugName as name, batchId, currentQty as qty, expiryDate as expiry, 
        CAST((expiryDate - :today) / (1000 * 60 * 60 * 24) AS INTEGER) as daysLeft 
        FROM batches 
        WHERE currentQty > 0 AND expiryDate >= :today AND expiryDate <= :futureDate 
        ORDER BY expiryDate ASC
    """)
    suspend fun getNearExpiryBatches(today: Long, futureDate: Long): List<NearExpiryReportItem>

    // تقرير حركة الأصناف (المبيعات المجمعة حسب الصنف في فترة معينة)
    @Query("""
        SELECT legacyName as name, SUM(quantity) as qty, SUM(totalAmount) as sales, SUM(profit) as profit 
        FROM sales_history 
        WHERE saleDate BETWEEN :start AND :end 
        GROUP BY legacyName 
        ORDER BY sales DESC
    """)
    suspend fun getMovementReport(start: Long, end: Long): List<MovementReportItem>

    // --- استعلام جديد: تقرير الأدوية المنتهية (الصلاحية أقل من اليوم والكمية أكبر من صفر) ---
    @Query("""
        SELECT drugName as name, batchId, currentQty as qty, expiryDate as expiry,
        CAST((:today - expiryDate) / (1000 * 60 * 60 * 24) AS INTEGER) as daysExpired
        FROM batches 
        WHERE currentQty > 0 AND expiryDate < :today 
        ORDER BY expiryDate ASC
    """)
    suspend fun getExpiredBatches(today: Long): List<ExpiredReportItem>

    // استعلام لحساب إجمالي المصاريف في فترة معينة
    @Query("SELECT SUM(amount) FROM expenses WHERE expenseDate BETWEEN :start AND :end")
    suspend fun getExpensesBetween(start: Long, end: Long): Double?

    // 1. الأبطال: الأفضل مبيعاً (بالكمية) على الإطلاق
    @Query("""
    SELECT legacyName as name, SUM(quantity) as qty, SUM(totalAmount) as sales, SUM(profit) as profit 
    FROM sales_history 
    GROUP BY legacyName 
    ORDER BY qty DESC 
    LIMIT 10
""")
    suspend fun getTopSellingByQuantity(): List<MovementReportItem>

    // 2. الأبطال: الأفضل مبيعاً (بالقيمة) على الإطلاق
    @Query("""
    SELECT legacyName as name, SUM(quantity) as qty, SUM(totalAmount) as sales, SUM(profit) as profit 
    FROM sales_history 
    GROUP BY legacyName 
    ORDER BY sales DESC 
    LIMIT 10
""")
    suspend fun getTopSellingByValue(): List<MovementReportItem>

    // 3. الأبطال: الأكثر ربحاً على الإطلاق
    @Query("""
    SELECT legacyName as name, SUM(quantity) as qty, SUM(totalAmount) as sales, SUM(profit) as profit 
    FROM sales_history 
    GROUP BY legacyName 
    ORDER BY profit DESC 
    LIMIT 10
""")
    suspend fun getTopProfitByValue(): List<MovementReportItem>

    // 4. الديون والموردين: إجمالي المشتريات والمدفوع والدين لكل مورد
    @Query("""
    SELECT supplierName, SUM(orderValue) as totalPurchases, SUM(paidAmount) as totalPaid, SUM(debt) as totalDebt
    FROM supplier_ledger 
    GROUP BY supplierName
""")
    suspend fun getSupplierSummary(): List<SupplierSummary>
}