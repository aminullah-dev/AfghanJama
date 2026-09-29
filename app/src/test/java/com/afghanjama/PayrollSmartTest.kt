package com.afghanjama

import com.afghanjama.data.PayrollSmart
import com.afghanjama.data.PayrollSmart.Config
import com.afghanjama.data.PayrollSmart.Shift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حقوقِ هوشمند — حضور به حقوق وصل می‌شود، به شکلِ پیشنهاد نه کسرِ خودکار.
 *
 * کارمندِ آزمون: حقوقِ ماهانه ۱۳٬۰۰۰، ۲۶ روزِ کاری، شیفتِ ۸ ساعته.
 * پس حقوقِ هر روز ۵۰۰ و دستمزدِ هر ساعت ۶۲ (۱۳۰۰۰÷۲۰۸).
 */
class PayrollSmartTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val cfg = Config(monthlySalary = 13_000, workingDays = 26, shiftHours = 8)

    /** نیمه‌شبِ همان روز — بی وابستگی به منطقهٔ زمانی. */
    private val dayOf: (Long) -> Long = { it / day * day }

    /** یک شیفتِ بسته: روزِ [d]، به مدتِ [hours] ساعت. */
    private fun shift(d: Int, hours: Long) = Shift(d * day + 8 * hour, d * day + 8 * hour + hours * hour)

    private fun analyze(shifts: List<Shift>) = PayrollSmart.analyze(shifts, cfg, dayOf)

    @Test
    fun `full attendance suggests the full salary, no overtime`() {
        val s = analyze((1..26).map { shift(it, 8) })
        assertEquals(26, s.daysPresent)
        assertEquals(13_000, s.proratedSalary)
        assertEquals(0L, s.overtimeHours)
        assertEquals(13_000, s.suggestedTotal)
        assertFalse(s.hasDeduction)
    }

    @Test
    fun `absence prorates the salary down by day`() {
        val s = analyze((1..20).map { shift(it, 8) })
        assertEquals(20, s.daysPresent)
        assertEquals(6, s.daysShort)
        assertEquals(500L, s.dailyRate)
        assertEquals(10_000, s.proratedSalary)   // ۵۰۰ × ۲۰
        assertTrue(s.hasDeduction)
    }

    @Test
    fun `hours beyond the shift become suggested overtime, counted per day`() {
        // یک روزِ ۱۰ ساعته (۲ ساعت اضافه) و یک روزِ ۷ ساعته (اضافه ندارد).
        val s = analyze(listOf(shift(1, 10), shift(2, 7)))
        assertEquals(2, s.daysPresent)
        assertEquals(62L, s.hourlyRate)          // ۱۳۰۰۰ ÷ (۲۶×۸)
        assertEquals(2L, s.overtimeHours)        // فقط روزِ اول؛ روزِ ۷ ساعته خنثی نمی‌کند
        assertEquals(124L, s.overtimePay)        // ۶۲ × ۲
    }

    @Test
    fun `two shifts in one day are one present day, hours summed for overtime`() {
        // صبح ۵ ساعت + بعدازظهر ۵ ساعت = ۱۰ ساعت در یک روز → ۲ ساعت اضافه.
        val s = analyze(listOf(
            Shift(3 * day + 6 * hour, 3 * day + 11 * hour),
            Shift(3 * day + 13 * hour, 3 * day + 18 * hour),
        ))
        assertEquals(1, s.daysPresent)
        assertEquals(10L, s.workedHours)
        assertEquals(2L, s.overtimeHours)
    }

    @Test
    fun `an open shift counts as a present day but adds no hours`() {
        val s = analyze(listOf(Shift(4 * day + 8 * hour, null)))
        assertEquals(1, s.daysPresent)
        assertEquals(0L, s.workedHours)
        assertEquals(0L, s.overtimeHours)
        assertEquals(500L, s.proratedSalary)     // یک روز حاضر
    }

    @Test
    fun `present more days than the working month never inflates the base salary`() {
        val s = analyze((1..28).map { shift(it, 8) })
        assertEquals(28, s.daysPresent)
        assertEquals(13_000, s.proratedSalary)   // سقف = حقوقِ کامل
        assertEquals(0, s.daysShort)
    }

    @Test
    fun `no attendance means no suggestion`() {
        val s = analyze(emptyList())
        assertFalse(s.hasData)
        assertEquals(0, s.daysPresent)
        assertEquals(0, s.proratedSalary)
        assertEquals(0, s.suggestedTotal)
    }
}
