package com.example.myapplication // تأكد أن هذا هو نفس الباكيدج الرئيسي لديك

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.data.local.AppDatabase // غير الاسم إذا كانت قاعدة البيانات باسم آخر
import java.util.Calendar

class NotificationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        // 1. جلب البيانات من قاعدة البيانات
        val db = AppDatabase.getDatabase(context) // تأكد من طريقة استدعاء قاعدة البيانات لديك
        val dashboardDao = db.dashboardDao()

        val now = Calendar.getInstance()
        val startOfToday = now.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        val futureDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 90) }.timeInMillis

        val lowStockCount = dashboardDao.getLowStockCount()
        val expiringSoonCount = dashboardDao.getExpiringSoonCount(startOfToday, futureDate)

        // 2. إذا كان هناك شيء يحتاج للتنبيه، أرسل الإشعار
        if (lowStockCount > 0 || expiringSoonCount > 0) {
            showNotification(context, lowStockCount, expiringSoonCount)
        }

        return Result.success()
    }

    private fun showNotification(context: Context, lowStock: Int, expiring: Int) {
        val channelId = "pharmacy_daily_alerts"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // إنشاء قناة الإشعارات (مطلوب لأندرويد 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "التنبيهات اليومية للصيدلية",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // بناء رسالة الإشعار
        val message = StringBuilder("تذكير يومي:\n")
        if (lowStock > 0) message.append("- لديك $lowStock صنف كميته منخفضة.\n")
        if (expiring > 0) message.append("- لديك $expiring دفعة قاربت على الانتهاء.")

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // يمكنك تغييره بأيقونة تطبيقك
            .setContentTitle("تنبيهات الصيدلية 💊")
            .setContentText(message.toString())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.toString()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // إظهار الإشعار
        notificationManager.notify(1, notification)
    }
}