package com.example.myapplication.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.data.local.entity.CompanyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies ORDER BY companyName ASC")
    fun getAllCompanies(): Flow<List<CompanyEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompany(company: CompanyEntity)

    // دالة مساعدة لملء جدول الشركات من الأصناف الموجودة حالياً
    @Query("INSERT OR IGNORE INTO companies (companyName) SELECT DISTINCT company FROM items WHERE company IS NOT NULL AND company != ''")
    suspend fun populateCompaniesFromItems()
}