package com.example.myapplication.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.example.myapplication.data.local.entity.AuditLogEntity

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insertLog(log: AuditLogEntity)
}