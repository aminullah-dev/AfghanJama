package com.afghanjama.lan

import com.afghanjama.AppInfo
import com.afghanjama.util.nowMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * آنچه گوشی پس از «اجازه» نگه می‌دارد. کلید فقط در انبارِ امنِ سکو
 * ([HubSecrets]) می‌نشیند و [toString] چیزی از آن نمی‌گوید.
 */
class DeviceCred(
    val deviceId: String,
    val key: ByteArray,
    val hubId: String,
    val hubName: String,
    val role: HubRole,
    val person: String,
) {
    override fun toString(): String =
        "DeviceCred(deviceId=$deviceId, hubId=$hubId, role=$role, person=$person, key=…)"
}

/** درخواستِ جفت شدن که فرستاده شده و منتظرِ «اجازه» است. */
class PairPending(
    val host: String,
    val port: Int,
    val requestId: String,
    val hubId: String,
    val via: PairVia,
    internal val key: ByteArray,
) {
    override fun toString(): String = "PairPending(host=$host, requestId=$requestId, hubId=$hubId, via=$via, key=…)"
}

/** نتیجهٔ یک همگام‌سازی — هر شکست نوعِ خودش را دارد تا صفحه درست بگوید. */
sealed interface SyncResult {
    /** [offsetMs] فاصلهٔ ساعتِ کامپیوتر از ساعتِ گوشی، تازه‌شده. */
    data class Ok(val reply: SyncReply, val offsetMs: Long) : SyncResult

    /** به کامپیوتر نرسید (شبکه، نشانیِ کهنه، کامپیوترِ خاموش). */
    data class Unreachable(val message: String) : SyncResult

    /** کامپیوتر این گوشی را نمی‌شناسد — دسترسی برداشته شده یا کلید جور نیست. */
    data class Unpaired(val message: String) : SyncResult

    /** ساعتِ گوشی از بازه بیرون است؛ [serverTime] برای تنظیمِ دوباره. */
    data class Clock(val serverTime: Long) : SyncResult

    data class Blocked(val message: String) : SyncResult

    data class Failed(val message: String) : SyncResult
}

/**
 * مشتریِ قراردادِ ۲ — همان که گوشیِ اندرویدی، آیفون و آزمونِ دودی به کار
 * می‌برند. هیچ وضعیتی نگه نمی‌دارد؛ هر چه ماندنی است (کلید، صف، تصویرِ
 * آخر) دستِ صداکننده است.
 */
class HubClient(
    private val transport: HubTransport,
    private val clock: () -> Long = { nowMillis() },
) {

    private fun errorOf(body: String): Map<String, Any?> = LanJson.parseObject(body) ?: emptyMap()

    suspend fun ping(host: String, port: Int = HubProtocol.PORT, connectTimeoutMs: Int = 6_000): LanResult<PingInfo> {
        val r = transport.exchange(host, port, "GET", Lan2.PATH_PING, emptyMap(), null, 8_000, connectTimeoutMs)
        if (r is LanResult.Err) return r
        val reply = (r as LanResult.Ok).value
        val info = LanJson.parseObject(reply.body)?.let { PingInfo.parse(it) }
            ?: return LanResult.Err("این نشانی خیاط‌یار نیست.")
        if (info.app.isNotEmpty() && info.app != EXPECTED_APP) return LanResult.Err("این نشانی خیاط‌یار نیست.")
        return LanResult.Ok(info)
    }

    suspend fun hello(host: String, port: Int): LanResult<PairHello> {
        val r = transport.exchange(host, port, "POST", HubProtocol.PATH_HELLO, emptyMap(), "{}", 8_000)
        if (r is LanResult.Err) return r
        val reply = (r as LanResult.Ok).value
        if (reply.status == 404) return LanResult.Err(PAIR_CLOSED)
        val o = LanJson.parseObject(reply.body) ?: return LanResult.Err(HUB_UNREACHABLE)
        if (reply.status != 200) return LanResult.Err(o.jStr("error") ?: PAIR_CLOSED)
        return PairHello.parse(o)?.let { LanResult.Ok(it) } ?: LanResult.Err("جوابِ کامپیوتر خوانده نشد.")
    }

    /**
     * درخواستِ جفت شدن. برای QR، [expected] همان QR است و `hubId` و
     * `pairingId`ِ «سلام» باید با آن بخوانند — QRِ پنجرهٔ قبلی یا QRی که به
     * کامپیوترِ دیگری اشاره کند پذیرفته نمی‌شود.
     */
    suspend fun requestPair(
        host: String,
        port: Int,
        via: PairVia,
        secret: ByteArray,
        info: PhoneInfo,
        expected: PairingV2.Link? = null,
    ): LanResult<PairPending> {
        if (via == PairVia.CODE && info.wantRole == HubRole.OWNER) return LanResult.Err(OWNER_NEEDS_QR)
        val hello = when (val h = hello(host, port)) {
            is LanResult.Err -> return h
            is LanResult.Ok -> h.value
        }
        if (expected != null && (expected.hubId != hello.hubId || expected.pairingId != hello.pairingId)) {
            return LanResult.Err(STALE_QR)
        }
        val draft = runCatching { HubEnvelope.pairRequest(hello, via, secret, info) }
            .getOrElse { return LanResult.Err("جوابِ کامپیوتر سنجیده نشد.") }
        val r = transport.exchange(host, port, "POST", HubProtocol.PATH_PAIR, emptyMap(), draft.body, 10_000)
        if (r is LanResult.Err) return r
        val reply = (r as LanResult.Ok).value
        val o = errorOf(reply.body)
        if (reply.status != 200 || o.jBool("ok") != true) {
            return LanResult.Err(o.jStr("error") ?: "درخواستِ جفت شدن پذیرفته نشد.")
        }
        val requestId = o.jStr("requestId") ?: return LanResult.Err("جوابِ کامپیوتر خوانده نشد.")
        return LanResult.Ok(PairPending(host, port, requestId, hello.hubId, via, draft.key))
    }

    /** «اجازه دادید؟» — تا ۲۵ ثانیه کامپیوتر جواب را نگه می‌دارد. */
    suspend fun pollPair(p: PairPending): LanResult<PairGrant> {
        val ask = HubEnvelope.pairStatusAsk(p.requestId, p.key, clock())
        val r = transport.exchange(
            p.host, p.port, "POST", HubProtocol.PATH_PAIR_STATUS, emptyMap(), ask.body,
            HubProtocol.PAIR_CLIENT_TIMEOUT_MS,
        )
        if (r is LanResult.Err) return r
        val reply = (r as LanResult.Ok).value
        val o = errorOf(reply.body)
        if (reply.status != 200) return LanResult.Err(o.jStr("error") ?: "درخواستِ جفت شدن دیگر نیست.")
        val grant = HubEnvelope.openPairReply(o, p.requestId, p.key, ask.nonce)
            ?: return LanResult.Err("جوابِ کامپیوتر سنجیده نشد.")
        if (grant.hubId != p.hubId) return LanResult.Err("جوابِ کامپیوتر سنجیده نشد.")
        return LanResult.Ok(grant)
    }

    /** یک رفت‌وبرگشتِ همگام‌سازی. [offsetMs] آخرین فاصلهٔ شناخته‌شدهٔ دو ساعت. */
    suspend fun sync(cred: DeviceCred, host: String, port: Int, ask: SyncAsk, offsetMs: Long): SyncResult {
        val time = clock() + offsetMs
        val sealed = HubEnvelope.sealRequest(cred.deviceId, cred.key, HubProtocol.PATH_SYNC, time, ask.toJson())
        val r = transport.exchange(host, port, "POST", HubProtocol.PATH_SYNC, sealed.headers, sealed.body, 15_000)
        if (r is LanResult.Err) return SyncResult.Unreachable(r.message)
        val reply = (r as LanResult.Ok).value
        if (reply.status != 200) {
            val o = errorOf(reply.body)
            val msg = o.jStr("error") ?: "همگام‌سازی پذیرفته نشد."
            return when (o.jStr("code")) {
                HubProtocol.ERR_TIME -> o.jLong("serverTime")?.let { SyncResult.Clock(it) } ?: SyncResult.Failed(msg)
                HubProtocol.ERR_AUTH -> SyncResult.Unpaired(msg)
                HubProtocol.ERR_BLOCKED -> SyncResult.Blocked(msg)
                else -> SyncResult.Failed(msg)
            }
        }
        val plain = HubEnvelope.openResponse(cred.deviceId, cred.key, HubProtocol.PATH_SYNC, sealed.nonce, reply.body)
            ?: return SyncResult.Failed("جوابِ کامپیوتر سنجیده نشد.")
        val parsed = SyncReply.parse(plain) ?: return SyncResult.Failed("جوابِ کامپیوتر خوانده نشد.")
        if (parsed.hubId != cred.hubId) return SyncResult.Failed("جوابِ کامپیوتر سنجیده نشد.")
        return SyncResult.Ok(parsed, parsed.serverTime - clock())
    }

    companion object {
        /** نامی که `/ping`ِ خیاط‌یار با آن خودش را معرفی می‌کند (`AppInfo.NAME_LATIN`). */
        const val EXPECTED_APP = AppInfo.NAME_LATIN

        const val PAIR_CLOSED =
            "پنجرهٔ «افزودنِ گوشی» روی کامپیوترِ کارگاه باز نیست. روی کامپیوتر «افزودنِ گوشی» را بزنید و دوباره امتحان کنید."
        const val STALE_QR =
            "این QR کهنه است یا مالِ کامپیوترِ دیگری است. روی کامپیوترِ کارگاه دوباره «افزودنِ گوشی» را بزنید و QRِ تازه را اسکن کنید."
        const val OWNER_NEEDS_QR =
            "گوشیِ صاحبِ کار فقط با اسکنِ QR جفت می‌شود، نه با رمزِ دستی."
    }
}

/** مسیرِ `/ping` که هر دو قرارداد دارند — بی وابستگی به [Lan]ِ جاوایی. */
object Lan2 {
    const val PATH_PING = "/ping"
}

/**
 * پیدا کردنِ دوبارهٔ کامپیوترِ کارگاه وقتی نشانی‌اش عوض شده.
 *
 * ترتیب، از ارزان به گران:
 *
 * 1. آخرین نشانی و نشانی‌های دیگری که کامپیوتر خودش گفته بود.
 * 2. **جست‌وجوی همان /۲۴ِ خودِ گوشی**: `GET /ping` روی ۲۵۴ نشانی، ۳۲ تا هم‌زمان
 *    با ۳۰۰ میلی‌ثانیه سقفِ اتصال — حدودِ سه ثانیه. فقط کامپیوتری پذیرفته می‌شود
 *    که `hubId`ش همان باشد؛ کامپیوترِ همسایه یا هر چیزِ دیگری که روی ۸۷۹۷
 *    جواب بدهد کنار می‌رود.
 * 3. اگر نشد، صفحه می‌گوید QR را دوباره اسکن کنید.
 *
 * چرا mDNS نه: کتابخانهٔ تازه روی کامپیوتر، و روی آیفون مجوزِ multicastِ اپل.
 * این جست‌وجو هیچ مجوز و کتابخانه‌ای نمی‌خواهد و فقط پس از یک شکست اجرا می‌شود.
 */
object HubFinder {

    const val PROBE_CONNECT_MS = 300
    const val PARALLEL = 32

    /** نشانی‌های شناخته، بی تکرار و فقط از شبکهٔ کارگاه. */
    fun known(last: String?, alternatives: List<String>): List<String> =
        (listOfNotNull(last) + alternatives).map { it.trim() }.filter { HostAddress.isPrivate(it) }.distinct()

    /** همهٔ نشانی‌های /۲۴ِ [own] جز خودش، ۰ و ۲۵۵. */
    fun subnet24(own: String?): List<String> {
        if (own == null || !HostAddress.isPrivate(own)) return emptyList()
        val p = own.split('.')
        val me = p[3].toIntOrNull() ?: return emptyList()
        val base = "${p[0]}.${p[1]}.${p[2]}."
        return (1..254).filter { it != me }.map { base + it }
    }

    /**
     * نشانیِ کامپیوتری که `hubId`ش [hubId] است، یا `null`. [skip] نشانی‌هایی
     * که پیش‌تر سنجیده شده‌اند.
     */
    suspend fun find(
        client: HubClient,
        hubId: String,
        port: Int,
        last: String?,
        alternatives: List<String>,
        own: String?,
    ): String? {
        val firsts = known(last, alternatives)
        for (ip in firsts) {
            if (isHub(client, ip, port, hubId, 1_500)) return ip
        }
        val rest = subnet24(own).filter { it !in firsts }
        for (chunk in rest.chunked(PARALLEL)) {
            val hit = coroutineScope {
                chunk.map { ip -> async { ip.takeIf { isHub(client, ip, port, hubId, PROBE_CONNECT_MS) } } }
                    .awaitAll().firstOrNull { it != null }
            }
            if (hit != null) return hit
        }
        return null
    }

    private suspend fun isHub(client: HubClient, ip: String, port: Int, hubId: String, connectMs: Int): Boolean =
        (client.ping(ip, port, connectMs) as? LanResult.Ok)?.value?.hubId == hubId
}

/** نشانیِ همین گوشی در شبکهٔ محلی — برای جست‌وجوی /۲۴ ([HubFinder]). */
expect fun phoneLanIp(): String?

/**
 * انبارِ امنِ کلیدِ دستگاه روی هر سکو: اندروید با کلیدِ Keystore پیچیده،
 * آیفون در Keychain. برنامه هنگامِ راه‌اندازی [HubSecrets.store] را می‌گذارد؛
 * بی آن، گوشی جفت نمی‌شود (نه اینکه کلید را جای ناامن بگذارد).
 */
interface SecretStore {
    fun put(name: String, value: ByteArray): Boolean
    fun get(name: String): ByteArray?
    fun remove(name: String)
}

object HubSecrets {
    @kotlin.concurrent.Volatile
    var store: SecretStore? = null
}
