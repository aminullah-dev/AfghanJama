package com.afghanjama

import com.afghanjama.lan.HubProtocol
import com.afghanjama.lan.LanPrims
import com.afghanjama.lan.ReplayGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تکرارِ درخواست: بازهٔ ده دقیقه و حافظهٔ بیست‌وپنج دقیقه‌ایِ nonce.
 */
class ReplayGuardTest {

    private var now = 10_000_000_000L
    private val guard = ReplayGuard(clock = { now })
    private val phone = "dev-00000001"

    @Test
    fun `the time window is ten minutes each way, inclusive`() {
        val w = HubProtocol.TIME_WINDOW_MS
        assertEquals(ReplayGuard.Verdict.Ok, guard.timeOk(now - w))
        assertEquals(ReplayGuard.Verdict.Ok, guard.timeOk(now + w))
        assertEquals(ReplayGuard.Verdict.Stale(now), guard.timeOk(now - w - 1))
        assertEquals(ReplayGuard.Verdict.Stale(now), guard.timeOk(now + w + 1))
    }

    @Test
    fun `a nonce is accepted once`() {
        val n = LanPrims.random(12)
        assertEquals(ReplayGuard.Verdict.Ok, guard.admit(phone, n, now))
        assertEquals(ReplayGuard.Verdict.Replay, guard.admit(phone, n, now))
        assertEquals(ReplayGuard.Verdict.Replay, guard.admit(phone, n.copyOf(), now + 1_000))
    }

    @Test
    fun `the same nonce from another device is that device's own business`() {
        val n = LanPrims.random(12)
        assertEquals(ReplayGuard.Verdict.Ok, guard.admit(phone, n, now))
        assertEquals(ReplayGuard.Verdict.Ok, guard.admit("dev-00000002", n, now))
    }

    /**
     * کهنه‌ترین حالت: درخواستی که ساعتش ده دقیقه جلو بود، تا بیست دقیقه بعد
     * هنوز در بازه است. nonceش باید تا آن وقت به یاد بماند.
     */
    @Test
    fun `a nonce is remembered for as long as its request stays inside the window`() {
        val n = LanPrims.random(12)
        val sent = now + HubProtocol.TIME_WINDOW_MS
        assertEquals(ReplayGuard.Verdict.Ok, guard.admit(phone, n, sent))
        now += 2 * HubProtocol.TIME_WINDOW_MS
        assertEquals("still in the window at minute 20", ReplayGuard.Verdict.Ok, guard.timeOk(sent))
        assertEquals("replayed at minute 20", ReplayGuard.Verdict.Replay, guard.admit(phone, n, sent))
        // پس از آن هم دیگر در بازه نیست؛ فراموش شدنِ nonce دری باز نمی‌کند.
        now += HubProtocol.NONCE_KEEP_MS
        assertTrue(guard.admit(phone, n, sent) is ReplayGuard.Verdict.Stale)
    }

    @Test
    fun `a full memory refuses rather than forgetting old nonces`() {
        val small = ReplayGuard(clock = { now }, maxPerDevice = 3)
        repeat(3) { assertEquals(ReplayGuard.Verdict.Ok, small.admit(phone, LanPrims.random(12), now)) }
        assertEquals(ReplayGuard.Verdict.Full, small.admit(phone, LanPrims.random(12), now))
        now += HubProtocol.NONCE_KEEP_MS + 1
        assertEquals("room again once the old ones expire", ReplayGuard.Verdict.Ok, small.admit(phone, LanPrims.random(12), now))
    }

    @Test
    fun `forgetting a revoked device clears its memory`() {
        val n = LanPrims.random(12)
        guard.admit(phone, n, now)
        guard.forget(phone)
        assertEquals(ReplayGuard.Verdict.Ok, guard.admit(phone, n, now))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `memory shorter than the window is refused at construction`() {
        ReplayGuard(clock = { now }, windowMs = 600_000, keepMs = 900_000)
    }
}
