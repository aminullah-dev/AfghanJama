package com.afghanjama

import com.afghanjama.lan.Ack
import com.afghanjama.lan.AckStatus
import com.afghanjama.lan.HttpReply
import com.afghanjama.lan.HubEnvelope
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
import com.afghanjama.lan.ReplayGuard
import com.afghanjama.lan.SyncAsk
import com.afghanjama.lan.SyncReply
import com.afghanjama.lan.WorkLine
import com.afghanjama.lan.WorkerView
import com.afghanjama.lan.workshopHostRefusal
import com.afghanjama.prefs.Settings
import com.afghanjama.lan.SecretStore

/*
 * کامپیوترِ ساختگیِ قراردادِ ۲ و شبکهٔ ساختگی — برای آزمونِ مشتری و جلسهٔ گوشی
 * بی سوکت. سرورِ واقعی روی سوکت در `lanLoopSmoke` سنجیده می‌شود.
 */

/** کامپیوترِ ساختگی: فقط پاکت‌ها را درست باز و بسته می‌کند. */
internal class FakeHub(val ip: String, val hubId: String = "hub-0001-aaaa") {
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
    /** دسترسیِ گوشی برداشته شد: هر همگام‌سازی «ناشناس». */
    var revoked = false
    /** بارِ بعد ساعتِ گوشی را نادرست بداند (یک بار). */
    var clockOnce = false
    val received = mutableListOf<OutboxWire>()
    val paths = mutableListOf<String>()

    fun link() = PairingV2.Link(ip, HubProtocol.PORT, hubId, pairingId, qr)

    fun handle(method: String, path: String, headers: Map<String, String>, body: String?): HttpReply {
        fun json(status: Int, v: Map<String, Any?>) = HttpReply(status, LanJson.write(v))
        paths += path
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
                if (clockOnce) {
                    clockOnce = false
                    return json(401, mapOf("ok" to false, "code" to HubProtocol.ERR_TIME, "serverTime" to now, "error" to "ساعت"))
                }
                if (revoked || head.deviceId != deviceId) return json(401, mapOf("ok" to false, "code" to HubProtocol.ERR_AUTH, "error" to "ناشناس"))
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
internal class FakeNet(hubs: Map<String, FakeHub>) : HubTransport {
    val hubs: MutableMap<String, FakeHub> = hubs.toMutableMap()
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


/** تنظیماتِ در حافظه. */
internal class MemSettings : Settings {
    private val m = HashMap<String, Any>()
    override fun getString(file: String, key: String, def: String): String = m["$file/$key"] as? String ?: def
    override fun putString(file: String, key: String, value: String) { m["$file/$key"] = value }
    override fun getBoolean(file: String, key: String, def: Boolean): Boolean = m["$file/$key"] as? Boolean ?: def
    override fun putBoolean(file: String, key: String, value: Boolean) { m["$file/$key"] = value }
    override fun getLong(file: String, key: String, def: Long): Long = m["$file/$key"] as? Long ?: def
    override fun putLong(file: String, key: String, value: Long) { m["$file/$key"] = value }
    override fun remove(file: String, key: String) { m.remove("$file/$key") }
    fun dump(): String = m.entries.joinToString("\n") { "${it.key}=${it.value}" }
}

/** انبارِ امنِ در حافظه. */
internal class MemSecrets : SecretStore {
    val m = HashMap<String, ByteArray>()
    override fun put(name: String, value: ByteArray): Boolean { m[name] = value.copyOf(); return true }
    override fun get(name: String): ByteArray? = m[name]?.copyOf()
    override fun remove(name: String) { m.remove(name) }
}
