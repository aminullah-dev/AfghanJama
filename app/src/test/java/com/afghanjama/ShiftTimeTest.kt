package com.afghanjama

import com.afghanjama.data.SalePolicy
import com.afghanjama.data.ShiftPolicy
import com.afghanjama.util.hourOf
import com.afghanjama.util.minuteOf
import com.afghanjama.util.withTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قاعدهٔ شیفت، سیاستِ فروشِ کسری، و کمکی‌های ساعتِ دیوار.
 * ساعت‌ها با منطقهٔ زمانیِ خودِ دستگاه ساخته می‌شوند، پس آزمون در هر
 * منطقه‌ای همان نتیجه را می‌دهد.
 */
class ShiftTimeTest {

    private val min = 60_000L
    private val hour = 60 * min

    @After
    fun resetPolicy() {
        SalePolicy.set(SalePolicy.DEFAULT_ALLOW)
    }

    @Test
    fun shiftWarnsAtSevenAndHalfHours() {
        val checkIn = 1_000_000_000_000L
        assertFalse(ShiftPolicy.nearShiftEnd(checkIn, checkIn + 7 * hour + 29 * min))
        assertTrue(ShiftPolicy.nearShiftEnd(checkIn, checkIn + 7 * hour + 30 * min))
        assertTrue(ShiftPolicy.nearShiftEnd(checkIn, checkIn + 9 * hour))
    }

    @Test
    fun shiftNotNearRightAfterCheckIn() {
        val checkIn = 1_000_000_000_000L
        assertFalse(ShiftPolicy.nearShiftEnd(checkIn, checkIn))
        // ساعتِ دستگاه عقب رفته: نباید هشدار بدهد
        assertFalse(ShiftPolicy.nearShiftEnd(checkIn, checkIn - hour))
    }

    @Test
    fun negativeStockAllowedByDefaultAndSettable() {
        assertTrue(SalePolicy.DEFAULT_ALLOW)
        assertTrue(SalePolicy.allowNegativeStock())
        SalePolicy.set(false)
        assertFalse(SalePolicy.allowNegativeStock())
        SalePolicy.set(true)
        assertTrue(SalePolicy.allowNegativeStock())
    }

    @Test
    fun withTimeSetsHourAndMinuteAndRoundTrips() {
        val base = 1_700_000_000_000L
        val t = withTime(base, 14, 35)
        assertEquals(14, hourOf(t))
        assertEquals(35, minuteOf(t))
        assertEquals(0L, t % min)
    }

    @Test
    fun withTimeKeepsTheSameDay() {
        val base = 1_700_000_000_000L
        val t = withTime(base, 9, 5)
        // بیش از ۲۴ ساعت از روزِ پایه دور نمی‌شود، و دو بار اعمال همان نتیجه را می‌دهد
        assertTrue(kotlin.math.abs(t - base) < 24 * hour)
        assertEquals(t, withTime(t, 9, 5))
    }

    @Test
    fun withTimeClampsOutOfRangeInput() {
        val base = 1_700_000_000_000L
        val t = withTime(base, 99, 99)
        assertEquals(23, hourOf(t))
        assertEquals(59, minuteOf(t))
        val z = withTime(base, -5, -5)
        assertEquals(0, hourOf(z))
        assertEquals(0, minuteOf(z))
    }
}
