# Pharmacy POS V8 — D2 implementation status

## Scope implemented in this working copy

- Added `baseUnitsPerBox` snapshots to purchase lines, sales lines, and batches.
- Documented base-unit semantics: strip for divisible items; box for non-divisible items (`stripsPerBox = NULL`, factor `1`).
- Enforced `stripsPerBox IS NULL OR stripsPerBox >= 2` with SQLite triggers.
- Added the D2 history lock across batches, stock movements, purchase lines (including DRAFT), and sales lines.
- Added factor-snapshot validation and immutability triggers for purchase lines, sales lines, and batches.
- Added automatic `item_changes` logging for `stripsPerBox`, plus append-only and deletion protections.
- Removed the full-row `ItemDao.update(ItemEntity)` method and added targeted update queries.
- Added `ItemService.changeStripsPerBox` with a Room transaction and a typed result.
- Added a dedicated V8 database builder using `pharmacy_v8.db`, with trigger repair/verification and integrity checks on create/open.
- Added Android instrumentation tests for key D2 behaviors and trigger repair.

## Validation actually performed in this environment

- Extracted and executed all 24 persistent trigger definitions against an in-memory SQLite 3.46.1 database: passed.
- Exercised automatic audit logging, same-value no-op, invalid factor rejection, each of the four history-lock sources, factor-snapshot mismatch rejection, purchase-batch inheritance, customer-return factor inheritance, immutable snapshots, append-only audit, and deletion protection: passed.
- Parsed and executed the six data-verifier SQL queries against a matching minimal SQLite schema: passed.
- Compared normalized `sqlite_master.sql` trigger definitions with the expected trigger source: exact match passed.
- Ran a Kotlin parser check; no syntax/parser diagnostics were detected. This was not a full Kotlin/Android compilation because Android/Room dependencies were not available to the standalone compiler.

## Not performed / still required

- Gradle/KSP compilation: not run. The wrapper attempted to download Gradle 9.6.1, but this environment had no DNS/network access (`UnknownHostException: services.gradle.org`).
- Room schema export (`schemas/8.json`): not generated because KSP could not run.
- Android instrumentation tests: added but not executed.
- Android API 24 runtime verification: not executed.
- POCO X6 Pro device verification: not executed.
- Purchase/Sales services do not exist in this V8 source set, so full purchase/sale/cancel/return workflow tests remain pending until those services are implemented.

## Safety boundaries

- V7 and the legacy `AppDatabase` were not modified.
- V8 remains disconnected from the production application.
- No destructive migration or V7-to-V8 data migration was added.
- No claim of Android/Room test success is made.

## Current status

D2 code changes are present in this working copy, but D2 is **not verified**. The next required gate is to open this project on the user's Windows/Android Studio environment, run KSP/build, review the generated `schemas/8.json`, fix any compiler/schema errors, and then run the V8 instrumentation tests before proceeding.
