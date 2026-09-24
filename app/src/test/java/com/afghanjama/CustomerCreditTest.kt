package com.afghanjama

import com.afghanjama.data.CustomerCredit
import com.afghanjama.data.CustomerCredit.Row
import com.afghanjama.data.CustomerCredit.Split
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * دریافت از مشتری: اول طلب، بعد پیش‌دریافت.
 *
 * **از یک گزارشِ واقعی آمد:** فروشِ نسیهٔ ۲۴٬۸۵۰ و دریافتِ ۲۴٬۸۰۰، و
 * «وضعیت مالی» هم ۲۴٬۸۵۰ طلب نشان می‌داد هم ۲۴٬۸۰۰ بدهی. هر دریافتی به
 * پیش‌دریافت نوشته می‌شد.
 *
 * اینجا ژورنالِ یک مشتری کنارِ دفترش شبیه‌سازی می‌شود و بعد از هر حرکت
 * سنجیده می‌شود که طلب و پیش‌دریافت هیچ‌کدام منفی نشوند و تفاضلشان همان
 * ماندهٔ دفترِ مشتری باشد.
 */
class CustomerCreditTest {

    /** ژورنالِ یک مشتری: طلب (۱۰۳۰) و پیش‌دریافت (۲۰۳۰). */
    private class Book {
        val rows = mutableListOf<Row>()
        var receivable = 0L
        var prepay = 0L
        val openOrders = mutableSetOf<String>()

        fun net() = CustomerCredit.recognizedNet(rows, openOrders)

        fun check() {
            assertTrue("طلب منفی شد: $receivable", receivable >= 0)
            assertTrue("پیش‌دریافت منفی شد: $prepay", prepay >= 0)
            assertEquals("ماندهٔ ژورنال با دفترِ مشتری نمی‌خوانَد", net(), receivable - prepay)
        }

        /** فروشِ نسیه از انبار: دفتر بدهکار، ژورنال طلب. */
        fun creditSale(code: String, amount: Long) {
            rows += Row("SALE_BILLING", code, amount, 0)
            receivable += amount
            check()
        }

        fun receive(amount: Long): Split {
            val s = CustomerCredit.credit(net(), amount)
            rows += Row("CUSTOMER_MANUAL", "", 0, amount)
            receivable -= s.receivable
            prepay += s.prepay
            check()
            return s
        }

        fun payBack(amount: Long): Split {
            val s = CustomerCredit.debit(net(), amount)
            rows += Row("MANUAL", "", amount, 0)
            receivable += s.receivable
            prepay -= s.prepay
            check()
            return s
        }

        /** بیعانه پیش از تحویل: فقط پیش‌دریافت. */
        fun advance(amount: Long) {
            rows += Row("CUSTOMER_ADVANCE", "", 0, amount)
            prepay += amount
            check()
        }

        /** ثبتِ سفارش: دفتر بدهکار می‌شود، ژورنال هنوز هیچ. */
        fun placeOrder(code: String, amount: Long) {
            rows += Row("SALE_BILLING", code, amount, 0)
            openOrders += code
            check()
        }

        /** سفارش کامل شد و به انبار رفت: بدهیِ ثبت خنثی می‌شود. */
        fun orderToStock(code: String, amount: Long) {
            rows += Row("SALE_TO_STOCK", code, 0, amount)
            openOrders -= code
            check()
        }

        fun cancelOrder(code: String, amount: Long) {
            rows += Row("SALE_CANCEL", code, 0, amount)
            openOrders -= code
            check()
        }
    }

    @Test
    fun `the reported case - a paid credit sale leaves no phantom debt`() {
        val b = Book()
        b.creditSale("FR-2026-A", 24_850)
        val s = b.receive(24_800)
        assertEquals(Split(receivable = 24_800, prepay = 0), s)
        assertEquals(50L, b.receivable)
        assertEquals(0L, b.prepay)
    }

    @Test
    fun `paying more than owed turns only the excess into a prepayment`() {
        val b = Book()
        b.creditSale("FR-1", 1_000)
        assertEquals(Split(receivable = 1_000, prepay = 500), b.receive(1_500))
        assertEquals(0L, b.receivable)
        assertEquals(500L, b.prepay)
    }

    @Test
    fun `money before any sale is a prepayment`() {
        val b = Book()
        assertEquals(Split(receivable = 0, prepay = 2_000), b.receive(2_000))
    }

    @Test
    fun `a new order does not make an early payment close a receivable that does not exist`() {
        val b = Book()
        b.placeOrder("AJ-2026-000001", 5_000)
        // دفترِ مشتری ۵٬۰۰۰ بدهکار است ولی ژورنال طلبی ندارد: پیش‌دریافت.
        assertEquals(Split(receivable = 0, prepay = 3_000), b.receive(3_000))
        b.orderToStock("AJ-2026-000001", 5_000)
        b.cancelOrder("AJ-2026-000002", 0) // سطرِ خنثیِ بی‌سفارش نباید چیزی را بشکند
    }

    @Test
    fun `a deleted order is recognised from its cancel row`() {
        val b = Book()
        b.placeOrder("AJ-2026-000009", 4_000)
        b.cancelOrder("AJ-2026-000009", 4_000)
        b.creditSale("FR-9", 700)
        assertEquals(Split(receivable = 700, prepay = 0), b.receive(700))
    }

    @Test
    fun `paying a customer back first returns what we hold for them`() {
        val b = Book()
        b.advance(1_000)
        assertEquals(Split(receivable = 0, prepay = 1_000), b.payBack(1_000))
        // پولی که از او پیشِ ما نیست، طلبِ ما از او می‌شود — پیش‌دریافت منفی نمی‌شود.
        assertEquals(Split(receivable = 300, prepay = 0), b.payBack(300))
    }

    @Test
    fun `a long mixed history keeps both accounts non-negative and in step`() {
        val b = Book()
        b.advance(500)
        b.placeOrder("AJ-1", 3_000)
        b.creditSale("FR-1", 2_000)
        b.receive(1_200)
        b.orderToStock("AJ-1", 3_000)
        b.creditSale("FR-2", 800)
        b.receive(2_000)
        b.payBack(100)
        b.receive(10)
        assertEquals(b.net(), b.receivable - b.prepay)
    }

    @Test
    fun `zero or negative amounts move nothing`() {
        assertEquals(Split(0, 0), CustomerCredit.credit(1_000, 0))
        assertEquals(Split(0, 0), CustomerCredit.credit(1_000, -5))
        assertEquals(Split(0, 0), CustomerCredit.debit(-1_000, 0))
    }
}
