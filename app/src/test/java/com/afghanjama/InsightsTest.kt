package com.afghanjama

import com.afghanjama.data.FinancialHealth
import com.afghanjama.data.FinancialHealth.Kind
import com.afghanjama.data.FinancialHealth.Level
import com.afghanjama.data.ProductInsights
import com.afghanjama.lan.Lan
import com.afghanjama.lan.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * سه قاعدهٔ «هوشمند»ی که عدد می‌دهند و کسی دستی نمی‌سنجدشان:
 * وضعِ مالی، پیشنهادِ دوختِ دوباره و کالای راکد، و انتخابِ نشانیِ شبکه.
 *
 * هر کدام چند حالتِ مرزی دارد که اگر غلط باشد هشدارِ دروغ می‌دهد — و
 * هشدارِ دروغ از نبودنِ هشدار بدتر است، چون کاربر یاد می‌گیرد نادیده‌اش
 * بگیرد.
 */
class InsightsTest {

    private fun fig(
        cash: Long = 0, receivable: Long = 0, stock: Long = 0, debts: Long = 0,
        expense30: Long = 0, sales30: Long = 0, salesPrev30: Long = 0,
    ) = FinancialHealth.Figures(
        cash = cash, receivable = receivable, stock = stock, debts = debts,
        expense30 = expense30, sales30 = sales30, salesPrev30 = salesPrev30,
    )

    // ─────────── وضعِ مالی ───────────

    @Test
    fun `a healthy workshop has no warnings`() {
        val s = FinancialHealth.assess(
            fig(cash = 300_000, receivable = 50_000, stock = 200_000, debts = 40_000,
                expense30 = 90_000, sales30 = 400_000, salesPrev30 = 380_000)
        )
        assertEquals(Level.GOOD, s.level)
        assertTrue(s.signals.none { it.level != Level.GOOD })
        assertEquals(100, s.coverDays)               // ۳۰۰هزار ÷ (۹۰هزار/۳۰)
        assertEquals(510_000L, s.netWorth)
        assertEquals(5, s.salesChangePct)
    }

    @Test
    fun `debt that cash cannot pay but receivables can is a watch, not a risk`() {
        val s = FinancialHealth.assess(fig(cash = 20_000, receivable = 60_000, debts = 50_000))
        assertEquals(Level.WATCH, s.level)
        assertEquals(Kind.DEBT_OVER_CASH, s.signals.first().kind)
        assertEquals(30_000L, s.signals.first().value)   // کسریِ نقد
    }

    @Test
    fun `debt beyond cash and receivables together is a risk`() {
        val s = FinancialHealth.assess(fig(cash = 20_000, receivable = 10_000, stock = 100_000, debts = 50_000))
        assertEquals(Level.RISK, s.level)
        assertTrue(s.signals.any { it.kind == Kind.DEBT_OVER_CASH_AND_RECEIVABLE })
    }

    @Test
    fun `cover days bands and no expenses means no guess`() {
        assertEquals(Kind.COVER_SHORT, FinancialHealth.assess(fig(cash = 10_000, expense30 = 30_000)).signals.single().kind)
        assertEquals(Kind.COVER_LOW, FinancialHealth.assess(fig(cash = 20_000, expense30 = 30_000)).signals.single().kind)
        assertTrue(FinancialHealth.assess(fig(cash = 30_000, expense30 = 30_000)).signals.isEmpty())
        // هزینه‌ای ثبت نشده: حدس زدن بدتر از نگفتن است.
        assertNull(FinancialHealth.assess(fig(cash = 5_000)).coverDays)
        assertEquals(FinancialHealth.MAX_COVER_DAYS, FinancialHealth.assess(fig(cash = Long.MAX_VALUE / 100, expense30 = 1)).coverDays)
    }

    @Test
    fun `sales drop is noticed and a first month is not a drop`() {
        val drop = FinancialHealth.assess(fig(sales30 = 60_000, salesPrev30 = 100_000))
        assertEquals(Kind.SALES_DROP, drop.signals.single().kind)
        assertEquals(40L, drop.signals.single().value)
        // ماهِ اول: دورهٔ قبل فروشی نداشته، پس «رشدِ بی‌نهایت» هم نیست.
        val first = FinancialHealth.assess(fig(sales30 = 60_000))
        assertNull(first.salesChangePct)
        assertTrue(first.signals.isEmpty())
        val rise = FinancialHealth.assess(fig(sales30 = 130_000, salesPrev30 = 100_000))
        assertEquals(Level.GOOD, rise.level)
        assertEquals(Kind.SALES_RISE, rise.signals.single().kind)
    }

    @Test
    fun `worst signal decides the level and comes first`() {
        val s = FinancialHealth.assess(
            fig(cash = 1_000, debts = 500_000, expense30 = 90_000, sales30 = 10, salesPrev30 = 100)
        )
        assertEquals(Level.RISK, s.level)
        assertEquals(Level.RISK, s.signals.first().level)
        assertTrue(s.signals.any { it.kind == Kind.NET_NEGATIVE })
        assertFalse(s.signals.zipWithNext().any { (a, b) -> a.level == Level.WATCH && b.level == Level.RISK })
    }

    // ─────────── انبارِ محصول ───────────

    private val day = 24L * 60 * 60 * 1000
    private val now = 400 * day

    private fun stock(name: String, qty: Int, value: Long = qty * 1_000L, updatedDaysAgo: Int = 1, size: String = "") =
        ProductInsights.Stock(name, size, qty, value, now - updatedDaysAgo * day)

    private fun sale(name: String, qty: Int, daysAgo: Int, size: String = "") =
        ProductInsights.Sale(name, size, qty, now - daysAgo * day)

    @Test
    fun `a bestseller about to run out is suggested for sewing`() {
        val sum = ProductInsights.analyze(
            listOf(stock("پیراهن", 3), stock("واسکت", 40)),
            listOf(sale("پیراهن", 10, 2), sale("پیراهن", 10, 20), sale("واسکت", 4, 5)),
            now,
        )
        val r = sum.restock.single()
        assertEquals("پیراهن", r.name)
        assertEquals(20, r.sold30)
        assertEquals(4, r.coverDays)          // ۳ عدد ÷ (۲۰/۳۰ در روز)
        assertEquals(17, r.suggestedMake)     // یک ماه فروش منهای موجودی
    }

    @Test
    fun `a shortage row is first and includes what customers already bought`() {
        val sum = ProductInsights.analyze(
            listOf(stock("پیراهن", 2), stock("کرتی", -3, value = 0)),
            listOf(sale("پیراهن", 30, 3), sale("کرتی", 2, 4)),
            now,
        )
        assertEquals("کرتی", sum.restock.first().name)
        assertEquals(5, sum.restock.first().suggestedMake) // ۲ فروش + ۳ کسری
    }

    @Test
    fun `sold out and deleted rows still show up`() {
        val sum = ProductInsights.analyze(emptyList(), listOf(sale("چپن", 6, 3)), now)
        assertEquals("چپن", sum.restock.single().name)
        assertEquals(6, sum.restock.single().suggestedMake)
    }

    @Test
    fun `dead stock is idle for sixty days and fresh stock is not dead`() {
        val sum = ProductInsights.analyze(
            listOf(
                stock("کهنه", 12, value = 120_000, updatedDaysAgo = 90),
                stock("تازه‌دوخت", 12, value = 80_000, updatedDaysAgo = 5),
                stock("کندفروش", 5, value = 50_000, updatedDaysAgo = 70),
            ),
            listOf(sale("کندفروش", 1, 45)),
            now,
        )
        assertEquals(listOf("کهنه"), sum.dead.map { it.name })
        assertEquals(120_000L, sum.deadValue)
    }

    @Test
    fun `returned sales do not count and size is part of the product`() {
        val sum = ProductInsights.analyze(
            listOf(stock("پیراهن", 1, size = "L"), stock("پیراهن", 30, size = "M")),
            listOf(sale("پیراهن", 0, 2, size = "L"), sale("پیراهن", 12, 2, size = "M")),
            now,
        )
        assertTrue("فروشِ کاملاً برگشتی فروش نیست", sum.restock.none { it.size == "L" })
        assertTrue(sum.restock.none { it.size == "M" }) // ۳۰ عدد برای ۱۲ فروش در ماه کافی است
    }

    // ─────────── نشانیِ شبکه ───────────

    @Test
    fun `the wifi address wins over virtual and cellular adapters`() {
        val picked = Lan.rank(
            listOf(
                Lan.Candidate("eth1", "vEthernet (WSL)", "172.24.160.1"),
                Lan.Candidate("rmnet_data0", "rmnet_data0", "100.72.10.5"),
                Lan.Candidate("tun0", "tun0", "10.8.0.2"),
                Lan.Candidate("wlan0", "wlan0", "192.168.1.7"),
                Lan.Candidate("eth9", "VirtualBox Host-Only Ethernet Adapter", "192.168.56.1"),
            )
        )
        assertEquals("192.168.1.7", picked.first())
        assertEquals(5, picked.size)
    }

    @Test
    fun `a phone hotspot beats its own sim card`() {
        val picked = Lan.rank(
            listOf(
                Lan.Candidate("rmnet_data1", "rmnet_data1", "10.120.4.9"),
                Lan.Candidate("ap0", "ap0", "192.168.43.1"),
            )
        )
        assertEquals("192.168.43.1", picked.first())
    }

    @Test
    fun `unreachable addresses are dropped`() {
        assertTrue(
            Lan.rank(
                listOf(
                    Lan.Candidate("lo", "lo", "127.0.0.1"),
                    Lan.Candidate("eth0", "eth0", "169.254.3.4"),
                    Lan.Candidate("eth0", "eth0", "not-an-ip"),
                )
            ).isEmpty()
        )
    }

    // ─────────── QRِ اتصال ───────────

    @Test
    fun `pairing qr round trips and rejects foreign codes`() {
        val link = Pairing.parse(Pairing.encode("192.168.1.7", "482913"))
        assertEquals(Pairing.Link("192.168.1.7", "482913"), link)
        assertEquals(Pairing.Link("10.0.0.101", "1234"), Pairing.parse(" KYLINK:10.0.0.101:1234 "))
        assertNull(Pairing.parse("kylink:10.0.0101:482913"))   // همان خطای گزارش‌شده
        assertNull(Pairing.parse("kylink:192.168.1.7:12"))
        assertNull(Pairing.parse("ky31235"))                    // کارتِ کارمند
        assertNull(Pairing.parse("https://192.168.1.7:8797"))
    }
}
