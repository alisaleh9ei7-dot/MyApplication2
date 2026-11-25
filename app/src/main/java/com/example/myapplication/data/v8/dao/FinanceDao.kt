package com.example.myapplication.data.v8.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.myapplication.data.v8.entity.CashEntryEntity
import com.example.myapplication.data.v8.entity.ExpenseEntity

@Dao
interface FinanceDao {
    @Insert suspend fun insertExpense(expense: ExpenseEntity): Long

    /** لا حذف للمصروف؛ التصحيح بالإلغاء. */
    @Query("UPDATE expenses SET status = 'CANCELLED' WHERE expenseId = :id AND status = 'ACTIVE'")
    suspend fun cancelExpense(id: Long): Int

    @Query(
        """SELECT * FROM expenses WHERE status = 'ACTIVE' AND expenseDate >= :start AND expenseDate < :end
           ORDER BY expenseDate DESC, expenseId DESC"""
    )
    suspend fun expensesInRange(start: Long, end: Long): List<ExpenseEntity>

    @Query(
        """SELECT COALESCE(SUM(amount), 0) FROM expenses
           WHERE status = 'ACTIVE' AND expenseDate >= :start AND expenseDate < :end"""
    )
    suspend fun expensesTotal(start: Long, end: Long): Long

    @Insert suspend fun insertCashEntry(entry: CashEntryEntity): Long

    @Query("UPDATE cash_entries SET status = 'CANCELLED' WHERE entryId = :id AND status = 'ACTIVE'")
    suspend fun cancelCashEntry(id: Long): Int

    @Query(
        """SELECT COALESCE(SUM(amount), 0) FROM cash_entries
           WHERE status = 'ACTIVE' AND type = :type AND entryDate >= :start AND entryDate < :end"""
    )
    suspend fun cashTotalByType(type: String, start: Long, end: Long): Long
}
