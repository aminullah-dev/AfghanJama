package com.afghanjama

import com.afghanjama.lan.Ack
import com.afghanjama.lan.AckStatus
import com.afghanjama.lan.DeviceCred
import com.afghanjama.lan.HttpReply
import com.afghanjama.lan.HubClient
import com.afghanjama.lan.HubEnvelope
import com.afghanjama.lan.HubFinder
import com.afghanjama.lan.HubProtocol
import com.afghanjama.lan.HubRole
import com.afghanjama.lan.HubSnapshot
import com.afghanjama.lan.HubTransport
import com.afghanjama.lan.LanJson
import com.afghanjama.lan.LanPrims
import com.afghanjama.lan.LanResult
import com.afghanjama.lan.OutboxWire
import com.afghanjama.lan.PairGrant
import com.afghanjama.lan.PairHello
import com.afghanjama.lan.PairState
import com.afghanjama.lan.PairVia
import com.afghanjama.lan.PairingV2
import com.afghanjama.lan.PhoneInfo
import com.afghanjama.lan.ReplayGuard
import com.afghanjama.lan.SyncAsk
import com.afghanjama.lan.SyncReply
import com.afghanjama.lan.SyncResult
import com.afghanjama.lan.WorkLine
import com.afghanjama.lan.WorkerView
import com.afghanjama.lan.jStr
import com.afghanjama.lan.workshopHostRefusal
import com.afghanjama.lan.hubTransport
import com.afghanjama.lan.HUB_UNREACHABLE
import com.afghanjama.lan.HostAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * مشتریِ قراردادِ ۲ در برابرِ یک کامپیوترِ ساختگی در حافظه — همان پاکت‌ها،
 * بی شبکه. سرورِ واقعی روی سوکت در `lanLoopSmoke` سنجیده می‌شود؛ اینجا رفتارِ
 * گوشی: کدام جواب را بپذیرد و کدام را نه.
 */
class HubClientTest {

    /** کامپیوترِ ساختگی: فقط پاکت‌ها را درست باز و بسته می‌کند. */
    private class FakeHub(val ip: String, val hubId: String = "hub-0001-aaaa") {
        var now = 1_760_000_000_000L
        val hubPair = LanPrims.p256KeyPair()
        val pairingId = "PAIRING0001"
        val qr = LanPrims.random(16)
        var windowOpen = true
        var decision: PairState = PairState.WAIT
        var pendingKey: ByteArray? = null
        val deviceKey = LanPrims.random(32)
        val deviceId = "dev-00000001"
        val replay = ReplayGuard(clock = { now })
        var pingBody: String? = null
        var tamperReply = false
        var otherHubId: String? = null
        val received = mutableListOf<OutboxWire>()

        fun link() = PairingV2.Link(ip, HubProtocol.PORT, hubId, pairingId, qr)

        fun handle(method: String, path: String, headers: Map<String, String>, body: String?): HttpReply {
            fun json(status: Int, v: Map<String, Any?>) = HttpReply(status, LanJson.write(v))
            return when (path) {
                "/ping" -> HttpReply(
                    200,
                    pingBody ?: LanJson.write(
                        mapOf("ok" to true, "app" to "KhayatYar", "protocol" to 1, "protocolMin" to 1, "protocolMax" to 2,
                            "hubId" to hubId, "serverTime" to now, "pairingOpen" to windowOpen)
                    ),
                )
                HubProtocol.PATH_HELLO ->
                    if (!windowOpen) json(404, mapOf("ok" to false, "error" to "بسته"))
                    else json(200, PairHello(hubId, pairingId, hubPair.publicX963, 600_000).toJson())
                HubProtocol.PATH_PAIR -> {
                    val opened = HubEnvelope.openPairRequest(LanJson.parseObject(body!!)!!, pairingId, hubPair) {
                        if (it == PairVia.QR) qr else "482913572046".encodeToByteArray()
                    } ?: return json(401, mapOf("ok" to false, "error" to "رمز درست نیست.", "code" to HubProtocol.ERR_AUTH))
                    pendingKey = opened.key
                    json(200, mapOf("ok" to true, "requestId" to "REQ1"))
                }
                HubProtocol.PATH_PAIR_STATUS -> {
                    val key = pendingKey!!
                    val ask = HubEnvelope.openPairStatus(LanJson.parseObject(body!!)!!, "REQ1", key)
                        ?: return json(401, mapOf("ok" to false))
                    val grant = if (decision == PairState.APPROVED) PairGrant(
                        PairState.APPROVED, otherHubId ?: hubId, "کارگاهِ نمونه", now, deviceId, deviceKey,
                        HubRole.WORKER, "[T1] احمد", listOf(ip),
                    ) else PairGrant(decision, hubId, "کارگاهِ نمونه", now)
                    val replyNonce = if (tamperReply) LanPrims.random(12) else ask
                    HttpReply(200, HubEnvelope.pairReply(grant, "REQ1", key, replyNonce))
                }
                HubProtocol.PATH_SYNC -> {
                    val head = HubEnvelope.readHead { headers[it] }
                        ?: return json(401, mapOf("ok" to false, "code" to HubProtocol.ERR_AUTH, "error" to "x"))
                    if (replay.timeOk(head.time) is ReplayGuard.Verdict.Stale) {
                        return json(401, mapOf("ok" to false, "code" to HubProtocol.ERR_TIME, "serverTime" to now, "error" to "ساعت"))
                    }
                    if (head.deviceId != deviceId) return json(401, mapOf("ok" to false, "code" to HubProtocol.ERR_AUTH, "error" to "ناشناس"))
                    val plain = HubEnvelope.openRequest(head, deviceKey, path, body!!)
                        ?: return json(401, mapOf("ok" to false, "code" to HubProtocol.ERR_AUTH, "error" to "ناشناس"))
                    if (replay.admit(head.deviceId, head.nonce, head.time) != ReplayGuard.Verdict.Ok) {
                        return json(401, mapOf("ok" to false, "code" to HubProtocol.ERR_AUTH, "error" to "تکرار"))
                    }
                    val ask = SyncAsk.parse(plain)!!
                    received += ask.outbox
                    val snap = HubSnapshot(
                        HubRole.WORKER,
                        worker = WorkerView("[T1] احمد", listOf(WorkLine(1, "AJ-1", "D-1", 5, 40, now)), 0, emptyList(), null),
                    )
                    val reply = SyncReply(
                        now, ask.outbox.map { Ack(it.key, AckStatus.QUEUED) }, "c1",
                        if (ask.cursor == "c1") null else snap, "LICENSED", true, HubRole.WORKER, "[T1] احمد", "گوشیِ احمد",
                        otherHubId ?: hubId, "کارگاه", "1.11.0", listOf(ip),
                    )
                    HttpReply(200, HubEnvelope.sealResponse(head, deviceKey, path, reply.toJson()))
                }
                else -> json(404, mapOf("ok" to false))
            }
        }
    }

    /** شبکهٔ ساختگی: هر نشانی یا یک کامپیوترِ ساختگی است یا هیچ. */
    private class FakeNet(val hubs: Map<String, FakeHub>) : HubTransport {
        val asked = mutableListOf<String>()
        override suspend fun exchange(
            host: String, port: Int, method: String, path: String, headers: Map<String, String>,
            body: String?, timeoutMs: Int, connectTimeoutMs: Int,
        ): LanResult<HttpReply> {
            workshopHostRefusal(host, false)?.let { return LanResult.Err(it) }
            synchronized(asked) { asked += host }
            val hub = hubs[host] ?: return LanResult.Err("نرسید")
            return LanResult.Ok(hub.handle(method, path, headers, body))
        }
    }

    private val hub = FakeHub("192.168.1.7")
    private val net = FakeNet(mapOf("192.168.1.7" to hub))
    private val client = HubClient(net, clock = { hub.now })
    private val info = PhoneInfo("گوشیِ احمد", "android", "1.11.0", HubRole.WORKER)

    private fun pairAndApprove(): DeviceCred = runBlocking {
        val pending = (client.requestPair("192.168.1.7", HubProtocol.PORT, PairVia.QR, hub.qr, info, hub.link())
            as LanResult.Ok).value
        hub.decision = PairState.APPROVED
        val grant = (client.pollPair(pending) as LanResult.Ok).value
        DeviceCred(grant.deviceId!!, grant.deviceKey!!, grant.hubId, grant.hubName, grant.role!!, grant.person)
    }

    @Test
    fun `pairing by QR waits, then receives the device key on approval`() = runBlocking {
        val pending = (client.requestPair("192.168.1.7", HubProtocol.PORT, PairVia.QR, hub.qr, info, hub.link()) as LanResult.Ok).value
        val waiting = (client.pollPair(pending) as LanResult.Ok).value
        assertEquals(PairState.WAIT, waiting.state)
        assertNull(waiting.deviceKey)
        hub.decision = PairState.APPROVED
        val grant = (client.pollPair(pending) as LanResult.Ok).value
        assertEquals(PairState.APPROVED, grant.state)
        assertTrue(grant.deviceKey!!.contentEquals(hub.deviceKey))
        assertEquals("[T1] احمد", grant.person)
    }

    @Test
    fun `a reply bound to another ask, or from another hub, is refused`() = runBlocking {
        val pending = (client.requestPair("192.168.1.7", HubProtocol.PORT, PairVia.QR, hub.qr, info, hub.link()) as LanResult.Ok).value
        hub.decision = PairState.APPROVED
        hub.tamperReply = true
        assertTrue(client.pollPair(pending) is LanResult.Err)
        hub.tamperReply = false
        hub.otherHubId = "hub-9999-zzzz"
        assertTrue(client.pollPair(pending) is LanResult.Err)
    }

    @Test
    fun `a stale QR from an earlier window is refused before anything is sent`() = runBlocking {
        val old = hub.link().copy(pairingId = "PAIRING0000")
        val r = client.requestPair("192.168.1.7", HubProtocol.PORT, PairVia.QR, hub.qr, info, old)
        assertEquals(HubClient.STALE_QR, (r as LanResult.Err).message)
        assertNull(hub.pendingKey)
    }

    @Test
    fun `a wrong secret is told apart from a closed window`() = runBlocking {
        val wrong = client.requestPair("192.168.1.7", HubProtocol.PORT, PairVia.CODE, "000000000000".encodeToByteArray(), info)
        assertEquals("رمز درست نیست.", (wrong as LanResult.Err).message)
        hub.windowOpen = false
        val closed = client.requestPair("192.168.1.7", HubProtocol.PORT, PairVia.CODE, "482913572046".encodeToByteArray(), info)
        assertEquals(HubClient.PAIR_CLOSED, (closed as LanResult.Err).message)
    }

    @Test
    fun `the owner role is never asked for with the manual code`() = runBlocking {
        val r = client.requestPair(
            "192.168.1.7", HubProtocol.PORT, PairVia.CODE, "482913572046".encodeToByteArray(), info.copy(wantRole = HubRole.OWNER)
        )
        assertEquals(HubClient.OWNER_NEEDS_QR, (r as LanResult.Err).message)
        assertNull(hub.pendingKey)
    }

    @Test
    fun `a sync carries the outbox out and the snapshot back, and an unchanged cursor brings nothing`() = runBlocking {
        val cred = pairAndApprove()
        val ask = SyncAsk(hub.now, "", listOf(OutboxWire("k-00000001", hub.now, "ATTENDANCE_IN", emptyMap())), "1.11.0", "android")
        val first = client.sync(cred, "192.168.1.7", HubProtocol.PORT, ask, 0) as SyncResult.Ok
        assertEquals(listOf(Ack("k-00000001", AckStatus.QUEUED)), first.reply.acks)
        assertEquals("AJ-1", first.reply.snapshot!!.worker!!.inHand.single().orderCode)
        assertEquals(1, hub.received.size)
        val second = client.sync(cred, "192.168.1.7", HubProtocol.PORT, ask.copy(cursor = "c1", outbox = emptyList()), 0) as SyncResult.Ok
        assertNull("unchanged", second.reply.snapshot)
    }

    @Test
    fun `a phone clock far off gets TIME with the hub's time, then succeeds with the learned offset`() = runBlocking {
        val cred = pairAndApprove()
        val ask = SyncAsk(hub.now, "", emptyList(), "1.11.0", "android")
        val off = client.sync(cred, "192.168.1.7", HubProtocol.PORT, ask, offsetMs = -3_600_000)
        assertEquals(SyncResult.Clock(hub.now), off)
        assertTrue(client.sync(cred, "192.168.1.7", HubProtocol.PORT, ask, 0) is SyncResult.Ok)
    }

    @Test
    fun `a revoked or unknown device is told it is no longer paired`() = runBlocking {
        val cred = pairAndApprove()
        val stranger = DeviceCred("dev-00000099", LanPrims.random(32), cred.hubId, "", HubRole.WORKER, "")
        val r = client.sync(stranger, "192.168.1.7", HubProtocol.PORT, SyncAsk(hub.now, "", emptyList(), "", ""), 0)
        assertTrue(r is SyncResult.Unpaired)
        val wrongKey = DeviceCred(cred.deviceId, LanPrims.random(32), cred.hubId, "", HubRole.WORKER, "")
        assertTrue(client.sync(wrongKey, "192.168.1.7", HubProtocol.PORT, SyncAsk(hub.now, "", emptyList(), "", ""), 0) is SyncResult.Unpaired)
    }

    @Test
    fun `a reply from a computer with another ledger is refused`() = runBlocking {
        val cred = pairAndApprove()
        hub.otherHubId = "hub-9999-zzzz"
        val r = client.sync(cred, "192.168.1.7", HubProtocol.PORT, SyncAsk(hub.now, "", emptyList(), "", ""), 0)
        assertTrue(r is SyncResult.Failed)
    }

    @Test
    fun `ping from a 1_10 hub has no protocolMax and means protocol 1`() = runBlocking {
        hub.pingBody = "{\"ok\":true,\"app\":\"KhayatYar\",\"protocol\":1}"
        val p = (client.ping("192.168.1.7") as LanResult.Ok).value
        assertEquals(1, p.protocolMax)
        assertNull(p.hubId)
    }

    @Test
    fun `ping from something that is not KhayatYar is refused`() = runBlocking {
        hub.pingBody = "{\"ok\":true,\"app\":\"Printer\"}"
        assertTrue(client.ping("192.168.1.7") is LanResult.Err)
        hub.pingBody = "<html>"
        assertTrue(client.ping("192.168.1.7") is LanResult.Err)
    }

    @Test
    fun `the real transport refuses a public or loopback address before any connection`() = runBlocking {
        val t = hubTransport()
        for (host in listOf("8.8.8.8", "127.0.0.1", "169.254.1.1", "example.com")) {
            val r = t.exchange(host, HubProtocol.PORT, "GET", "/ping", emptyMap(), null, 1_000, 1_000)
            assertEquals(host, HostAddress.NOT_WORKSHOP, (r as LanResult.Err).message)
        }
        // loopback فقط با پرچمِ آزمونِ دودی؛ آن‌وقت به درگاهِ بسته می‌رسد و «نرسید» می‌گوید.
        val probe = java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }
        val r = hubTransport(allowLoopback = true).exchange("127.0.0.1", probe, "GET", "/ping", emptyMap(), null, 1_000, 1_000)
        assertEquals(HUB_UNREACHABLE, (r as LanResult.Err).message)
    }

    // ---------------------------------------------------------------- پیدا کردنِ دوباره

    @Test
    fun `the finder tries the last address first, then the phone's own subnet, and only accepts the same hub`() = runBlocking {
        val moved = FakeHub("192.168.1.42")
        val neighbour = FakeHub("192.168.1.20", hubId = "hub-0002-neighbour")
        val net2 = FakeNet(mapOf("192.168.1.42" to moved, "192.168.1.20" to neighbour))
        val c = HubClient(net2)
        val found = HubFinder.find(c, moved.hubId, HubProtocol.PORT, "192.168.1.7", listOf("10.0.0.5"), "192.168.1.30")
        assertEquals("192.168.1.42", found)
        assertEquals("192.168.1.7", net2.asked[0])
        assertEquals("10.0.0.5", net2.asked[1])
        assertTrue("never probed itself", "192.168.1.30" !in net2.asked)
        assertNull(HubFinder.find(c, "hub-0003-gone", HubProtocol.PORT, null, emptyList(), "192.168.1.30"))
    }

    @Test
    fun `the subnet list is the phone's own slash 24, without itself`() {
        val s = HubFinder.subnet24("192.168.1.30")
        assertEquals(253, s.size)
        assertTrue("192.168.1.30" !in s && "192.168.1.0" !in s && "192.168.1.255" !in s)
        assertTrue(HubFinder.subnet24("8.8.8.8").isEmpty())
        assertTrue(HubFinder.subnet24(null).isEmpty())
        assertEquals(listOf("192.168.1.7", "10.0.0.5"), HubFinder.known("192.168.1.7", listOf("10.0.0.5", "192.168.1.7", "8.8.8.8")))
    }

    @Test
    fun `the device key never shows up when credentials are printed`() {
        val cred = DeviceCred("dev-1", ByteArray(32) { 7 }, "hub", "x", HubRole.WORKER, "p")
        assertNotNull(cred.toString())
        assertTrue(!cred.toString().contains("7, 7"))
        assertTrue(cred.toString().contains("key=…"))
        assertEquals(null, LanJson.parseObject(cred.toString())?.jStr("key"))
    }
}
