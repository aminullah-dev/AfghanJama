package com.afghanjama

import com.afghanjama.data.Margin
import com.afghanjama.data.PriceAdvisor
import com.afghanjama.data.PriceAdvisor.Sold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * پیشنهادِ قیمت — از فروش‌های واقعی، نه از فرمول؛ و بی تاریخچه، هیچ.
 */
class PriceAdvisorTest {

    private val day = 86_400_000L
    private val now = 1_000 * day
    private val shirt = PriceAdvisor.key("پیراهن", "L")

    private fun sold(price: Long, cost: Long, daysAgo: Int, key: String = shirt, net: Long = price) =
        Sold(key, price, cost, now - daysAgo * day, net)

    @Test
    fun `the usual margin is the median, so one clearance sale does not drag it`() {
        val s = listOf(
            sold(1_200, 1_000, 1),   // ۲۰٪
            sold(1_250, 1_000, 2),   // ۲۵٪
            sold(1_300, 1_000, 3),   // ۳۰٪
            sold(800, 1_000, 4),     // حراج: −۲۰٪
        )
        // میانه — پایینیِ دو وسطی: ۲۰ و ۲۵ → ۲۰
        assertEquals(20, PriceAdvisor.usualMargin(s, now))
    }

    @Test
    fun `margin is measured after discount, and needs enough sales`() {
        val discounted = listOf(
            sold(1_300, 1_000, 1, net = 1_100),
            sold(1_300, 1_000, 2, net = 1_100),
            sold(1_300, 1_000, 3, net = 1_100),
        )
        assertEquals(10, PriceAdvisor.usualMargin(discounted, now))
        assertNull(PriceAdvisor.usualMargin(discounted.take(2), now))
        // فروشِ بی‌بها در سود شمرده نمی‌شود
        assertNull(PriceAdvisor.usualMargin(discounted.map { it.copy(unitCost = 0) }, now))
    }

    @Test
    fun `last and usual price come from the same item only, and old sales fade out`() {
        val s = listOf(
            sold(1_500, 1_000, 1),
            sold(1_400, 1_000, 5),
            sold(1_400, 1_000, 9),
            sold(9_999, 1_000, 2, key = PriceAdvisor.key("کت", "L")),
            sold(500, 1_000, 400),   // بیرون از پنجره
        )
        val a = PriceAdvisor.advise(shirt, 1_100, s, now, margin = 25)
        assertEquals(1_500L, a.lastPrice)
        assertEquals(1_400L, a.usualPrice)
        assertEquals(3, a.samples)
        // بهای امروز ۱۱۰۰ با سودِ ۲۵٪ — همان عددی که Margin برمی‌گرداند
        assertEquals(Margin.priceFor(1_100, 25), a.marginPrice)
        assertEquals(25, Margin.Line(1_100, a.marginPrice!!, 1).percent)
    }

    @Test
    fun `the same item is matched despite ye, kaf and spaces`() {
        assertEquals(PriceAdvisor.key("پيراهن ", "L"), PriceAdvisor.key("پیراهن", " L"))
    }

    @Test
    fun `when cost rose above the usual price it says so`() {
        val s = listOf(sold(1_000, 800, 3), sold(1_000, 800, 6))
        val a = PriceAdvisor.advise(shirt, 1_050, s, now, margin = 20)
        assertTrue(a.usualBelowCost)
        assertTrue(a.marginPrice!! > a.usualPrice!!)
        assertFalse(PriceAdvisor.advise(shirt, 900, s, now, margin = 20).usualBelowCost)
    }

    @Test
    fun `no history, no numbers`() {
        val a = PriceAdvisor.advise(shirt, 1_000, emptyList(), now, margin = null)
        assertTrue(a.isEmpty)
        assertNull(a.lastPrice)
        assertNull(a.marginPrice)
        // بی بها، درصد به عدد تبدیل نمی‌شود
        assertNull(PriceAdvisor.advise(shirt, 0, emptyList(), now, margin = 20).marginPrice)
    }
}
