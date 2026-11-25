package com.example.myapplication.data.v8.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.data.v8.entity.SaleAllocationEntity
import com.example.myapplication.data.v8.entity.SalesInvoiceEntity
import com.example.myapplication.data.v8.entity.SalesLineEntity

@Dao
interface SalesDao {
    @Insert suspend fun insertInvoice(invoice: SalesInvoiceEntity): Long
    @Insert suspend fun insertLine(line: SalesLineEntity): Long
    @Insert suspend fun insertAllocation(allocation: SaleAllocationEntity): Long

    @Query("SELECT * FROM sales_invoices WHERE saleId = :id")
    suspend fun getInvoice(id: Long): SalesInvoiceEntity?

    /** لمنع تكرار التأكيد عند النقر المزدوج/إعادة المحاولة. */
    @Query("SELECT * FROM sales_invoices WHERE cartUuid = :uuid LIMIT 1")
    suspend fun findByCartUuid(uuid: String): SalesInvoiceEntity?

    @Query("SELECT * FROM sales_lines WHERE saleId = :saleId ORDER BY lineId")
    suspend fun linesFor(saleId: Long): List<SalesLineEntity>

    @Query(
        """SELECT a.* FROM sale_allocations a
           JOIN sales_lines l ON a.lineId = l.lineId
           WHERE l.saleId = :saleId ORDER BY a.allocId"""
    )
    suspend fun allocationsFor(saleId: Long): List<SaleAllocationEntity>

    @Query("SELECT * FROM sales_invoices ORDER BY COALESCE(confirmedAt, 0) DESC, saleId DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SalesInvoiceEntity>

    /** التثبيت الفعلي فقط ينقل الفاتورة من PENDING إلى CONFIRMED ويسجل وقته. */
    @Query(
        """UPDATE sales_invoices SET status = 'CONFIRMED', confirmedAt = :confirmedAt
           WHERE saleId = :saleId AND status = 'PENDING'"""
    )
    suspend fun confirmPending(saleId: Long, confirmedAt: Long): Int

    /** الإلغاء بشرط CONFIRMED؛ السبب اختياري ويُنسخ إلى الحركة العكسية بواسطة Trigger. */
    @Query(
        """UPDATE sales_invoices SET status = 'CANCELLED', cancelledAt = :cancelledAt,
           cancelReason = :cancelReason
           WHERE saleId = :saleId AND status = 'CONFIRMED'"""
    )
    suspend fun markCancelled(saleId: Long, cancelledAt: Long, cancelReason: String?): Int

    // ---- العدّاد الشهري: يُستدعى كله داخل معاملة التأكيد ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureCounter(counter: com.example.myapplication.data.v8.entity.InvoiceCounterEntity): Long

    @Query("UPDATE invoice_counters SET lastNumber = lastNumber + 1 WHERE yearMonth = :yearMonth AND lastNumber < 999999")
    suspend fun bumpCounter(yearMonth: Int): Int

    @Query("SELECT lastNumber FROM invoice_counters WHERE yearMonth = :yearMonth")
    suspend fun counterValue(yearMonth: Int): Long?

    // ---- تجميعات التقارير: فترات نصف مفتوحة [start, end) على فواتير CONFIRMED ----
    @Query(
        """SELECT COALESCE(SUM(netTotal), 0) FROM sales_invoices
           WHERE status = 'CONFIRMED' AND confirmedAt >= :start AND confirmedAt < :end"""
    )
    suspend fun netSales(start: Long, end: Long): Long

    @Query(
        """SELECT COALESCE(SUM(a.costFrozen), 0) FROM sale_allocations a
           JOIN sales_lines l ON a.lineId = l.lineId
           JOIN sales_invoices s ON l.saleId = s.saleId
           WHERE s.status = 'CONFIRMED' AND s.confirmedAt >= :start AND s.confirmedAt < :end"""
    )
    suspend fun frozenCost(start: Long, end: Long): Long
}
