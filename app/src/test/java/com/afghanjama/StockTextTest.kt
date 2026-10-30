package com.afghanjama

import com.afghanjama.ui.format.isShortage
import com.afghanjama.ui.format.stockBadge
import com.afghanjama.ui.format.stockText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * وضعیتِ ردیفِ انبار به زبانِ کارگاه.
 *
 * مرزِ مهم: تعدادِ منفی هرگز با علامتِ منفی نمایش داده نمی‌شود؛ «کسری»
 * است، یعنی جنسی فروخته‌ایم که هنوز واردش نکرده‌ایم.
 */
class StockTextTest {

    @Test
    fun `positive stock is shown with Persian digits`() {
        assertEquals("موجودیِ انبار: ۲۱ عدد", stockText(21))
        assertEquals("موجودی: ۲۱", stockBadge(21))
    }

    @Test
    fun `zero stock is written as zero, not as a shortage`() {
        assertEquals("موجودیِ انبار: صفر", stockText(0))
        assertEquals("موجودی: ۰", stockBadge(0))
    }

    @Test
    fun `negative stock reads as shortage without a minus sign`() {
        assertEquals("کسری: ۲۱ عدد", stockText(-21))
        assertEquals("کسری: ۲۱", stockBadge(-21))
        assertFalse(stockText(-21).contains('-'))
        assertFalse(stockBadge(-21).contains('-'))
    }

    @Test
    fun `only a negative quantity is flagged as shortage`() {
        assertTrue(isShortage(-1))
        assertFalse(isShortage(0))
        assertFalse(isShortage(1))
    }
}
