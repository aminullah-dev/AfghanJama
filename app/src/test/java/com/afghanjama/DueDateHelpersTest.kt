package com.afghanjama

import com.afghanjama.ui.format.DUE_SOON_DAYS
import com.afghanjama.ui.format.dueDaysLate
import com.afghanjama.ui.format.dueDaysLeft
import com.afghanjama.ui.format.elapsedHm
import com.afghanjama.ui.format.isDueSoon
import com.afghanjama.ui.format.isOverdue
import com.afghanjama.ui.format.stageDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * مهلتِ سفارش و معطلیِ مرحله (`ui/format/Time.kt`) — پایهٔ نشانه‌های «تأخیر» و
 * «نزدیکِ مهلت» روی تابلو و صفحهٔ تحویل در هر سه سکو. «الان» همیشه داده
 * می‌شود تا آزمون به ساعتِ ماشین بستگی نداشته باشد.
 */
class DueDateHelpersTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L

    @Test
    fun `stage days count from the stage change, else from creation`() {
        assertEquals(2L, stageDays(stageChangedAt = now - 2 * day, createdAt = now - 10 * day, now = now))
        assertEquals(10L, stageDays(stageChangedAt = 0L, createdAt = now - 10 * day, now = now))
    }

    @Test
    fun `stage days round down and never go negative`() {
        assertEquals(1L, stageDays(now - day - day / 2, 0L, now))
        assertEquals(0L, stageDays(now - day + 1, 0L, now))
        // ساعتِ دستگاه عقب رفته: زمانِ شروع در آینده است.
        assertEquals(0L, stageDays(now + 5 * day, 0L, now))
    }

    @Test
    fun `no due date is never overdue or due soon`() {
        assertFalse(isOverdue(0L, now))
        assertFalse(isDueSoon(0L, now))
    }

    @Test
    fun `overdue starts only after the due moment`() {
        assertFalse(isOverdue(now, now))
        assertTrue(isOverdue(now - 1, now))
        assertFalse(isOverdue(now + 1, now))
    }

    @Test
    fun `days left round up so a part day still shows one day`() {
        assertEquals(3L, dueDaysLeft(now + 3 * day, now))
        assertEquals(1L, dueDaysLeft(now + 1, now))
        assertEquals(4L, dueDaysLeft(now + 3 * day + 1, now))
        assertEquals(0L, dueDaysLeft(now, now))
    }

    @Test
    fun `days left is zero once past due`() {
        assertEquals(0L, dueDaysLeft(now - 5 * day, now))
    }

    @Test
    fun `days late round down and are zero before the due date`() {
        assertEquals(2L, dueDaysLate(now - 2 * day - day / 2, now))
        assertEquals(0L, dueDaysLate(now - day / 2, now))
        assertEquals(0L, dueDaysLate(now + 3 * day, now))
    }

    @Test
    fun `due soon covers the threshold day but not the day after, and not overdue`() {
        assertEquals(3L, DUE_SOON_DAYS)
        assertTrue(isDueSoon(now + DUE_SOON_DAYS * day, now))
        assertFalse(isDueSoon(now + DUE_SOON_DAYS * day + 1, now))
        assertTrue(isDueSoon(now, now))
        assertFalse(isDueSoon(now - 1, now))
    }

    @Test
    fun `elapsed time shows hours and zero-padded minutes in Persian digits`() {
        val min = 60_000L
        assertEquals("۳:۲۵", elapsedHm(now - (3 * 60 + 25) * min, now))
        assertEquals("۰:۰۵", elapsedHm(now - 5 * min, now))
        assertEquals("۲۶:۰۰", elapsedHm(now - 26 * 60 * min, now))
    }

    @Test
    fun `elapsed time from the future is zero`() {
        assertEquals("۰:۰۰", elapsedHm(now + 60_000L, now))
    }
}
