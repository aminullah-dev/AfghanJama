package com.afghanjama

import com.afghanjama.data.OrderMessage
import com.afghanjama.data.entities.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * متنِ پیام به مشتری برای هر مرحله از سفارش.
 */
class OrderMessageTest {

    private fun msg(status: String, design: String = "کت", shop: String = "خیاط‌یار") =
        OrderMessage.forCustomer(shop, design, status)

    @Test
    fun `ready to deliver asks the customer to come`() {
        val m = msg(OrderStatus.STORED.name)
        assertTrue(m.contains("آماده شد"))
        assertTrue(m.contains("تحویل"))
        assertTrue(m.contains("کت"))
    }

    @Test
    fun `delivered thanks the customer`() {
        val m = msg(OrderStatus.SENT.name)
        assertTrue(m.contains("تحویل شد"))
        assertTrue(m.contains("سپاسگزاریم"))
    }

    @Test
    fun `in-progress names the current stage`() {
        assertTrue(msg(OrderStatus.SEWING.name).contains("دوخت"))
        assertTrue(msg(OrderStatus.REVIEW.name).contains("نظارت"))
        assertTrue(msg(OrderStatus.CUTTING.name).contains("برش"))
    }

    @Test
    fun `a fresh order says it was registered`() {
        assertTrue(msg(OrderStatus.IN_STOCK.name).contains("ثبت شد"))
        assertTrue(msg("NEW").contains("ثبت شد"))
    }

    @Test
    fun `an unknown status still makes a whole sentence`() {
        val m = msg("WAT")
        assertTrue(m.startsWith("سلام"))
        assertTrue(m.contains("ثبت شد"))
    }

    @Test
    fun `a blank design never leaves a hole`() {
        val m = msg(OrderStatus.SEWING.name, design = "")
        assertTrue(m.contains("سفارشِ شما"))
        assertFalse(m.contains("«»"))
    }

    @Test
    fun `the workshop name signs the message, and a blank one adds no dash`() {
        assertTrue(msg(OrderStatus.STORED.name, shop = "خیاطیِ نور").contains("— خیاطیِ نور"))
        val noShop = msg(OrderStatus.STORED.name, shop = "")
        assertFalse(noShop.contains("—"))
    }

    @Test
    fun `every message greets first`() {
        for (s in OrderStatus.entries) {
            assertTrue(OrderMessage.forCustomer("کارگاه", "شلوار", s.name).startsWith("سلام،"))
        }
    }
}
