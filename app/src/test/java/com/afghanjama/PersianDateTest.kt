package com.afghanjama

import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.toLatinDigits
import com.afghanjama.ui.format.toPersianDigits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * تقویمِ شمسی زیرِ همهٔ تاریخ‌های فاکتور، حقوق و گزارش است؛ یک روزِ
 * اشتباه یعنی قسط یا حقوق در ماهِ دیگر بیفتد.
 */
class PersianDateTest {

    @Test
    fun `Nowruz maps to the first of Hamal`() {
        assertArrayEquals(intArrayOf(1404, 1, 1), PersianDate.gregorianToJalali(2025, 3, 21))
        assertArrayEquals(intArrayOf(1403, 1, 1), PersianDate.gregorianToJalali(2024, 3, 20))
    }

    @Test
    fun `the last day of a leap year is the 30th of Hoot`() {
        assertArrayEquals(intArrayOf(1403, 12, 30), PersianDate.gregorianToJalali(2025, 3, 20))
        assertArrayEquals(intArrayOf(2025, 3, 20), PersianDate.jalaliToGregorian(1403, 12, 30))
    }

    @Test
    fun `every day of 1396 to 1408 survives a round trip`() {
        for (jy in 1396..1408) for (jm in 1..12) {
            for (jd in 1..PersianDate.daysInJalaliMonth(jy, jm)) {
                val g = PersianDate.jalaliToGregorian(jy, jm, jd)
                val back = PersianDate.gregorianToJalali(g[0], g[1], g[2])
                assertArrayEquals("$jy/$jm/$jd", intArrayOf(jy, jm, jd), back)
            }
        }
    }

    @Test
    fun `month lengths follow 31, 30 and a leap Hoot`() {
        for (m in 1..6) assertEquals(31, PersianDate.daysInJalaliMonth(1404, m))
        for (m in 7..11) assertEquals(30, PersianDate.daysInJalaliMonth(1404, m))
        assertEquals(29, PersianDate.daysInJalaliMonth(1404, 12))
        assertEquals(30, PersianDate.daysInJalaliMonth(1403, 12))
    }

    @Test
    fun `a Jalali day ends one millisecond before the next begins`() {
        val start = PersianDate.startOfJalaliDay(1404, 4, 15)
        assertEquals(start + 86_400_000L - 1, PersianDate.endOfJalaliDay(1404, 4, 15))
    }

    @Test
    fun `recentMonths crosses the new year in order`() {
        val now = PersianDate.startOfJalaliDay(1405, 2, 10) + 3_600_000L
        val keys = PersianDate.recentMonths(4, now).map { it.first }
        assertEquals(listOf("1404-11", "1404-12", "1405-01", "1405-02"), keys)
        assertEquals("حوت ۱۴۰۴", PersianDate.recentMonths(4, now)[1].second)
        assertEquals(emptyList<Pair<String, String>>(), PersianDate.recentMonths(0, now))
    }

    @Test
    fun `formatters agree for one instant`() {
        val ms = PersianDate.startOfJalaliDay(1404, 4, 5) + (10 * 60 + 30) * 60_000L
        assertEquals("1404/04/05 10:30", PersianDate.csv(ms))
        assertEquals("۱۴۰۴/۰۴/۰۵", PersianDate.short(ms))
        assertEquals("۵ سرطان ۱۴۰۴", PersianDate.long(ms))
        assertEquals("1404-04", PersianDate.monthKey(ms))
        assertEquals(5, PersianDate.dayOfMonth(ms))
    }

    @Test
    fun `digit conversion handles Persian, Arabic-Indic and separators`() {
        assertEquals("۱٬۲۳۴", "1,234".toPersianDigits())
        assertEquals("123", "۱۲۳".toLatinDigits())
        assertEquals("123", "١٢٣".toLatinDigits())
        assertEquals("1500", "۱۵۰۰ af".digitsOnly())
    }
}
