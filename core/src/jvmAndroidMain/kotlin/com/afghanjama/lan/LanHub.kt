package com.afghanjama.lan

import com.afghanjama.data.Db
import com.afghanjama.licence.LicenceGate
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import com.afghanjama.prefs.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import java.net.InetAddress

/**
 * صاحبِ یگانهٔ سرورِ اشتراکِ کارگاه در این پروسه — همان‌قدر عمر می‌کند که
 * خودِ برنامه، نه یک صفحه.
 *
 * **چرا لازم شد.** تا ۱.۹.۱ سرور را `WorkshopLinkViewModel` می‌ساخت و فقط
 * وقتی روشن می‌شد که کسی صفحهٔ «اشتراکِ کارگاه» را باز کند و «روشن» بزند؛
 * با بسته شدنِ همان صفحه (`onCleared`) هم خاموش می‌شد. یعنی کارفرمایی که
 * صبح برنامه را باز می‌کرد و سراغِ کارِ دیگری می‌رفت، تا وقتی به آن صفحه
 * سر نمی‌زد هیچ گوشیِ کارگری وصل نمی‌شد.
 *
 * حالا:
 *
 * - [autoStart] سرِ باز شدنِ برنامه یک بار صدا زده می‌شود (روی گوشی در
 *   `App.onCreate`، روی کامپیوتر در `main`). **اول صبر می‌کند تا لایسنس
 *   خوانده شود**: پیش از آن `LicenceGate.canWrite()` همیشه «بله» است و
 *   دستگاهِ فقط‌خواندنی در همان ثانیهٔ اول درخواستِ کارگر را می‌نشاند.
 * - **فقط روی شبکهٔ کارگاه روشن می‌شود** ([LanNetwork])؛ همانی که وقتِ
 *   «این دستگاهِ اصلی است» یا «روشن»ِ دستی به یاد سپرده شد. روی شبکهٔ
 *   دیگر خاموش می‌ماند و می‌گوید چرا ([HubOff]).
 * - تا وقتی خواسته شده، هر چند ثانیه شبکه را دوباره می‌بیند: لپ‌تاپی که
 *   در کارگاه به خواب رفت و در چایخانه بیدار شد خاموش می‌شود، و برگشته به
 *   کارگاه دوباره روشن. برنامه‌ای که پیش از وصل شدنِ وای‌فای باز شد هم
 *   همین‌طور، همین که وای‌فای وصل شد.
 * - صفحه فقط می‌خوانَد ([state]) و فرمان می‌دهد ([start]، [stop]،
 *   [changeCode]). بسته شدنِ صفحه سرور را خاموش نمی‌کند.
 *
 * برنامه همیشه [shared] را می‌گیرد؛ سازنده برای آزمونِ دودی باز است که
 * روی loopback و درگاهِ خودش، با ساعت‌های کوتاه، کار می‌کند.
 */
class LanHub(
    private val bindAddress: InetAddress? = null,
    private val port: Int = Lan.PORT,
    /** ساعتِ نگهبانِ رمز ([LanGuard]) و شمارِ گوشی‌ها ([LanPeers])؛ آزمون ساعتِ خودش را می‌دهد. */
    private val guardClock: () -> Long = { System.currentTimeMillis() },
    /** صبر تا لایسنس خوانده شود — پیش از آن هر نوشتنی «مجاز» است. */
    private val licenceLoaded: suspend () -> Unit = { LicenceGate.status.first { it != null } },
    /** هر چند وقت شبکه دوباره دیده شود، وقتی خاموش است و منتظرِ کارگاه. */
    private val offIntervalMs: Long = 15_000,
    /** و وقتی روشن است. */
    private val onIntervalMs: Long = 60_000,
    /** کدِ این دستگاه برای شناسنامهٔ کارگاه؛ آزمون کدِ خودش را می‌دهد. */
    private val machine: (Settings) -> String = { com.afghanjama.licence.MachineIds.resolve(it).code },
    /** سقفِ انتظارِ پرسشِ «اجازه» ([HubService]). */
    private val pairLongPollMs: Long = HubProtocol.PAIR_LONG_POLL_MS,
    /**
     * دستگاهِ دیگری با همین hubId جواب می‌دهد؟ — پیش از «مرکز اینجاست» پس از
     * بازیابی. برنامه /۲۴ِ همین دستگاه را می‌گردد ([HubFinder])؛ آزمون جوابِ
     * خودش را می‌دهد.
     */
    private val findTwin: suspend (hubId: String, own: List<String>, port: Int) -> String? = { id, own, p ->
        val client = HubClient(hubTransport())
        own.firstNotNullOfOrNull { ip ->
            HubFinder.subnet24(ip).filter { it !in own }.chunked(HubFinder.PARALLEL).firstNotNullOfOrNull { chunk ->
                kotlinx.coroutines.coroutineScope {
                    chunk.map { cand ->
                        async {
                            cand.takeIf { (client.ping(it, p, HubFinder.PROBE_CONNECT_MS) as? LanResult.Ok)?.value?.hubId == id }
                        }
                    }.map { it.await() }.firstOrNull { it != null }
                }
            }
        }
    },
) {

    /** آنچه صفحه نشان می‌دهد. */
    data class State(
        val serving: Boolean = false,
        /** چرا روشن نیست؛ `null` وقتی روشن است یا هنوز کسی چیزی نخواسته. */
        val off: HubOff? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * گوشی‌هایی که به این دستگاه رسیده‌اند ([LanPeers]). با هاب می‌ماند، نه با
     * سرور: سرورِ تازه پس از بازیابی همان گوشی‌ها را دوباره نمی‌شمارد.
     */
    private val peers = LanPeers(guardClock)
    val seen: StateFlow<LanPeers.Seen> get() = peers.seen

    /**
     * پنجرهٔ «افزودنِ گوشی» و حافظهٔ تکرارِ قراردادِ ۲ — با هاب می‌مانند:
     * خاموش و روشن شدنِ سرور (یا شبکه) پنجرهٔ باز را نمی‌بندد و nonceهای
     * پذیرفته را فراموش نمی‌کند.
     */
    private val pairingWindow = HubPairing(guardClock)
    private val replay = ReplayGuard(guardClock)
    val pairing: StateFlow<HubPairing.View> get() = pairingWindow.view
    private var service: HubService? = null

    /** سرویسِ قراردادِ ۲ِ همین لحظه — برای صفحه و آزمونِ دودی. */
    fun hubService(): HubService? = synchronized(lock) { service }

    private val lock = Any()
    // IO، چون شناختنِ شبکه روی کامپیوتر یک دستورِ سیستم را اجرا می‌کند و منتظرش می‌ماند.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var server: LanServer? = null
    private var serverDb: Db? = null
    private var settings: Settings? = null
    private var lan: LanHost? = null

    /** شبکهٔ کارگاه، در حافظه — فقط [start] و [autoStart] عوضش می‌کنند. */
    @Volatile private var remembered: LanNetwork? = null

    /** کارفرما می‌خواهد روشن باشد (هر وقت روی شبکهٔ کارگاه است). */
    @Volatile private var wanted = false
    private var autoStarted = false
    private var watch: Job? = null

    /** درگاهی که سرور گرفته؛ صفر وقتی خاموش است. */
    val boundPort: Int get() = synchronized(lock) { server?.takeIf { it.running }?.boundPort ?: 0 }

    /**
     * سرِ باز شدنِ برنامه، **یک بار در هر پروسه**. بارِ دوم (مثلاً با
     * چرخاندنِ گوشی) هیچ نمی‌کند؛ وگرنه «خاموش»ِ کارفرما را پس می‌گرفت.
     */
    fun autoStart(settings: Settings, lan: LanHost) {
        synchronized(lock) {
            if (autoStarted) return
            autoStarted = true
            this.settings = settings
            this.lan = lan
            remembered = LanNetwork.decode(LanPrefs.network(settings))
            if (server?.running == true) return
            wanted = true
            _state.value = State(off = HubOff.WAITING)
            watch?.cancel()
            watch = scope.launch {
                licenceLoaded()
                watchLoop()
            }
        }
    }

    /**
     * کارفرما «روشن» زد، یا همین حالا «این دستگاهِ اصلی است». شبکه‌ای که
     * دستگاه رویش است شبکهٔ کارگاه می‌شود — همان رضایتی که «اصلی» شدن بود.
     *
     * @return `false` اگر روشن نشد؛ دلیل در [state].
     */
    fun start(settings: Settings, lan: LanHost): Boolean {
        val ok = synchronized(lock) {
            this.settings = settings
            this.lan = lan
            autoStarted = true // کارفرما خودش روشن کرد؛ بازِ دیگرِ همین پروسه دخالت نکند
            wanted = true
            open(lan)
        }
        // شناختنِ شبکه روی کامپیوتر یک دستورِ سیستم است؛ نه روی رشتهٔ صفحه.
        restartWatch(rememberFirst = true)
        return ok
    }

    /** کارفرما «خاموش» زد، اشتراک را کنار گذاشت، یا دفتر بازیابی می‌شود. */
    fun stop() {
        synchronized(lock) {
            wanted = false
            watch?.cancel()
            watch = null
            server?.stop()
            _state.value = State(off = HubOff.STOPPED)
        }
    }

    /**
     * دفتر بازیابی می‌شود: مثلِ [stop]، ولی بارِ بعدی که برنامه باز شود
     * ([autoStart]) دوباره خودش روشن می‌کند.
     *
     * روی گوشی «بستنِ اپ» پس از بازیابی پروسه را نمی‌کُشد و `App.onCreate`
     * دوباره اجرا نمی‌شود؛ با [stop]ِ ساده اشتراک تا مرگِ پروسه خاموش
     * می‌ماند، در حالی که صفحه می‌گفت «دفعهٔ بعد که برنامه باز شود خودش
     * روشن می‌شود». `MainActivity` هم [autoStart] را صدا می‌زند؛ بی این،
     * آن صدا هیچ نمی‌کند.
     */
    fun stopForRestore() {
        synchronized(lock) {
            stop()
            autoStarted = false
        }
    }

    /**
     * رمزِ تازه، بی بستنِ درگاه؛ گوشی‌هایی که با رمزِ کهنه بسته شده بودند
     * باز می‌شوند ([LanServer.changeCode]). اگر خاموش است، بارِ بعد که
     * روشن شود رمزِ تازه را از تنظیمات می‌خوانَد.
     */
    fun changeCode(code: String): Boolean = synchronized(lock) {
        server?.takeIf { it.running }?.changeCode(code) ?: true
    }

    // ---------------------------------------------------------------

    private fun restartWatch(rememberFirst: Boolean) {
        synchronized(lock) {
            watch?.cancel()
            watch = scope.launch {
                if (rememberFirst) rememberNetwork()
                delay(onIntervalMs)
                watchLoop()
            }
        }
    }

    /** شبکهٔ نشانیِ نشان‌داده‌شده (همانی که در QR است) شبکهٔ کارگاه می‌شود. */
    private fun rememberNetwork() {
        val (s, l) = synchronized(lock) { (settings ?: return) to (lan ?: return) }
        val ip = l.localIp() ?: return
        val net = runCatching { l.networks()[ip] }.getOrNull() ?: return
        remembered = net
        LanPrefs.setNetwork(s, net.encode())
    }

    private suspend fun watchLoop() {
        while (scope.isActive) {
            val (s, l) = synchronized(lock) {
                if (!wanted) return
                (settings ?: return) to (lan ?: return)
            }
            val mode = LanPrefs.mode(s)
            val code = LanPrefs.code(s)
            val known = remembered
            // شبکه فقط وقتی دیده می‌شود که به کار بیاید: روی کامپیوتر یعنی
            // اجرای `route`/`arp`، و کامپیوتری که اصلاً «اصلی» نیست نباید سرِ
            // هر باز شدن ابزارِ سیستم اجرا کند.
            val current = if (mode == DeviceMode.MAIN && code.isNotBlank() && known != null) {
                runCatching { l.networks().values }.getOrDefault(emptyList())
            } else emptyList()
            val off = AutoStartRule.decide(mode, code, known, current)
            val serving = synchronized(lock) {
                if (!wanted) return
                val running = server?.running == true
                when (off) {
                    null -> if (!running) open(l)
                    HubOff.OTHER_NETWORK -> {
                        // بیرون از کارگاه: بسته، و منتظرِ برگشت.
                        server?.stop()
                        _state.value = State(off = off)
                    }
                    HubOff.NO_NETWORK -> {
                        // بی‌شبکه به کسی گوش نمی‌دهد؛ اگر روشن است بماند تا وای‌فای برگردد.
                        if (!running) _state.value = State(off = off)
                    }
                    else -> if (!running) {
                        // نقش، رمز یا شبکهٔ کارگاه کم است: کارِ کارفرماست، نه انتظار.
                        _state.value = State(off = off)
                        wanted = false
                        return
                    }
                }
                server?.running == true
            }
            delay(if (serving) onIntervalMs else offIntervalMs)
        }
    }

    /** روشن کردن با رمز و دفترِ همین لحظه. باید درونِ [lock] صدا زده شود. */
    private fun open(l: LanHost): Boolean {
        val code = settings?.let { LanPrefs.code(it) }.orEmpty()
        if (code.isBlank()) {
            _state.value = State(off = HubOff.NO_CODE)
            return false
        }
        val db = runCatching { l.ledger() }.getOrNull()
        if (db == null) {
            _state.value = State(off = HubOff.NO_LEDGER)
            return false
        }
        // دفترِ تازه (پس از بازیابی) یعنی سرورِ تازه؛ نگهبانِ رمز با همان سرور می‌ماند.
        var srv = server
        if (srv == null || serverDb !== db) {
            srv?.stop()
            val guard = LanGuard(guardClock)
            val hub = HubService(
                db, guard, guardClock,
                addresses = { l.localIps() to (server?.boundPort?.takeIf { it > 0 } ?: port) },
                pairing = pairingWindow,
                replay = replay,
                longPollMs = pairLongPollMs,
            )
            service = hub
            srv = LanServer(db, bindAddress, port, guard, peers, hub)
            server = srv
            serverDb = db
            // شناسنامهٔ کارگاه از دفتر — کند است (پرس‌وجو)، پس بیرون از قفل.
            val s = settings
            scope.launch {
                val here = s?.let { runCatching { machine(it) }.getOrNull() }.orEmpty()
                val name = s?.let { com.afghanjama.prefs.CompanyPrefs.shopName(it) } ?: l.deviceName
                val ok = runCatching { hub.prepare(here, name) }.getOrDefault(false)
                if (!ok) synchronized(lock) {
                    if (service === hub) {
                        server?.stop()
                        _state.value = State(off = HubOff.MOVED)
                    }
                }
            }
        }
        if (service?.moved == true) {
            _state.value = State(off = HubOff.MOVED)
            return false
        }
        val ok = srv.running || srv.start(code)
        _state.value = if (ok) State(serving = true) else State(off = HubOff.PORT_BUSY)
        return ok
    }

    // ---------------------------------------------------------------
    // قراردادِ ۲ — آنچه صفحهٔ «کارگاهِ وصل» صدا می‌زند

    /**
     * «افزودنِ گوشی»: پنجرهٔ ده‌دقیقه‌ای با QR و رمزِ دستی. فقط وقتی سرور روشن
     * و آماده است؛ QRی که به سرورِ خاموش اشاره کند کارگر را سرگردان می‌کند.
     */
    fun openPairing(allowCode: Boolean = true): HubPairing.View? = synchronized(lock) {
        val hub = service?.takeIf { it.ready } ?: return null
        val srv = server?.takeIf { it.running } ?: return null
        val host = lan?.localIp() ?: return null
        pairingWindow.open(host, srv.boundPort, hub.hubId(), allowCode)
    }

    fun closePairing() = pairingWindow.close()

    /** «اجازه»؛ `null` یعنی انجام شد، وگرنه دلیل. */
    suspend fun approvePairing(role: HubRole, person: String, name: String? = null): String? {
        val hub = hubService() ?: return "کارگاهِ وصل روشن نیست."
        return hub.approve(role, person, name)
    }

    fun rejectPairing(): Boolean = hubService()?.reject() ?: false

    /** «قطعِ دسترسی». */
    suspend fun revokeDevice(deviceId: String): Boolean = hubService()?.revoke(deviceId) ?: false

    /**
     * «مرکز اینجاست» — پس از بازیابیِ دفتر روی این دستگاه. پیش از آن
     * /۲۴ِ همین دستگاه گشته می‌شود: اگر دستگاهِ دیگری هنوز با همین hubId
     * جواب بدهد، روشن نمی‌شود (نقدِ ۶). دو مرکز با یک دفتر یعنی دو حسابِ جدا.
     *
     * @return نشانیِ دستگاهِ دیگر، اگر پیدا شد؛ `null` یعنی روشن شد.
     */
    suspend fun confirmHere(): String? {
        val (hub, s, l) = synchronized(lock) { Triple(service, settings, lan) }
        if (hub == null || s == null || l == null) return null
        val id = hub.identityRow()?.hubId ?: return null
        val twin = findTwin(id, l.localIps(), port)
        if (twin != null) {
            _state.value = State(off = HubOff.DUPLICATE_HUB)
            return twin
        }
        hub.confirmHere(runCatching { machine(s) }.getOrDefault(""))
        start(s, l)
        return null
    }

    companion object {
        /** تنها هابِ برنامه. */
        val shared: LanHub by lazy { LanHub() }
    }
}
