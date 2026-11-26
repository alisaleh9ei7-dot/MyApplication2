# Pharmacy POS V8 — Batch 1 Prepared Working Copy

## Scope
This is an isolated copy of the uploaded project ZIP with only the V8 Batch 1 source package added under:
`app/src/main/java/com/example/myapplication/data/v8/`

No original uploaded ZIP was modified. No source files outside the new `data/v8/` package were changed in this working copy.

## Included files (17 Kotlin files)
- 8 entity files: the 7 prepared V8 entity files plus `SettingEntity.kt` copied in structure from the current V7 settings model.
- 8 DAO files: the 7 prepared V8 DAOs plus `SettingDao.kt`.
- 1 database file: `PharmacyDatabaseV8.kt`.

## Decisions applied
- Batch sale price is stored on `BatchEntity` as nullable `salePrice` (box price for that batch); `ItemEntity.boxSalePrice` remains the default for newly created batches. Null means no batch sale price has been assigned yet.
- Both `BatchEntity.expiryDate` and `PurchaseLineEntity.expiryDate` are `epoch-day` values representing the last calendar day of the expiry month. DAO parameters were renamed to `todayEpochDay` / `limitEpochDay`.
- `exportSchema = true`.
- The V8 `settings` table and DAO are included to preserve the existing settings model. `audit_log` remains deferred.
- No production Room Builder was added. Existing V7 `AppDatabase` remains unchanged.

## Explicitly not included
- Services, ViewModels, Compose UI, workers, backup/restore changes, Manifest/XML changes, V7 data migration, production Builder, and trigger-on-every-open verification.
- No Gradle build, KSP generation, unit tests, or instrumented tests were run.

## Important limitation
`PharmacyDatabaseV8.TRIGGERS_CALLBACK` still creates triggers only in `onCreate`. This draft must not be used as a production database until the separate Builder/open-validation phase installs and verifies required triggers on every open.

## Version source
The uploaded project ZIP's version catalog was retained as-is. This preparation does not change Kotlin/Gradle/AGP/JDK versions.

---

## D2 implementation — conversion-factor integrity

The V8 draft now defines the base unit as follows:

- If `items.stripsPerBox` is not null, the base unit is one strip and `baseUnitsPerBox` equals `stripsPerBox`.
- If `items.stripsPerBox` is null, the item is not divisible and its base unit is one box (`baseUnitsPerBox = 1`).
- `paidQty`, `freeQty`, `qtyReceived`, `qtyRemaining`, `qtySupplierReturned`, `StockMovementEntity.qtyDelta`, and `SaleAllocationEntity.qtyBase` are expressed in base units.
- `SalesLineEntity.qty` remains expressed in its selected `unitType` (BOX or STRIP).

`purchase_lines`, `sales_lines`, and `batches` store an immutable `baseUnitsPerBox` snapshot. A customer-return batch must copy the factor from its parent batch. Purchase batches must copy the factor from their purchase line. OPENING and STOCK_IN batches must match the current item factor.

`items.stripsPerBox` accepts only NULL or an integer >= 2. SQLite triggers reject a change once any batch, stock movement, purchase line (including DRAFT), or sales line exists. An AFTER UPDATE trigger writes `item_changes` in the same SQLite statement/transaction. `item_changes` is append-only.

`ItemDao.update(ItemEntity)` was removed. `ItemService.changeStripsPerBox` is the application path; SQLite triggers remain the final enforcement layer. `PharmacyDatabaseV8.getDatabase(context)` uses the isolated database name `pharmacy_v8.db` and installs/verifies the expected persistent trigger definitions on create and every open. A failed trigger or data-integrity check prevents normal database opening.

D2 instrumentation tests are in `app/src/androidTest/java/com/example/myapplication/data/v8/ItemD2IntegrityTest.kt`. They have not been executed unless a test report explicitly records a successful run. V8 remains disconnected from the production application and V7.
