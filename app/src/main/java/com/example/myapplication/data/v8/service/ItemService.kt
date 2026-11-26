package com.example.myapplication.data.v8.service

import androidx.room.withTransaction
import com.example.myapplication.core.AppClock
import com.example.myapplication.core.SystemAppClock
import com.example.myapplication.data.v8.db.PharmacyDatabaseV8
import com.example.myapplication.data.v8.entity.ItemChangeEntity

/** Result of changing an item's box-to-strip conversion factor. */
enum class ChangeStripsPerBoxResult {
    CHANGED,
    UNCHANGED,
    ITEM_NOT_FOUND,
    INVALID_VALUE,
    /** Non-zero remaining stock exists in at least one batch. */
    LOCKED_BY_STOCK,
    /** No remaining stock, but the item has a batch, purchase line, sales line, or stock movement. */
    LOCKED_BY_HISTORY
}

/**
 * The application service owns the audit record. Updating the item and inserting
 * item_changes are performed in one Room transaction.
 */
class ItemService(
    private val database: PharmacyDatabaseV8,
    private val clock: AppClock = SystemAppClock
) {

    suspend fun changeStripsPerBox(
        itemId: Long,
        newValue: Int?
    ): ChangeStripsPerBoxResult = database.withTransaction {
        val dao = database.itemDao()
        val current = dao.getById(itemId)
            ?: return@withTransaction ChangeStripsPerBoxResult.ITEM_NOT_FOUND

        if (newValue != null && newValue < 2) {
            return@withTransaction ChangeStripsPerBoxResult.INVALID_VALUE
        }

        if (current.stripsPerBox == newValue) {
            return@withTransaction ChangeStripsPerBoxResult.UNCHANGED
        }

        // The factor can change only for an item that has never had any batch, purchase
        // line, sales line, or stock movement. The DAO update query checks this atomically;
        // when it refuses, we only classify the reason (stock first, then history).
        val updated = dao.updateStripsPerBoxWhenNoHistory(itemId, newValue)
        if (updated != 1) {
            return@withTransaction when {
                dao.hasNonZeroBatchStock(itemId) -> ChangeStripsPerBoxResult.LOCKED_BY_STOCK
                dao.hasFactorHistory(itemId) -> ChangeStripsPerBoxResult.LOCKED_BY_HISTORY
                else -> error("stripsPerBox update refused for item $itemId without a lock reason")
            }
        }

        dao.insertChange(
            ItemChangeEntity(
                itemId = itemId,
                changedAt = clock.nowMillis(),
                fieldName = "stripsPerBox",
                oldValue = current.stripsPerBox?.toString(),
                newValue = newValue?.toString()
            )
        )

        ChangeStripsPerBoxResult.CHANGED
    }
}
