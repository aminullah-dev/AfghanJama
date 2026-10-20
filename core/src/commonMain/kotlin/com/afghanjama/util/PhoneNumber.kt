package com.afghanjama.util

/**
 * شمارهٔ تلفن به شکلِ بین‌المللی — برای پیوندِ واتساپ (`wa.me`) و `tel:`.
 *
 * **چرا لازم شد.** کامپیوترِ کارگاه تلفن ندارد. «تماس» و «پیام» در پیگیریِ
 * طلب روی کامپیوتر تا ۱.۹ فقط شماره یا متن را بی‌صدا در کلیپ‌بورد کپی
 * می‌کردند. حالا واتساپ (دسکتاپ یا وب) با شماره و متنِ آماده باز می‌شود — و
 * `wa.me` شمارهٔ بین‌المللی می‌خواهد، نه «۰۷۰۰…»ی که کارگاه ثبت می‌کند.
 *
 * **قاعده:**
 * - رقمِ فارسی (۰–۹) و عربی (٠–٩) رقمِ لاتین می‌شود؛ فاصله، خط تیره،
 *   نقطه، پرانتز و نشانه‌های جهت (RLM/LRM) کنار می‌روند.
 * - «+…» و «00…» همان‌اند که نوشته شده (۸ تا ۱۵ رقم، سقفِ E.164).
 * - «93…»ی ۱۱ رقمی افغانستان است.
 * - «0» و یک رقمِ ۲ تا ۷ و هشت رقمِ دیگر (۱۰ رقم) شمارهٔ داخلیِ افغانستان
 *   است: همراه «07…» و ثابت «020…» و مانندِ آن → «+93…».
 * - «7…»ی ۹ رقمی همان همراهِ افغانستان است که صفرِ اولش افتاده.
 * - **هر چیزِ دیگر `null`:** حرف، شمارهٔ کوتاه یا بلند، یا شکلی که روشن
 *   نیست. آن‌وقت صداکننده به کپی در کلیپ‌بورد برمی‌گردد — حدس زدنِ شماره
 *   یعنی پیامِ طلب به آدمِ دیگری برود.
 */
object PhoneNumber {

    /** «+93700123456»، یا `null` اگر شماره روشن نیست. */
    fun international(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val plus = s.startsWith('+') || s.startsWith('＋')
        val digits = StringBuilder()
        for ((i, c) in s.withIndex()) {
            val d = asciiDigit(c)
            when {
                d != null -> digits.append(d)
                i == 0 && plus -> Unit
                c in SEPARATORS -> Unit
                else -> return null
            }
        }
        val n = digits.toString()
        return when {
            plus -> n.takeIf { it.length in 8..15 && it[0] != '0' }?.let { "+$it" }
            n.startsWith("00") -> n.drop(2).takeIf { it.length in 8..15 && it[0] != '0' }?.let { "+$it" }
            n.startsWith("93") && n.length == 11 -> "+$n"
            n.length == 10 && n[0] == '0' && n[1] in '2'..'7' -> "+93" + n.drop(1)
            n.length == 9 && n[0] == '7' -> "+93$n"
            else -> null
        }
    }

    /** همان، بی «+» — شکلی که `wa.me/<شماره>` می‌خواهد. */
    fun forWhatsApp(raw: String): String? = international(raw)?.removePrefix("+")

    private val SEPARATORS = setOf(
        ' ', ' ', '-', '‐', '–', '.', '(', ')', '/',
        '‎', '‏', '‌', '‪', '‫', '‬', '⁦', '⁧', '⁩'
    )

    private fun asciiDigit(c: Char): Char? = when (c) {
        in '0'..'9' -> c
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        else -> null
    }
}
