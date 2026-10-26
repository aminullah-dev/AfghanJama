package com.afghanjama

import com.afghanjama.ui.vm.IncomeStatement
import com.afghanjama.ui.vm.breakEvenOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * وصلِ «نقطهٔ سربه‌سر» به صورتِ سود و زیان — [breakEvenOf].
 *
 * منطقِ خودِ سربه‌سر در [BreakEvenTest] سنجیده شده؛ اینجا فقط مونتاژ: که
 * حاشیه از صورتِ سود و زیان درست و از **سودِ ناخالص** (نه خالص) ساخته شود.
 */
class DashboardBreakEvenTest {

    @Test
    fun `margin and revenue come straight from the income statement`() {
        // درآمد ۱۵۰هزار، بهای‌تمام‌شده ۹۰هزار → سودِ ناخالص ۶۰هزار، حاشیه ۴۰٪.
        val st = IncomeStatement(totalRevenue = 150_000, cogs = 90_000, grossProfit = 60_000)
        val s = breakEvenOf(st, fixedCostsMonthly = 100_000)
        assertEquals(250_000L, s.breakEvenRevenue)   // ۱۰۰هزار ÷ ۴۰٪
        assertEquals(60, s.coveredPercent)           // ۶۰هزار سودِ ناخالص ÷ ۱۰۰هزار
        assertEquals(100_000L, s.revenueToGo)
    }

    @Test
    fun `break-even uses the GROSS margin, not the net margin`() {
        // درآمد ۱۰۰هزار، بهای‌تمام‌شده ۵۰هزار → ناخالص ۵۰هزار (۵۰٪).
        // هزینهٔ عمومی ۴۰هزار → خالص ۱۰هزار (۱۰٪). سربه‌سر باید با ۵۰٪ باشد.
        val st = IncomeStatement(
            totalRevenue = 100_000, cogs = 50_000, grossProfit = 50_000,
            totalExpense = 40_000, netProfit = 10_000,
        )
        val s = breakEvenOf(st, fixedCostsMonthly = 50_000)
        // با حاشیهٔ ناخالصِ ۵۰٪: ۵۰هزار ÷ ۵۰٪ = ۱۰۰هزار. (با خالصِ ۱۰٪ می‌شد ۵۰۰هزار.)
        assertEquals(100_000L, s.breakEvenRevenue)
    }

    @Test
    fun `no revenue means no break-even revenue`() {
        val s = breakEvenOf(IncomeStatement(), fixedCostsMonthly = 100_000)
        assertNull(s.breakEvenRevenue)   // حاشیهٔ صفر، فروشِ بیشتر سربه‌سر نمی‌آورد
    }
}
