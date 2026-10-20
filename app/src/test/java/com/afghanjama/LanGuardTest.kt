package com.afghanjama

import com.afghanjama.lan.LanGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * سقفِ رمزِ نادرست و مقایسهٔ رمز با زمانِ ثابت.
 *
 * **چیزی که این آزمون نگه می‌دارد:** رمزِ اتصالِ شش‌رقمی تا ۱.۹.۱ هیچ
 * سقفی برای حدس نداشت. حالا هر نشانی در هر دقیقه ده رمزِ نادرست دارد و
 * بعد پنج دقیقه بسته است — حتی با رمزِ درست.
 */
class LanGuardTest {

    private var now = 1_000_000L
    private val guard = LanGuard(clock = { now })
    private val phone = "192.168.1.23"

    @Test
    fun `nine wrong codes leave the phone open, the tenth closes it for five minutes`() {
        repeat(9) { assertFalse("failure ${it + 1} must not block", guard.failed(phone)) }
        assertEquals(0L, guard.blockedFor(phone))
        assertTrue("the tenth failure blocks", guard.failed(phone))
        assertEquals(LanGuard.BLOCK_MS, guard.blockedFor(phone))

        now += LanGuard.BLOCK_MS - 1
        assertEquals(1L, guard.blockedFor(phone))
        now += 1
        assertEquals(0L, guard.blockedFor(phone))
    }

    @Test
    fun `wrong codes spread over more than a minute never add up to a block`() {
        // تابلوی دیواری با رمزِ کهنه: هر ۱۵ ثانیه یک بار، یک ساعت تمام.
        repeat(240) {
            assertFalse(guard.failed(phone))
            now += 15_000
        }
        assertEquals(0L, guard.blockedFor(phone))
    }

    @Test
    fun `the window slides - ten failures inside any sixty seconds block`() {
        repeat(5) { guard.failed(phone) }
        now += 59_000
        repeat(4) { assertFalse(guard.failed(phone)) }
        // پنج تای اول هنوز در همان شصت ثانیه‌اند: این دهمی است.
        assertTrue(guard.failed(phone))
    }

    @Test
    fun `a right code clears the count, a blocked phone stays blocked`() {
        repeat(9) { guard.failed(phone) }
        guard.succeeded(phone)
        assertFalse("the count started again after a right code", guard.failed(phone))

        repeat(9) { guard.failed(phone) }
        assertTrue(guard.blockedFor(phone) > 0)
        guard.succeeded(phone)
        assertTrue("a right code must not lift a block", guard.blockedFor(phone) > 0)
    }

    @Test
    fun `one phone's block does not touch another`() {
        repeat(10) { guard.failed(phone) }
        assertTrue(guard.blockedFor(phone) > 0)
        assertEquals(0L, guard.blockedFor("192.168.1.24"))
    }

    @Test
    fun `a new code lifts every block`() {
        repeat(10) { guard.failed(phone) }
        guard.reset()
        assertEquals(0L, guard.blockedFor(phone))
    }

    @Test
    fun `the table stays bounded and forgets blocked phones last`() {
        val small = LanGuard(clock = { now }, maxPeers = 4)
        repeat(10) { small.failed("10.0.0.1") }
        // سه نشانیِ دیگر و بعد پنجمی — جا نیست؛ نشانیِ بسته نباید برود.
        listOf("10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5").forEach {
            now += 1
            small.failed(it)
        }
        assertTrue("the blocked phone was forgotten to make room", small.blockedFor("10.0.0.1") > 0)
    }

    @Test
    fun `guesses arriving together are judged one after another - exactly ten wrong, the rest shut out`() {
        val threads = 64
        val gate = java.util.concurrent.CountDownLatch(1)
        val verdicts = java.util.concurrent.ConcurrentLinkedQueue<LanGuard.Verdict>()
        val pool = List(threads) {
            Thread {
                gate.await()
                verdicts += guard.admit(phone, codeOk = false)
            }.apply { start() }
        }
        gate.countDown()
        pool.forEach { it.join() }
        assertEquals(LanGuard.MAX_FAILURES, verdicts.count { it == LanGuard.Verdict.Wrong })
        assertEquals(threads - LanGuard.MAX_FAILURES, verdicts.count { it is LanGuard.Verdict.Blocked })
    }

    @Test
    fun `admit - a right code passes and clears the count, but never lifts a block`() {
        repeat(9) { assertEquals(LanGuard.Verdict.Wrong, guard.admit(phone, codeOk = false)) }
        assertEquals(LanGuard.Verdict.Pass, guard.admit(phone, codeOk = true))
        assertEquals("the count started again", LanGuard.Verdict.Wrong, guard.admit(phone, codeOk = false))
        repeat(9) { guard.admit(phone, codeOk = false) }
        val v = guard.admit(phone, codeOk = true)
        assertTrue("the right code got through a block: $v", v is LanGuard.Verdict.Blocked)
    }

    @Test
    fun `only IPv4 peers get a key - IPv6 can take a fresh address for every ten guesses`() {
        fun addr(vararg b: Int) = java.net.InetAddress.getByAddress(ByteArray(b.size) { b[it].toByte() })
        assertEquals("192.168.1.23", LanGuard.peerKey(addr(192, 168, 1, 23)))
        val mapped = IntArray(16).also { it[10] = 0xFF; it[11] = 0xFF; it[12] = 192; it[13] = 168; it[14] = 1; it[15] = 23 }
        assertEquals("an IPv4 wrapped in IPv6 is the same phone", "192.168.1.23", LanGuard.peerKey(addr(*mapped)))
        val linkLocal = IntArray(16).also { it[0] = 0xFE; it[1] = 0x80; it[15] = 7 }
        assertEquals(null, LanGuard.peerKey(addr(*linkLocal)))
        assertEquals(null, LanGuard.peerKey(null))
    }

    @Test
    fun `codes are compared whole, and an empty code never matches`() {
        assertTrue(LanGuard.sameCode("482913", "482913"))
        assertFalse(LanGuard.sameCode("482914", "482913"))
        assertFalse(LanGuard.sameCode("48291", "482913"))
        assertFalse(LanGuard.sameCode("4829130", "482913"))
        assertFalse(LanGuard.sameCode("", "482913"))
        assertFalse("a hub with no code accepts nothing", LanGuard.sameCode("", ""))
    }

    @Test
    fun `the blocked message names the minutes left in Dari digits`() {
        assertTrue(LanGuard.blockedMessage(LanGuard.BLOCK_MS).contains("۵ دقیقه"))
        assertTrue(LanGuard.blockedMessage(1).contains("۱ دقیقه"))
        assertTrue(LanGuard.blockedMessage(60_001).contains("۲ دقیقه"))
    }
}
