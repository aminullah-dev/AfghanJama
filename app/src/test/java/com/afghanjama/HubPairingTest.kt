package com.afghanjama

import com.afghanjama.data.entities.LegacyMode
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.entities.clockSuspect
import com.afghanjama.lan.HubEnvelope
import com.afghanjama.lan.HubPairing
import com.afghanjama.lan.HubProtocol
import com.afghanjama.lan.HubRole
import com.afghanjama.lan.LanJson
import com.afghanjama.lan.LanPrims
import com.afghanjama.lan.PairGrant
import com.afghanjama.lan.PairState
import com.afghanjama.lan.PairVia
import com.afghanjama.lan.PairingV2
import com.afghanjama.lan.PhoneInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * پنجرهٔ «افزودنِ گوشی»: ده دقیقه، یک گوشی، ده رمزِ نادرست و بسته؛ و دو
 * قاعدهٔ کوچکِ دیگرِ فازِ B (گوشی‌های قدیم، ساعتِ گوشیِ نامعتبر).
 */
class HubPairingTest {

    private var now = 1_760_000_000_000L
    private val pairing = HubPairing(clock = { now })
    private val hubId = "hub-0001-aaaa"
    private val worker = PhoneInfo("گوشیِ احمد", "android", "1.11.0", HubRole.WORKER)

    private fun open(allowCode: Boolean = true) = pairing.open("192.168.1.7", 8797, hubId, allowCode)

    private fun request(via: PairVia, info: PhoneInfo = worker, secret: ByteArray? = null): Map<String, Any?> {
        val view = pairing.view.value
        val link = PairingV2.parse(view.qrText)!!
        val s = secret ?: if (via == PairVia.QR) link.secret else view.code.encodeToByteArray()
        val hello = pairing.hello(hubId)!!
        return LanJson.parseObject(HubEnvelope.pairRequest(hello, via, s, info).body)!!
    }

    @Test
    fun `nothing is offered while the window is shut`() {
        assertNull(pairing.hello(hubId))
        assertFalse(pairing.isOpen())
        assertEquals(HubPairing.Submit.Closed, pairing.submit(mapOf("pairingId" to "x"), "192.168.1.20"))
    }

    @Test
    fun `the window shows a QR for this hub and a twelve-digit code, and lasts ten minutes`() {
        val v = open()
        val link = PairingV2.parse(v.qrText)!!
        assertEquals(hubId, link.hubId)
        assertEquals("192.168.1.7", link.host)
        assertEquals(12, v.code.length)
        assertTrue(pairing.isOpen())
        assertFalse("secrets in a printed view", v.toString().contains(v.code))
        now += HubProtocol.PAIR_WINDOW_MS - 1
        assertNotNull(pairing.hello(hubId))
        now += 1
        assertNull(pairing.hello(hubId))
        assertEquals(HubPairing.EXPIRED, pairing.view.value.closedReason)
    }

    @Test
    fun `a new window means the old QR no longer works`() {
        open()
        val old = request(PairVia.QR)
        open()
        assertTrue(pairing.submit(old, "192.168.1.20") is HubPairing.Submit.Wrong)
    }

    @Test
    fun `ten wrong codes switch the manual code off, but the window and its QR stay`() {
        open()
        repeat(HubProtocol.PAIR_MAX_FAILURES - 1) {
            assertEquals(HubPairing.Submit.Wrong(codeShut = false), pairing.submit(request(PairVia.CODE, secret = "000000000000".encodeToByteArray()), "192.168.1.20"))
        }
        assertEquals(HubPairing.Submit.Wrong(codeShut = true), pairing.submit(request(PairVia.CODE, secret = "000000000000".encodeToByteArray()), "192.168.1.20"))
        // پنجره باز است و صفحه می‌گوید چرا رمزِ دستی خاموش شد.
        assertTrue(pairing.isOpen())
        assertFalse(pairing.view.value.allowCode)
        assertEquals(HubPairing.TOO_MANY, pairing.view.value.codeNote)
        // حتی رمزِ درست دیگر نمی‌گذرد — حدسِ یازدهم هیچ نمی‌سنجد.
        assertEquals(HubPairing.Submit.Wrong(codeShut = false), pairing.submit(request(PairVia.CODE), "192.168.1.20"))
        // ولی QR هنوز جفت می‌کند.
        assertTrue(pairing.submit(request(PairVia.QR), "192.168.1.21") is HubPairing.Submit.Ok)
    }

    @Test
    fun `nobody on the Wi-Fi can shut the window with wrong packets`() {
        open()
        repeat(10 * HubProtocol.PAIR_MAX_FAILURES) {
            assertEquals(HubPairing.Submit.Wrong(codeShut = false), pairing.submit(request(PairVia.QR, secret = LanPrims.random(16)), "192.168.1.66"))
            assertEquals(HubPairing.Submit.Wrong(codeShut = false), pairing.submit(mapOf("pairingId" to "junk", "via" to "qr"), "192.168.1.66"))
        }
        assertTrue(pairing.isOpen())
        assertTrue("the code survives QR noise", pairing.view.value.allowCode)
        assertTrue(pairing.submit(request(PairVia.CODE), "192.168.1.20") is HubPairing.Submit.Ok)
    }

    @Test
    fun `the manual code can be switched off, and never brings the owner`() {
        open(allowCode = false)
        assertTrue(pairing.submit(request(PairVia.CODE), "192.168.1.20") is HubPairing.Submit.Wrong)
        open()
        assertEquals(HubPairing.Submit.OwnerNeedsQr, pairing.submit(request(PairVia.CODE, worker.copy(wantRole = HubRole.OWNER)), "192.168.1.20"))
        assertTrue(pairing.submit(request(PairVia.QR, worker.copy(wantRole = HubRole.OWNER)), "192.168.1.20") is HubPairing.Submit.Ok)
    }

    @Test
    fun `one phone at a time, one phone per window`() = runBlocking {
        open()
        val first = pairing.submit(request(PairVia.QR), "192.168.1.20") as HubPairing.Submit.Ok
        assertEquals(HubPairing.Submit.Busy, pairing.submit(request(PairVia.QR), "192.168.1.21"))
        assertEquals("گوشیِ احمد", pairing.view.value.waiting?.name)
        assertNull(pairing.await(first.requestId, 10))
        val grant = PairGrant(PairState.APPROVED, hubId, "کارگاه", now, "dev-00000001", LanPrims.random(32), HubRole.WORKER, "[T1] احمد")
        assertTrue(pairing.decide(first.requestId, HubPairing.Outcome.Approved(grant)))
        assertEquals(HubPairing.Outcome.Approved(grant), pairing.await(first.requestId, 10))
        // پس از «اجازه» پنجره درِ تازه را می‌بندد ولی جواب را برای همان گوشی نگه می‌دارد.
        assertNull(pairing.hello(hubId))
        assertFalse(pairing.isOpen())
        assertTrue(pairing.alive(first.requestId))
    }

    @Test
    fun `after a refusal the window takes another phone`() = runBlocking {
        open()
        val first = pairing.submit(request(PairVia.QR), "192.168.1.20") as HubPairing.Submit.Ok
        assertTrue(pairing.decide(first.requestId, HubPairing.Outcome.Rejected("نه")))
        assertEquals(HubPairing.Outcome.Rejected("نه"), pairing.await(first.requestId, 10))
        assertTrue(pairing.isOpen())
        assertTrue(pairing.submit(request(PairVia.QR), "192.168.1.21") is HubPairing.Submit.Ok)
    }

    // ---------------------------------------------------------------- گوشی‌های قدیم

    @Test
    fun `old phones are allowed only when asked, or within thirty days of the last one`() {
        val day = 86_400_000L
        assertFalse("a hub that never saw one", LegacyMode.allowed(LegacyMode.AUTO, null, now))
        assertTrue(LegacyMode.allowed(LegacyMode.AUTO, now - 29 * day, now))
        assertFalse(LegacyMode.allowed(LegacyMode.AUTO, now - 30 * day, now))
        assertTrue(LegacyMode.allowed(LegacyMode.ON, null, now))
        assertFalse(LegacyMode.allowed(LegacyMode.OFF, now, now))
        assertEquals(LegacyMode.AUTO, LegacyMode.of("garbage"))
    }

    // ---------------------------------------------------------------- ساعتِ گوشی

    @Test
    fun `only a protocol 2 attendance press outside the window is flagged`() {
        val base = SyncRequest(deviceName = "x", worker = "[T1] احمد", type = "ATTENDANCE_IN", summary = "ورود", createdAt = now)
        assertFalse("protocol 1 never sends a phone time", base.clockSuspect())
        val v2 = base.copy(protocol = 2, receivedAt = now, clientAt = now - 2 * 3_600_000L)
        assertFalse(v2.clockSuspect())
        assertTrue(v2.copy(clientAt = now - 13 * 3_600_000L).clockSuspect())
        assertTrue("no usable phone time", v2.copy(clientAt = null).clockSuspect())
        assertFalse("a note has no clock", v2.copy(type = "NOTE", clientAt = null).clockSuspect())
    }
}
