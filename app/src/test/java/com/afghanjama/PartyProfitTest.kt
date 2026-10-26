package com.afghanjama

import com.afghanjama.data.PartyProfit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * سود کنارِ هر طرف (مشتری/خیاط) — جمع، مرتب‌سازی، و تقسیمِ سودِ خیاط به
 * نسبتِ دستِ کارش.
 */
class PartyProfitTest {

    @Test
    fun `groups by name, sums, and sorts by profit descending`() {
        val rows = PartyProfit.summarize(
            listOf(
                PartyProfit.Entry("کریم", qty = 2, revenue = 3_000, cost = 1_000),  // سود ۲۰۰۰
                PartyProfit.Entry("کریم", qty = 1, revenue = 1_000, cost = 400),     // سود ۶۰۰
                PartyProfit.Entry("رحیم", qty = 5, revenue = 10_000, cost = 2_000),  // سود ۸۰۰۰
            ),
            unknown = "بی‌نام",
        )
        assertEquals(2, rows.size)
        // پرسودترین اول
        assertEquals("رحیم", rows[0].name)
        assertEquals(8_000L, rows[0].profit)
        val karim = rows[1]
        assertEquals("کریم", karim.name)
        assertEquals(2, karim.count)          // دو سفارش
        assertEquals(3, karim.qty)            // ۲ + ۱ عدد
        assertEquals(4_000L, karim.revenue)
        assertEquals(2_600L, karim.profit)    // ۲۰۰۰ + ۶۰۰
    }

    @Test
    fun `a blank name falls into the unknown bucket, not lost`() {
        val rows = PartyProfit.summarize(
            listOf(
                PartyProfit.Entry("  ", qty = 1, revenue = 500, cost = 100),
                PartyProfit.Entry("", qty = 1, revenue = 500, cost = 100),
            ),
            unknown = "بی‌نام",
        )
        assertEquals(1, rows.size)
        assertEquals("بی‌نام", rows[0].name)
        assertEquals(2, rows[0].count)
    }

    @Test
    fun `no revenue means a null margin, not zero`() {
        val rows = PartyProfit.summarize(
            listOf(PartyProfit.Entry("کریم", qty = 1, revenue = 0, cost = 0)),
            unknown = "بی‌نام",
        )
        assertNull(rows[0].marginPercent)
    }

    @Test
    fun `a loss shows negative profit`() {
        val rows = PartyProfit.summarize(
            listOf(PartyProfit.Entry("کریم", qty = 1, revenue = 1_000, cost = 1_500)),
            unknown = "بی‌نام",
        )
        assertEquals(-500L, rows[0].profit)
        assertEquals(-50, rows[0].marginPercent)
    }

    @Test
    fun `a tailor's share of an order follows the pieces he sewed`() {
        // سفارش: درآمد ۱۰٬۰۰۰، بها ۴٬۰۰۰ (سود ۶٬۰۰۰). ۲۰ دست به احمد، ۵ به بلال.
        val entries = PartyProfit.tailorEntries(
            listOf(
                PartyProfit.SewnOrder(
                    revenue = 10_000, cost = 4_000,
                    sewers = listOf("احمد" to 20, "بلال" to 5),
                )
            )
        )
        val rows = PartyProfit.summarize(entries, unknown = "بی‌خیاط")
        val ahmad = rows.first { it.name == "احمد" }
        val bilal = rows.first { it.name == "بلال" }
        // ۲۰ از ۲۵ = ۴/۵
        assertEquals(8_000L, ahmad.revenue)
        assertEquals(3_200L, ahmad.cost)
        assertEquals(4_800L, ahmad.profit)
        // ۵ از ۲۵ = ۱/۵
        assertEquals(2_000L, bilal.revenue)
        assertEquals(1_200L, bilal.profit)
    }

    @Test
    fun `an order with no sewer is dropped, not divided by zero`() {
        val entries = PartyProfit.tailorEntries(
            listOf(PartyProfit.SewnOrder(revenue = 5_000, cost = 1_000, sewers = emptyList()))
        )
        assertTrue(entries.isEmpty())
    }
}
