package com.afghanjama

import com.afghanjama.data.entities.FinishedSale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ریاضیِ برگشت از فروش — آزمونِ واقعی، نه بررسیِ متنی.
 *
 * **چرا نوشته شد:** کارگاه ۲۰ عدد فروخته بود و ۲ عدد برگشت خورده بود.
 * ثبت‌ها همه درست بود ولی صفحهٔ گزارش‌ها همان ۲۰ را نشان می‌داد، چون از
 * `total`/`qty`ِ ناخالص می‌خواند. سودِ گزارش‌شده از سودِ واقعی بیشتر بود.
 *
 * `FinishedSale` کاتلینِ خالص است و هیچ چیزِ اندرویدی ندارد، پس این
 * ریاضی را می‌شود واقعاً **اجرا** کرد نه اینکه فقط در متنِ کد دنبالش
 * گشت. این آزمون با هر push در CI می‌دود.
 */
class SaleReturnTest {

    private fun sale(qty: Int, total: Long, cost: Long, returned: Int = 0) =
        FinishedSale(
            code = "S-1", productName = "پیراهن", size = "L",
            qty = qty, unitPrice = if (qty > 0) total / qty else 0,
            total = total, cost = cost, returnedQty = returned
        )

    @Test
    fun `20 sold and 2 returned - the workshop case`() {
        val s = sale(20, 20_000, 12_000, returned = 2)
        assertEquals("تعدادِ خالص", 18, s.qty - s.returnedQty)
        assertEquals("درآمدِ خالص", 18_000L, s.netTotal)
        assertEquals("بهای تمام‌شدهٔ خالص", 10_800L, s.netCost)
        assertEquals("هنوز ۱۸ عدد قابلِ برگشت است", 18, s.returnableQty)
    }

    @Test
    fun `full return zeroes the net exactly`() {
        val s = sale(5, 999, 501, returned = 5)
        assertEquals(0L, s.netTotal)
        assertEquals(0L, s.netCost)
        assertEquals(0, s.returnableQty)
    }

    @Test
    fun `no return means nothing changes`() {
        val s = sale(7, 700, 400)
        assertEquals(700L, s.netTotal)
        assertEquals(400L, s.netCost)
    }

    /**
     * ردیفِ تخفیف‌دار: جمعِ ردیف دیگر «تعداد × فی» نیست. فرمولِ ساده
     * بیشتر از چیزی که مشتری داده بود پس می‌داد.
     */
    @Test
    fun `partial refunds add up to the line total`() {
        for ((qty, total) in listOf(3 to 250L, 3 to 100L, 7 to 999L, 20 to 20_000L)) {
            var s = sale(qty, total, total / 2)
            var paid = 0L
            repeat(qty) {
                paid += s.refundFor(1)
                s = s.copy(returnedQty = s.returnedQty + 1)
            }
            assertEquals(
                "با $qty عدد و جمعِ $total، مجموعِ برگشت‌ها باید دقیقاً $total شود",
                total, paid
            )
            assertEquals("و خالص باید صفر بماند", 0L, s.netTotal)
        }
    }

    @Test
    fun `cost leaves no remainder either`() {
        var s = sale(3, 100, 70)
        var back = 0L
        repeat(3) {
            back += s.costFor(1)
            s = s.copy(returnedQty = s.returnedQty + 1)
        }
        assertEquals(70L, back)
    }

    @Test
    fun `refunding four at once equals four singles`() {
        val one = run {
            var s = sale(10, 1_000, 600)
            var sum = 0L
            repeat(4) { sum += s.refundFor(1); s = s.copy(returnedQty = s.returnedQty + 1) }
            sum
        }
        assertEquals("۴ بار یکی، برابرِ یک بار چهارتا", one, sale(10, 1_000, 600).refundFor(4))
    }

    @Test
    fun `refund never exceeds what is left`() {
        val s = sale(10, 1_000, 600, returned = 8)
        assertEquals("فقط ۲ عدد مانده", 200L, s.refundFor(2))
        assertTrue("درخواستِ بیشتر از سقف هم بیش از مانده پس نمی‌دهد", s.refundFor(50) <= 200L)
    }

    /** جمعِ گزارش روی چند ردیف — همان کاری که صفحهٔ گزارش‌ها می‌کند. */
    @Test
    fun `report totals are net`() {
        val rows = listOf(
            sale(20, 20_000, 12_000, returned = 2),
            sale(5, 5_000, 3_000, returned = 5),
            sale(4, 4_000, 2_000)
        )
        assertEquals("درآمدِ خالص", 22_000L, rows.sumOf { it.netTotal })
        assertEquals("تعدادِ خالص", 22, rows.sumOf { it.qty - it.returnedQty })
        assertEquals("مبلغِ برگشتی", 7_000L, rows.sumOf { it.total - it.netTotal })
        assertEquals(
            "فروشی که تمامش برگشته دیگر یک فروش نیست",
            2, rows.count { it.returnedQty < it.qty }
        )
    }
}
