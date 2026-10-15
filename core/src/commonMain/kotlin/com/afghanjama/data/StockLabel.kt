package com.afghanjama.data

/**
 * برچسبِ قفسه برای یک کالای انبارِ محصول — کدی که روی QR می‌نشیند و در
 * «اسکنِ کالا» خوانده می‌شود تا بهای تمام‌شدهٔ فی‌عددِ همان کالا دیده شود.
 *
 * **چرا شناسه و نه نام.** مثلِ [EmployeeCard]: نامِ طرح عوض می‌شود ولی
 * شناسهٔ ردیفِ `FinishedStock` نه. کد شناسه را می‌بَرد و نام/موجودی/بها
 * سرِ اسکن از دیتابیس خوانده می‌شود، پس برچسبِ چاپ‌شده با تغییرِ نام باطل
 * نمی‌شود. روی برچسب فقط کد می‌نشیند، **نه بها** — وگرنه مشتری سودِ
 * کارگاه را روی قفسه می‌خواند.
 *
 * **چرا از کارتِ کارمند جدا می‌مانَد.** کدِ کالا و کدِ کارمند نباید
 * اشتباه خوانده شوند. دو نگهبان این را تضمین می‌کنند:
 *  - پیشوندِ «ks» (کالا) در برابرِ «ky» (کارمند)؛
 *  - یک **رقمِ نشانگر** `9` سرِ بدنه که کارتِ کارمند هرگز نمی‌سازد
 *    (کارمند رقمِ نوعش ۱/۲/۳ است). پس [EmployeeCard.parse] یک کدِ کالا
 *    را رد می‌کند و این‌جا یک کارتِ کارمند رد می‌شود — حتی اگر کسی برچسب
 *    را جلوی اسکنرِ اشتباه بگیرد.
 *
 * **رقمِ کنترلی** همان ISO 7064 mod-97 کارتِ کارمند است: دو رقمِ آخر هر
 * خطای تک‌رقمی و هر جابه‌جاییِ دو رقمِ کنارِ هم را می‌گیرد. بی‌وابستگی به
 * اندروید/دیتابیس تا CI بسنجدش.
 */
object StockLabel {

    /** پیشوندِ روی برچسب. حرفِ کوچک، تا اسکنرِ کیبوردی Shift نزند. */
    const val PREFIX = "ks"

    /** رقمِ نشانگرِ «کالا» سرِ بدنه — جدا از نوع‌های کارمند (۱/۲/۳). */
    private const val MARKER = '9'

    /** نشانگر + دستِ‌کم یک رقمِ شناسه + دو رقمِ کنترلی. */
    private const val MIN_DIGITS = 4

    /** نشانگر + شناسهٔ Long (حداکثر ۱۹ رقم) + دو رقمِ کنترلی. */
    private const val MAX_DIGITS = 1 + 19 + 2

    /** کدِ کامل برای QR، مثل `ks9742xx`. */
    fun encode(id: Long): String {
        require(id > 0) { "شناسهٔ کالا باید مثبت باشد" }
        val body = "$MARKER$id"
        return PREFIX + body + checkDigits(body)
    }

    /**
     * شناسهٔ کالا، یا `null` اگر کدِ کالا نباشد — کارتِ کارمند، QRِ سفارش،
     * یا کدی که یک رقمش بد خوانده شده.
     */
    fun parse(raw: String): Long? {
        val s = normalizeDigits(raw.trim())
        if (s.length < 2 + MIN_DIGITS || s.length > 2 + MAX_DIGITS) return null
        if (s[0].isAsciiDigit() || s[1].isAsciiDigit()) return null
        val digits = s.substring(2)
        if (!digits.all { it.isAsciiDigit() }) return null
        if (mod97(digits) != 1) return null
        if (digits[0] != MARKER) return null
        val idText = digits.substring(1, digits.length - 2)
        if (idText.isEmpty() || idText[0] == '0') return null
        return idText.toLongOrNull()?.takeIf { it > 0 }
    }

    // ---------------- جزئیات (آینهٔ EmployeeCard) ----------------

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    internal fun checkDigits(body: String): String {
        val c = 98 - mod97(body + "00")
        return c.toString().padStart(2, '0')
    }

    private fun mod97(digits: String): Int {
        var r = 0
        for (ch in digits) r = (r * 10 + (ch - '0')) % 97
        return r
    }

    private fun normalizeDigits(s: String): String = buildString(s.length) {
        for (ch in s) {
            append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                }
            )
        }
    }
}
