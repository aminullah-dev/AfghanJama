package com.afghanjama

import com.afghanjama.lan.HubEnvelope
import com.afghanjama.lan.HubProtocol
import com.afghanjama.lan.HubRole
import com.afghanjama.lan.LanCrypto
import com.afghanjama.lan.LanJson
import com.afghanjama.lan.LanPrims
import com.afghanjama.lan.PairGrant
import com.afghanjama.lan.PairHello
import com.afghanjama.lan.PairState
import com.afghanjama.lan.PairVia
import com.afghanjama.lan.PhoneInfo
import com.afghanjama.lan.jStr
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * پاکت‌های قراردادِ ۲ از هر دو سو — بی شبکه، فقط بایت.
 *
 * هر بند یک راهِ حمله است که باید بسته باشد: رازِ نادرست، کلیدِ عمومیِ
 * عوض‌شده وسطِ راه، جوابِ کهنه برای پرسشِ تازه، درخواستی که سرخطش عوض
 * شده، و پیامی که از یک جهت برداشته و در جهتِ دیگر گذاشته شود.
 */
class HubEnvelopeTest {

    private val hubPair = LanPrims.p256KeyPair()
    private val pairingId = "PAIRING0001"
    private val hello = PairHello("hub-0001-aaaa", pairingId, hubPair.publicX963, 600_000)
    private val qrSecret = LanPrims.random(16)
    private val code = "482913572046".encodeToByteArray()
    private val info = PhoneInfo("گوشیِ احمد", "android", "1.11.0", HubRole.WORKER)

    private fun secrets(v: PairVia): ByteArray? = when (v) {
        PairVia.QR -> qrSecret
        PairVia.CODE -> code
    }

    private fun obj(s: String) = LanJson.parseObject(s)!!

    @Test
    fun `a phone with the QR secret pairs, and both sides hold the same key`() {
        val draft = HubEnvelope.pairRequest(hello, PairVia.QR, qrSecret, info)
        val opened = HubEnvelope.openPairRequest(obj(draft.body), pairingId, hubPair, ::secrets)
        assertNotNull(opened)
        assertEquals(info, opened!!.info)
        assertEquals(PairVia.QR, opened.via)
        assertArrayEquals(draft.key, opened.key)
    }

    @Test
    fun `the manual code pairs too, through its own secret`() {
        val draft = HubEnvelope.pairRequest(hello, PairVia.CODE, code, info)
        assertNotNull(HubEnvelope.openPairRequest(obj(draft.body), pairingId, hubPair, ::secrets))
    }

    @Test
    fun `a wrong secret, a wrong window or the other route opens nothing`() {
        val wrong = HubEnvelope.pairRequest(hello, PairVia.QR, LanPrims.random(16), info)
        assertNull("wrong QR secret", HubEnvelope.openPairRequest(obj(wrong.body), pairingId, hubPair, ::secrets))
        val wrongCode = HubEnvelope.pairRequest(hello, PairVia.CODE, "000000000000".encodeToByteArray(), info)
        assertNull("wrong code", HubEnvelope.openPairRequest(obj(wrongCode.body), pairingId, hubPair, ::secrets))
        val good = HubEnvelope.pairRequest(hello, PairVia.QR, qrSecret, info)
        assertNull("another window", HubEnvelope.openPairRequest(obj(good.body), "OTHERWINDOW", hubPair, ::secrets))
        // «via» را از qr به code عوض کن: رازِ دیگری سنجیده می‌شود و AAD هم فرق دارد.
        val swapped = obj(good.body).toMutableMap().also { it["via"] = "code" }
        assertNull("route swapped", HubEnvelope.openPairRequest(swapped, pairingId, hubPair, ::secrets))
        // پنجره‌ای که رمزِ دستی ندارد.
        assertNull(
            "route closed",
            HubEnvelope.openPairRequest(obj(HubEnvelope.pairRequest(hello, PairVia.CODE, code, info).body), pairingId, hubPair) {
                if (it == PairVia.QR) qrSecret else null
            },
        )
    }

    @Test
    fun `someone in the middle who swaps the hub key cannot read or forge, even knowing the secret`() {
        // میانجی کلیدِ خودش را به‌جای کلیدِ کامپیوتر به گوشی می‌دهد.
        val mallory = LanPrims.p256KeyPair()
        val fake = hello.copy(hubPub = mallory.publicX963)
        val draft = HubEnvelope.pairRequest(fake, PairVia.QR, qrSecret, info)
        // کامپیوترِ واقعی آن را باز نمی‌کند: ECDH با کلیدِ دیگری ساخته شده.
        assertNull(HubEnvelope.openPairRequest(obj(draft.body), pairingId, hubPair, ::secrets))
        // و میانجی بی رازِ QR هم باز نمی‌کند، حتی با کلیدِ خصوصیِ خودش.
        assertNull(HubEnvelope.openPairRequest(obj(draft.body), pairingId, mallory) { LanPrims.random(16) })
    }

    @Test
    fun `the phone's public key is bound into the key - replacing it breaks the box`() {
        val draft = HubEnvelope.pairRequest(hello, PairVia.QR, qrSecret, info)
        val other = obj(draft.body).toMutableMap().also { it["phonePub"] = LanCrypto.b64(LanPrims.p256KeyPair().publicX963) }
        assertNull(HubEnvelope.openPairRequest(other, pairingId, hubPair, ::secrets))
    }

    @Test
    fun `the approval reply opens only for the ask it answers`() {
        val key = LanPrims.random(32)
        val ask1 = HubEnvelope.pairStatusAsk("REQ1", key, 1L)
        assertNotNull(HubEnvelope.openPairStatus(obj(ask1.body), "REQ1", key))
        assertNull("another request id", HubEnvelope.openPairStatus(obj(ask1.body), "REQ2", key))
        assertNull("another key", HubEnvelope.openPairStatus(obj(ask1.body), "REQ1", LanPrims.random(32)))

        val grant = PairGrant(
            PairState.APPROVED, "hub-0001-aaaa", "کارگاه", 5L, "dev-00000001", LanPrims.random(32),
            HubRole.WORKER, "[T1] احمد", listOf("192.168.1.7"),
        )
        val reply = HubEnvelope.pairReply(grant, "REQ1", key, ask1.nonce)
        assertEquals(grant, HubEnvelope.openPairReply(obj(reply), "REQ1", key, ask1.nonce))

        // جوابِ همان پرسشِ کهنه به پرسشِ تازه نمی‌خورد.
        val ask2 = HubEnvelope.pairStatusAsk("REQ1", key, 2L)
        assertNull("old reply for a new ask", HubEnvelope.openPairReply(obj(reply), "REQ1", key, ask2.nonce))
    }

    // ---------------------------------------------------------------- همگام‌سازی

    private val deviceId = "dev-00000001"
    private val deviceKey = LanPrims.random(32)
    private val path = HubProtocol.PATH_SYNC

    private fun head(s: HubEnvelope.Sealed) = HubEnvelope.readHead { s.headers[it] }!!

    @Test
    fun `a sealed request opens on the hub and the reply opens on the phone`() {
        val s = HubEnvelope.sealRequest(deviceId, deviceKey, path, 1_000L, mapOf("cursor" to "x", "متن" to "ورود"))
        val h = head(s)
        assertEquals(deviceId, h.deviceId)
        assertEquals(1_000L, h.time)
        val plain = HubEnvelope.openRequest(h, deviceKey, path, s.body)
        assertEquals("ورود", plain!!.jStr("متن"))
        val reply = HubEnvelope.sealResponse(h, deviceKey, path, mapOf("ok" to true, "n" to 7L))
        assertEquals(7L, HubEnvelope.openResponse(deviceId, deviceKey, path, s.nonce, reply)!!["n"])
    }

    @Test
    fun `changing any header, the path or the key breaks the request`() {
        val s = HubEnvelope.sealRequest(deviceId, deviceKey, path, 1_000L, mapOf("a" to 1L))
        val h = head(s)
        assertNull("time", HubEnvelope.openRequest(HubEnvelope.Head(h.deviceId, 1_001L, h.nonce), deviceKey, path, s.body))
        assertNull("device", HubEnvelope.openRequest(HubEnvelope.Head("dev-00000002", h.time, h.nonce), deviceKey, path, s.body))
        assertNull("nonce", HubEnvelope.openRequest(HubEnvelope.Head(h.deviceId, h.time, LanPrims.random(12)), deviceKey, path, s.body))
        assertNull("path", HubEnvelope.openRequest(h, deviceKey, "/v2/other", s.body))
        assertNull("key", HubEnvelope.openRequest(h, LanPrims.random(32), path, s.body))
        assertNull("body", HubEnvelope.openRequest(h, deviceKey, path, "{\"box\":\"AAAA\"}"))
        assertNull("not json", HubEnvelope.openRequest(h, deviceKey, path, "garbage"))
    }

    @Test
    fun `a reply is bound to its own request`() {
        val s1 = HubEnvelope.sealRequest(deviceId, deviceKey, path, 1_000L, emptyMap())
        val s2 = HubEnvelope.sealRequest(deviceId, deviceKey, path, 1_001L, emptyMap())
        val reply1 = HubEnvelope.sealResponse(head(s1), deviceKey, path, mapOf("ok" to true))
        assertNotNull(HubEnvelope.openResponse(deviceId, deviceKey, path, s1.nonce, reply1))
        assertNull("old reply for a new request", HubEnvelope.openResponse(deviceId, deviceKey, path, s2.nonce, reply1))
    }

    @Test
    fun `a message cannot be reflected - the hub's reply is not a valid request`() {
        val s = HubEnvelope.sealRequest(deviceId, deviceKey, path, 1_000L, emptyMap())
        val h = head(s)
        val reply = LanJson.parseObject(HubEnvelope.sealResponse(h, deviceKey, path, mapOf("ok" to true)))!!
        // جواب را به‌شکلِ درخواست برگردان: همان nonce، همان جعبه.
        val asRequest = HubEnvelope.Head(deviceId, 1_000L, LanCrypto.unb64(reply.jStr("nonce"))!!)
        assertNull(HubEnvelope.openRequest(asRequest, deviceKey, path, LanJson.write(mapOf("box" to reply.jStr("box")))))
    }

    @Test
    fun `malformed headers are refused before any key is looked up`() {
        assertNull(HubEnvelope.readHead { null })
        val ok = mapOf(
            HubProtocol.HEADER_DEVICE to deviceId,
            HubProtocol.HEADER_TIME to "1000",
            HubProtocol.HEADER_NONCE to LanCrypto.b64(ByteArray(12)),
        )
        assertNotNull(HubEnvelope.readHead { ok[it] })
        assertNull("short nonce", HubEnvelope.readHead { (ok + (HubProtocol.HEADER_NONCE to LanCrypto.b64(ByteArray(8))))[it] })
        assertNull("device id with a slash", HubEnvelope.readHead { (ok + (HubProtocol.HEADER_DEVICE to "../../etc"))[it] })
        assertNull("time not a number", HubEnvelope.readHead { (ok + (HubProtocol.HEADER_TIME to "soon"))[it] })
    }
}
