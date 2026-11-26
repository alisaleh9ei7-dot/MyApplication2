# تطبيق تصحيحات D6 على V8

1. خذ نسخة احتياطية من مجلد V8 الحالي.
2. تأكد أن مسار المشروع هو:
   `C:\Users\AL-Ameen\AndroidStudioProjects\MyApplication2-fix-releas-hardening`
3. فك محتويات `V8_D6_Implementation.zip` فوق جذر مشروع V8 مع السماح باستبدال `PharmacyDatabaseV8.kt`.
4. لا تطبق أي ملف على مشروع V7.
5. افتح Android Studio وانتظر Gradle Sync.
6. نفّذ من Terminal:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew :app:compileDebugKotlin
```

7. إذا نجح compile، وصّل POCO X6 Pro وتأكد من ظهوره:

```powershell
.\gradlew :app:connectedDebugAndroidTest
```

8. أرسل مخرجات الأمرين كاملة. لا نضع D6 في حالة «تم التحقق» قبل نجاح الاختبارين.

## الملفات الموجودة في الحزمة
- `app/src/main/java/com/example/myapplication/data/v8/db/PharmacyDatabaseV8.kt` (استبدال)
- `app/src/main/java/com/example/myapplication/data/v8/error/SaleError.kt` (إضافة)
- `app/src/main/java/com/example/myapplication/data/v8/error/V8DatabaseErrorParser.kt` (إضافة)
- `app/src/androidTest/java/com/example/myapplication/data/v8/V8DatabaseErrorParserTest.kt` (إضافة)
