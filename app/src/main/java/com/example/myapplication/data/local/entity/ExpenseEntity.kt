package com.example.myapplication.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val expenseId: String, // EXP-0001
    val expenseDate: Long,
    val category: String,
    val description: String?,
    val amount: Double,
    val month: Long?
)