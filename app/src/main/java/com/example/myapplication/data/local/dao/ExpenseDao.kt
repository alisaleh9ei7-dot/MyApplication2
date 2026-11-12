package com.example.myapplication.data.local.dao

import androidx.room.*
import com.example.myapplication.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT expenseId FROM expenses ORDER BY expenseId DESC LIMIT 1")
    suspend fun getLatestExpenseId(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE expenseId = :id")
    suspend fun deleteById(id: String)
}