package com.example.myapplication.data.v8.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** المصروف يُسجَّل عند الدفع فقط. لا حذف؛ الإلغاء بتغيير الحالة. الشهر يُحسب من expenseDate. */
@Entity(tableName = "expenses", indices = [Index(value = ["expenseDate"])])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val expenseId: Long = 0,
    val expenseDate: Long,
    val category: String,
    val description: String? = null,
    val amount: Long,
    val status: RecordStatus = RecordStatus.ACTIVE
)

/** حركات نقدية يدوية فقط؛ لا تدخل في الربح أو المصروفات. */
@Entity(tableName = "cash_entries", indices = [Index(value = ["entryDate"])])
data class CashEntryEntity(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0,
    val entryDate: Long,
    val type: CashEntryType,
    val amount: Long,
    val note: String? = null,
    val status: RecordStatus = RecordStatus.ACTIVE
)
