package com.example.myapplication.data.local.dao

import androidx.room.*
import com.example.myapplication.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SettingEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(setting: SettingEntity): Long

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}