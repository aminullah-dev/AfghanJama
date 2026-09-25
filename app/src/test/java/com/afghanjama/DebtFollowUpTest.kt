package com.afghanjama

import com.afghanjama.data.CustomerCredit
import com.afghanjama.data.DebtFollowUp
import com.afghanjama.data.DebtFollowUp.Contact
import com.afghanjama.data.DebtFollowUp.Debtor
import com.afghanjama.data.DebtFollowUp.Move
import com.afghanjama.data.DebtFollowUp.Outcome
import com.afghanjama.data.DebtFollowUp.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * پیگیریِ طلب — «امروز به چه کسی زنگ بزنم؟».
 *
 * ترتیب از دلیل می‌آید: قولِ شکسته، قولِ امروز، قسطِ گذشته، بدهیِ
 * مانده. کسی که قول داده و روزش نرسیده امروز زنگ نمی‌خورد.
 */
class DebtFollowUpTest {

    private val day = 86_400_000L
    private val today = 100 * day          // نیمه‌شبِ امروز
    private val now = today + 10 * 3_600_000L  // ساعتِ ۱۰ صبح

    private fun plan(vararg d: Debtor) = DebtFollowUp.plan(d.toList(), now, today)

    @Test
    fun `the oldest unpaid debt sets the age, not the last payment`() {
        // ۴۵ روز پیش ۱۰٬۰۰۰ نسیه؛ هفتهٔ پیش ۲٬۰۰۰ داد.
        val moves = listOf(
            Move(today - 45 * day, 10_000, 0),
            Move(today - 7 * day, 0, 2_000),
        )
        assertEquals(today - 45 * day, DebtFollowUp.oldestUnpaidAt(moves))
    }

    @Test
    fun `payments close the oldest debts first`() {
        val moves = listOf(
            Move(today - 40 * day, 5_000, 0),
            Move(today - 10 * day, 3_000, 0),
            Move(today - 2 * day, 0, 5_000),
        )
        // اولی کامل بسته شد؛ بازِ قدیمی همان ۱۰ روزه است.
        assertEquals(today - 10 * day, DebtFollowUp.oldestUnpaidAt(moves))
        assertNull(DebtFollowUp.oldestUnpaidAt(moves + Move(today, 0, 3_000)))
    }

    @Test
    fun `an order still being sewn is not a debt to chase`() {
        // ثبتِ سفارش بدهی می‌سازد ولی سفارش هنوز باز است؛ فروشِ نسیهٔ
        // جدا از انبار، واقعی است.
        val rows = listOf(
            CustomerCredit.Row("SALE_BILLING", "AJ-7", 20_000, 0, today - 60 * day),
            CustomerCredit.Row("SALE_BILLING", "FS-3", 4_000, 0, today - 20 * day),
        )
        val kept = CustomerCredit.recognized(rows, openOrderCodes = setOf("AJ-7"))
        assertEquals(listOf("FS-3"), kept.map { it.refId })
        assertEquals(4_000, CustomerCredit.recognizedNet(rows, setOf("AJ-7")))
        val oldest = DebtFollowUp.oldestUnpaidAt(kept.map { Move(it.at, it.debit, it.credit) })
        assertEquals(today - 20 * day, oldest)
    }

    @Test
    fun `order of the list follows the reason, then the amount`() {
        val p = plan(
            Debtor("کهنه", owed = 50_000, oldestUnpaidAt = today - 30 * day),
            Debtor("قسط", owed = 1_000, lateInstallment = 2_000, lateInstallmentDue = today - 5 * day),
            Debtor(
                "بدقول", owed = 3_000, oldestUnpaidAt = today - 20 * day,
                lastContact = Contact(today - 5 * day, Outcome.PROMISED, today - 2 * day, 3_000)
            ),
            Debtor(
                "امروزی", owed = 9_000, oldestUnpaidAt = today - 20 * day,
                lastContact = Contact(today - 3 * day, Outcome.PROMISED, today, 9_000)
            ),
        )
        assertEquals(listOf("بدقول", "امروزی", "قسط", "کهنه"), p.today.map { it.debtor.name })
        assertEquals(
            listOf(Reason.BROKEN_PROMISE, Reason.PROMISE_DUE, Reason.LATE_INSTALLMENT, Reason.OLD_DEBT),
            p.today.map { it.reason }
        )
        assertEquals(2, p.today.first().days)
        assertTrue(p.later.isEmpty())
    }

    @Test
    fun `a promise not yet due waits, and fresh debt waits too`() {
        val p = plan(
            Debtor(
                "منتظر", owed = 7_000, oldestUnpaidAt = today - 40 * day,
                lastContact = Contact(today - day, Outcome.PROMISED, today + 3 * day, 7_000)
            ),
            Debtor("تازه", owed = 2_000, oldestUnpaidAt = today - 3 * day),
        )
        assertTrue(p.today.isEmpty())
        assertEquals(listOf(Reason.WAITING_PROMISE, Reason.RECENT), p.later.map { it.reason })
        assertEquals(3, p.later.first().days)
    }

    @Test
    fun `paying part of a promise is not a broken promise`() {
        val c = Contact(today - 5 * day, Outcome.PROMISED, today - 2 * day, 10_000)
        val kept = plan(Debtor("نیمه", owed = 4_000, oldestUnpaidAt = today - 20 * day, lastContact = c))
        assertEquals(Reason.OLD_DEBT, kept.today.single().reason)
        val broken = plan(Debtor("هیچ", owed = 10_000, oldestUnpaidAt = today - 20 * day, lastContact = c))
        assertEquals(Reason.BROKEN_PROMISE, broken.today.single().reason)
    }

    @Test
    fun `someone reached today drops to the bottom but stays`() {
        val p = plan(
            Debtor(
                "زنگ‌خورده", owed = 90_000, oldestUnpaidAt = today - 30 * day,
                lastContact = Contact(today + 3_600_000L, Outcome.NO_ANSWER)
            ),
            Debtor("مانده", owed = 1_000, oldestUnpaidAt = today - 30 * day),
        )
        assertEquals(listOf("مانده", "زنگ‌خورده"), p.today.map { it.debtor.name })
        assertTrue(p.today.last().contactedToday)
        assertEquals(1, p.pending)
    }

    @Test
    fun `nobody owing nothing is listed`() {
        val p = plan(Debtor("صاف", owed = 0), Debtor("پیش‌پرداخت", owed = -3_000))
        assertTrue(p.today.isEmpty() && p.later.isEmpty())
    }

    @Test
    fun `a follow-up survives being stored and read back`() {
        val c = Contact(0L, Outcome.PROMISED, until = 123_456L, owedThen = 7_500L)
        val back = DebtFollowUp.decode(DebtFollowUp.encode(c), at = 99L)!!
        assertEquals(c.copy(at = 99L), back)
        assertNull(DebtFollowUp.decode("نتیجه=FLYING؛تا=1", 1L))
        assertNull(DebtFollowUp.decode("", 1L))
    }

    @Test
    fun `the reminder names the shop, the person and the amount`() {
        val row = plan(Debtor("احمد", owed = 5_000, oldestUnpaidAt = today - 30 * day)).today.single()
        val text = DebtFollowUp.message("خیاطیِ نور", row, "۵٬۰۰۰ ؋")
        assertTrue(text.contains("احمد"))
        assertTrue(text.contains("خیاطیِ نور"))
        assertTrue(text.contains("۵٬۰۰۰ ؋"))
        assertFalse(text.contains("قسط"))
    }
}
