# تقرير تنفيذ D6 — Pharmacy V8

## نطاق العمل
تم إعداد تغييرات D6 على نسخة مستخرجة من `V8_Project_Full(1).zip` فقط. لم يتم تعديل V7، ولم يتم تنفيذ Batch B.

## الملفات المعدلة/المضافة
1. `app/src/main/java/com/example/myapplication/data/v8/db/PharmacyDatabaseV8.kt`
   - توحيد رسائل جميع مواضع `RAISE(ABORT, ...)` وعددها 26 إلى بادئة `PHARMACY_V8:` ورمز ثابت.
   - 19 رمزاً فريداً حالياً في Triggers.
2. `app/src/main/java/com/example/myapplication/data/v8/error/SaleError.kt`
   - قاموس `V8ErrorCode`.
   - `SaleError.Known` و`SaleError.Unknown` مع حفظ الرسالة الخام والسبب الأصلي.
3. `app/src/main/java/com/example/myapplication/data/v8/error/V8DatabaseErrorParser.kt`
   - استخراج الرمز الصريح فقط، دون التخمين من رسائل SQLite.
   - الاحتفاظ بسلسلة الرسائل والـThrowable الأصلي.
   - إعادة رمي `CancellationException` حتى لو كان ضمن سلسلة الأسباب.
4. `app/src/androidTest/java/com/example/myapplication/data/v8/V8DatabaseErrorParserTest.kt`
   - اختبار Trigger SQLite حقيقي على Android.
   - اختبار الرسالة غير المعروفة.
   - اختبار عدم تحويل إلغاء Coroutine إلى خطأ بيع.

## نتيجة الجرد
- مواضع `RAISE(ABORT, ...)`: 26.
- رسائل غير مسبوقة بالبادئة بعد التعديل: 0.
- رموز Triggers الفريدة: 19.

## حدود التنفيذ الحالية
- لم توجد `SaleService` أو `SaleCancelService` في الأرشيف المرجعي؛ لذلك لم يتم ربط parser بخدمات غير موجودة.
- رموز عمليات البيع والإلغاء المستقبلية موجودة في قاموس الرموز، لكنها ليست ادعاءً بأن Triggers أو الخدمات المنفذة حالياً تطلقها.
- لم يتم تشغيل Android instrumentation tests على POCO.
- لم يكتمل compile: محاولة Gradle في بيئة الفحص توقفت لأن Gradle wrapper يحتاج تنزيل Gradle 9.6.1 من الإنترنت، والاتصال غير متاح. لا يوجد ادعاء بنجاح البناء.

## حالة D6
قيد التنفيذ — التغييرات أُعدت، لكن لا تُعتمد نهائياً قبل تطبيقها على نسخة V8، نجاح `compileDebugKotlin`، ثم نجاح `connectedDebugAndroidTest` على الجهاز الفعلي.

## حماية النطاق
لم يتم تعديل ملفات V7 أو تشغيل أي commit/push أو حذف ملفات.
