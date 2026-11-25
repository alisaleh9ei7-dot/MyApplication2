package com.example.myapplication.data.v8.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.data.v8.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

/** DAO للإعدادات؛ لا يستخدم REPLACE لتجنب دلالات الحذف وإعادة الإدراج. */
@Dao
interface SettingDao {
    @Query("SELECT * FROM settings ORDER BY `key`")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): SettingEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(setting: SettingEntity): Long

    @Update
    suspend fun update(setting: SettingEntity): Int

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteByKey(key: String): Int
}
