package com.afghanjama

import com.afghanjama.data.ShiftPolicy
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.BreakTime
import com.afghanjama.work.ReminderPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * یادآورِ کامپیوتر: پایانِ شیفت و وقتِ نان و چای — همان قاعدهٔ گوشی، هر
 * کدام یک بار.
 */
class ReminderPlanTest {

    private val min = 60_000L
    private val hour = 60 * min
    private val now = 1_760_000_000_000L

    private fun open(name: String, agoMs: Long, out: Long? = null) =
        AttendanceRecord(employee = name, checkIn = now - agoMs, checkOut = out)

    @Test
    fun `a shift is quiet until its last half hour`() {
        val due = ReminderPlan.shifts(listOf(open("احمد", 7 * hour)), now, emptySet())
        assertTrue(due.isEmpty())
    }

    @Test
    fun `the last half hour is announced with the minutes left`() {
        val due = ReminderPlan.shifts(listOf(open("احمد", 7 * hour + 31 * min)), now, emptySet())
        assertEquals(1, due.size)
        assertTrue(due[0].text, due[0].text.contains("«احمد»"))
        assertTrue(due[0].text, due[0].text.contains("۲۹ دقیقهٔ دیگر"))
        assertEquals(ShiftPolicy.WARN_AFTER_MS, 7 * hour + 30 * min)
    }

    @Test
    fun `a shift past eight hours says it is over, not minutes left`() {
        val due = ReminderPlan.shifts(listOf(open("محمود", 9 * hour)), now, emptySet())
        assertTrue(due.single().text, due.single().text.contains("تمام شده"))
    }

    @Test
    fun `each shift is announced once, and a closed shift never`() {
        val r = open("احمد", 7 * hour + 40 * min)
        val first = ReminderPlan.shifts(listOf(r), now, emptySet())
        assertEquals(1, first.size)
        assertTrue(ReminderPlan.shifts(listOf(r), now + min, setOf(first[0].key)).isEmpty())
        assertTrue(ReminderPlan.shifts(listOf(open("کریم", 8 * hour, out = now - min)), now, emptySet()).isEmpty())
    }

    @Test
    fun `the same worker on a new day is a new shift`() {
        val today = open("احمد", 7 * hour + 40 * min)
        val yesterday = today.copy(checkIn = today.checkIn - 24 * hour)
        val keys = ReminderPlan.shifts(listOf(yesterday, today), now, emptySet()).map { it.key }
        assertEquals(2, keys.toSet().size)
    }

    private val tea = BreakTime(id = 3, title = "چای صبح", hour = 9, minute = 0)

    @Test
    fun `a break fires from its minute for ten minutes`() {
        assertTrue(ReminderPlan.breaks(listOf(tea), 8 * 60 + 59, 1, 4, emptySet()).isEmpty())
        assertEquals(1, ReminderPlan.breaks(listOf(tea), 9 * 60, 1, 4, emptySet()).size)
        assertEquals(1, ReminderPlan.breaks(listOf(tea), 9 * 60 + ReminderPlan.BREAK_GRACE_MIN - 1, 1, 4, emptySet()).size)
        assertTrue(ReminderPlan.breaks(listOf(tea), 9 * 60 + ReminderPlan.BREAK_GRACE_MIN, 1, 4, emptySet()).isEmpty())
    }

    @Test
    fun `a break fires once a day and not when switched off`() {
        val d = ReminderPlan.breaks(listOf(tea), 9 * 60 + 2, 7, 4, emptySet()).single()
        assertTrue(ReminderPlan.breaks(listOf(tea), 9 * 60 + 3, 7, 4, setOf(d.key)).isEmpty())
        assertEquals(1, ReminderPlan.breaks(listOf(tea), 9 * 60 + 3, 8, 4, setOf(d.key)).size)
        assertTrue(ReminderPlan.breaks(listOf(tea.copy(enabled = false)), 9 * 60, 7, 4, emptySet()).isEmpty())
    }

    @Test
    fun `the break text says how many are inside, as the phone does`() {
        val d = ReminderPlan.breaks(listOf(tea), 9 * 60, 1, 4, emptySet()).single()
        assertEquals("چای صبح", d.title)
        assertEquals("۰۹:۰۰ — ۴ نفر داخلِ کارگاه‌اند.", d.text)
        val empty = ReminderPlan.breaks(listOf(tea), 9 * 60, 1, 0, emptySet()).single()
        assertEquals("۰۹:۰۰ — کسی ورود نزده است.", empty.text)
    }
}
