package com.example.myapplication.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    indices = [Index(value = ["barcode"]), Index(value = ["name"])]
)
data class ItemEntity(
    @PrimaryKey val itemId: String,
    val name: String,
    val dosageForm: String? = null,
    val company: String? = null,
    val currentStock: Double = 0.0,
    val reorderLevel: Double = 2.0,
    val activeIngredient: String? = null,
    val therapeuticClass: String? = null,
    val location: String? = null,
    val requiresPrescription: Boolean = false,
    val notes: String? = null,
    val barcode: String? = null,
)