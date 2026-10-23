package com.afghanjama

import com.afghanjama.data.BreakEven
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نقطهٔ سربه‌سرِ ماه — خطی که زیرش ضرر است و بالایش سود.
 *
 * مرزِ مهم: با حاشیهٔ صفر یا منفی، فروشِ بیشتر سربه‌سر نمی‌آورد؛ عددِ قلابیِ
 * «این‌قدر بفروش» نباید داده شود، چون کاربر را به ضررِ بیشتر می‌کشاند.
 */
class BreakEvenTest {

    private fun inp(
        fixedCostsMonthly: Long = 0,
        grossMarginPercent: Int = 0,
        revenueThisMonth: Long = 0,
        grossProfitThisMonth: Long = 0,
    ) = BreakEven.Inputs(
        fixedCostsMonthly = fixedCostsMonthly,
        grossMarginPercent = grossMarginPercent,
        revenueThisMonth = revenueThisMonth,
        grossProfitThisMonth = grossProfitThisMonth,
    )

    @Test
    fun `mid-month, still covering the fixed costs`() {
        val s = BreakEven.assess(
            inp(fixedCostsMonthly = 100_000, grossMarginPercent = 40,
                revenueThisMonth = 150_000, grossProfitThisMonth = 60_000)
        )
        assertEquals(250_000L, s.breakEvenRevenue)   // ۱۰۰هزار ÷ ۴۰٪
        assertEquals(60, s.coveredPercent)
        assertFalse(s.pastBreakEven)
        assertEquals(100_000L, s.revenueToGo)        // ۴۰هزار سودِ مانده ÷ ۴۰٪
    }

    @Test
    fun `past break-even, the month is in profit`() {
        val s = BreakEven.assess(
            inp(fixedCostsMonthly = 100_000, grossMarginPercent = 40, grossProfitThisMonth = 120_000)
        )
        assertTrue(s.pastBreakEven)
        assertEquals(120, s.coveredPercent)
        assertEquals(0L, s.revenueToGo)
    }

    @Test
    fun `a non-positive margin cannot break even by selling more`() {
        val s = BreakEven.assess(
            inp(fixedCostsMonthly = 100_000, grossMarginPercent = 0, grossProfitThisMonth = 20_000)
        )
        assertNull(s.breakEvenRevenue)
        assertNull(s.revenueToGo)        // فروشِ بیشتر فقط ضررِ بیشتر
        assertFalse(s.pastBreakEven)
        assertEquals(20, s.coveredPercent)
    }

    @Test
    fun `no fixed costs means always past break-even`() {
        val s = BreakEven.assess(inp(grossMarginPercent = 40, grossProfitThisMonth = 50_000))
        assertTrue(s.pastBreakEven)
        assertEquals(100, s.coveredPercent)
        assertEquals(0L, s.revenueToGo)
        assertEquals(0L, s.breakEvenRevenue)
    }

    @Test
    fun `break-even revenue scales with the margin`() {
        assertEquals(200_000L, BreakEven.assess(inp(fixedCostsMonthly = 50_000, grossMarginPercent = 25)).breakEvenRevenue)
        assertEquals(100_000L, BreakEven.assess(inp(fixedCostsMonthly = 50_000, grossMarginPercent = 50)).breakEvenRevenue)
    }

    @Test
    fun `a brand-new month has no data`() {
        assertFalse(BreakEven.assess(inp()).hasData)
    }
}
