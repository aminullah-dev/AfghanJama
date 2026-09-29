package com.afghanjama

import com.afghanjama.data.EntryGuard
import com.afghanjama.data.EntryGuard.Kind
import com.afghanjama.data.EntryGuard.Past
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نگهبانِ خطا — تکرار، صفرِ اضافه یا کم، و مبلغِ غیرعادی؛ و سکوت وقتی
 * نمونه کافی نیست.
 */
class EntryGuardTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_000 * day
    private val money: (Long) -> String = { it.toString() }

    /** حقوقِ هفتگیِ احمد — معمولاً ۱٬۵۰۰. */
    private val ahmad = listOf(
        Past("احمد", 1_500, now - 7 * day),
        Past("احمد", 1_500, now - 14 * day),
        Past("احمد", 1_600, now - 21 * day),
    )

    private fun kinds(party: String, amount: Long, past: List<Past>) =
        EntryGuard.check(party, amount, past, now, money).map { it.kind }

    @Test
    fun `the same amount to the same person today is a likely double entry`() {
        val past = ahmad + Past("احمد", 1_500, now - 2 * hour)
        val w = EntryGuard.check("احمد", 1_500, past, now, money)
        assertEquals(listOf(Kind.DUPLICATE), w.map { it.kind })
        assertTrue(w.single().text.contains("2 ساعت پیش"))
        // دیروزِ دیروز تکرار نیست
        assertTrue(kinds("احمد", 1_500, ahmad).isEmpty())
    }

    @Test
    fun `ten times the usual that becomes usual without a zero is an extra zero`() {
        val w = EntryGuard.check("احمد", 15_000, ahmad, now, money).single()
        assertEquals(Kind.EXTRA_ZERO, w.kind)
        assertEquals(1_500L, w.suggested)
    }

    @Test
    fun `a tenth of the usual is a missing zero`() {
        val w = EntryGuard.check("احمد", 150, ahmad, now, money).single()
        assertEquals(Kind.MISSING_ZERO, w.kind)
        assertEquals(1_500L, w.suggested)
    }

    @Test
    fun `far above the usual without a zero story is just unusual`() {
        assertEquals(listOf(Kind.UNUSUAL), kinds("احمد", 40_000, ahmad))
        // دو برابر عادی است — پاداش، دو هفته با هم
        assertTrue(kinds("احمد", 3_000, ahmad).isEmpty())
    }

    @Test
    fun `a new person is judged by the kind, and silence without enough samples`() {
        val staff = ahmad + listOf(
            Past("محمود", 1_400, now - 3 * day),
            Past("زهرا", 1_450, now - 4 * day),
        )
        assertEquals(listOf(Kind.EXTRA_ZERO), kinds("کریم", 14_000, staff))
        assertTrue(kinds("کریم", 14_000, ahmad.take(2)).isEmpty())
    }

    @Test
    fun `names are matched regardless of ye and spaces`() {
        val past = listOf(Past("حاجي نصير", 2_000, now - hour))
        assertEquals(listOf(Kind.DUPLICATE), kinds(" حاجی  نصیر", 2_000, past))
    }
}
