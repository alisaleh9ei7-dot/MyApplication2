package com.example.myapplication.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * المبالغ المالية أعداد صحيحة Long بمقياس ثابت: 1000 جزء لكل وحدة نقدية.
 * مثال: 12.5 تُخزَّن 12500. لا Double في أي حساب مالي.
 */
object Money {
    const val SCALE: Long = 1000L
    private const val DECIMALS: Int = 3
    private const val MAX_INPUT_LENGTH: Int = 32

    // الأرقام ASCII فقط. الفواصل مقبولة كآلاف عندما تكون كل مجموعة من 3 أرقام
    // ولا تبدأ المجموعة الأولى بصفر، لتجنب تفسير 0,500 على أنها 500.
    private val VALID = Regex("""^([0-9]+|[1-9][0-9]{0,2}(,[0-9]{3})+)(\.[0-9]+)?$""")

    /** يحوّل نص الإدخال إلى Long مقيَّس. يرجع null إذا كان النص غير صالح؛ لا يتحول أبداً إلى صفر. */
    fun parse(input: String, allowNegative: Boolean = false): Long? {
        // حد مبكر يمنع معالجة نصوص ضخمة، بما فيها النصوص المليئة بالمسافات.
        if (input.length > MAX_INPUT_LENGTH) return null

        var s = normalize(input).trim()
        if (s.isEmpty()) return null

        var negative = false
        if (s.startsWith("-")) {
            if (!allowNegative) return null
            negative = true
            s = s.substring(1)
        }
        if (!VALID.matches(s)) return null

        val clean = s.replace(",", "")
        return try {
            // نرفض الدقة الزائدة غير الصفرية ولا نقرّب الإدخال بصمت.
            val exact = BigDecimal(clean).movePointRight(DECIMALS).toBigIntegerExact().longValueExact()
            if (negative) Math.negateExact(exact) else exact
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** نص للعرض: يحذف الأصفار الزائدة (12500 ← "12.5"، 12000 ← "12"). */
    fun format(amount: Long): String =
        BigDecimal.valueOf(amount, DECIMALS).stripTrailingZeros().toPlainString()

    /** نص للعرض بعدد خانات ثابت مع تقريب للعرض فقط (لا يغيّر القيمة المخزنة). */
    fun formatFixed(amount: Long, decimals: Int): String {
        require(decimals in 0..DECIMALS) { "decimals must be between 0 and $DECIMALS" }
        return BigDecimal.valueOf(amount, DECIMALS).setScale(decimals, RoundingMode.HALF_UP).toPlainString()
    }

    /** القسمة مع التقريب للأعلى للقيم غير السالبة فقط. يرمي IllegalArgumentException للمدخلات غير الصالحة. */
    fun ceilDiv(numerator: Long, denominator: Long): Long {
        require(numerator >= 0) { "numerator must be >= 0" }
        require(denominator > 0) { "denominator must be > 0" }
        val q = numerator / denominator
        return if (numerator % denominator == 0L) q else q + 1
    }

    /**
     * يحسب (a × b ÷ c) بتقريب HALF_UP باستخدام BigInteger لتجنب التجاوز الوسيط.
     * يشترط a وb غير سالبين وc موجباً (وإلا يرمي IllegalArgumentException).
     * إذا تجاوزت النتيجة النهائية نطاق Long، يرمي ArithmeticException.
     */
    fun mulDivHalfUp(a: Long, b: Long, c: Long): Long {
        require(a >= 0 && b >= 0) { "a and b must be >= 0" }
        require(c > 0) { "c must be > 0" }
        val numerator = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b))
        return BigDecimal(numerator)
            .divide(BigDecimal.valueOf(c), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    private fun normalize(s: String): String = buildString(s.length) {
        for (ch in s) {
            // إزالة محارف Unicode من الفئة FORMAT، ومنها علامات الاتجاه والمحارف
            // عديمة العرض، قبل trim والتحقق من صيغة الرقم.
            if (Character.getType(ch) == Character.FORMAT.toInt()) continue
            when (ch) {
                in '\u0660'..'\u0669' -> append('0' + (ch - '\u0660'))
                in '\u06F0'..'\u06F9' -> append('0' + (ch - '\u06F0'))
                '\u066B' -> append('.')
                '\u066C' -> append(',')
                else -> append(ch)
            }
        }
    }
}
