package com.afghanjama

import com.afghanjama.lan.HubEnvelope
import com.afghanjama.lan.HubProtocol
import com.afghanjama.lan.LanCrypto
import com.afghanjama.lan.LanJson
import com.afghanjama.lan.LanPrims
import com.afghanjama.lan.jStr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * حمله‌های بازبینیِ فازِ B روی پاکت‌ها — هر لایه **جدا**.
 *
 * آزمون‌های `HubEnvelopeTest` بازتابِ پیام را با کلید و AADِ هر دو عوض‌شده
 * می‌سنجند؛ اگر یکی از دو لایه روزی بیفتد، دیگری هنوز جلویش را می‌گیرد و آن
 * آزمون سبز می‌ماند. اینجا هر لایه به‌تنهایی سنجیده می‌شود: کلیدِ جهت بی AAD،
 * AAD بی کلید، و nonce پس از «روشن شدنِ دوباره»ی گوشی.
 */
class HubAttackTest {

    private val deviceId = "dev-00000001"
    private val deviceKey = LanPrims.random(32)
    private val path = HubProtocol.PATH_SYNC

    private fun head(s: HubEnvelope.Sealed) = HubEnvelope.readHead { s.headers[it] }!!

    private fun box(body: String) = LanCrypto.unb64(LanJson.parseObject(body)!!.jStr("box"))!!

    @Test
    fun `no GCM nonce repeats under one device key, across phone restarts and hub restarts`() {
        // هر «اجرا» کلیدها را از نو از کلیدِ دستگاهِ ذخیره‌شده می‌سازد — همان کاری که
        // گوشی و کامپیوتر پس از روشن شدنِ دوباره می‌کنند. شمارنده‌ای که با هر اجرا از
        // صفر شروع شود همین‌جا تکرار می‌دهد.
        val requestNonces = HashSet<String>()
        val responseNonces = HashSet<String>()
        repeat(2) { run ->
            repeat(2_000) { i ->
                val s = HubEnvelope.sealRequest(deviceId, deviceKey, path, 1_000L + i, mapOf("run" to run.toLong()))
                assertEquals("request nonce reused (run $run, #$i)", true, requestNonces.add(LanCrypto.b64(s.nonce)))
                val reply = LanJson.parseObject(HubEnvelope.sealResponse(head(s), deviceKey, path, mapOf("ok" to true)))!!
                assertEquals("response nonce reused (run $run, #$i)", true, responseNonces.add(reply.jStr("nonce")!!))
            }
        }
        // پاکتِ جفت شدن هم: یک کلید برای پرسش و جواب، پس nonceها باید از هم جدا باشند.
        val pairKey = LanPrims.random(32)
        val pairNonces = HashSet<String>()
        repeat(2_000) {
            val ask = HubEnvelope.pairStatusAsk("REQ1", pairKey, it.toLong())
            assertEquals(true, pairNonces.add(LanCrypto.b64(ask.nonce)))
        }
    }

    @Test
    fun `a box swapped between directions fails on the key alone and on the associated data alone`() {
        val keys = LanCrypto.directional(deviceKey)
        val s = HubEnvelope.sealRequest(deviceId, deviceKey, path, 1_000L, mapOf("a" to 1L))
        val h = head(s)
        val reqBox = box(s.body)
        val reqAad = HubProtocol.aadRequest(path, deviceId, h.time, h.nonce)
        val respAad = HubProtocol.aadResponse(path, deviceId, h.nonce)
        assertNotNull("sanity", LanCrypto.open(keys.request, h.nonce, reqBox, reqAad))
        // ۱. همان AAD، کلیدِ جهتِ دیگر.
        assertNull("request box under the response key", LanCrypto.open(keys.response, h.nonce, reqBox, reqAad))
        // ۲. همان کلید، AADِ جهتِ دیگر.
        assertNull("request box under the response AAD", LanCrypto.open(keys.request, h.nonce, reqBox, respAad))

        val reply = LanJson.parseObject(HubEnvelope.sealResponse(h, deviceKey, path, mapOf("ok" to true)))!!
        val rNonce = LanCrypto.unb64(reply.jStr("nonce"))!!
        val rBox = LanCrypto.unb64(reply.jStr("box"))!!
        assertNotNull("sanity", LanCrypto.open(keys.response, rNonce, rBox, respAad))
        assertNull("response box under the request key", LanCrypto.open(keys.request, rNonce, rBox, respAad))
        assertNull("response box under the request AAD", LanCrypto.open(keys.response, rNonce, rBox, reqAad))
        // و AADِ دو جهت حتی با nonce و دستگاهِ یکسان یکی نیست.
        assertEquals(false, reqAad == respAad)
    }

    @Test
    fun `the three pairing messages share one key, so their associated data alone keeps them apart`() {
        val key = LanPrims.random(32)
        val ask = HubEnvelope.pairStatusAsk("REQ1", key, 7L)
        val askBox = box(ask.body)
        assertNotNull(LanCrypto.open(key, ask.nonce, askBox, HubProtocol.aadPairStatus("REQ1")))
        // پرسشِ گوشی به‌جای جوابِ کامپیوتر، یا به‌جای درخواستِ جفت شدن، باز نمی‌شود.
        assertNull(LanCrypto.open(key, ask.nonce, askBox, HubProtocol.aadPairReply("REQ1", ask.nonce)))
        assertNull(LanCrypto.open(key, ask.nonce, askBox, HubProtocol.aadPairRequest("REQ1", com.afghanjama.lan.PairVia.QR)))
        val labels = setOf(
            HubProtocol.aadPairStatus("REQ1"),
            HubProtocol.aadPairReply("REQ1", ask.nonce),
            HubProtocol.aadPairRequest("REQ1", com.afghanjama.lan.PairVia.QR),
            HubProtocol.aadPairRequest("REQ1", com.afghanjama.lan.PairVia.CODE),
        )
        assertEquals(4, labels.size)
    }
}
