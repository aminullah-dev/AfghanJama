package com.afghanjama

import com.afghanjama.data.DebtFollowUp
import com.afghanjama.data.Figure
import com.afghanjama.data.FinancialHealth
import com.afghanjama.data.HubDigest
import com.afghanjama.data.OrderPipeline
import com.afghanjama.data.PriceAdvisor
import com.afghanjama.data.ProductInsights
import com.afghanjama.data.TodayOrders
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.data.entities.LanDevice
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.ui.vm.nextTickDelay
import com.afghanjama.work.DeliveryForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * داشبورد: هر عدد یا معلوم است یا «نامعلوم با دلیل» — هرگز صفرِ ساختگی.
 *
 * تابع‌های خالص‌اند ([HubDigest.build]، [TodayOrders]، [OrderPipeline.counts])،
 * پس بی دفتر آزموده می‌شوند. زمان ثابت است و نیمه‌شب از بیرون داده می‌شود.
 */
class HubDigestTest {

    private val day = 86_400_000L
    private val dayStart = 1_800_000_000_000L - (1_800_000_000_000L % day)
    private val now = dayStart + 10 * 3_600_000L // ساعتِ ۱۰ صبح

    private fun order(
        code: String,
        status: OrderStatus = OrderStatus.SEWING,
        due: Long = 0L,
        created: Long = now - 10 * day,
        customer: String = "مشتری",
        stageChangedAt: Long = 0L,
    ) = Order(
        orderCode = code, shortCode = code, designTitle = "پیراهن", qty = 2,
        fabricType = "", fabricColor = "", size = "", fabricUnit = "متر", fabricAmount = 0.0,
        fabricPrice = 0, workCost = 0, customerName = customer, customerPhone = "",
        status = status.name, dueDate = due, createdAt = created, stageChangedAt = stageChangedAt,
    )

    private fun build(i: HubDigest.Inputs) = HubDigest.build(i, now, dayStart)

    private fun <T> known(f: Figure<T>): T {
        assertTrue("باید معلوم باشد: $f", f is Figure.Known)
        return (f as Figure.Known).value
    }

    private fun unknown(f: Figure<*>): String {
        assertTrue("باید نامعلوم باشد: $f", f is Figure.Unknown)
        return (f as Figure.Unknown).reason
    }

    // ───────────────────────────── «در حالِ خواندن»

    @Test
    fun `before any source arrives every card is unknown, none is zero`() {
        val d = HubDigest.loading(now)
        listOf(
            d.alerts, d.requests, d.today, d.ready, d.inside, d.pipeline, d.atRisk, d.tailors, d.money,
            d.health, d.sales, d.debts, d.debtsTotal, d.wages, d.materials, d.products, d.belowCost, d.devices,
        ).forEach { assertEquals(HubDigest.READING, unknown(it)) }
    }

    @Test
    fun `an empty ledger gives honest zeros where zero is real, and reasons where it is not`() {
        val f = DeliveryForecast.of(emptyList(), emptyList(), now)
        val h = FinancialHealth.assess(FinancialHealth.Figures(0, 0, 0, 0, 0, 0, 0, 0))
        val d = build(
            HubDigest.Inputs(
                alerts = emptyList(), requests = emptyList(), orders = emptyList(), inside = emptyList(),
                money = HubDigest.Money(0, 0, 0), health = h, sales30 = 0, openWagesTotal = 0, openWagesCount = 0,
                debts = DebtFollowUp.Plan(emptyList(), emptyList()), forecast = f, materials = emptyList(),
                products = ProductInsights.Summary(emptyList(), emptyList()), priceBook = PriceAdvisor.book(emptyList(), now),
                finishedStock = emptyList(), devices = emptyList(),
            )
        )
        // شمار‌ها صفرِ واقعی‌اند.
        assertEquals(0, known(d.ready).count)
        assertNull(known(d.ready).oldestDays)
        assertEquals(listOf(0, 0, 0, 0, 0), known(d.pipeline).counts)
        assertEquals(0, known(d.alerts).total)
        // ولی سرعتِ دوخت و وضعِ مالیِ کارگاهِ تازه نامعلوم‌اند، نه صفر.
        assertEquals(HubDigest.NO_SPEED, unknown(d.atRisk))
        assertTrue(unknown(d.health).contains("داده‌ای"))
        // ‌مقایسهٔ فروش بی ۳۰ روزِ قبل نامعلوم است.
        assertEquals(HubDigest.NO_PREV_SALES, unknown(known(d.sales).changePct))
    }

    // ───────────────────────────── هر مسیرِ نامعلوم

    @Test
    fun `cash cover is unknown with no expenses, and capped cover is flagged not invented`() {
        val noExpense = FinancialHealth.assess(FinancialHealth.Figures(cash = 50_000, receivable = 0, stock = 0, debts = 0, expense30 = 0, sales30 = 1000, salesPrev30 = 0))
        assertEquals(HubDigest.NO_EXPENSE, unknown(known(build(HubDigest.Inputs(health = noExpense)).health).cover))

        val rich = FinancialHealth.assess(FinancialHealth.Figures(cash = 10_000_000, receivable = 0, stock = 0, debts = 0, expense30 = 30, sales30 = 1, salesPrev30 = 1))
        val h = known(build(HubDigest.Inputs(health = rich)).health)
        assertEquals(FinancialHealth.MAX_COVER_DAYS, known(h.cover))
        assertTrue(h.coverCapped)
    }

    @Test
    fun `a tailor with no finished work this month has unknown days, not zero`() {
        val sewing = listOf(SewingAssignment(orderId = "1", orderCode = "A", tailorLabel = "[T9] نو", qty = 3, unitWage = 0, status = "ASSIGNED"))
        val history = listOf(SewingAssignment(orderId = "old", orderCode = "OLD", tailorLabel = "[T1] احمد", qty = 30, unitWage = 0, status = "DONE", doneAt = now - 3 * day))
        val f = DeliveryForecast.of(listOf(order("A")), sewing + history, now)
        val t = known(build(HubDigest.Inputs(forecast = f)).tailors).first { it.name.contains("نو") }
        assertTrue(t.days is Figure.Unknown)
    }

    @Test
    fun `materials below their minimum with no recorded use show unknown days`() {
        val rows = HubDigest.materialRows(
            listOf(
                HubDigest.Material("نخ", "عدد", stock = 2.0, minLevel = 10.0, daysLeft = null, runsOutSoon = false),
                HubDigest.Material("کتان", "متر", stock = 5.0, minLevel = 0.0, daysLeft = 3, runsOutSoon = true),
                HubDigest.Material("مخمل", "متر", stock = 50.0, minLevel = 10.0, daysLeft = 90, runsOutSoon = false),
            )
        )
        assertEquals(listOf("کتان", "نخ"), rows.map { it.name })
        assertEquals(3, known(rows[0].days))
        assertEquals(HubDigest.NO_USE, unknown(rows[1].days))
    }

    @Test
    fun `no NaN, infinity or negative days anywhere`() {
        val inside = listOf(AttendanceRecord(employee = "احمد", checkIn = now + 3_600_000L)) // ساعتِ جلوتر
        val d = build(HubDigest.Inputs(inside = inside, orders = listOf(order("X", OrderStatus.STORED, stageChangedAt = now + day))))
        assertEquals(0, known(d.inside).first().hours)
        assertEquals(0, known(d.ready).oldestDays)
    }

    // ───────────────────────────── سفارش‌های امروز

    @Test
    fun `today orders use the open-status rule and day boundaries`() {
        val orders = listOf(
            order("DUE", due = dayStart + 5 * 3_600_000L),
            order("DUE-END", due = dayStart + day - 1),
            order("TOMORROW", due = dayStart + day),
            order("LATE", due = dayStart - 1),
            order("NO-DUE", due = 0L),                                    // بی مهلت: هرگز عقب نیست
            order("DONE-LATE", OrderStatus.STORED, due = dayStart - day),  // ساخته‌شده: کارِ کارگاه تمام است
            order("SOLD-LATE", OrderStatus.SENT, due = dayStart - day),
            order("NEW", OrderStatus.IN_STOCK, created = dayStart + 60_000),
        )
        val t = TodayOrders.select(orders, dayStart, dayStart + day)
        assertEquals(listOf("DUE", "DUE-END"), t.dueToday.map { it.code })
        assertEquals(listOf("LATE"), t.late.map { it.code })
        assertEquals(1, t.createdToday)
    }

    // ───────────────────────────── خطِ تولید

    @Test
    fun `pipeline counts only open orders, finished ones never fill the delivery stage`() {
        val counts = OrderPipeline.counts(
            listOf("IN_STOCK", "CUTTING", "CUT_DONE", "SEWING", "REVIEW", "STORED", "SENT", "SALES", "SENT", "WHAT")
        )
        assertEquals(listOf(1, 2, 1, 1, 1), counts)
    }

    // ───────────────────────────── زیرِ قیمتِ تمام‌شده

    private fun sold(n: Int, price: Long) = (1..n).map {
        PriceAdvisor.Sold(PriceAdvisor.key("پیراهن", "M"), unitPrice = price, unitCost = 0, at = now - it * day)
    }

    private val shirt = FinishedStock(name = "پیراهن", size = "M", qty = 4, totalValue = 4 * 1_000, updatedAt = now)

    @Test
    fun `below cost needs at least three sales, never a guess`() {
        val two = PriceAdvisor.book(sold(2, 900), now)
        assertTrue(HubDigest.belowCost(two, listOf(shirt)).isEmpty())
        val three = PriceAdvisor.book(sold(3, 900), now)
        val rows = HubDigest.belowCost(three, listOf(shirt))
        assertEquals(1, rows.size)
        assertEquals(900, rows[0].usualPrice)
        assertEquals(1_000, rows[0].cost)
    }

    @Test
    fun `a product sold above cost, or with no cost, is not listed`() {
        assertTrue(HubDigest.belowCost(PriceAdvisor.book(sold(5, 1_200), now), listOf(shirt)).isEmpty())
        val noCost = shirt.copy(totalValue = 0, avgCost = 0)
        assertTrue(HubDigest.belowCost(PriceAdvisor.book(sold(5, 100), now), listOf(noCost)).isEmpty())
    }

    // ───────────────────────────── دستگاه‌ها

    @Test
    fun `devices are online within 45 seconds and behind versions are flagged`() {
        fun dev(id: String, seen: Long?, ver: String, revoked: Long? = null) = LanDevice(
            deviceId = id, name = id, role = "worker", keyB64 = "k", createdAt = 0, lastSeenAt = seen, appVersion = ver, revokedAt = revoked,
        )
        val d = build(
            HubDigest.Inputs(
                devices = listOf(dev("a", now - 10_000, "1.12.0"), dev("b", now - 60_000, "1.10.0"), dev("c", now, "1.12.0", revoked = 1)),
                hubServing = true, appVersion = "1.12.0",
            )
        )
        val list = known(d.devices).list
        assertEquals(listOf("a", "b"), list.map { it.name })
        assertTrue(list[0].online)
        assertFalse(list[1].online)
        assertFalse(list[0].outdated)
        assertTrue(list[1].outdated)
        assertEquals(1, known(d.devices).online)
    }

    // ───────────────────────────── هشدارها

    @Test
    fun `alerts put urgent first and show at most five`() {
        val rows = (1..8).map { HubDigest.AlertRow("w$it", urgent = false, warn = true, title = "w$it", detail = "", route = null) } +
            HubDigest.AlertRow("u", urgent = true, warn = false, title = "u", detail = "", route = null) +
            HubDigest.AlertRow("i", urgent = false, warn = false, title = "i", detail = "", route = null)
        val a = known(build(HubDigest.Inputs(alerts = rows)).alerts)
        assertEquals("u", a.top.first().id)
        assertEquals(HubDigest.TOP, a.top.size)
        assertEquals(10, a.total)
        assertEquals(1, a.urgent)
        assertEquals(8, a.warn)
    }

    // ───────────────────────────── «الان» که جلو می‌رود

    @Test
    fun `ticks land on the next minute boundary`() {
        assertEquals(60_000L, nextTickDelay(0L, 60_000L))
        assertEquals(1L, nextTickDelay(59_999L, 60_000L))
        assertEquals(30_000L, nextTickDelay(1_800_000_000_000L + 30_000L, 60_000L))
    }

    // ───────────────────────────── تأیید از داشبورد

    /** دفتری که اگر کسی به آن دست بزند، آزمون می‌فهمد. */
    private fun touchyRepo(): Pair<com.afghanjama.data.repo.Repo, () -> Int> {
        var touched = 0
        val loader = com.afghanjama.data.Db::class.java.classLoader
        val dao = java.lang.reflect.InvocationHandler { _, _, _ -> touched++; null }
        val db = java.lang.reflect.Proxy.newProxyInstance(loader, arrayOf(com.afghanjama.data.Db::class.java)) { _, m, _ ->
            touched++
            if (m.name.endsWith("Dao")) java.lang.reflect.Proxy.newProxyInstance(loader, arrayOf(m.returnType), dao) else null
        } as com.afghanjama.data.Db
        return com.afghanjama.data.repo.Repo(db) to { touched }
    }

    @Test
    fun `approving from the dashboard asks permission before touching the ledger`() = kotlinx.coroutines.runBlocking {
        val (repo, touched) = touchyRepo()
        val seller = com.afghanjama.ui.vm.Access.forRole(com.afghanjama.ui.vm.UserRole.SALES)
        val r = com.afghanjama.data.entities.SyncRequest(deviceName = "گوشی", worker = "احمد", type = "SEWING_DONE", summary = "تمام شد")
        val before = touched()
        assertEquals(com.afghanjama.ui.vm.WorkshopLinkAccess.NOT_YOURS, com.afghanjama.ui.vm.RequestDecisions.approve(repo, seller, r))
        assertEquals(com.afghanjama.ui.vm.WorkshopLinkAccess.NOT_YOURS, com.afghanjama.ui.vm.RequestDecisions.reject(repo, seller, r))
        assertEquals("the ledger must not be touched", before, touched())
    }
}
