package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.myapplication.data.local.dao.*
import com.example.myapplication.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    version = AppDatabase.DB_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun batchDao(): BatchDao
    abstract fun saleDao(): SaleDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun supplierLedgerDao(): SupplierLedgerDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun settingDao(): SettingDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun companyDao(): CompanyDao
    abstract fun reportDao(): ReportDao

    companion object {
        const val DB_NAME = "my_pharmacy_db"
        const val DB_VERSION = 6

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun getDatabase(context: Context): AppDatabase {
            INSTANCE?.let { return it }
            return synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { created ->
                    INSTANCE = created
                    // يُنفَّذ مرة لكل إنشاء للنسخة (عند بدء التطبيق وبعد كل استعادة).
                    // لا نستخدم Callback.onCreate/onOpen لأن INSTANCE يكون null وقتها
                    // فلا تُنفَّذ العملية أبداً في أول فتح.
                    ioScope.launch {
                        try {
                            created.companyDao().populateCompaniesFromItems()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase =
            Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME)
                // لا fallbackToDestructiveMigration: أي تغيير في المخطط يحتاج Migration صريح
                .build()

        /** يغلق القاعدة ويصفّر النسخة، ليُعاد بناؤها عند أول getDatabase() تالٍ. */
        fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.let { if (it.isOpen) it.close() }
                INSTANCE = null
            }
        }
    }
}
