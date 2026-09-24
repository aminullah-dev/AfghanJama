package com.afghanjama.data

/**
 * کارتِ کارمند — کدی که روی QRِ کارت می‌نشیند و در اسکنِ حضور خوانده
 * می‌شود.
 *
 * **چرا شناسه و نه نام.** حضور و غیاب با نام ثبت می‌شود، ولی نام عوض
 * می‌شود («احمد» می‌شود «احمد نوری») و کارتِ چاپ‌شده عوض نمی‌شود. کارت
 * نوع و شناسهٔ ردیف را می‌بَرد و نامِ امروز سرِ اسکن از دیتابیس خوانده
 * می‌شود؛ کارت با تغییرِ نام باطل نمی‌شود.
 *
 * **چرا فقط رقم.** اسکنرِ USBِ کمپیوتر خودش را کیبورد جا می‌زند و حرف را
 * با کلیدِ کیبورد تایپ می‌کند. وقتی ویندوز روی زبانِ فارسی است، به‌جای
 * `ky` حروفِ `نغ` می‌رسد — ولی ردیفِ رقم‌ها رقم می‌مانَد (گاهی رقمِ
 * فارسی). پس بدنهٔ کد تمامْ رقم است و پیشوندِ دوحرفی فقط نشانه است:
 * هر دو حرفِ غیرِرقمی پذیرفته می‌شود و رقمِ فارسی و عربی به لاتین
 * برمی‌گردد.
 *
 * **رقمِ کنترلی (ISO 7064، mod 97).** دو رقمِ آخر هر خطای تک‌رقمی و هر
 * جابه‌جاییِ دو رقمِ کنارِ هم را می‌گیرد — همان روشِ شمارهٔ IBAN. بی
 * آن، QRِ دیگری که تصادفاً رقمی باشد یا یک رقمِ بدخوانده، ورودِ کسِ
 * دیگری را ثبت می‌کرد.
 *
 * **این کارت جعل‌ناپذیر نیست** و قرار هم نیست باشد: هر کس الگو را بداند
 * می‌تواند کدِ درست بسازد. نگهبانِ واقعی مدیری است که پشتِ اسکنر
 * ایستاده و چهره را می‌بیند؛ کارت فقط تایپ و انتخابِ نام را حذف می‌کند.
 */
object EmployeeCard {

    /** پیشوندِ روی کارت. حرفِ کوچک، تا اسکنرِ کیبوردی Shift نزند. */
    const val PREFIX = "ky"

    /**
     * فاصلهٔ کمینه بینِ دو ثبت برای یک نفر.
     *
     * اسکنر در یک ثانیه چند بار می‌خوانَد و کارمند گاهی کارت را دو بار
     * جلوی دوربین می‌گیرد. بی این فاصله، ورودی که همین حالا ثبت شد با
     * اسکنِ دوم خروج می‌خورد و بازه‌ای صفردقیقه‌ای در کارکرد می‌ماند.
     */
    const val MIN_GAP_MS: Long = 60_000L

    enum class Kind(val digit: Char, val label: String) {
        TAILOR('1', "خیاط"),
        INSPECTOR('2', "ناظر"),
        STAFF('3', "کارمند");

        companion object {
            fun of(digit: Char): Kind? = entries.firstOrNull { it.digit == digit }
        }
    }

    data class Ref(val kind: Kind, val id: Long)

    /** کدِ کامل برای QR، مثل `ky1000742`. */
    fun encode(kind: Kind, id: Long): String {
        require(id > 0) { "شناسهٔ کارمند باید مثبت باشد" }
        val body = "${kind.digit}$id"
        return PREFIX + body + checkDigits(body)
    }

    fun encode(ref: Ref): String = encode(ref.kind, ref.id)

    /**
     * خواندنِ کدِ اسکن‌شده. `null` یعنی «این کارتِ کارمند نیست» — QRِ
     * سفارش، رسید، یا کدی که یک رقمش بد خوانده شده.
     */
    fun parse(raw: String): Ref? {
        val s = normalizeDigits(raw.trim())
        if (s.length < 2 + MIN_DIGITS || s.length > 2 + MAX_DIGITS) return null
        if (s[0].isAsciiDigit() || s[1].isAsciiDigit()) return null
        val digits = s.substring(2)
        if (!digits.all { it.isAsciiDigit() }) return null
        if (mod97(digits) != 1) return null

        val kind = Kind.of(digits[0]) ?: return null
        val idText = digits.substring(1, digits.length - 2)
        if (idText.isEmpty() || idText[0] == '0') return null
        val id = idText.toLongOrNull() ?: return null
        return Ref(kind, id)
    }

    /** اقدامی که یک اسکن باید بکند. */
    enum class Action { CHECK_IN, CHECK_OUT, TOO_SOON }

    /**
     * ورود یا خروج — از روی آخرین رویدادِ همین نفر.
     *
     * [isIn]: بازهٔ بازی دارد. [lastEventAt]: زمانِ آخرین ورود یا خروجِ
     * ثبت‌شده، یا `null` اگر هرگز ثبتی نداشته.
     */
    fun decide(isIn: Boolean, lastEventAt: Long?, now: Long): Action = when {
        lastEventAt != null && now - lastEventAt in 0 until MIN_GAP_MS -> Action.TOO_SOON
        isIn -> Action.CHECK_OUT
        else -> Action.CHECK_IN
    }

    // ---------------- جزئیات ----------------

    /** نوع (۱) + دستِ‌کم یک رقمِ شناسه + دو رقمِ کنترلی. */
    private const val MIN_DIGITS = 4

    /** شناسهٔ Long حداکثر ۱۹ رقم است. */
    private const val MAX_DIGITS = 1 + 19 + 2

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

    /** رقمِ فارسی (۰–۹) و عربی (٠–٩) به لاتین؛ بقیه دست‌نخورده. */
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

/** نتیجهٔ یک اسکنِ کارت — هر حالت پیامِ خودش را دارد و هیچ‌کدام بی‌صدا نیست. */
sealed interface CardScan {
    /** ورود یا خروج ثبت شد. */
    data class Recorded(val name: String, val checkedIn: Boolean, val at: Long) : CardScan

    /** همین نفر کمتر از [EmployeeCard.MIN_GAP_MS] پیش ثبت شده — دوباره نمی‌زند. */
    data class TooSoon(val name: String, val isIn: Boolean) : CardScan

    /** QRِ دیگری است، یا کدی که یک رقمش بد خوانده شده. */
    data object NotACard : CardScan

    /** کد درست است ولی صاحبش از «اطلاعات پایه» پاک شده. */
    data object Unknown : CardScan
}
