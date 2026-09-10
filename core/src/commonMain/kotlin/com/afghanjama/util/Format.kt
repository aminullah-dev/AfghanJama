package com.afghanjama.util

import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * قالب‌بندیِ عدد — **بی `String.format`.**
 *
 * `String.format` و `"…".format(…)` هر دو فقط روی JVM هستند؛ در کدِ
 * مشترکی که باید روی Kotlin/Native هم کامپایل شود وجود ندارند. اینجا
 * همان چند کاری که پروژه واقعاً از آن‌ها می‌خواست دستی نوشته شده.
 *
 * **و چرا دستی و نه یک کتابخانه:** آنچه لازم بود سه چیزِ کوچک است و
 * خروجی‌شان باید بایت‌به‌بایت همان قبلی بماند — این رشته‌ها روی فاکتورِ
 * چاپی و کلیدِ گزارش‌های ماهانه می‌نشینند و اگر یک کاراکتر عوض شود،
 * گزارشِ ماهِ گذشته با ماهِ آینده جور درنمی‌آید.
 */

/** جای `"%02d"`. */
fun Int.pad2(): String = if (this in 0..9) "0$this" else this.toString()

/**
 * جای `String.format(Locale.US, "%,d", n)` — جداکنندهٔ هزارگان با کاما.
 *
 * `Locale.US` در نسخهٔ قبل صریح بود تا روی گوشیِ فارسی‌زبان هم کاما
 * بماند و بعد `toPersianDigits` آن را به `٬` تبدیل کند. این پیاده‌سازی
 * اصلاً محلی‌سازی نمی‌شناسد، پس همان تضمین را ذاتاً دارد.
 *
 * روی رشته کار می‌کند نه روی `abs`، چون `abs(Long.MIN_VALUE)` سرریز
 * می‌کند و همان یک عدد بی‌صدا منفی می‌مانْد.
 */
fun Long.withThousands(): String {
    val s = this.toString()
    val negative = s.startsWith("-")
    val digits = if (negative) s.substring(1) else s
    val out = StringBuilder(digits.length + digits.length / 3 + 1)
    for (i in digits.indices) {
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
        out.append(digits[i])
    }
    return if (negative) "-$out" else out.toString()
}

/**
 * جای `"%.<digits>f"`.
 *
 * `roundToLong` مثلِ `String.format` نیمه را از صفر دور می‌کند، پس
 * ۰٫۲۵ با یک رقم می‌شود ۰٫۳ — همان چیزی که خودآزمایی تا امروز چاپ
 * می‌کرد.
 */
fun Double.toFixed(digits: Int): String {
    var factor = 1L
    repeat(digits) { factor *= 10 }
    val scaled = (this * factor).roundToLong()
    val negative = scaled < 0
    val body = abs(scaled).toString().padStart(digits + 1, '0')
    val whole = body.dropLast(digits)
    val frac = body.takeLast(digits)
    val text = if (digits == 0) whole else "$whole.$frac"
    return if (negative) "-$text" else text
}

/** جای `"%02x".format(byte)`. */
fun Byte.toHex2(): String {
    val v = this.toInt() and 0xFF
    val hex = "0123456789abcdef"
    return "${hex[v ushr 4]}${hex[v and 0x0F]}"
}

/**
 * همان [toFixed] برای `Float`.
 *
 * عرضِ ستون‌های کاغذ در PDF با `Float` نگه داشته می‌شود (واحدِ نقطه)، و
 * خودآزمایی همان‌ها را چاپ می‌کند.
 */
fun Float.toFixed(digits: Int): String = this.toDouble().toFixed(digits)
