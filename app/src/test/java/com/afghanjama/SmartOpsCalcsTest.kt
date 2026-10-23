package com.afghanjama

import com.afghanjama.data.CapacityCheck
import com.afghanjama.data.ReorderAdvice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * دو محاسبهٔ عملیاتیِ هوشمند: «این سفارش را به‌موقع می‌رسانم؟» و «این ماده را
 * چقدر بخرم؟». هر دو مرزِ «نامعلوم، نه صفر» را نگه می‌دارند: سرعت یا مصرفِ
 * نامعلوم جوابِ قلابی نمی‌دهد.
 */
class SmartOpsCalcsTest {

    // ─────────── ظرفیتِ تحویل ───────────

    private fun cap(openPieces: Int = 0, piecesPerDay: Double = 0.0, newOrderPieces: Int = 0, daysUntilDue: Int = 0) =
        CapacityCheck.Inputs(openPieces, piecesPerDay, newOrderPieces, daysUntilDue)

    @Test
    fun `a new order that fits the queue is on time`() {
        val r = CapacityCheck.check(cap(openPieces = 10, piecesPerDay = 5.0, newOrderPieces = 5, daysUntilDue = 10))
        assertEquals(3, r.readyInDays)          // (۱۰+۵) ÷ ۵
        assertEquals(0, r.lateByDays)
        assertEquals(true, r.canMakeIt)
    }

    @Test
    fun `an overloaded workshop is late by the gap`() {
        val r = CapacityCheck.check(cap(openPieces = 20, piecesPerDay = 2.0, newOrderPieces = 10, daysUntilDue = 5))
        assertEquals(15, r.readyInDays)         // ۳۰ ÷ ۲
        assertEquals(10, r.lateByDays)
        assertEquals(false, r.canMakeIt)
    }

    @Test
    fun `exactly on the due date still counts as making it`() {
        val r = CapacityCheck.check(cap(piecesPerDay = 2.0, newOrderPieces = 10, daysUntilDue = 5))
        assertEquals(5, r.readyInDays)
        assertEquals(true, r.canMakeIt)
    }

    @Test
    fun `partial days round up`() {
        val r = CapacityCheck.check(cap(piecesPerDay = 3.0, newOrderPieces = 10, daysUntilDue = 100))
        assertEquals(4, r.readyInDays)          // ceil(۱۰ ÷ ۳)
    }

    @Test
    fun `no known speed means no guess`() {
        val r = CapacityCheck.check(cap(openPieces = 10, piecesPerDay = 0.0, newOrderPieces = 5, daysUntilDue = 3))
        assertFalse(r.hasRate)
        assertNull(r.readyInDays)
        assertNull(r.canMakeIt)
    }

    // ─────────── خریدِ هوشمند ───────────

    private fun reo(stock: Double = 0.0, dailyUse: Double = 0.0, coverDays: Int = 30, minLevel: Double = 0.0) =
        ReorderAdvice.Inputs(stock, dailyUse, coverDays, minLevel)

    @Test
    fun `buy enough to reach the cover target`() {
        val r = ReorderAdvice.advise(reo(stock = 10.0, dailyUse = 2.0, coverDays = 30, minLevel = 5.0))
        assertEquals(5, r.daysLeft)             // ۱۰ ÷ ۲
        assertEquals(50.0, r.suggestedBuy, 0.0) // ۶۰ هدف − ۱۰ موجودی
        assertFalse(r.belowMin)
    }

    @Test
    fun `plenty of stock suggests no purchase`() {
        val r = ReorderAdvice.advise(reo(stock = 100.0, dailyUse = 2.0, coverDays = 30, minLevel = 5.0))
        assertEquals(0.0, r.suggestedBuy, 0.0)
        assertEquals(50, r.daysLeft)
    }

    @Test
    fun `no recorded use leaves days unknown and tops up to the min only`() {
        val r = ReorderAdvice.advise(reo(stock = 3.0, dailyUse = 0.0, coverDays = 30, minLevel = 10.0))
        assertFalse(r.hasUse)
        assertNull(r.daysLeft)
        assertEquals(7.0, r.suggestedBuy, 0.0)  // تا حدِ هشدار
        assertTrue(r.belowMin)
    }

    @Test
    fun `a fractional suggestion rounds up to a whole unit`() {
        val r = ReorderAdvice.advise(reo(stock = 0.5, dailyUse = 1.0, coverDays = 10))
        assertEquals(10.0, r.suggestedBuy, 0.0) // ceil(۱۰ − ۰٫۵)
    }

    @Test
    fun `below the warning level is flagged even with use`() {
        val r = ReorderAdvice.advise(reo(stock = 4.0, dailyUse = 2.0, coverDays = 30, minLevel = 10.0))
        assertTrue(r.belowMin)
        assertEquals(2, r.daysLeft)
    }
}
