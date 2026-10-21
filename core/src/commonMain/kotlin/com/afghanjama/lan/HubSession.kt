package com.afghanjama.lan

import com.afghanjama.AppVersion
import com.afghanjama.licence.LicenceGate
import com.afghanjama.licence.LicenceState
import com.afghanjama.licence.MainLicence
import com.afghanjama.prefs.Settings
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import com.afghanjama.util.nowMillis
import com.afghanjama.util.randomUuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** آنچه صفحهٔ گوشیِ «کارگاهِ وصل» نشان می‌دهد. */
data class HubClientState(
    val paired: Boolean = false,
    val role: HubRole? = null,
    val person: String = "",
    val hubName: String = "",
    val hubVersion: String = "",
    val host: String = "",
    val snapshot: HubSnapshot? = null,
    val lastSyncAt: Long? = null,
    val connected: Boolean = false,
    val syncing: Boolean = false,
    val outbox: List<OutboxItem> = emptyList(),
    /** چرا وصل نیست، به زبانِ کاربر؛ `null` یعنی همه‌چیز درست. */
    val problem: String? = null,
    /** گوشی باید دوباره جفت شود (دسترسی برداشته شده، یا کامپیوتر پایین آمده). */
    val needsPairing: Boolean = false,
    /** منتظرِ «اجازه»ی کارفرما. */
    val waitingApproval: Boolean = false,
    /** دستگاهِ اصلی فقط‌خواندنی است. */
    val hubReadOnly: Boolean = false,
) {
    val pending: Int get() = Outbox.pendingCount(outbox)
}

/**
 * گوشیِ «کارگاهِ وصل» — جفت شدن، صفِ آفلاین، همگام‌سازی، و پیدا کردنِ دوبارهٔ
 * کامپیوتر. هر سه سکو همین را دارند (اندروید، آیفون؛ کامپیوتر هم اگر روزی
 * مشتری شد).
 *
 * **قاعده‌های B.6 همه اینجا اجرا می‌شوند:**
 *
 * - کارِ زده‌شده **همان لحظه** در صف می‌نشیند ([enqueue]) و با همگام‌سازی
 *   می‌رود؛ وصل نبودن آن را گم نمی‌کند، و فرستادنِ دوباره دو ردیف نمی‌سازد.
 * - هر جوابِ کامپیوتر جای دیده‌شدنی دارد ([Outbox.label])؛ ردشده با دلیل،
 *   کهنه‌شده با «فرستاده نشد — کهنه شد».
 * - ساعتِ نادرست: گوشی ساعتِ کامپیوتر را یاد می‌گیرد و یک بار دوباره می‌فرستد.
 * - نشانیِ عوض‌شده: پس از دو شکستِ پیاپی، کامپیوتر را با `hubId`ش دوباره
 *   پیدا می‌کند ([HubFinder]) — نه زودتر از هر دو دقیقه.
 * - گوشیِ جفت‌شده با قراردادِ ۲ **هرگز** به قراردادِ ۱ برنمی‌گردد
 *   ([ProtocolChoice]).
 */
class HubSession(
    private val settings: Settings,
    /** «android» یا «ios» — فقط برای فهرستِ دستگاه‌های کامپیوتر. */
    private val platform: String,
    transport: HubTransport = hubTransport(),
    private val clock: () -> Long = { nowMillis() },
    private val store: () -> SecretStore? = { HubSecrets.store },
    private val ownIp: () -> String? = { phoneLanIp() },
    private val appVersion: String = AppVersion.current,
    /** مکثِ میانِ پرسش‌های «اجازه دادید؟» وقتی شبکه نرسید. */
    private val pairRetryMs: Long = 2_000,
) {
    private val client = HubClient(transport, clock)
    private val lock = Mutex()
    private var pending: PairPending? = null
    private var failures = 0
    private var lastFinderAt = 0L

    private val _state = MutableStateFlow(HubClientState())
    val state: StateFlow<HubClientState> = _state.asStateFlow()

    init {
        reload()
    }

    /** وضعیت از تنظیمات — پس از هر تغییر. */
    fun reload() {
        val paired = HubLinkPrefs.paired(settings)
        _state.update {
            it.copy(
                paired = paired,
                role = HubLinkPrefs.role(settings),
                person = HubLinkPrefs.person(settings),
                hubName = HubLinkPrefs.hubName(settings),
                hubVersion = HubLinkPrefs.hubVersion(settings),
                host = HubLinkPrefs.host(settings),
                snapshot = if (paired) HubLinkPrefs.snapshot(settings) else null,
                lastSyncAt = HubLinkPrefs.lastSync(settings),
                outbox = HubLinkPrefs.outbox(settings),
                needsPairing = paired && it.needsPairing,
            )
        }
    }

    // ================================================================ جفت شدن

    /** QRِ `kylink2:` (یا لینکی که دوربینِ آیفون باز کرد). `null` یعنی جفت شد. */
    suspend fun pairWithLink(raw: String, info: PhoneInfo): String? {
        val link = PairingV2.parse(raw) ?: return PairingV2.refusal(raw)
        return waitFor(link.host) {
            client.requestPair(link.host, link.port, PairVia.QR, link.secret, info.copy(platform = platform), link)
        }
    }

    /** نشانی و رمزِ دستیِ ۱۲ رقمی — فقط کارگر و تابلو. */
    suspend fun pairWithCode(rawHost: String, rawCode: String, info: PhoneInfo): String? {
        val host = when (val h = HostAddress.check(rawHost)) {
            is HostAddress.Result.Bad -> return h.reason
            is HostAddress.Result.Ok -> h.host
        }
        val code = PairingV2.normalizeManualCode(rawCode)
            ?: return "رمزِ جفت شدن باید ${HubProtocol.MANUAL_CODE_DIGITS.fa()} رقم باشد."
        if (info.wantRole == HubRole.OWNER) return HubClient.OWNER_NEEDS_QR
        return waitFor(host) {
            client.requestPair(host, HubProtocol.PORT, PairVia.CODE, code.encodeToByteArray(), info.copy(platform = platform))
        }
    }

    fun cancelPairing() {
        pending = null
        _state.update { it.copy(waitingApproval = false) }
    }

    private suspend fun waitFor(host: String, request: suspend () -> LanResult<PairPending>): String? {
        if (store() == null) return NO_SAFE_STORE
        val p = when (val r = request()) {
            is LanResult.Err -> return r.message
            is LanResult.Ok -> r.value
        }
        pending = p
        _state.update { it.copy(waitingApproval = true, problem = null) }
        try {
            val deadline = clock() + HubProtocol.PAIR_WINDOW_MS
            while (pending === p && clock() < deadline) {
                when (val r = client.pollPair(p)) {
                    is LanResult.Err -> {
                        // کامپیوتر «دیگر نیست» گفت: تمام. شبکه نرسید: کمی بعد دوباره.
                        if (r.message != HUB_UNREACHABLE) return r.message
                        delay(pairRetryMs)
                    }
                    is LanResult.Ok -> when (r.value.state) {
                        // کامپیوتر ۲۵ ثانیه نگه داشت و هنوز «منتظر» است؛ مکثی کوتاه و دوباره.
                        PairState.WAIT -> delay(pairRetryMs)
                        PairState.REJECTED -> return r.value.reason.ifBlank { "کارفرما اجازه نداد." }
                        PairState.EXPIRED -> return "پنجرهٔ «افزودنِ گوشی» بسته شد؛ روی کامپیوتر دوباره باز کنید."
                        PairState.APPROVED -> return adopt(r.value, host)
                    }
                }
            }
            return if (pending === p) "ده دقیقه گذشت و کارفرما جواب نداد؛ دوباره امتحان کنید." else "جفت شدن لغو شد."
        } finally {
            if (pending === p) pending = null
            _state.update { it.copy(waitingApproval = false) }
        }
    }

    private suspend fun adopt(grant: PairGrant, host: String): String? {
        val name = PhoneName.current
        if (!HubLinkPrefs.save(settings, store(), grant, host, name)) return NO_SAFE_STORE
        // کارِ منتظرِ همان آدم با جفتِ تازه می‌رود؛ کارِ آدمِ دیگر «فرستاده نشد».
        val items = Outbox.rebind(HubLinkPrefs.outbox(settings), grant.deviceId.orEmpty(), grant.person, clock())
        HubLinkPrefs.saveOutbox(settings, items)
        failures = 0
        _state.update { HubClientState() }
        reload()
        sync()
        return null
    }

    /** «جدا کردنِ این گوشی» — کلید پاک؛ صفِ ارسال می‌ماند و دیده می‌شود. */
    fun forget() {
        HubLinkPrefs.forget(settings, store())
        _state.update { HubClientState() }
        reload()
    }

    // ================================================================ صف

    /**
     * یک کار در صف — همین لحظه، چه وصل باشد چه نه. `null` یعنی نشست؛ وگرنه
     * دلیلِ نپذیرفتن (نقشِ نادرست، صفِ پر).
     */
    fun enqueue(cmd: HubCommand, payload: Map<String, Any?>, summary: String): String? {
        val s = _state.value
        val role = s.role ?: return "این گوشی به کامپیوترِ کارگاه جفت نشده."
        if (role !in cmd.roles) return "این کار از «${role.label}» پذیرفته نیست."
        val deviceId = HubLinkPrefs.cred(settings, store())?.deviceId ?: return NO_KEY
        val item = OutboxItem(randomUuid().toString(), cmd.wire, payload, summary, clock(), deviceId, s.person)
        val (next, err) = Outbox.add(HubLinkPrefs.outbox(settings), item)
        HubLinkPrefs.saveOutbox(settings, next)
        _state.update { it.copy(outbox = next) }
        return err
    }

    fun clearFinished() {
        val next = Outbox.clearDone(HubLinkPrefs.outbox(settings))
        HubLinkPrefs.saveOutbox(settings, next)
        _state.update { it.copy(outbox = next) }
    }

    /** شبکه برگشت یا برنامه جلو آمد: همهٔ کارهای منتظر همین حالا. */
    suspend fun retryNow(): Boolean {
        HubLinkPrefs.saveOutbox(settings, Outbox.retryNow(HubLinkPrefs.outbox(settings), clock()))
        return sync()
    }

    // ================================================================ همگام‌سازی

    /** یک رفت‌وبرگشت؛ `true` اگر به کامپیوتر رسید. */
    suspend fun sync(): Boolean = lock.withLock {
        if (!HubLinkPrefs.paired(settings)) return@withLock false
        val cred = HubLinkPrefs.cred(settings, store())
        if (cred == null) {
            _state.update { it.copy(problem = NO_KEY, needsPairing = true, connected = false) }
            return@withLock false
        }
        _state.update { it.copy(syncing = true) }
        try {
            once(cred, retryClock = true)
        } finally {
            _state.update { it.copy(syncing = false) }
        }
    }

    private suspend fun once(cred: DeviceCred, retryClock: Boolean): Boolean {
        val now = clock()
        var items = Outbox.expire(HubLinkPrefs.outbox(settings), now)
        val due = Outbox.due(items, now)
        val host = HubLinkPrefs.host(settings)
        val port = HubLinkPrefs.port(settings)
        val ask = SyncAsk(now, HubLinkPrefs.cursor(settings), due.map { it.toWire() }, appVersion, platform)
        return when (val r = client.sync(cred, host, port, ask, HubLinkPrefs.offset(settings))) {
            is SyncResult.Ok -> {
                val at = clock()
                failures = 0
                items = Outbox.acked(items, r.reply.acks, at)
                HubLinkPrefs.saveOutbox(settings, items)
                HubLinkPrefs.setOffset(settings, r.offsetMs)
                HubLinkPrefs.saveReply(settings, r.reply, at)
                r.reply.snapshot?.let { HubLinkPrefs.saveSnapshot(settings, it, r.reply.cursor) }
                reportLicence(r.reply, at)
                _state.update {
                    it.copy(connected = true, problem = null, needsPairing = false, hubReadOnly = !r.reply.licenceWritable)
                }
                reload()
                true
            }
            is SyncResult.Clock -> {
                HubLinkPrefs.setOffset(settings, r.serverTime - clock())
                if (retryClock) once(cred, retryClock = false)
                else fail(items, due, CLOCK_OFF)
            }
            is SyncResult.Unpaired -> {
                _state.update { it.copy(connected = false, problem = r.message, needsPairing = true) }
                false
            }
            is SyncResult.Blocked -> fail(items, due, r.message)
            is SyncResult.Failed -> fail(items, due, r.message)
            is SyncResult.Unreachable -> {
                fail(items, due, offlineText())
                failures++
                if (failures >= 2 && clock() - lastFinderAt > FINDER_GAP_MS) {
                    lastFinderAt = clock()
                    if (refind(cred, host, port)) return once(cred, retryClock)
                }
                false
            }
        }
    }

    private fun fail(items: List<OutboxItem>, due: List<OutboxItem>, message: String): Boolean {
        val next = Outbox.failed(items, due.map { it.key }, clock())
        HubLinkPrefs.saveOutbox(settings, next)
        _state.update { it.copy(connected = false, problem = message, outbox = next) }
        return false
    }

    /**
     * کامپیوتر را دوباره پیدا کن؛ پیش از آن، اگر همان نشانی جواب می‌دهد ولی
     * فقط قراردادِ ۱ می‌گوید، **پایین نیا** ([ProtocolChoice]).
     */
    private suspend fun refind(cred: DeviceCred, host: String, port: Int): Boolean {
        (client.ping(host, port) as? LanResult.Ok)?.value?.let { ping ->
            if (ProtocolChoice.decide(pairedV2 = true, ping = ping) == ProtocolChoice.Use.REFUSE_DOWNGRADE) {
                _state.update { it.copy(problem = ProtocolChoice.DOWNGRADE_REFUSED, needsPairing = true) }
                return false
            }
        }
        val found = HubFinder.find(client, cred.hubId, port, host, HubLinkPrefs.alternatives(settings), ownIp())
            ?: return false
        if (found == host) return false
        HubLinkPrefs.setHost(settings, found)
        _state.update { it.copy(host = found) }
        return true
    }

    /** گوشی لایسنسِ خودش را ندارد؛ پیروِ کامپیوتر است — همان راهِ قراردادِ ۱. */
    private fun reportLicence(r: SyncReply, at: Long) {
        val state = r.licenceState?.let { s -> LicenceState.entries.firstOrNull { it.name == s } } ?: return
        LicenceGate.onMainReport?.invoke(MainLicence(state, r.licenceWritable, at))
    }

    private fun offlineText(): String {
        val last = HubLinkPrefs.lastSync(settings)
        return "به کامپیوترِ کارگاه وصل نیست" +
            (last?.let { " — آخرین داده: ${PersianDate.shortWithTime(it)}" } ?: "") + "."
    }

    companion object {
        const val FINDER_GAP_MS = 2 * 60_000L
        const val NO_SAFE_STORE = "این گوشی جای امنی برای کلیدِ اتصال ندارد؛ جفت نشد."
        const val NO_KEY = "کلیدِ اتصالِ این گوشی پیدا نشد؛ دوباره جفت کنید."
        const val CLOCK_OFF = "ساعتِ این گوشی با کامپیوترِ کارگاه جور نشد؛ ساعتِ گوشی را درست کنید."
    }
}

/** نامی که گوشی در درخواستِ جفت شدن می‌گوید — سکو سرِ راه‌اندازی می‌گذارد. */
object PhoneName {
    @kotlin.concurrent.Volatile
    var current: String = "گوشی"

    /** «android» یا «ios». */
    @kotlin.concurrent.Volatile
    var platform: String = ""
}

/** یک [HubSession] در هر پروسه — صفحه، تابلو و بیدار شدنِ برنامه همان را می‌خوانند. */
object HubSessions {
    @kotlin.concurrent.Volatile
    private var session: HubSession? = null

    fun of(settings: Settings, platform: String): HubSession =
        session ?: HubSession(settings, platform).also { session = it }

    /** اگر برنامه تا حالا ساخته‌اش — برای «برنامه جلو آمد» و «شبکه برگشت». */
    fun current(): HubSession? = session
}
