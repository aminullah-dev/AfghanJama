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
 * - «+…» و «00…» همان‌اند که نوشته شده (۸ تا ۱۵ رقم، سقفِ E.164)؛ «+93»
 *   فقط با ۹ رقم پس از آن.
 * - «93…»ی ۱۱ رقمی افغانستان است.
 * - «0» و یک رقمِ ۲ تا ۷ و هشت رقمِ دیگر (۱۰ رقم) شمارهٔ داخلیِ افغانستان
 *   است: همراه «07…» و ثابت «020…» و مانندِ آن → «+93…».
 * - «7…»ی ۹ رقمی همان همراهِ افغانستان است که صفرِ اولش افتاده.
 * - **صفرِ داخلی پس از کدِ کشور می‌افتد.** «+93 (0) 700…» شکلِ رایجِ
 *   کارت‌ویزیت است: «(0)» یعنی «در داخلِ کشور صفر را هم بگیرید» و جزوِ
 *   شمارهٔ بین‌المللی نیست. تا ۱.۱۰ همین صفر می‌ماند و «+930700…»ی
 *   نادرست ساخته می‌شد. «+93 0700…» (بی پرانتز) هم همین است، چون شمارهٔ
 *   افغانستان پس از ۹۳ هرگز با صفر شروع نمی‌شود.
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
        var i = 0
        while (i < s.length) {
            val c = s[i]
            // «(0)» پس از کدِ کشور: صفرِ داخلی، نه رقمی از شماره.
            if (c == '(' && digits.isNotEmpty()) {
                val end = trunkZeroEnd(s, i)
                if (end >= 0) {
                    i = end + 1
                    continue
                }
            }
            val d = asciiDigit(c)
            when {
                d != null -> digits.append(d)
                i == 0 && plus -> Unit
                c in SEPARATORS -> Unit
                else -> return null
            }
            i++
        }
        val n = digits.toString()
        return when {
            plus -> withCountryCode(n)
            n.startsWith("00") -> withCountryCode(n.drop(2))
            n.startsWith("93") && n.length == 11 -> "+$n"
            n.length == 10 && n[0] == '0' && n[1] in '2'..'7' -> "+93" + n.drop(1)
            n.length == 9 && n[0] == '7' -> "+93$n"
            else -> null
        }
    }

    /**
     * شمارهٔ پس از «+» یا «00». «930…»ی ۱۲ رقمی همان «93…» است که صفرِ
     * داخلی‌اش بی پرانتز مانده؛ هیچ کدِ کشوری با «930» شروع نمی‌شود. شمارهٔ
     * افغانستان پس از ۹۳ همیشه ۹ رقم است؛ جز آن روشن نیست.
     */
    private fun withCountryCode(n: String): String? {
        val m = if (n.length == 12 && n.startsWith("930") && n[3] in '2'..'7') "93" + n.drop(3) else n
        if (m.startsWith("93") && m.length != 11) return null
        return m.takeIf { it.length in 8..15 && it[0] != '0' }?.let { "+$it" }
    }

    /**
     * اگر از [open] (یک «(») تنها یک صفر و «)» آمده باشد — با فاصلهٔ
     * دلخواه — جای «)» را برمی‌گرداند؛ وگرنه ۱-. «(0700)» صفرِ داخلی نیست.
     */
    private fun trunkZeroEnd(s: String, open: Int): Int {
        var j = open + 1
        while (j < s.length && s[j] in SPACES) j++
        if (j >= s.length || asciiDigit(s[j]) != '0') return -1
        j++
        while (j < s.length && s[j] in SPACES) j++
        return if (j < s.length && s[j] == ')') j else -1
    }

    private val SPACES = setOf(' ', ' ')

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
