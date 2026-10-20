package com.afghanjama

import com.afghanjama.lan.DeviceKind
import com.afghanjama.lan.LanPeers
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.vm.phonesSeenText
import com.afghanjama.ui.vm.phonesToday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «امروز چند گوشی به این کامپیوتر رسید؟» — شمارِ کاشیِ صفحهٔ اول و کارتِ
 * دستگاهِ اصلی.
 *
 * **چیزی که این آزمون نگه می‌دارد:** هر گوشی یک بار شمرده شود هر چند بار
 * هم که بیاید، «امروز» از نیمه‌شبِ همین دستگاه باشد نه از بیست‌وچهار ساعتِ
 * پیش، و جدول بی‌سقف بزرگ نشود.
 */
class LanPeersTest {

    // امروز، ساعتِ ۱۰ صبح به وقتِ همین دستگاه.
    private val today = PersianDate.todayJalali(System.currentTimeMillis())
    private val midnight = PersianDate.startOfJalaliDay(today[0], today[1], today[2])
    private var now = midnight + 10 * 3_600_000L
    private val peers = LanPeers(clock = { now })

    @Test
    fun `nothing has reached the hub yet`() {
        assertEquals(0, phonesToday(peers.seen.value, now))
        assertNull(peers.seen.value.lastAt)
        val text = phonesSeenText(peers.seen.value, DeviceKind.COMPUTER, now)
        assertTrue(text, text.contains("هیچ گوشی‌ای به این کامپیوتر نرسیده"))
        assertTrue("the computer points at its firewall", text.contains("دیوارِ آتش"))
        assertFalse(
            "a phone hub has no firewall to blame",
            phonesSeenText(peers.seen.value, DeviceKind.PHONE, now).contains("دیوارِ آتش")
        )
    }

    @Test
    fun `a phone that comes back many times is still one phone`() {
        repeat(20) {
            peers.accepted("192.168.1.23")
            now += 15_000 // تابلوی دیواری هر ۱۵ ثانیه
        }
        peers.accepted("192.168.1.40")
        assertEquals(2, phonesToday(peers.seen.value, now))
        assertEquals(now, peers.seen.value.lastAt)
        val text = phonesSeenText(peers.seen.value, DeviceKind.COMPUTER, now)
        assertTrue(text, text.contains("۲ گوشی به این کامپیوتر وصل شد"))
        assertTrue(text, text.contains(PersianDate.time(now)))
    }

    @Test
    fun `today starts at this device's midnight, not twenty-four hours ago`() {
        now = midnight - 60_000 // دیشب ساعتِ ۲۳:۵۹
        peers.accepted("192.168.1.23")
        now = midnight + 8 * 3_600_000L
        assertEquals("yesterday's phone is not today's", 0, phonesToday(peers.seen.value, now))
        peers.accepted("192.168.1.23")
        assertEquals(1, phonesToday(peers.seen.value, now))
    }

    @Test
    fun `the table is capped and the oldest phone goes first`() {
        val small = LanPeers(clock = { now }, cap = 3)
        listOf("10.0.0.1", "10.0.0.2", "10.0.0.3").forEach {
            small.accepted(it)
            now += 1_000
        }
        small.accepted("10.0.0.1") // دوباره آمد؛ حالا تازه‌ترین است
        now += 1_000
        small.accepted("10.0.0.4")
        val kept = small.seen.value.lastOk.keys
        assertEquals(setOf("10.0.0.1", "10.0.0.3", "10.0.0.4"), kept)
    }
}
