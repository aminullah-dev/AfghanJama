package com.afghanjama.ui.format

import java.util.Calendar
import java.util.Date

/**
 * تقویم هجری شمسی با نام ماه‌های افغانی + تبدیل اعداد فارسی.
 * الگوریتم تبدیل، پیاده‌سازی حسابیِ استاندارد جلالی است و به هیچ
 * کتابخانهٔ خارجی نیاز ندارد.
 */
object PersianDate {

    /** نام ماه‌های هجری شمسی در افغانستان. */
    val afghanMonths = arrayOf(
        "حمل", "ثور", "جوزا", "سرطان", "اسد", "سنبله",
        "میزان", "عقرب", "قوس", "جدی", "دلو", "حوت"
    )

    /** میلادی → جلالی. خروجی: [سال، ماه ۱..۱۲، روز]. */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return intArrayOf(jy, jm, jd)
    }

    private fun jalaliOf(millis: Long): Triple<IntArray, Int, Int> {
        val cal = Calendar.getInstance().apply { time = Date(millis) }
        val j = gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        return Triple(j, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    /** «۱۴۰۴/۰۴/۱۵» — عددی و فشرده برای لیست‌ها. */
    fun short(millis: Long): String {
        val (j, _, _) = jalaliOf(millis)
        return "%d/%02d/%02d".format(j[0], j[1], j[2]).toPersianDigits()
    }

    /** «۱۴۰۴/۰۴/۱۵ ۱۰:۳۰» — با ساعت. */
    fun shortWithTime(millis: Long): String {
        val (j, h, m) = jalaliOf(millis)
        return "%d/%02d/%02d %02d:%02d".format(j[0], j[1], j[2], h, m).toPersianDigits()
    }

    /** «۱۵ سرطان ۱۴۰۴» — بلند و رسمی برای فاکتور و رسید. */
    fun long(millis: Long): String {
        val (j, _, _) = jalaliOf(millis)
        return "${j[2].toString().toPersianDigits()} ${afghanMonths[j[1] - 1]} ${j[0].toString().toPersianDigits()}"
    }

    /** «سنبله ۱۴۰۴» — نامِ ماهِ شمسی؛ برای دورهٔ حقوق. */
    fun monthLabel(millis: Long): String {
        val (j, _, _) = jalaliOf(millis)
        return "${afghanMonths[j[1] - 1]} ${j[0].toString().toPersianDigits()}"
    }

    /**
     * «1404-06» — کلیدِ ماه با ارقامِ لاتین؛ برای گروه‌بندی و مقایسه.
     * Locale.US صریح است تا روی گوشیِ فارسی‌زبان هم کلید همیشه یکسان بماند.
     */
    fun monthKey(millis: Long): String {
        val (j, _, _) = jalaliOf(millis)
        return String.format(java.util.Locale.US, "%d-%02d", j[0], j[1])
    }

    /**
     * [count] ماهِ اخیرِ شمسی (شاملِ ماهِ جاری)، از قدیمی به جدید،
     * به شکلِ «کلید به برچسب» — مثلاً `"1405-05" to "اسد ۱۴۰۵"`.
     * محاسبه روی شمارندهٔ ماه انجام می‌شود تا عبور از سالِ نو درست باشد.
     */
    fun recentMonths(count: Int, now: Long = System.currentTimeMillis()): List<Pair<String, String>> {
        if (count <= 0) return emptyList()
        val (j, _, _) = jalaliOf(now)
        val base = j[0] * 12 + (j[1] - 1)
        return ((count - 1) downTo 0).map { back ->
            val idx = base - back
            val y = idx / 12
            val m = idx % 12 + 1
            String.format(java.util.Locale.US, "%d-%02d", y, m) to
                "${afghanMonths[m - 1]} ${y.toString().toPersianDigits()}"
        }
    }

    /** «1404/04/15 10:30» — با ارقام لاتین؛ برای CSV (قابل مرتب‌سازی در اکسل). */
    fun csv(millis: Long): String {
        val (j, h, m) = jalaliOf(millis)
        return "%d/%02d/%02d %02d:%02d".format(j[0], j[1], j[2], h, m)
    }
}

private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

/** تبدیل ارقام لاتین به فارسی؛ جداکنندهٔ هزارگان هم فارسی می‌شود. */
fun String.toPersianDigits(): String = buildString(length) {
    for (c in this@toPersianDigits) {
        append(
            when (c) {
                in '0'..'9' -> persianDigits[c - '0']
                ',' -> '٬'
                else -> c
            }
        )
    }
}

/**
 * تبدیل ارقام فارسی/عربی به لاتین — برای ورودی کاربر.
 * (کیبوردهای فارسی «۱۲۳» می‌فرستند که toLongOrNull آن را نمی‌فهمد.)
 */
fun String.toLatinDigits(): String = buildString(length) {
    for (c in this@toLatinDigits) {
        append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')   // فارسی
                in '٠'..'٩' -> '0' + (c - '٠')   // عربی
                else -> c
            }
        )
    }
}

/** فقط ارقام (با نرمال‌سازی فارسی/عربی → لاتین) — برای فیلدهای عددی. */
fun String.digitsOnly(): String =
    toLatinDigits().filter { it in '0'..'9' }

/** ارقام + ممیز — برای فیلدهای اعشاری مثل مقدار پارچه. */
fun String.decimalOnly(): String =
    toLatinDigits().replace('،', '.').replace('٫', '.').filter { it in '0'..'9' || it == '.' }

/** نمایش فارسی یک عدد صحیح. */
fun Int.fa(): String = toString().toPersianDigits()
fun Long.fa(): String = toString().toPersianDigits()
