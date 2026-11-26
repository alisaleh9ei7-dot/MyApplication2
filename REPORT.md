# تقرير مراجعة وتصحيح — Pharmacy POS V8 P1-A

## المصدر
تمت مراجعة `Pharmacy_V8_latest.zip` المرفوع بتاريخ 25 نوفمبر 2026. المشروع المضغوط يحوي V7 وV8 وملفات build مولدة؛ لم يتم تعديل الأرشيف الأصلي.

## الحالة قبل التصحيح

1. **عدم تطابق الكيانات مع Room schema 8:**
   - `BatchAndMovementEntities.kt` لا يعرّف `BatchEntity.baseUnitsPerBox`، بينما `8.json` يعرّف العمود.
   - `SalesEntities.kt` يعرّف `stripsPerBoxFrozen` و`baseQty`، بينما `8.json` ما زال يعرّف `baseUnitsPerBox` داخل `sales_lines`.
   - `PharmacyDatabaseV8.kt` كانت triggers وعمليات التدقيق تستعمل `sales_lines.baseUnitsPerBox`؛ وهذا لا يطابق Entity الحالي.
2. **تعارض D2:** Trigger قفل `stripsPerBox` يمنع التغيير عند وجود أي تاريخ، رغم اعتماد السماح عند انعدام الرصيد. كما أن Trigger آخر ينشئ `item_changes` تلقائياً، فيكرر تسجيل `ItemService`.
3. **خطأ تدقيق تاريخي:** استعلامات `verifyData` تقارن snapshots التاريخية بعامل الصنف الحالي. هذا يرفض تغيير العامل عند رصيد صفر رغم أن snapshots القديمة يجب أن تبقى كما كانت وقت الشراء/البيع/إنشاء الدفعة.
4. **D9 غير مكتمل:** لا توجد قواعد كافية لحركة OPENING، ولا منع صريح لكل الأنواع غير المدعومة مثل `SUPPLIER_RETURN_DIFFERENCE`.
5. **اختبارات Android قديمة:** الاختبار كان يستعمل `LOCKED_BY_HISTORY` وحقول `baseUnitsPerBox` في SalesLine، ويتوقع التسجيل الآلي، ويستدعي `updateLocation`؛ وهذه لا تطابق ملفات المصدر الحالية وقرارات D2.
6. ملفات `build/` ونتيجة البناء داخل ZIP لا تثبت أن المصادر الحالية نفسها بُنيت بعد آخر تعديل؛ سجل Gradle يحتوي مهام كثيرة `UP-TO-DATE`.

## ما عالجته الحزمة

- إضافة `baseUnitsPerBox: Int` إلى `BatchEntity` بوصفه snapshot ثابتاً.
- تحديث تعليق `ItemChangeEntity` ليوضح أن `ItemService` هو من يسجل التغيير داخل المعاملة.
- تعديل قفل D2 ليمنع التغيير فقط عند وجود `batches.qtyRemaining <> 0`.
- إزالة Trigger التسجيل الآلي، والإبقاء على تحقق `item_changes` وحماية append-only.
- تصحيح Trigger SalesLine ليتحقق من `stripsPerBoxFrozen` و`baseQty`:
  - BOX: `baseQty = qty * stripsPerBoxFrozen`.
  - STRIP: `baseQty = qty`، ومعامل التحويل لا يقل عن 2.
  - التحقق من الكمية الموجبة وتجنب تجاوز Long في الضرب.
- جعل حقول snapshot في SalesLine غير قابلة للتغيير بعد الإدراج.
- جعل تدقيق البيانات يحترم snapshots التاريخية ولا يقارنها بعامل الصنف الحالي بعد تغيير مسموح.
- تقييد مصادر الدفعات في V1 إلى PURCHASE وOPENING.
- تفعيل قواعد OPENING: حركة موجبة، مرتبطة بدفعة OPENING للصنف نفسه، مرة واحدة لكل دفعة، وبلا روابط بيع/إلغاء.
- منع أنواع الحركات غير المدعومة، ويتضمن ذلك `SUPPLIER_RETURN_DIFFERENCE`.
- جعل ترتيب التحقق: تدقيق البيانات أولاً، ثم إصلاح/التحقق من triggers، ثم إعادة تدقيق البيانات.
- إعادة كتابة `ItemD2IntegrityTest` ليتطابق مع الواجهات والقرارات الحالية.

## تحقق محلي أُجري على SQL فقط

باستخدام SQLite 3.46.1 داخل بيئة المراجعة:
- تم تحليل وتنفيذ تعريفات الـ26 persistent trigger على مخطط اختبار مصغر: **نجاح**.
- تغيير عامل الصنف مع وجود PurchaseLine وBatch تاريخيين ورصيد صفر: **نجاح**.
- تسجيل `item_changes` بعد التغيير: **نجاح**.
- منع التغيير مع رصيد غير صفري: **نجاح**.
- قبول SalesLine متسق ورفض `baseQty` غير المتسق: **نجاح**.
- قبول حركة OPENING موجبة أولى ورفض الحركة المكررة والسالبة و`SUPPLIER_RETURN_DIFFERENCE`: **نجاح**.

هذا تحقق SQL مصغر فقط، وليس اختبار Room أو Android.

## ما لم يُنفذ

- لم أشغل Gradle أو KSP في بيئة المستخدم.
- لم أشغل اختبارات Android على POCO.
- لم أُعد توليد `8.json`؛ يجب توليده محلياً بعد استبدال الملفات.
- لم أختبر `onOpen` على Room فعلي أو سلوك Room TEMP triggers.
- D7 لم يكتمل: لا توجد بعد سياسة قراءة فقط عند فشل التدقيق، ولا سجل دائم لحوادث إصلاح triggers، ولا تدقيق خاص بعد restore/migration/unclean shutdown.
- لم أضف Purchase/Sale/SaleCancel services أو ترحيل V7→V8 أو Backup/Restore.
- لم ألمس V7 أو إعدادات Gradle/Java أو Manifest.

## الحكم

**الحزمة تصحيح مقترح لبوابة P1-A وليست اعتماداً نهائياً.**
يجب أن ينجح compile، ثم يتولد schema 8 متوافق، ثم تنجح اختبارات Android على الجهاز. لا تنتقل إلى Batch B قبل مراجعة هذه الأدلة.
