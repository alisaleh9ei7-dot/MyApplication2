package com.example.myapplication.core

import java.util.TimeZone

/** مصدر الوقت الموحد لمنطق الأعمال؛ يُحقن في الخدمات لاختبار حدود اليوم والشهر. */
interface AppClock {
    fun nowMillis(): Long
    fun timeZone(): TimeZone
}

/** الساعة الحقيقية للجهاز. */
object SystemAppClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun timeZone(): TimeZone = TimeZone.getDefault()
}


/**
 * ساعة ثابتة للاختبارات.
 *
 * تبقى هنا مؤقتاً لتجنب كسر أي استخدام قائم قبل إجراء بحث شامل عن المراجع
 * في المشروع؛ نقلها إلى src/test قرار تنظيمي منفصل.
 */
class FixedAppClock(
    var millis: Long,
    private val zone: TimeZone = TimeZone.getTimeZone("UTC")
) : AppClock {
    override fun nowMillis(): Long = millis
    override fun timeZone(): TimeZone = zone
}

sealed class ClockCheck {
    object Ok : ClockCheck()
    data class RolledBack(val lastKnownMillis: Long, val nowMillis: Long) : ClockCheck()
}

/**
 * كشف بسيط لرجوع ساعة الجهاز للخلف، دون إنترنت.
 *
 * يجب على المستدعي بعد كل عملية ناجحة حفظ ClockGuard.nextLastKnown(now, lastKnown)
 * وليس وقت العملية وحده؛ وإلا قد يتراكم التراجع المسموح به عبر عمليات متتابعة.
 *
 * هذه الأداة مستقلة حالياً وغير مدمجة في تدفقات البيع أو الشراء.
 * لا توفر وحدها مساراً لمعالجة قفزة كبيرة إلى المستقبل أو إعادة ضبط الساعة.
 */
object ClockGuard {
    const val DEFAULT_TOLERANCE_MILLIS: Long = 5 * 60 * 1000L

    fun check(
        nowMillis: Long,
        lastKnownMillis: Long?,
        toleranceMillis: Long = DEFAULT_TOLERANCE_MILLIS
    ): ClockCheck {
        require(toleranceMillis >= 0L) { "toleranceMillis must be >= 0" }
        if (lastKnownMillis == null || nowMillis >= lastKnownMillis) return ClockCheck.Ok

        // الطرح الدقيق يمنع overflow. إذا تجاوز الفرق Long، فهو بالتأكيد أكبر
        // من أي هامش مسموح غير سالب يمكن تمثيله في Long.
        val rollbackExceedsTolerance = try {
            Math.subtractExact(lastKnownMillis, nowMillis) > toleranceMillis
        } catch (_: ArithmeticException) {
            true
        }

        return if (rollbackExceedsTolerance) {
            ClockCheck.RolledBack(lastKnownMillis, nowMillis)
        } else {
            ClockCheck.Ok
        }
    }

    /** القيمة التي يجب حفظها بعد عملية ناجحة: لا تنقص أبداً. */
    fun nextLastKnown(nowMillis: Long, lastKnownMillis: Long?): Long =
        maxOf(nowMillis, lastKnownMillis ?: nowMillis)
}
