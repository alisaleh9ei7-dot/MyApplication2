# Pharmacy POS V8 — تصحيح P1-A

هذه الحزمة مخصصة لمشروع V8 فقط. لا تعدّل V7 ولا تربط قاعدة V8 بالتطبيق الإنتاجي.

## الملفات الأربعة

1. `app/src/main/java/com/example/myapplication/data/v8/entity/BatchAndMovementEntities.kt`
2. `app/src/main/java/com/example/myapplication/data/v8/entity/ItemEntities.kt`
3. `app/src/main/java/com/example/myapplication/data/v8/db/PharmacyDatabaseV8.kt`
4. `app/src/androidTest/java/com/example/myapplication/data/v8/ItemD2IntegrityTest.kt`

## طريقة التطبيق

1. أغلق Android Studio أو أوقف Gradle Sync/Build الجاري.
2. أنشئ نسخة احتياطية مستقلة من مجلد مشروع V8 كاملاً.
3. فك الحزمة في مجلد مؤقت.
4. انسخ الملفات الأربعة إلى المسارات المطابقة داخل مجلد V8، واستبدل الملفات الأربعة فقط.
5. لا تستبدل `app/schemas/.../8.json` يدوياً؛ يجب أن يعيد Room/KSP توليده من الكيانات المصححة.
6. من PowerShell داخل مجلد V8 نفّذ:

```powershell
.\gradlew clean :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

7. افحص `app/schemas/com.example.myapplication.data.v8.db.PharmacyDatabaseV8/8.json` بعد التوليد:
   - `batches` يجب أن يحتوي `baseUnitsPerBox`.
   - `sales_lines` يجب أن يحتوي `stripsPerBoxFrozen` و`baseQty`.
   - يجب ألا يحتوي `sales_lines` على `baseUnitsPerBox`.
8. إذا نجح البناء وفحص المخطط، نفّذ على POCO:

```powershell
.\gradlew connectedDebugAndroidTest
```

9. أرسل المخرجات كاملة وملف `8.json` الناتج للمراجعة. لا تبدأ Batch B قبل اجتياز بوابة A.

## حدود السلامة

- لا تنفذ `git commit` أو `git push`.
- لا تحذف قواعد بيانات أو ملفات يدوياً.
- لا تشغل التطبيق الإنتاجي ولا تربط V8 بـV7.
- الحزمة لا تحتوي ترحيلاً من V7 إلى V8 ولا Backup/Restore.
- لا تعتبر A متحققة بمجرد نجاح الترجمة؛ يلزم نجاح اختبارات Android ومراجعة المخطط وعدم ظهور تراجعات.
