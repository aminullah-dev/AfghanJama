package com.afghanjama

import com.afghanjama.data.OrderCostSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * برگهٔ هزینهٔ سفارش — سود و حاشیه و «زیرِ قیمتِ تمام‌شده».
 *
 * مرزِ مهم: قیمتِ توافق‌نشده نباید سودِ قلابی بسازد، و فروشِ زیرِ بها باید
 * دیده شود — ضررِ پنهان بدترین چیزِ یک کارگاه است.
 */
class OrderCostSheetTest {

    @Test
    fun `a priced order shows profit, margin and unit cost`() {
        val s = OrderCostSheet.of(fabricPrice = 300, workCost = 200, sewingCost = 100, agreedPrice = 1000, qty = 2)
        assertTrue(s.hasPrice)
        assertEquals(600L, s.cost)
        assertEquals(400L, s.profit)
        assertEquals(40, s.marginPercent)
        assertEquals(300L, s.unitCost)   // ۶۰۰ ÷ ۲
        assertFalse(s.belowCost)
    }

    @Test
    fun `an order with no agreed price has no fake profit`() {
        val s = OrderCostSheet.of(fabricPrice = 300, workCost = 200, sewingCost = 100, agreedPrice = 0, qty = 1)
        assertFalse(s.hasPrice)
        assertNull(s.profit)
        assertNull(s.marginPercent)
        assertFalse(s.belowCost)
    }

    @Test
    fun `selling below cost is flagged with a negative margin`() {
        val s = OrderCostSheet.of(fabricPrice = 300, workCost = 200, sewingCost = 100, agreedPrice = 500, qty = 1)
        assertTrue(s.belowCost)
        assertEquals(-100L, s.profit)
        assertEquals(-20, s.marginPercent)
    }

    @Test
    fun `unit cost does not divide by zero`() {
        val s = OrderCostSheet.of(fabricPrice = 100, workCost = 0, sewingCost = 0, agreedPrice = 0, qty = 0)
        assertEquals(100L, s.unitCost)
    }

    @Test
    fun `a full-margin order has no cost`() {
        val s = OrderCostSheet.of(fabricPrice = 0, workCost = 0, sewingCost = 0, agreedPrice = 1000, qty = 1)
        assertEquals(100, s.marginPercent)
        assertEquals(1000L, s.profit)
    }

    @Test
    fun `a summary counts only priced orders for profit, but all for the tally`() {
        val sheets = listOf(
            OrderCostSheet.of(300, 200, 100, 1000, 2),  // priced, profit 400
            OrderCostSheet.of(300, 200, 100, 0, 1),      // no price
            OrderCostSheet.of(300, 200, 100, 500, 1),    // priced, below cost
        )
        val sum = OrderCostSheet.summarize(sheets)
        assertEquals(3, sum.orders)
        assertEquals(2, sum.priced)
        assertEquals(1500L, sum.revenue)
        assertEquals(1200L, sum.cost)
        assertEquals(300L, sum.profit)
        assertEquals(20, sum.marginPercent)
        assertEquals(1, sum.belowCostCount)
    }

    @Test
    fun `an empty summary has no data and no margin`() {
        val sum = OrderCostSheet.summarize(emptyList())
        assertFalse(sum.hasData)
        assertNull(sum.marginPercent)
    }
}
