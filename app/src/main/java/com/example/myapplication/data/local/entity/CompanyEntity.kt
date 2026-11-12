package com.example.myapplication.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey val companyName: String
)