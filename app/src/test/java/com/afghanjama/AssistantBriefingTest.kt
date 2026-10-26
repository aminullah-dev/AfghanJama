package com.afghanjama

import com.afghanjama.data.BreakEven
import com.afghanjama.data.PartyProfit
import com.afghanjama.ui.vm.DashboardStats
import com.afghanjama.ui.vm.InsightTone
import com.afghanjama.ui.vm.MonthPoint
import com.afghanjama.ui.vm.businessBriefing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * خلاصهٔ دستیار — فقط چیزی که داده‌اش هست، و با لحنِ درست.
 */
class AssistantBriefingTest {

    private fun be(fixed: Long, margin: Int, grossProfit: Long, revenue: Long = 0) =
        BreakEven.assess(BreakEven.Inputs(fixed, margin, revenue, grossProfit))

    private fun row(name: String, profit: Long) =
        PartyProfit.Row(name = name, count = 1, qty = 1, revenue = profit + 100, cost = 100)

    @Test
    fun `an empty business just says all is calm`() {
        val out = businessBriefing(DashboardStats(), be = null, customers = emptyList(), tailors = emptyList())
        assertEquals(1, out.size)
        assertEquals("همه‌چیز آرام است", out[0].title)
    }

    @Test
    fun `past break-even is a GOOD insight`() {
        val out = businessBriefing(
            DashboardStats(),
            be = be(fixed = 100_000, margin = 40, grossProfit = 120_000),
            customers = emptyList(), tailors = emptyList(),
        )
        val be = out.first { it.title == "سربه‌سرِ این ماه" }
        assertEquals(InsightTone.GOOD, be.tone)
    }

    @Test
    fun `below break-even warns with the amount still to sell`() {
        val out = businessBriefing(
            DashboardStats(),
            be = be(fixed = 100_000, margin = 40, grossProfit = 60_000, revenue = 150_000),
            customers = emptyList(), tailors = emptyList(),
        )
        val be = out.first { it.title == "سربه‌سرِ این ماه" }
        assertEquals(InsightTone.WATCH, be.tone)
    }

    @Test
    fun `no fixed costs means no break-even insight`() {
        val out = businessBriefing(
            DashboardStats(),
            be = be(fixed = 0, margin = 40, grossProfit = 50_000),
            customers = emptyList(), tailors = emptyList(),
        )
        assertTrue(out.none { it.title == "سربه‌سرِ این ماه" })
    }

    @Test
    fun `best customer and tailor show only when they made a profit`() {
        val out = businessBriefing(
            DashboardStats(),
            be = null,
            customers = listOf(row("بوتیک آرزو", 73_200)),
            tailors = listOf(row("احمد", 50_360)),
        )
        assertTrue(out.any { it.title == "ارزشمندترین مشتری" && it.body.contains("بوتیک آرزو") })
        assertTrue(out.any { it.title == "سودآورترین خیاط" && it.body.contains("احمد") })
    }

    @Test
    fun `a losing party is not celebrated`() {
        val out = businessBriefing(
            DashboardStats(),
            be = null,
            customers = listOf(PartyProfit.Row("ضررده", 1, 1, 100, 500)),
            tailors = emptyList(),
        )
        assertTrue(out.none { it.title == "ارزشمندترین مشتری" })
    }

    @Test
    fun `rising sales read as GOOD`() {
        val months = listOf(
            MonthPoint("1", "سنبله", revenue = 80_000, cost = 40_000, salesCount = 3, returned = 0),
            MonthPoint("2", "میزان", revenue = 120_000, cost = 50_000, salesCount = 5, returned = 0),
        )
        val out = businessBriefing(DashboardStats(salesMonths = months), be = null, customers = emptyList(), tailors = emptyList())
        val s = out.first { it.title == "روندِ فروش" }
        assertEquals(InsightTone.GOOD, s.tone)
        assertTrue(s.body.contains("بیشتر"))
    }
}
