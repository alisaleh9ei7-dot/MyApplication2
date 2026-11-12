package com.example.myapplication.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val opTime: Long,
    val userEmail: String?,
    val operation: String,
    val recordType: String?,
    val affectedId: String?,
    val beforeJson: String?,
    val afterJson: String?,
    val result: String,
    val details: String?
)