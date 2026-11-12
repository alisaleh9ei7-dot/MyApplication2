package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.myapplication.data.local.dao.*
import com.example.myapplication.data.local.entity.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ItemEntity::class,
        BatchEntity::class,
        SaleHistoryEntity::class,
        StockMovementEntity::class,
        SupplierLedgerEntity::class,
        ExpenseEntity::class,
        SettingEntity::class,
        CompanyEntity::class,
        AuditLogEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // ربط الـ DAOs
    abstract fun itemDao(): ItemDao
    abstract fun batchDao(): BatchDao
    abstract fun saleDao(): SaleDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun supplierLedgerDao(): SupplierLedgerDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun settingDao(): SettingDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun dashboardDao(): DashboardDao // <--- تمت الإضافة هنا
    abstract fun companyDao(): CompanyDao
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "my_pharmacy_db" // تأكد من الاسم
                )
                    .fallbackToDestructiveMigration() // 👈 ضروري لأننا رفعنا الإصدار لـ 6
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.companyDao()?.populateCompaniesFromItems()
                            }
                        }
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.companyDao()?.populateCompaniesFromItems()
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}