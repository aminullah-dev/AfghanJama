package com.afghanjama

import com.afghanjama.data.Installments
import com.afghanjama.data.entities.CustomerInstallment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * پخشِ پرداخت روی اقساط — قدیمی‌ترین-اول، بدون پرچمِ «پرداخت‌شده».
 */
class InstallmentsTest {

    private fun inst(id: Long, amount: Long, due: Long) =
        CustomerInstallment(id = id, customerName = "c", amount = amount, dueDate = due)

    @Test
    fun paymentFillsOldestFirst() {
        val rows = Installments.allocate(
            listOf(inst(1, 100, 20), inst(2, 100, 10)),
            totalPaid = 150,
        )
        // قسطِ زودتر (id 2) اول بسته می‌شود.
        assertEquals(listOf(2L, 1L), rows.map { it.plan.id })
        assertEquals(listOf(100L, 50L), rows.map { it.paid })
        assertEquals(listOf(0L, 50L), rows.map { it.remaining })
    }

    @Test
    fun inputOrderDoesNotChangeResult() {
        val a = listOf(inst(1, 100, 10), inst(2, 100, 20), inst(3, 100, 30))
        assertEquals(
            Installments.allocate(a, 250),
            Installments.allocate(a.reversed(), 250),
        )
    }

    @Test
    fun sameDueDateBreaksTieById() {
        val rows = Installments.allocate(listOf(inst(5, 100, 10), inst(4, 100, 10)), 100)
        assertEquals(listOf(4L, 5L), rows.map { it.plan.id })
        assertEquals(listOf(100L, 0L), rows.map { it.paid })
    }

    @Test
    fun overpaymentIsNotSpilledAnywhere() {
        val rows = Installments.allocate(listOf(inst(1, 100, 10)), 500)
        assertEquals(100L, rows.single().paid)
        assertEquals(0L, rows.single().remaining)
    }

    @Test
    fun negativePaidAndNegativeAmountAreClamped() {
        val rows = Installments.allocate(listOf(inst(1, -50, 10), inst(2, 100, 20)), -10)
        assertEquals(listOf(0L, 0L), rows.map { it.paid })
    }

    @Test
    fun emptyScheduleGivesEmpty() {
        assertTrue(Installments.allocate(emptyList(), 100).isEmpty())
    }

    @Test
    fun overdueNeedsUnpaidRemainderAndPastDueDate() {
        val unpaid = Installments.Row(inst(1, 100, 1000), paid = 40)
        assertTrue(unpaid.overdue(now = 2000))
        assertFalse(unpaid.overdue(now = 1000)) // روزِ سررسید هنوز گذشته نیست
        assertFalse(Installments.Row(inst(1, 100, 1000), paid = 100).overdue(now = 2000))
        assertFalse(Installments.Row(inst(1, 100, 0), paid = 0).overdue(now = 2000)) // بدون تاریخ
    }
}
