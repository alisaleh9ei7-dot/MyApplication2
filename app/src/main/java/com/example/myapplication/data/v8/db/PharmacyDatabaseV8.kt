package com.example.myapplication.data.v8.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.data.v8.dao.BatchDao
import com.example.myapplication.data.v8.dao.FinanceDao
import com.example.myapplication.data.v8.dao.ItemDao
import com.example.myapplication.data.v8.dao.PurchaseDao
import com.example.myapplication.data.v8.dao.SalesDao
import com.example.myapplication.data.v8.dao.StockMovementDao
import com.example.myapplication.data.v8.dao.SupplierDao
import com.example.myapplication.data.v8.dao.SettingDao
import com.example.myapplication.data.v8.entity.BatchEntity
import com.example.myapplication.data.v8.entity.CashEntryEntity
import com.example.myapplication.data.v8.entity.ExpenseEntity
import com.example.myapplication.data.v8.entity.InvoiceCounterEntity
import com.example.myapplication.data.v8.entity.ItemChangeEntity
import com.example.myapplication.data.v8.entity.ItemEntity
import com.example.myapplication.data.v8.entity.PurchaseInvoiceEntity
import com.example.myapplication.data.v8.entity.PurchaseLineEntity
import com.example.myapplication.data.v8.entity.SaleAllocationEntity
import com.example.myapplication.data.v8.entity.SalesInvoiceEntity
import com.example.myapplication.data.v8.entity.SalesLineEntity
import com.example.myapplication.data.v8.entity.StockMovementEntity
import com.example.myapplication.data.v8.entity.SupplierEntity
import com.example.myapplication.data.v8.entity.SupplierEntryEntity
import com.example.myapplication.data.v8.entity.SettingEntity

/**
 * قاعدة V8 مستقلة. لا تستبدل AppDatabase القديمة ولا تُربط بالتطبيق الإنتاجي في هذه المرحلة.
 * لا تُفتح القاعدة قبل تثبيت Triggers والتحقق من تعريفاتها وسلامة بياناتها.
 */
@Database(
    entities = [
        ItemEntity::class, ItemChangeEntity::class,
        SupplierEntity::class, SupplierEntryEntity::class,
        PurchaseInvoiceEntity::class, PurchaseLineEntity::class,
        BatchEntity::class, StockMovementEntity::class,
        SalesInvoiceEntity::class, SalesLineEntity::class, SaleAllocationEntity::class,
        InvoiceCounterEntity::class,
        ExpenseEntity::class, CashEntryEntity::class,
        SettingEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class PharmacyDatabaseV8 : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun batchDao(): BatchDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun salesDao(): SalesDao
    abstract fun financeDao(): FinanceDao
    abstract fun settingDao(): SettingDao

    companion object {
        const val DATABASE_NAME = "pharmacy_v8.db"

        @Volatile
        private var INSTANCE: PharmacyDatabaseV8? = null

        /** Builder مستقل؛ لا يوجد fallback destructive ولا أي ربط بقاعدة V7. */
        fun getDatabase(context: Context): PharmacyDatabaseV8 =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PharmacyDatabaseV8::class.java,
                    DATABASE_NAME
                ).addCallback(TRIGGERS_CALLBACK).build().also { INSTANCE = it }
            }

        /** التعريفات المرجعية الوحيدة للـ persistent triggers في V8. */
        val TRIGGERS: List<String> = listOf(
            // سجل الحركات للإضافة فقط.
            """CREATE TRIGGER trg_movements_no_update BEFORE UPDATE ON stock_movements
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_MOVEMENT_IMMUTABLE'); END""",
            """CREATE TRIGGER trg_movements_no_delete BEFORE DELETE ON stock_movements
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_MOVEMENT_IMMUTABLE'); END""",

            // لا رصيد أو تكلفة سالبة في الدفعات.
            """CREATE TRIGGER trg_batches_no_negative_ins BEFORE INSERT ON batches
               WHEN NEW.qtyRemaining < 0 OR NEW.costRemaining < 0
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_NEGATIVE_BATCH_BALANCE'); END""",
            """CREATE TRIGGER trg_batches_no_negative_upd BEFORE UPDATE ON batches
               WHEN NEW.qtyRemaining < 0 OR NEW.costRemaining < 0
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_NEGATIVE_BATCH_BALANCE'); END""",
            """CREATE TRIGGER trg_batches_no_delete BEFORE DELETE ON batches
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_RECORD_DELETE_NOT_ALLOWED'); END""",

            // D1: لا تُنشأ فاتورة مؤكدة مباشرة؛ تبدأ كل فاتورة PENDING بلا أوقات انتقال.
            """CREATE TRIGGER trg_sales_invoice_initial_state
               BEFORE INSERT ON sales_invoices
               WHEN NEW.status <> 'PENDING'
                 OR NEW.confirmedAt IS NOT NULL
                 OR NEW.cancelledAt IS NOT NULL
                 OR NEW.cancelReason IS NOT NULL
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_SALE_INITIAL_STATE_INVALID'); END""",

            // D1: الانتقالات المسموحة فقط PENDING -> CONFIRMED -> CANCELLED،
            // مع تثبيت الطوابع الزمنية ومنع تعديلها خارج انتقال الحالة.
            """CREATE TRIGGER trg_sales_invoice_state_transition
               BEFORE UPDATE OF status, confirmedAt, cancelledAt, cancelReason ON sales_invoices
               WHEN
                 (OLD.status = 'PENDING' AND NEW.status = 'CONFIRMED' AND (
                      NEW.confirmedAt IS NULL
                      OR NEW.cancelledAt IS NOT NULL
                      OR NEW.cancelReason IS NOT NULL
                  )) OR
                 (OLD.status = 'CONFIRMED' AND NEW.status = 'CANCELLED' AND (
                      OLD.confirmedAt IS NULL
                      OR NEW.confirmedAt IS NOT OLD.confirmedAt
                      OR NEW.cancelledAt IS NULL
                      OR NEW.cancelledAt < OLD.confirmedAt
                      OR NOT EXISTS(
                          SELECT 1 FROM stock_movements s
                          WHERE s.saleId = OLD.saleId AND s.type = 'SALE'
                      )
                      OR EXISTS(
                          SELECT 1 FROM stock_movements s
                          WHERE s.saleId = OLD.saleId AND s.type = 'SALE'
                            AND NOT EXISTS(
                                SELECT 1 FROM stock_movements r
                                WHERE r.type = 'SALE_CANCEL'
                                  AND r.reversesMovementId = s.movementId
                                  AND r.saleId = OLD.saleId
                                  AND r.invoiceRef = OLD.displayNo
                                  AND r.batchId = s.batchId
                                  AND r.itemId = s.itemId
                                  AND r.qtyDelta = -s.qtyDelta
                                  AND r.costDelta = -s.costDelta
                                  AND r.allocId IS NULL
                                  AND r.reason IS NEW.cancelReason
                            )
                      )
                      OR EXISTS(
                          SELECT 1 FROM stock_movements r
                          WHERE r.saleId = OLD.saleId AND r.type = 'SALE_CANCEL'
                            AND (r.reason IS NOT NEW.cancelReason
                                 OR NOT EXISTS(
                                     SELECT 1 FROM stock_movements s
                                     WHERE s.movementId = r.reversesMovementId
                                       AND s.type = 'SALE' AND s.saleId = OLD.saleId
                                 ))
                      )
                  )) OR
                 (NEW.status = OLD.status AND (
                      NEW.confirmedAt IS NOT OLD.confirmedAt
                      OR NEW.cancelledAt IS NOT OLD.cancelledAt
                      OR NEW.cancelReason IS NOT OLD.cancelReason
                  )) OR
                 NOT (
                      (OLD.status = 'PENDING' AND NEW.status = 'CONFIRMED')
                      OR (OLD.status = 'CONFIRMED' AND NEW.status = 'CANCELLED')
                      OR (NEW.status = OLD.status)
                 )
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_SALE_STATE_TRANSITION_INVALID'); END""",

            // لا حذف تاريخي للفواتير وبنود البيع والتخصيص.
            """CREATE TRIGGER trg_sales_invoices_no_delete BEFORE DELETE ON sales_invoices
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_RECORD_DELETE_NOT_ALLOWED'); END""",
            """CREATE TRIGGER trg_sales_lines_no_delete BEFORE DELETE ON sales_lines
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_RECORD_DELETE_NOT_ALLOWED'); END""",
            """CREATE TRIGGER trg_sale_alloc_no_delete BEFORE DELETE ON sale_allocations
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_RECORD_DELETE_NOT_ALLOWED'); END""",

            // فواتير الشراء: تُحذف المسودة فقط.
            """CREATE TRIGGER trg_purchase_inv_delete_draft_only BEFORE DELETE ON purchase_invoices
               WHEN OLD.status <> 'DRAFT'
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_PURCHASE_DELETE_NOT_ALLOWED'); END""",
            """CREATE TRIGGER trg_purchase_lines_delete_draft_only BEFORE DELETE ON purchase_lines
               WHEN (SELECT status FROM purchase_invoices WHERE purchaseId = OLD.purchaseId) <> 'DRAFT'
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_PURCHASE_LINE_DELETE_NOT_ALLOWED'); END""",

            // U4: الصنف إما غير قابل للتقسيم (NULL) أو معامل فعلي >= 2.
            """CREATE TRIGGER trg_items_strips_per_box_insert
               BEFORE INSERT ON items
               WHEN NEW.stripsPerBox IS NOT NULL AND NEW.stripsPerBox < 2
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_INVALID_STRIPS_PER_BOX'); END""",
            """CREATE TRIGGER trg_items_strips_per_box_update
               BEFORE UPDATE OF stripsPerBox ON items
               WHEN NEW.stripsPerBox IS NOT NULL AND NEW.stripsPerBox < 2
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_INVALID_STRIPS_PER_BOX'); END""",

            // D2: التغيير مسموح مع التاريخ إذا كان رصيد كل دفعات الصنف صفراً.
            // ItemDao يكرر الشرط في UPDATE، وهذا Trigger هو خط الدفاع الأخير.
            """CREATE TRIGGER trg_items_strips_per_box_lock
               BEFORE UPDATE OF stripsPerBox ON items
               WHEN OLD.stripsPerBox IS NOT NEW.stripsPerBox
                AND EXISTS(
                    SELECT 1 FROM batches
                    WHERE itemId = OLD.itemId AND qtyRemaining <> 0
                )
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_ITEM_FACTOR_LOCKED_BY_STOCK'); END""",

            // D2-B: تثبيت معامل التحويل عند إنشاء بنود الشراء والبيع.
            """CREATE TRIGGER trg_purchase_lines_base_factor_insert
               BEFORE INSERT ON purchase_lines
               WHEN NEW.baseUnitsPerBox < 1 OR NEW.baseUnitsPerBox IS NOT
                    COALESCE((SELECT stripsPerBox FROM items WHERE itemId = NEW.itemId), 1)
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_PURCHASE_LINE_FACTOR_MISMATCH'); END""",
            """CREATE TRIGGER trg_sales_lines_base_factor_insert
               BEFORE INSERT ON sales_lines
               WHEN NEW.qty <= 0
                 OR NEW.stripsPerBoxFrozen < 1
                 OR NEW.stripsPerBoxFrozen IS NOT
                    COALESCE((SELECT stripsPerBox FROM items WHERE itemId = NEW.itemId), 1)
                 OR (NEW.unitType = 'STRIP' AND NEW.stripsPerBoxFrozen < 2)
                 OR NEW.baseQty <= 0
                 OR (NEW.unitType = 'BOX' AND (
                       NEW.qty > 9223372036854775807 / NEW.stripsPerBoxFrozen
                       OR NEW.baseQty <> NEW.qty * NEW.stripsPerBoxFrozen
                    ))
                 OR (NEW.unitType = 'STRIP' AND NEW.baseQty <> NEW.qty)
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_SALE_LINE_BASE_QTY_MISMATCH'); END""",
            """CREATE TRIGGER trg_purchase_lines_factor_immutable
               BEFORE UPDATE OF baseUnitsPerBox, itemId ON purchase_lines
               WHEN NEW.baseUnitsPerBox IS NOT OLD.baseUnitsPerBox OR NEW.itemId IS NOT OLD.itemId
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_PURCHASE_LINE_IMMUTABLE'); END""",
            """CREATE TRIGGER trg_sales_lines_factor_immutable
               BEFORE UPDATE OF stripsPerBoxFrozen, baseQty, unitType, qty, itemId ON sales_lines
               WHEN NEW.stripsPerBoxFrozen IS NOT OLD.stripsPerBoxFrozen
                 OR NEW.baseQty IS NOT OLD.baseQty
                 OR NEW.unitType IS NOT OLD.unitType
                 OR NEW.qty IS NOT OLD.qty
                 OR NEW.itemId IS NOT OLD.itemId
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_SALE_LINE_SNAPSHOT_IMMUTABLE'); END""",

            // D9: في الإصدار الأول مصادر الدفعات المدعومة هي PURCHASE وOPENING فقط.
            """CREATE TRIGGER trg_batches_source_v1
               BEFORE INSERT ON batches
               WHEN NEW.source NOT IN ('PURCHASE', 'OPENING')
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_BATCH_SOURCE_NOT_SUPPORTED'); END""",

            // D9: حركة OPENING موجبة، لدفعة OPENING فقط، مرة واحدة، وبلا روابط بيع.
            """CREATE TRIGGER trg_opening_movement_validate
               BEFORE INSERT ON stock_movements
               WHEN NEW.type = 'OPENING' AND (
                    NEW.qtyDelta <= 0
                    OR NEW.batchId IS NULL
                    OR NOT EXISTS(
                        SELECT 1 FROM batches b
                        WHERE b.batchId = NEW.batchId AND b.source = 'OPENING'
                          AND b.itemId = NEW.itemId
                    )
                    OR NEW.invoiceRef IS NOT NULL
                    OR NEW.saleId IS NOT NULL
                    OR NEW.allocId IS NOT NULL
                    OR NEW.reversesMovementId IS NOT NULL
                    OR EXISTS(
                        SELECT 1 FROM stock_movements m
                        WHERE m.batchId = NEW.batchId AND m.type = 'OPENING'
                    )
               )
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_OPENING_MOVEMENT_INVALID'); END""",

            // لا نسمح بأنواع حركة غير معرّفة في V1؛ نختبر خصوصاً SUPPLIER_RETURN_DIFFERENCE.
            """CREATE TRIGGER trg_movement_type_v1
               BEFORE INSERT ON stock_movements
               WHEN NEW.type NOT IN ('OPENING', 'PURCHASE', 'SALE', 'SALE_CANCEL')
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_MOVEMENT_TYPE_NOT_SUPPORTED'); END""",

                         // D5: كل حركة SALE_CANCEL يجب أن تكون عكساً مطابقاً لحركة SALE أصلية
             // ضمن فاتورة ما زالت CONFIRMED. سبب الإلغاء يُطابق عند انتقال الفاتورة لاحقاً.
             """CREATE TRIGGER trg_sale_cancel_movement_validate
                BEFORE INSERT ON stock_movements
                WHEN NEW.type = 'SALE_CANCEL' AND (
                     NEW.saleId IS NULL OR NEW.invoiceRef IS NULL
                     OR NEW.batchId IS NULL OR NEW.reversesMovementId IS NULL
                     OR NEW.allocId IS NOT NULL OR NEW.qtyDelta <= 0 OR NEW.costDelta < 0
                     OR NOT EXISTS(
                         SELECT 1 FROM sales_invoices i
                         WHERE i.saleId = NEW.saleId AND i.displayNo = NEW.invoiceRef
                           AND i.status = 'CONFIRMED'
                     )
                     OR NOT EXISTS(
                         SELECT 1 FROM stock_movements s
                         WHERE s.movementId = NEW.reversesMovementId
                           AND s.type = 'SALE'
                           AND s.saleId = NEW.saleId
                           AND s.invoiceRef = NEW.invoiceRef
                           AND s.batchId = NEW.batchId
                           AND s.itemId = NEW.itemId
                           AND s.qtyDelta < 0
                           AND s.costDelta <= 0
                           AND s.allocId IS NOT NULL
                           AND NEW.qtyDelta = -s.qtyDelta
                           AND NEW.costDelta = -s.costDelta
                     )
                )
                BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_SALE_CANCEL_MOVEMENT_INVALID'); END""",

// الدفعة تحفظ معاملها. PURCHASE يأخذه من بند الشراء، والمرتجع من دفعة الأصل.
            """CREATE TRIGGER trg_batches_base_factor_insert
               BEFORE INSERT ON batches
               WHEN NEW.baseUnitsPerBox < 1 OR
                 (NEW.source = 'PURCHASE' AND (
                    NEW.purchaseLineId IS NULL OR NOT EXISTS(
                      SELECT 1 FROM purchase_lines p
                      WHERE p.lineId = NEW.purchaseLineId
                        AND p.itemId = NEW.itemId
                        AND p.baseUnitsPerBox = NEW.baseUnitsPerBox
                    )
                 )) OR
                 (NEW.source = 'CUSTOMER_RETURN' AND (
                    NEW.parentBatchId IS NULL OR NOT EXISTS(
                      SELECT 1 FROM batches parent
                      WHERE parent.batchId = NEW.parentBatchId
                        AND parent.itemId = NEW.itemId
                        AND parent.baseUnitsPerBox = NEW.baseUnitsPerBox
                    )
                 )) OR
                 (NEW.source = 'OPENING' AND (
                    NEW.baseUnitsPerBox IS NOT
                      COALESCE((SELECT stripsPerBox FROM items WHERE itemId = NEW.itemId), 1)
                    OR NEW.purchaseLineId IS NOT NULL
                    OR NEW.parentBatchId IS NOT NULL
                 ))
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_BATCH_FACTOR_MISMATCH'); END""",
            """CREATE TRIGGER trg_batches_factor_immutable
               BEFORE UPDATE OF baseUnitsPerBox, itemId ON batches
               WHEN NEW.baseUnitsPerBox IS NOT OLD.baseUnitsPerBox OR NEW.itemId IS NOT OLD.itemId
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_BATCH_FACTOR_IMMUTABLE'); END""",

            // D2/U7: سجل تغييرات stripsPerBox يُنشأ آلياً، ويظل append-only.
            """CREATE TRIGGER trg_item_changes_validate_insert
               BEFORE INSERT ON item_changes
               WHEN NEW.fieldName <> 'stripsPerBox'
                 OR NEW.oldValue IS NEW.newValue
                 OR NOT EXISTS(
                    SELECT 1 FROM items i WHERE i.itemId = NEW.itemId
                      AND NEW.newValue IS CASE WHEN i.stripsPerBox IS NULL
                                               THEN NULL ELSE CAST(i.stripsPerBox AS TEXT) END
                 )
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_INVALID_ITEM_CHANGE'); END""",
            """CREATE TRIGGER trg_item_changes_no_update BEFORE UPDATE ON item_changes
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_ITEM_CHANGE_IMMUTABLE'); END""",
            """CREATE TRIGGER trg_item_changes_no_delete BEFORE DELETE ON item_changes
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_ITEM_CHANGE_IMMUTABLE'); END""",
            """CREATE TRIGGER trg_items_no_delete_after_change BEFORE DELETE ON items
               WHEN EXISTS(SELECT 1 FROM item_changes WHERE itemId = OLD.itemId)
               BEGIN SELECT RAISE(ABORT, 'PHARMACY_V8:E_ITEM_DELETE_NOT_ALLOWED'); END"""
        )

        private val triggerNameRegex = Regex("(?i)^\\s*CREATE\\s+TRIGGER\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z0-9_]+)")

        private fun triggerName(sql: String): String =
            triggerNameRegex.find(sql)?.groupValues?.get(1)
                ?: error("Invalid trigger definition: missing trigger name")

        private fun normalizeSql(sql: String): String = sql
            .replace(Regex("(?i)\\bIF\\s+NOT\\s+EXISTS\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix(";")

        /**
         * يعيد بناء أي Trigger ناقص أو مختلف، ثم يتحقق من المجموعة كاملة.
         * لا يحذف Triggers مجهولة؛ وجودها يوقف فتح القاعدة حتى لا نمسح حماية غير مفهومة.
         */
        private fun ensureTriggers(db: SupportSQLiteDatabase) {
            val expected = TRIGGERS.associateBy(::triggerName)
            check(expected.size == TRIGGERS.size) { "Duplicate V8 trigger name" }

            val existing = mutableMapOf<String, String>()
            db.query("SELECT name, sql FROM sqlite_master WHERE type = 'trigger' AND name NOT LIKE 'sqlite_%'").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val sqlIndex = cursor.getColumnIndexOrThrow("sql")
                while (cursor.moveToNext()) {
                    existing[cursor.getString(nameIndex)] = cursor.getString(sqlIndex)
                }
            }

            val unknown = existing.keys - expected.keys
            check(unknown.isEmpty()) { "Unexpected persistent V8 triggers: ${unknown.sorted()}" }

            db.beginTransaction()
            try {
                expected.forEach { (name, expectedSql) ->
                    val actualSql = existing[name]
                    if (actualSql == null || normalizeSql(actualSql) != normalizeSql(expectedSql)) {
                        db.execSQL("DROP TRIGGER IF EXISTS `$name`")
                        db.execSQL(expectedSql)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            val verified = mutableMapOf<String, String>()
            db.query("SELECT name, sql FROM sqlite_master WHERE type = 'trigger' AND name NOT LIKE 'sqlite_%'").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val sqlIndex = cursor.getColumnIndexOrThrow("sql")
                while (cursor.moveToNext()) verified[cursor.getString(nameIndex)] = cursor.getString(sqlIndex)
            }
            check(verified.keys == expected.keys) { "V8 trigger set verification failed" }
            expected.forEach { (name, expectedSql) ->
                check(normalizeSql(verified.getValue(name)) == normalizeSql(expectedSql)) {
                    "V8 trigger definition verification failed: $name"
                }
            }
        }

        /** فحص سلامة البيانات الذي يلي تثبيت Triggers عند كل فتح. */
        private fun verifyData(db: SupportSQLiteDatabase) {
            db.query("PRAGMA integrity_check").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                    "SQLite integrity_check failed"
                }
            }
            db.query("PRAGMA foreign_key_check").use { cursor ->
                check(!cursor.moveToFirst()) { "SQLite foreign_key_check found violations" }
            }

            val invalidCounts = listOf(
                "SELECT COUNT(*) FROM items WHERE stripsPerBox IS NOT NULL AND stripsPerBox < 2",
                """SELECT COUNT(*) FROM purchase_lines
                   WHERE baseUnitsPerBox < 1""",
                """SELECT COUNT(*) FROM sales_invoices
                   WHERE (status = 'PENDING' AND (
                            confirmedAt IS NOT NULL OR cancelledAt IS NOT NULL OR cancelReason IS NOT NULL
                         ))
                      OR (status = 'CONFIRMED' AND (
                            confirmedAt IS NULL OR cancelledAt IS NOT NULL OR cancelReason IS NOT NULL
                         ))
                      OR (status = 'CANCELLED' AND (
                            confirmedAt IS NULL OR cancelledAt IS NULL
                         ))
                      OR status NOT IN ('PENDING', 'CONFIRMED', 'CANCELLED')""",
                """SELECT COUNT(*) FROM sales_lines s
                   WHERE s.stripsPerBoxFrozen < 1 OR s.qty <= 0 OR s.baseQty <= 0
                      OR (s.unitType = 'STRIP' AND
                          (s.stripsPerBoxFrozen < 2 OR s.baseQty <> s.qty))
                      OR (s.unitType = 'BOX' AND (
                          s.qty > 9223372036854775807 / s.stripsPerBoxFrozen
                          OR s.baseQty <> s.qty * s.stripsPerBoxFrozen
                      ))""",
                """SELECT COUNT(*) FROM batches b JOIN items i ON i.itemId = b.itemId
                   LEFT JOIN purchase_lines p ON p.lineId = b.purchaseLineId
                   LEFT JOIN batches parent ON parent.batchId = b.parentBatchId
                   WHERE b.baseUnitsPerBox < 1 OR
                     (b.source = 'PURCHASE' AND (p.lineId IS NULL OR p.itemId <> b.itemId OR p.baseUnitsPerBox <> b.baseUnitsPerBox)) OR
                     (b.source = 'OPENING' AND (
                         b.purchaseLineId IS NOT NULL OR b.parentBatchId IS NOT NULL
                     )) OR
                     b.source NOT IN ('PURCHASE', 'OPENING')""",
                """SELECT COUNT(*) FROM item_changes c
                   LEFT JOIN items i ON i.itemId = c.itemId
                   WHERE i.itemId IS NULL OR c.fieldName <> 'stripsPerBox' OR c.oldValue IS c.newValue""",
                """SELECT COUNT(*) FROM batches b
                   WHERE b.source = 'OPENING' AND (
                     SELECT COUNT(*) FROM stock_movements m
                     WHERE m.batchId = b.batchId AND m.type = 'OPENING'
                   ) <> 1""",
                                 """SELECT COUNT(*) FROM sales_invoices i
                    WHERE i.status = 'CANCELLED' AND (
                      NOT EXISTS(SELECT 1 FROM stock_movements s
                                 WHERE s.saleId = i.saleId AND s.type = 'SALE')
                      OR EXISTS(
                        SELECT 1 FROM stock_movements s
                        WHERE s.saleId = i.saleId AND s.type = 'SALE'
                          AND NOT EXISTS(
                            SELECT 1 FROM stock_movements r
                            WHERE r.type = 'SALE_CANCEL'
                              AND r.reversesMovementId = s.movementId
                              AND r.saleId = i.saleId
                              AND r.invoiceRef = i.displayNo
                              AND r.batchId = s.batchId AND r.itemId = s.itemId
                              AND r.qtyDelta = -s.qtyDelta
                              AND r.costDelta = -s.costDelta
                              AND r.allocId IS NULL
                              AND r.reason IS i.cancelReason
                          )
                      )
                      OR EXISTS(
                        SELECT 1 FROM stock_movements r
                        WHERE r.saleId = i.saleId AND r.type = 'SALE_CANCEL'
                          AND (r.reason IS NOT i.cancelReason OR NOT EXISTS(
                            SELECT 1 FROM stock_movements s
                            WHERE s.movementId = r.reversesMovementId
                              AND s.type = 'SALE' AND s.saleId = i.saleId
                          ))
                      )
                    )""",
"""SELECT COUNT(*) FROM stock_movements m
                   WHERE m.type NOT IN ('OPENING', 'PURCHASE', 'SALE', 'SALE_CANCEL')
                      OR (m.type = 'OPENING' AND (
                         m.qtyDelta <= 0 OR m.batchId IS NULL
                         OR m.invoiceRef IS NOT NULL OR m.saleId IS NOT NULL
                         OR m.allocId IS NOT NULL OR m.reversesMovementId IS NOT NULL
                         OR NOT EXISTS(SELECT 1 FROM batches b
                                       WHERE b.batchId = m.batchId
                                         AND b.source = 'OPENING' AND b.itemId = m.itemId)
                      ))"""
            )
            invalidCounts.forEachIndexed { index, sql ->
                db.query(sql).use { cursor ->
                    check(cursor.moveToFirst() && cursor.getLong(0) == 0L) {
                        "V8 data integrity verification failed (check ${index + 1})"
                    }
                }
            }

            db.query(
                """SELECT COUNT(*) FROM items i
                   WHERE EXISTS(SELECT 1 FROM item_changes c WHERE c.itemId = i.itemId)
                     AND (SELECT c2.newValue FROM item_changes c2 WHERE c2.itemId = i.itemId
                          ORDER BY c2.changedAt DESC, c2.changeId DESC LIMIT 1)
                         IS NOT CASE WHEN i.stripsPerBox IS NULL THEN NULL ELSE CAST(i.stripsPerBox AS TEXT) END"""
            ).use { cursor ->
                check(cursor.moveToFirst() && cursor.getLong(0) == 0L) {
                    "V8 item_changes latest-value verification failed"
                }
            }
        }

        private fun ensureAndVerify(db: SupportSQLiteDatabase) {
            // لا نصلح الحماية قبل التأكد أن البيانات نفسها صالحة.
            verifyData(db)
            ensureTriggers(db)
            verifyData(db)
        }

        /** onCreate للتأسيس وonOpen لإصلاح/التحقق قبل استخدام DAO في كل فتح. */
        val TRIGGERS_CALLBACK = object : RoomDatabase.Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                ensureAndVerify(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                ensureAndVerify(db)
            }
        }
    }
}
