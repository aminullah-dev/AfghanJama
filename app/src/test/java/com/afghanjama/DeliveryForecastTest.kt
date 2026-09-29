package com.afghanjama

import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.work.DeliveryForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * پیش‌بینیِ تحویل — سفارشی که دیر می‌شود، پیش از گذشتنِ مهلتش دیده شود.
 *
 * کارگاهِ این آزمون در ۳۰ روزِ گذشته ۶۰ دست دوخته: روزی ۲ دست.
 */
class DeliveryForecastTest {

    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun order(
        code: String,
        qty: Int,
        dueInDays: Int?,
        status: OrderStatus = OrderStatus.SEWING,
        stored: Int = 0,
        createdDaysAgo: Int = 10,
    ) = Order(
        orderCode = code, shortCode = code, designTitle = "پیراهن", qty = qty,
        fabricType = "", fabricColor = "", size = "", fabricUnit = "متر", fabricAmount = 0.0,
        fabricPrice = 0, workCost = 0, customerName = "مشتریِ $code", customerPhone = "",
        status = status.name, storedQty = stored,
        dueDate = dueInDays?.let { now + it * day } ?: 0L,
        createdAt = now - createdDaysAgo * day,
    )

    /** ۶۰ دستِ تمام‌شده در ۳۰ روز — روزی ۲ دست. */
    private val history = listOf(
        SewingAssignment(orderId = "old", orderCode = "OLD", tailorLabel = "[T1] احمد", qty = 40,
            unitWage = 0, status = "DONE", doneAt = now - 10 * day),
        SewingAssignment(orderId = "old", orderCode = "OLD", tailorLabel = "[T2] محمود", qty = 20,
            unitWage = 0, status = "DONE", doneAt = now - 5 * day),
    )

    @Test
    fun `orders are queued by due date and the late one is seen before its due date`() {
        val a = order("A", qty = 10, dueInDays = 6)     // ۵ روز کار → به‌موقع
        val b = order("B", qty = 10, dueInDays = 8)     // تا روزِ ۱۰ → ۲ روز دیر
        val c = order("C", qty = 4, dueInDays = null)   // بی مهلت، پشتِ همه
        val r = DeliveryForecast.of(listOf(c, b, a), history, now)

        assertEquals(2.0, r.perDay, 1e-9)
        assertEquals(listOf("A", "B", "C"), r.lines.map { it.order.orderCode })
        assertEquals(now + 5 * day, r.lines[0].readyAt)
        assertEquals(0, r.lines[0].lateDays)
        assertEquals(2, r.lines[1].lateDays)
        assertEquals(listOf("B"), r.atRisk.map { it.order.orderCode })
    }

    @Test
    fun `pieces already sewn and waiting for review need no sewing time`() {
        val sewn = history + SewingAssignment(orderId = "", orderCode = "R", tailorLabel = "[T1] احمد",
            qty = 8, unitWage = 0, status = "DONE", doneAt = now - 40 * day)
        val r0 = order("R", qty = 10, dueInDays = 1, status = OrderStatus.REVIEW)
        val withId = sewn.map { if (it.orderCode == "R") it.copy(orderId = r0.id.toString()) else it }
        val r = DeliveryForecast.of(listOf(r0), withId, now)
        assertEquals(2, r.lines.single().remaining)
        assertTrue(r.atRisk.isEmpty())
    }

    @Test
    fun `an order whose due date already passed is not reported again as at risk`() {
        val late = order("L", qty = 30, dueInDays = -2)
        val r = DeliveryForecast.of(listOf(late), history, now)
        assertTrue(r.lines.single().lateDays > 0)
        assertTrue(r.atRisk.isEmpty())
    }

    @Test
    fun `without history nothing is guessed`() {
        val r = DeliveryForecast.of(listOf(order("A", 10, 3)), emptyList(), now)
        assertFalse(r.hasData)
        assertTrue(r.atRisk.isEmpty())
        assertNull(r.impactOf(5, now + 3 * day))
    }

    @Test
    fun `a new order with an early due date shows who it pushes late`() {
        val a = order("A", qty = 10, dueInDays = 6)     // تا روزِ ۵ → به‌موقع
        val r = DeliveryForecast.of(listOf(a), history, now)
        // سفارشِ تازهٔ ۶ دستی با مهلتِ ۲ روز جلوی A می‌نشیند.
        val hit = r.impactOf(qty = 6, dueDate = now + 2 * day)!!
        assertEquals(now + 3 * day, hit.readyAt)
        assertEquals(1, hit.lateDays)
        assertEquals(listOf("A"), hit.pushedLate.map { it.order.orderCode })
        assertEquals(2, hit.pushedLate.single().lateDays)

        // بی مهلت پشتِ همه می‌رود و کسی را عقب نمی‌اندازد.
        val calm = r.impactOf(qty = 6, dueDate = 0L)!!
        assertEquals(now + 8 * day, calm.readyAt)
        assertEquals(0, calm.lateDays)
        assertTrue(calm.pushedLate.isEmpty())
    }

    @Test
    fun `the earliest safe due date keeps everyone on time`() {
        val a = order("A", qty = 10, dueInDays = 6)     // تا روزِ ۵ آماده
        val r = DeliveryForecast.of(listOf(a), history, now)
        // ۶ دست: جلوی A بنشیند، A دیر می‌شود؛ پشتش بنشیند، روزِ ۸ آماده است.
        val d = r.earliestSafeDays(6)
        assertEquals(8, d)
        val hit = r.impactOf(6, now + 8 * day)!!
        assertEquals(0, hit.lateDays)
        assertTrue(hit.pushedLate.isEmpty())
        assertNull(DeliveryForecast.of(emptyList(), emptyList(), now).earliestSafeDays(6))
    }

    @Test
    fun `the least loaded tailor comes first`() {
        val inHand = history + listOf(
            SewingAssignment(orderId = "x", orderCode = "X", tailorLabel = "[T1] احمد", qty = 12,
                unitWage = 0, status = "SEWING"),
            SewingAssignment(orderId = "y", orderCode = "Y", tailorLabel = "[T3] تازه‌کار", qty = 3,
                unitWage = 0, status = "SEWING"),
        )
        val t = DeliveryForecast.of(emptyList(), inHand, now).tailors
        // محمود بیکار (۰ روز)، احمد ۱۲ دست با روزی ۴/۳ → ۹ روز، تازه‌کار سرعت ندارد.
        assertEquals(listOf("[T2] محمود", "[T1] احمد", "[T3] تازه‌کار"), t.map { it.tailor })
        assertEquals(0.0, t[0].daysOfWork!!, 1e-9)
        assertEquals(9.0, t[1].daysOfWork!!, 1e-9)
        assertNull(t[2].daysOfWork)
    }
}
