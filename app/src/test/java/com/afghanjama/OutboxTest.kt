package com.afghanjama

import com.afghanjama.lan.Ack
import com.afghanjama.lan.AckStatus
import com.afghanjama.lan.AttendanceClock
import com.afghanjama.lan.Outbox
import com.afghanjama.lan.OutboxItem
import com.afghanjama.lan.OutboxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * صفِ ارسالِ گوشی — تلاشِ دوباره، فاصله‌ها، کهنه شدن، و اینکه هیچ کاری
 * بی‌صدا گم نمی‌شود.
 */
class OutboxTest {

    private val t0 = 1_760_000_000_000L

    private fun item(key: String, at: Long = t0, person: String = "[T1] احمد") =
        OutboxItem(key, "ATTENDANCE_IN", emptyMap(), "ورود", at, "dev-00000001", person)

    @Test
    fun `backoff grows from five seconds to five minutes and stays there`() {
        assertEquals(5_000L, Outbox.backoff(1))
        assertEquals(15_000L, Outbox.backoff(2))
        assertEquals(30_000L, Outbox.backoff(3))
        assertEquals(60_000L, Outbox.backoff(4))
        assertEquals(120_000L, Outbox.backoff(5))
        assertEquals(300_000L, Outbox.backoff(6))
        assertEquals(300_000L, Outbox.backoff(60))
        assertEquals(5_000L, Outbox.backoff(0))
    }

    @Test
    fun `a failed send waits, a network change sends again at once`() {
        var q = listOf(item("a-000001"), item("b-000001", t0 + 1))
        assertEquals(2, Outbox.due(q, t0 + 1).size)
        q = Outbox.failed(q, setOf("a-000001"), t0 + 10)
        assertEquals(listOf("b-000001"), Outbox.due(q, t0 + 10).map { it.key })
        assertEquals(1, q.first { it.key == "a-000001" }.attempts)
        assertEquals(2, Outbox.due(q, t0 + 10 + 5_000).size)
        q = Outbox.failed(q, setOf("a-000001"), t0 + 20)
        assertEquals(t0 + 20 + 15_000, q.first { it.key == "a-000001" }.nextTryAt)
        q = Outbox.retryNow(q, t0 + 30)
        assertEquals(2, Outbox.due(q, t0 + 30).size)
    }

    @Test
    fun `the oldest press goes first, and no more than fifty at a time`() {
        val q = (0 until 70).map { item("k-%06d".format(it), t0 + (70 - it)) }
        val due = Outbox.due(q, t0 + 100)
        assertEquals(50, due.size)
        assertEquals("k-000069", due.first().key)
    }

    @Test
    fun `every answer lands somewhere visible`() {
        var q = listOf(item("q-000001"), item("p-000001"), item("d-000001"), item("r-000001"), item("n-000001"))
        q = Outbox.acked(
            q,
            listOf(
                Ack("q-000001", AckStatus.QUEUED),
                Ack("p-000001", AckStatus.APPLIED),
                Ack("d-000001", AckStatus.DUPLICATE),
                Ack("r-000001", AckStatus.REJECTED, "این کار قبلاً تحویل شده."),
            ),
            t0 + 5,
        )
        val by = q.associateBy { it.key }
        assertEquals(OutboxState.SENT, by["q-000001"]!!.state)
        assertEquals("رسید — منتظرِ تأییدِ کارفرما", Outbox.label(by["q-000001"]!!))
        assertEquals("انجام شد", Outbox.label(by["p-000001"]!!))
        assertEquals("رسیده بود", Outbox.label(by["d-000001"]!!))
        assertEquals(OutboxState.REJECTED, by["r-000001"]!!.state)
        assertEquals("رد شد: این کار قبلاً تحویل شده.", Outbox.label(by["r-000001"]!!))
        // جوابی نیامد: هنوز منتظر است و دوباره می‌رود.
        assertEquals(OutboxState.PENDING, by["n-000001"]!!.state)
        assertEquals(1, Outbox.pendingCount(q))
    }

    @Test
    fun `after seven days an unsent press becomes «sent nowhere - too old», never silently dropped`() {
        var q = listOf(item("a-000001", t0), item("b-000001", t0 + 1_000))
        q = Outbox.expire(q, t0 + Outbox.EXPIRE_MS)
        assertEquals("exactly seven days is still pending", 2, Outbox.pendingCount(q))
        q = Outbox.expire(q, t0 + Outbox.EXPIRE_MS + 1)
        val a = q.first { it.key == "a-000001" }
        assertEquals(OutboxState.EXPIRED, a.state)
        assertTrue(Outbox.label(a).startsWith("فرستاده نشد — کهنه شد"))
        assertEquals(OutboxState.PENDING, q.first { it.key == "b-000001" }.state)
    }

    @Test
    fun `re-pairing keeps the same person's queue and orphans someone else's`() {
        val q = listOf(item("a-000001", person = "[T1] احمد"), item("b-000001", person = "[T2] محمود"))
        val after = Outbox.rebind(q, "dev-00000009", "[T1] احمد", t0 + 1)
        val a = after.first { it.key == "a-000001" }
        assertEquals(OutboxState.PENDING, a.state)
        assertEquals("dev-00000009", a.deviceId)
        val b = after.first { it.key == "b-000001" }
        assertEquals(OutboxState.ORPHANED, b.state)
        assertEquals(Outbox.ORPHANED_REASON, Outbox.label(b))
    }

    @Test
    fun `adding twice is one item, and a full queue refuses loudly`() {
        val (once, _) = Outbox.add(emptyList(), item("a-000001"))
        val (twice, err) = Outbox.add(once, item("a-000001"))
        assertEquals(1, twice.size)
        assertNull(err)
        val full = (0 until Outbox.MAX_PENDING).map { item("f-%06d".format(it)) }
        val (still, why) = Outbox.add(full, item("x-000001"))
        assertEquals(Outbox.MAX_PENDING, still.size)
        assertEquals(Outbox.FULL_REASON, why)
    }

    @Test
    fun `finished items are trimmed to the newest fifty, pending ones never`() {
        val done = (0 until 80).map { item("d-%06d".format(it)).copy(state = OutboxState.SENT, doneAt = t0 + it) }
        val pending = (0 until 5).map { item("p-%06d".format(it)) }
        val trimmed = Outbox.trim(done + pending)
        assertEquals(55, trimmed.size)
        assertTrue(trimmed.any { it.key == "d-000079" })
        assertTrue(trimmed.none { it.key == "d-000000" })
        assertEquals(5, Outbox.pendingCount(trimmed))
        assertEquals(5, Outbox.clearDone(trimmed).size)
    }

    @Test
    fun `the queue survives a save and a load, and a damaged file is reported, not emptied`() {
        val q = listOf(
            item("a-000001").copy(payload = mapOf("assignmentId" to 12L, "qty" to 5L), summary = "AJ-12 — ۵ عدد"),
            item("b-000001").copy(state = OutboxState.REJECTED, reason = "نه", ack = AckStatus.REJECTED, doneAt = t0),
        )
        assertEquals(q, Outbox.decode(Outbox.encode(q)))
        assertEquals(emptyList<OutboxItem>(), Outbox.decode(""))
        assertNull(Outbox.decode("[{\"key\":1}]"))
        assertNull(Outbox.decode("{not json"))
    }

    // ---------------------------------------------------------------- ساعتِ حضور

    @Test
    fun `a phone three minutes slow still records the moment of the press`() {
        val received = t0
        val phoneNow = received - 180_000 // ساعتِ گوشی سه دقیقه عقب است
        val pressedOnPhone = phoneNow - 3_600_000 // یک ساعت پیش، آفلاین
        val at = AttendanceClock.corrected(pressedOnPhone, phoneNow, received)
        assertEquals(received - 3_600_000, at)
        assertTrue(AttendanceClock.trusted(at, received))
    }

    @Test
    fun `the trusted window is twelve hours back and five minutes ahead of arrival`() {
        val r = t0
        assertTrue(AttendanceClock.trusted(r - AttendanceClock.PAST_MS, r))
        assertTrue(!AttendanceClock.trusted(r - AttendanceClock.PAST_MS - 1, r))
        assertTrue(AttendanceClock.trusted(r + AttendanceClock.FUTURE_MS, r))
        assertTrue(!AttendanceClock.trusted(r + AttendanceClock.FUTURE_MS + 1, r))
        assertTrue(!AttendanceClock.trusted(null, r))
    }

    @Test
    fun `a press from three days of being offline is flagged, not guessed`() {
        val r = t0
        val at = AttendanceClock.corrected(r - 3 * 86_400_000L, r, r)
        assertNotNull(at)
        assertTrue(!AttendanceClock.trusted(at, r))
    }

    @Test
    fun `nonsense times are refused rather than overflowing`() {
        assertNull(AttendanceClock.corrected(Long.MIN_VALUE, t0, t0))
        assertNull(AttendanceClock.corrected(t0, Long.MAX_VALUE, t0))
        assertNull(AttendanceClock.corrected(t0, t0, 0L))
    }
}
