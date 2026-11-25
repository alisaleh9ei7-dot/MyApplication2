package com.example.myapplication.data.v8.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.myapplication.data.v8.entity.PurchaseInvoiceEntity
import com.example.myapplication.data.v8.entity.PurchaseLineEntity

@Dao
interface PurchaseDao {
    @Insert suspend fun insertInvoice(invoice: PurchaseInvoiceEntity): Long
    @Insert suspend fun insertLine(line: PurchaseLineEntity): Long

    @Query("SELECT * FROM purchase_invoices WHERE purchaseId = :id")
    suspend fun getInvoice(id: Long): PurchaseInvoiceEntity?

    /** لمنع تكرار التأكيد: إن وُجدت فاتورة بالمعرّف نفسه فهي نتيجة المحاولة الأولى. */
    @Query("SELECT * FROM purchase_invoices WHERE requestUuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): PurchaseInvoiceEntity?

    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :purchaseId ORDER BY lineId")
    suspend fun linesFor(purchaseId: Long): List<PurchaseLineEntity>

    /** يغيّر الحالة بشرط الحالة السابقة، فيرجع 0 إن تغيّرت الحالة من تحت الخدمة. */
    @Query(
        """UPDATE purchase_invoices SET status = :newStatus, confirmedAt = :confirmedAt, netTotal = :netTotal
           WHERE purchaseId = :id AND status = :expected"""
    )
    suspend fun transition(id: Long, expected: String, newStatus: String, confirmedAt: Long?, netTotal: Long): Int

    @Query("DELETE FROM purchase_lines WHERE purchaseId = :purchaseId")
    suspend fun deleteLinesOfDraft(purchaseId: Long): Int

    @Query("DELETE FROM purchase_invoices WHERE purchaseId = :purchaseId")
    suspend fun deleteDraft(purchaseId: Long): Int
}
