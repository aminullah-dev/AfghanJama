package com.afghanjama.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * ذخیره و وارسیِ رمزِ عددی — با نمک و تکرار، نه هشِ ساده.
 *
 * **وضعی که از آن آمدیم:**
 *
 *  • رمزِ ورود (`AuthViewModel`) **متنِ خام** ذخیره می‌شد. هر کسی که به
 *    فایلِ تنظیمات می‌رسید، رمزِ کارگاه را می‌خواند.
 *  • قفلِ اپ (`AppLock`) `SHA-256`ِ بی‌نمک بود. رمزِ ۴ رقمی فقط ۱۰٬۰۰۰
 *    حالت دارد؛ ساختنِ جدولِ همهٔ آن‌ها روی هر لپ‌تاپی کسری از ثانیه
 *    است. عملاً با متنِ خام فرقی نداشت.
 *
 * حالا هر دو از اینجا می‌گذرند: نمکِ تصادفیِ ۱۶ بایتی و PBKDF2 با
 * [ITERATIONS] تکرار. نمک یعنی جدولِ ازپیش‌ساخته بی‌فایده است، و تکرار
 * یعنی هر حدس هزینه دارد.
 *
 * **هیچ‌کس از اپ بیرون نمی‌ماند.** [verify] هر سه شکل را می‌شناسد —
 * تازه، هشِ قدیمی، و متنِ خام — و [needsUpgrade] می‌گوید کِی باید
 * دوباره نوشته شود. ارتقا همان لحظه‌ای انجام می‌شود که کاربر با رمزِ
 * درست وارد می‌شود، بی آنکه چیزی بپرسد.
 *
 * **چرا اینجا و نه در `:app`:** رمزِ ورود در `:core` است و ویندوز هم
 * همان را می‌خواند. دو پیاده‌سازی یعنی روزی یکی ارتقا می‌گیرد و آن یکی
 * نه، و بعد دفترِ یک سکو باز می‌شود و آن یکی نه.
 */
object PinHash {

    /** نشانهٔ قالبِ تازه. قالب: `v2$الگوریتم$تکرار$نمک$هش` */
    private const val TAG = "v2"

    /**
     * ۱۰۰٬۰۰۰ تکرار — تعادلِ بینِ هزینهٔ حمله و صبرِ کاربر.
     *
     * روی گوشیِ ارزان چند صدم تا چند دهمِ ثانیه طول می‌کشد و فقط سرِ
     * ورود یا باز کردنِ قفل اجرا می‌شود، نه در هر صفحه.
     */
    private const val ITERATIONS = 100_000

    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    /**
     * `…SHA256` روی اندروید از API 26 هست و minSdk اینجا ۲۴ است، پس
     * نمی‌شود فرضش کرد. الگوریتمِ واقعی **در خودِ رشته نوشته می‌شود**
     * تا وارسی همیشه بداند با کدام ساخته شده — وگرنه گوشیِ قدیمی
     * رمزی می‌ساخت که گوشیِ تازه نمی‌توانست بخواند.
     */
    private val ALGORITHMS = listOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")

    private fun factory(name: String): SecretKeyFactory? =
        runCatching { SecretKeyFactory.getInstance(name) }.getOrNull()

    private fun derive(pin: String, salt: ByteArray, iterations: Int, algo: String): ByteArray? {
        val f = factory(algo) ?: return null
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return runCatching { f.generateSecret(spec).encoded }.getOrNull()
    }

    /** رشته‌ای که باید ذخیره شود. */
    fun hash(pin: String): String {
        val p = pin.trim()
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        for (algo in ALGORITHMS) {
            val key = derive(p, salt, ITERATIONS, algo) ?: continue
            return "$TAG$$algo$$ITERATIONS$${salt.hex()}$${key.hex()}"
        }
        // اگر هیچ‌کدام نبود، بی‌صدا به قالبِ ضعیف برنمی‌گردیم: بهتر است
        // بلند بشکند تا اینکه کارگاه فکر کند رمزش امن است.
        error("هیچ الگوریتمِ PBKDF2ی روی این دستگاه نیست: $ALGORITHMS")
    }

    /**
     * آیا [pin] با [stored] می‌خواند؟
     *
     * سه قالب پذیرفته است:
     *  • `v2$…`     قالبِ تازه
     *  • ۶۴ نویسهٔ شانزده‌شانزدهی — هشِ `SHA-256`ِ قدیمیِ قفلِ اپ
     *  • هر چیزِ دیگر — رمزِ خامِ قدیمیِ صفحهٔ ورود
     */
    fun verify(pin: String, stored: String): Boolean {
        val p = pin.trim()
        val s = stored.trim()
        if (s.isEmpty()) return false

        if (s.startsWith("$TAG$")) {
            val parts = s.split("$")
            if (parts.size != 5) return false
            val (_, algo, iterText, saltHex, keyHex) = parts
            val iterations = iterText.toIntOrNull() ?: return false
            val salt = saltHex.unhex() ?: return false
            val key = derive(p, salt, iterations, algo) ?: return false
            return key.hex().constantTimeEquals(keyHex)
        }

        // قالب‌های قدیمی. مقایسه همچنان زمان‌ثابت است تا خودِ وارسی
        // سرنخِ تازه‌ای ندهد.
        if (s.length == 64 && s.all { it.isHexDigit() }) {
            return legacySha256(p).constantTimeEquals(s)
        }
        return p.constantTimeEquals(s)
    }

    /**
     * آیا این مقدار باید دوباره با قالبِ تازه نوشته شود؟
     *
     * صدا زدنش فقط **بعد از** [verify]ِ موفق معنا دارد: ارتقا بدونِ
     * دانستنِ رمزِ درست ممکن نیست، چون نمکِ تازه رمز می‌خواهد.
     */
    fun needsUpgrade(stored: String): Boolean = !stored.trim().startsWith("$TAG$")

    // ── کمکی‌ها ──────────────────────────────────────────────────

    private fun legacySha256(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).hex()

    private fun ByteArray.hex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.unhex(): ByteArray? {
        if (length % 2 != 0 || !all { it.isHexDigit() }) return null
        return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /**
     * مقایسهٔ زمان‌ثابت.
     *
     * مقایسهٔ معمولیِ رشته سرِ اولین نویسهٔ متفاوت برمی‌گردد، پس زمانِ
     * پاسخ می‌گوید چند نویسهٔ اول درست بوده. برای رمزِ ۴ رقمی این
     * حملهٔ عملی‌ای نیست، ولی هزینه‌اش هم صفر است.
     */
    private fun String.constantTimeEquals(other: String): Boolean {
        if (length != other.length) return false
        var diff = 0
        for (i in indices) diff = diff or (this[i].code xor other[i].code)
        return diff == 0
    }
}
