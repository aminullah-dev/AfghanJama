package com.afghanjama

import com.afghanjama.data.PartialFlow
import com.afghanjama.data.PartialFlow.Sewn
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ریاضیِ جریانِ جزئیِ سفارش (`PartialFlow`): چند عدد می‌تواند به نظارت
 * برود و چه مبلغی از «کار در جریان» به انبار محصول منتقل می‌شود.
 * مهم‌ترین قاعده: جمعِ مبلغِ دسته‌ها دقیقاً برابرِ کلِ بهای سفارش شود.
 */
class PartialFlowTest {

    // ---- readyToSend ----

    @Test
    fun `ready to send is the sewn count when nothing has gone yet`() {
        assertEquals(6, PartialFlow.readyToSend(qty = 20, sewn = 6, inReview = 0, stored = 0))
    }

    @Test
    fun `ready to send subtracts what is in review and in stock`() {
        assertEquals(2, PartialFlow.readyToSend(qty = 20, sewn = 6, inReview = 4, stored = 0))
        assertEquals(0, PartialFlow.readyToSend(qty = 20, sewn = 6, inReview = 0, stored = 6))
        assertEquals(0, PartialFlow.readyToSend(qty = 20, sewn = 6, inReview = 3, stored = 3))
    }

    @Test
    fun `ready to send is capped by the order quantity after re-sewing`() {
        assertEquals(10, PartialFlow.readyToSend(qty = 10, sewn = 14, inReview = 0, stored = 0))
        assertEquals(5, PartialFlow.readyToSend(qty = 10, sewn = 14, inReview = 2, stored = 3))
    }

    @Test
    fun `ready to send is never negative`() {
        assertEquals(0, PartialFlow.readyToSend(qty = 10, sewn = 2, inReview = 5, stored = 0))
    }

    // ---- pendingPerBatch ----

    @Test
    fun `pending per batch consumes the oldest batches first`() {
        assertEquals(listOf(0, 1, 5), PartialFlow.pendingPerBatch(listOf(3, 2, 5), 4))
    }

    @Test
    fun `pending per batch with nothing gone returns the input`() {
        assertEquals(listOf(3, 2, 5), PartialFlow.pendingPerBatch(listOf(3, 2, 5), 0))
        assertEquals(listOf(3, 2, 5), PartialFlow.pendingPerBatch(listOf(3, 2, 5), -3))
    }

    @Test
    fun `pending per batch is all zero when more has gone than was sewn`() {
        assertEquals(listOf(0, 0, 0), PartialFlow.pendingPerBatch(listOf(3, 2, 5), 100))
    }

    @Test
    fun `pending per batch ignores a negative batch quantity`() {
        assertEquals(listOf(0, 4), PartialFlow.pendingPerBatch(listOf(-2, 4), 0))
    }

    // ---- wageOfFirst ----

    private val cheap = Sewn(qty = 3, unitWage = 100, doneAt = 10)
    private val dear = Sewn(qty = 4, unitWage = 200, doneAt = 20)

    @Test
    fun `wage of first n follows delivery order not list order`() {
        // 3 x 100 from the earlier delivery + 2 x 200 from the later one.
        assertEquals(700L, PartialFlow.wageOfFirst(listOf(dear, cheap), 5))
    }

    @Test
    fun `wage of first n is zero for zero or negative n`() {
        assertEquals(0L, PartialFlow.wageOfFirst(listOf(cheap, dear), 0))
        assertEquals(0L, PartialFlow.wageOfFirst(listOf(cheap, dear), -1))
    }

    @Test
    fun `wage of first n stops at the total sewn`() {
        assertEquals(1100L, PartialFlow.wageOfFirst(listOf(cheap, dear), 100))
    }

    // ---- depositValue ----

    private val tenBatches = listOf(Sewn(3, 100, 10), Sewn(7, 200, 20))

    @Test
    fun `two partial deposits add up to the whole order cost`() {
        // fixed 1000, wages 3*100 + 7*200 = 1700, total 2700.
        val first = PartialFlow.depositValue(
            qty = 10, stored = 0, batch = 4,
            fixedCost = 1000, sewnCost = 1700, batches = tenBatches, storedCost = 0
        )
        // 1000*4/10 = 400, wages of first 4 = 3*100 + 1*200 = 500.
        assertEquals(900L, first)

        val last = PartialFlow.depositValue(
            qty = 10, stored = 4, batch = 6,
            fixedCost = 1000, sewnCost = 1700, batches = tenBatches, storedCost = first
        )
        assertEquals(1800L, last)
        assertEquals(2700L, first + last)
    }

    @Test
    fun `integer division remainder is not lost`() {
        val none = emptyList<Sewn>()
        val a = PartialFlow.depositValue(3, 0, 1, 100, 0, none, 0)
        val b = PartialFlow.depositValue(3, 1, 1, 100, 0, none, a)
        val c = PartialFlow.depositValue(3, 2, 1, 100, 0, none, a + b)
        assertEquals(33L, a)
        assertEquals(33L, b)
        assertEquals(34L, c)
        assertEquals(100L, a + b + c)
    }

    @Test
    fun `last batch takes re-sewing wages recorded after a rejection`() {
        val batches = listOf(Sewn(2, 100, 1))
        // Recorded wages are 500: 2 x 100 plus 300 for re-sewing.
        val first = PartialFlow.depositValue(2, 0, 1, 0, 500, batches, 0)
        assertEquals(100L, first)
        val last = PartialFlow.depositValue(2, 1, 1, 0, 500, batches, first)
        assertEquals(400L, last)
    }

    @Test
    fun `a batch larger than the remainder is treated as the last batch`() {
        val value = PartialFlow.depositValue(
            qty = 10, stored = 8, batch = 5,
            fixedCost = 1000, sewnCost = 1700, batches = tenBatches, storedCost = 2000
        )
        assertEquals(700L, value)
    }

    @Test
    fun `deposit value is zero for empty quantity or batch`() {
        assertEquals(0L, PartialFlow.depositValue(0, 0, 1, 1000, 0, emptyList(), 0))
        assertEquals(0L, PartialFlow.depositValue(10, 0, 0, 1000, 0, emptyList(), 0))
    }

    @Test
    fun `deposit value is never negative when more has already left than the target`() {
        assertEquals(0L, PartialFlow.depositValue(10, 0, 1, 0, 0, emptyList(), 50))
    }
}
