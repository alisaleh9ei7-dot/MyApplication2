package com.example.myapplication.data.v8.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.myapplication.data.v8.entity.BatchEntity

@Dao
interface BatchDao {
    @Insert suspend fun insert(batch: BatchEntity): Long

    @Query("SELECT * FROM batches WHERE batchId = :id")
    suspend fun getById(id: Long): BatchEntity?

    /**
     * دفعات الصنف القابلة للبيع بترتيب FEFO: الانتهاء ثم تاريخ الاستلام ثم المعرف،
     * والدفعات بلا صلاحية أخيراً. تستبعد المنتهية وغير القابلة للبيع والفارغة.
     */
    @Query(
        """SELECT * FROM batches
           WHERE itemId = :itemId AND isSellable = 1 AND qtyRemaining > 0
             AND (expiryDate IS NULL OR expiryDate >= :todayEpochDay)
           ORDER BY (expiryDate IS NULL) ASC, expiryDate ASC, receivedAt ASC, batchId ASC"""
    )
    suspend fun sellableFefo(itemId: Long, todayEpochDay: Long): List<BatchEntity>

    /**
     * خصم مشروط: لا ينجح إن كان المتبقي أقل من المطلوب. يرجع عدد الصفوف (0 = فشل، لا تغيير).
     * costOut تُحسب في الخدمة؛ آخر استهلاك يأخذ باقي التقريب (§3.3).
     */
    @Query(
        """UPDATE batches SET qtyRemaining = qtyRemaining - :qty, costRemaining = costRemaining - :costOut
           WHERE batchId = :batchId AND qtyRemaining >= :qty AND costRemaining >= :costOut"""
    )
    suspend fun deductIfAvailable(batchId: Long, qty: Long, costOut: Long): Int

    /**
     * استعادة مشروطة أثناء إلغاء البيع. تمنع overflow قبل تنفيذ الجمع،
     * وتعيد 0 إذا كانت الدفعة غير موجودة أو كانت القيم غير صالحة.
     */
    @Query(
        """UPDATE batches
           SET qtyRemaining = qtyRemaining + :qty,
               costRemaining = costRemaining + :costBack
           WHERE batchId = :batchId
             AND :qty > 0 AND :costBack >= 0
             -- لا نسمح باستعادة كمية تتجاوز الكمية التي دخلت الدفعة أصلاً.
             AND qtyRemaining <= qtyReceived - :qty
             -- سقف التكلفة هو مجموع تكاليف حركات الاستلام الأصلية (OPENING/PURCHASE).
             AND costRemaining <= (
                 SELECT COALESCE(SUM(m.costDelta), 0)
                 FROM stock_movements m
                 WHERE m.batchId = batches.batchId
                   AND m.type IN ('OPENING', 'PURCHASE')
                   AND m.costDelta > 0
             ) - :costBack
             -- حماية إضافية من تجاوز Long حتى لو كانت بيانات الاستلام غير سليمة.
             AND qtyRemaining <= 9223372036854775807 - :qty
             AND costRemaining <= 9223372036854775807 - :costBack"""
    )
    suspend fun restore(batchId: Long, qty: Long, costBack: Long): Int

    // تقييم المخزون بفئات متنافية بالترتيب: الحجر ← المنتهي غير المحجور ← القابل للبيع. مجموعها = Σ costRemaining.
    @Query("SELECT COALESCE(SUM(costRemaining), 0) FROM batches WHERE isSellable = 0")
    suspend fun valueQuarantine(): Long

    @Query(
        """SELECT COALESCE(SUM(costRemaining), 0) FROM batches
           WHERE isSellable = 1 AND expiryDate IS NOT NULL AND expiryDate < :todayEpochDay"""
    )
    suspend fun valueExpired(todayEpochDay: Long): Long

    @Query(
        """SELECT COALESCE(SUM(costRemaining), 0) FROM batches
           WHERE isSellable = 1 AND (expiryDate IS NULL OR expiryDate >= :todayEpochDay)"""
    )
    suspend fun valueSellable(todayEpochDay: Long): Long

    /** دفعات تقترب صلاحيتها (قبل :limitEpochDay) وما زالت صالحة، للتنبيه. */
    @Query(
        """SELECT * FROM batches
           WHERE isSellable = 1 AND qtyRemaining > 0 AND expiryDate IS NOT NULL
             AND expiryDate >= :todayEpochDay AND expiryDate < :limitEpochDay
           ORDER BY expiryDate ASC, batchId ASC"""
    )
    suspend fun expiringSoon(todayEpochDay: Long, limitEpochDay: Long): List<BatchEntity>
}
