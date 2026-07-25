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

    private val jalaliMonthDays = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
    private val gregorianMonthDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    /**
     * جلالی → میلادی. وارونهٔ دقیقِ [gregorianToJalali] است؛ رفت‌وبرگشتِ
     * همهٔ روزهای ۱۳۹۶ تا ۱۴۰۸ آزموده شده (شاملِ حوتِ ۳۰ روزهٔ کبیسه).
     * خروجی: [سال، ماه ۱..۱۲، روز].
     */
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): IntArray {
        val jy1 = jy - 979
        var dayNo = 365 * jy1 + (jy1 / 33) * 8 + ((jy1 % 33 + 3) / 4)
        for (i in 0 until (jm - 1)) dayNo += jalaliMonthDays[i]
        dayNo += jd - 1

        var g = dayNo + 79
        var gy = 1600 + 400 * (g / 146097)
        g %= 146097
        var leap = true
        if (g >= 36525) {
            g--
            gy += 100 * (g / 36524)
            g %= 36524
            if (g >= 365) g++ else leap = false
        }
        gy += 4 * (g / 1461)
        g %= 1461
        if (g >= 366) {
            leap = false
            g--
            gy += g / 365
            g %= 365
        }
        var i = 0
        while (g >= gregorianMonthDays[i] + (if (i == 1 && leap) 1 else 0)) {
            g -= gregorianMonthDays[i] + (if (i == 1 && leap) 1 else 0)
            i++
        }
        return intArrayOf(gy, i + 1, g + 1)
    }

    /** ابتدای (۰۰:۰۰) یک روزِ شمسی به میلی‌ثانیه. */
    fun startOfJalaliDay(jy: Int, jm: Int, jd: Int): Long {
        val g = jalaliToGregorian(jy, jm, jd)
        return Calendar.getInstance().apply {
            clear()
            set(g[0], g[1] - 1, g[2], 0, 0, 0)
        }.timeInMillis
    }

    /** پایان (۲۳:۵۹:۵۹٫۹۹۹) یک روزِ شمسی — تا کلِ آن روز در بازه بیفتد. */
    fun endOfJalaliDay(jy: Int, jm: Int, jd: Int): Long =
        startOfJalaliDay(jy, jm, jd) + 86_400_000L - 1

    /** تعدادِ روزهای یک ماهِ شمسی (حوت در سالِ کبیسه ۳۰ روز است). */
    fun daysInJalaliMonth(jy: Int, jm: Int): Int = when {
        jm in 1..6 -> 31
        jm in 7..11 -> 30
        // حوت: اگر ۳۰ حوت به همان سال برگردد، سال کبیسه است
        else -> if (gregorianToJalali(
                jalaliToGregorian(jy, 12, 30)[0],
                jalaliToGregorian(jy, 12, 30)[1],
                jalaliToGregorian(jy, 12, 30)[2]
            ).let { it[0] == jy && it[1] == 12 && it[2] == 30 }
        ) 30 else 29
    }

    /** امروز به شکلِ [سال، ماه، روز] شمسی. */
    fun todayJalali(millis: Long = System.currentTimeMillis()): IntArray =
        jalaliOf(millis).first

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

    /** روزِ ماهِ شمسی (۱..۳۱) — برای مقایسهٔ «تا امروز» با همین بازه در ماهِ قبل. */
    fun dayOfMonth(millis: Long): Int {
        val (j, _, _) = jalaliOf(millis)
        return j[2]
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
