package com.afghanjama

import com.afghanjama.data.CashForecast
import com.afghanjama.data.CashForecast.Kind
import com.afghanjama.data.CashForecast.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * پیش‌بینیِ نقدینگی — یک محاسبهٔ هوشمند که عدد می‌دهد و کسی دستی نمی‌سنجدش.
 *
 * حالت‌های مرزی مهم‌اند: هشدارِ دروغِ «پول کم می‌آید» از نبودنش بدتر است،
 * چون کاربر یاد می‌گیرد نادیده‌اش بگیرد. پس هر مرز اینجا سنجیده می‌شود.
 */
class CashForecastTest {

    private fun inp(
        cash: Long = 0,
        sales30: Long = 0,
        expense30: Long = 0,
        receivable: Long = 0,
        payable: Long = 0,
        openWages: Long = 0,
        horizonDays: Int = CashForecast.HORIZON_DAYS,
        safetyBuffer: Long = CashForecast.SAFETY_BUFFER,
    ) = CashForecast.Inputs(
        cash = cash, sales30 = sales30, expense30 = expense30,
        receivable = receivable, payable = payable, openWages = openWages,
        horizonDays = horizonDays, safetyBuffer = safetyBuffer,
    )

    @Test
    fun `today's cash is the start, and a full horizon collects all the receivable`() {
        val s = CashForecast.project(inp(cash = 300_000, receivable = 50_000))
        assertEquals(300_000L, s.start)
        // تا تهِ افق (۳۰ روز = COLLECT_DAYS) همهٔ طلب وصول می‌شود؛ تأثیرش
        // دقیقاً برابرِ خودِ طلب است.
        assertEquals(50_000L, s.end - s.endWithoutCollecting)
    }

    @Test
    fun `a healthy workshop never goes short and is good`() {
        val s = CashForecast.project(
            inp(cash = 300_000, sales30 = 400_000, expense30 = 100_000,
                receivable = 50_000, payable = 40_000, openWages = 20_000)
        )
        assertNull(s.shortDay)
        assertEquals(Level.GOOD, s.level)
        assertEquals(Kind.HEALTHY, s.signals.single().kind)
        // عملیاتِ مثبت، پس تهِ افق بیشتر از امروز.
        assertTrue(s.end > s.start)
    }

    @Test
    fun `cash that runs short even after collecting is a risk with a day`() {
        val s = CashForecast.project(
            inp(cash = 30_000, sales30 = 50_000, expense30 = 100_000,
                receivable = 10_000, payable = 60_000, openWages = 40_000)
        )
        assertNotNull(s.shortDay)
        assertEquals(Level.RISK, s.level)
        assertEquals(Kind.SHORT_EVEN_WITH_COLLECTING, s.signals.first().kind)
    }

    @Test
    fun `short only if the receivable is not collected is a watch, not a risk`() {
        val s = CashForecast.project(
            inp(cash = 50_000, sales30 = 90_000, expense30 = 100_000,
                receivable = 200_000, payable = 60_000, openWages = 30_000)
        )
        // با وصولِ طلب هیچ‌وقت زیرِ صفر نمی‌رود…
        assertNull(s.shortDay)
        // …ولی بدونِ وصول، جایی کم می‌آید — پس «اگر طلب را بگیری».
        assertEquals(Level.WATCH, s.level)
        assertEquals(Kind.SHORT_UNLESS_COLLECTING, s.signals.single().kind)
    }

    @Test
    fun `a declining but still-positive cash is a watch`() {
        val s = CashForecast.project(inp(cash = 200_000, sales30 = 80_000, expense30 = 100_000))
        assertNull(s.shortDay)
        assertEquals(Level.WATCH, s.level)
        assertEquals(Kind.DECLINING, s.signals.single().kind)
        assertEquals(180_000L, s.end)          // ۲۰۰ هزار − ۲۰ هزارِ افتِ ماه
        assertTrue(s.end < s.start)
    }

    @Test
    fun `the lowest point and its day are found`() {
        // فقط کارمزدِ باز: تا روزِ هفتم کم می‌شود، بعد صاف می‌ماند.
        val s = CashForecast.project(inp(cash = 100_000, openWages = 70_000))
        assertEquals(30_000L, s.lowest)
        assertEquals(CashForecast.WAGE_DAYS, s.lowestDay)   // روزِ ۷
        assertNull(s.shortDay)                              // ۳۰ هزار هنوز بالای صفر
    }

    @Test
    fun `the safety buffer moves the short day earlier`() {
        val s = CashForecast.project(inp(cash = 100_000, openWages = 70_000, safetyBuffer = 50_000))
        // روزِ ۶: ۱۰۰ هزار − ۶۰ هزار = ۴۰ هزار، زیرِ حاشیهٔ ۵۰ هزار.
        assertEquals(6, s.shortDay)
        assertEquals(Level.RISK, s.level)
    }

    @Test
    fun `a brand-new workshop has nothing to forecast`() {
        val s = CashForecast.project(inp())
        assertFalse(s.hasData)
        assertEquals(Level.GOOD, s.level)
        assertEquals(Kind.HEALTHY, s.signals.single().kind)
    }
}
