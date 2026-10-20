package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.DeviceKind
import com.afghanjama.lan.HostAddress
import com.afghanjama.lan.HubOff
import com.afghanjama.lan.LanClient
import com.afghanjama.lan.LanResult
import com.afghanjama.lan.LanHost
import com.afghanjama.lan.LanHub
import com.afghanjama.lan.LanPeers
import com.afghanjama.lan.RemoteWork
import com.afghanjama.prefs.Settings
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import com.afghanjama.licence.LicenceGate
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import com.afghanjama.util.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkshopLinkUi(
    /** گوشی یا کامپیوتر — صفحه «این کامپیوتر…» یا «این گوشی…» می‌گوید. */
    val kind: DeviceKind = DeviceKind.PHONE,
    val mode: DeviceMode = DeviceMode.STANDALONE,
    /** از [LanHub]، نه از خودِ صفحه — سرور با بسته شدنِ صفحه خاموش نمی‌شود. */
    val serving: Boolean = false,
    /** چرا اشتراک روشن نیست؛ `null` وقتی روشن است. */
    val hubOff: HubOff? = null,
    val ip: String? = null,
    /** نشانی‌های دیگرِ همین دستگاه — اگر حدسِ [ip] غلط بود. */
    val alternatives: List<String> = emptyList(),
    val code: String = "",
    val host: String = "",
    val deviceName: String = "",
    val myWork: List<RemoteWork> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false,
    val checking: Boolean = false
)

/**
 * اشتراکِ کارگاه روی وای‌فای — مدلِ «یک نویسنده».
 *
 * گوشیِ اصلی دفتر را دارد و سرور را بالا می‌آورد. گوشیِ کارگر فقط
 * می‌خواند و درخواست می‌فرستد؛ هیچ‌وقت چیزی نمی‌نویسد. برای همین
 * تضادِ دو نویسنده اصلاً پیش نمی‌آید و لازم نیست حلش کنیم.
 */
class WorkshopLinkViewModel(
    private val repo: Repo,
    /**
     * عمداً `lan` نام دارد نه `host`: در همین کلاس `host` از قبل به
     * معنیِ **نشانیِ دستگاهِ اصلی** به کار می‌رود و اگر هم‌نام می‌شدند،
     * `val host = LanPrefs.host(settings)`ِ محلی این را می‌پوشاند — که یک بار
     * شد و کامپایل را شکست.
     */
    private val lan: LanHost,
    /**
     * صاحبِ سرور. **صفحه دیگر سرور نمی‌سازد**: تا ۱.۹.۱ همین ViewModel
     * سرور را می‌ساخت و `onCleared` خاموشش می‌کرد، یعنی اشتراک فقط وقتی
     * روشن بود که کسی این صفحه را باز کرده بود. آزمون هابِ خودش را می‌دهد.
     */
    private val hub: LanHub = LanHub.shared,
    /**
     * دسترسیِ کسی که همین حالا وارد شده ([WorkshopLinkAccess]). صفحه
     * دکمه‌های غیرمجاز را پنهان می‌کند؛ اینجا دوباره سنجیده می‌شود تا
     * دکمه‌ای که روزی پنهان نشد هم کاری نکند.
     */
    private val access: () -> Access = { CurrentAccess.value },
) : ViewModel() {

    /**
     * کدِ طرحِ هر سفارش (DIP-12) — کلید: کدِ سفارش.
     * تعریفش یک‌جا در `Repo` است تا صفحه‌ها از هم جدا نیفتند.
     */
    val designCodeByOrder: StateFlow<Map<String, String>> =
        repo.observeDesignCodeByOrder()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _ui = MutableStateFlow(WorkshopLinkUi(kind = lan.kind))
    val ui: StateFlow<WorkshopLinkUi> =
        combine(_ui, hub.state) { u, h -> u.copy(serving = h.serving, hubOff = h.off) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, WorkshopLinkUi(kind = lan.kind))

    /** گوشی‌هایی که به این دستگاه رسیده‌اند ([LanPeers]) — از هاب، نه از صفحه. */
    val seen: StateFlow<LanPeers.Seen> = hub.seen

    /** درخواست‌های منتظر — فقط روی گوشیِ اصلی پر می‌شود. */
    val pending: StateFlow<List<SyncRequest>> =
        repo.observePendingRequests()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val busy = Busy()

    fun load(settings: Settings) {
        _ui.update {
            it.copy(
                mode = LanPrefs.mode(settings),
                code = LanPrefs.code(settings),
                host = LanPrefs.host(settings),
                deviceName = LanPrefs.deviceName(settings).ifBlank { lan.deviceName },
                ip = lan.localIp(),
                alternatives = otherAddresses()
            )
        }
    }

    fun setDeviceName(settings: Settings, name: String) {
        LanPrefs.setDeviceName(settings, name)
        _ui.update { it.copy(deviceName = name) }
    }

    // ---------------- گوشیِ اصلی ----------------

    /** دکمه‌ای که کارِ این نفر نیست: پیام، و هیچ کارِ دیگری. */
    private fun allowed(ok: Boolean): Boolean {
        if (!ok) _ui.update { it.copy(message = WorkshopLinkAccess.NOT_YOURS, isError = true) }
        return ok
    }

    private fun mayRunHub(): Boolean = allowed(WorkshopLinkAccess.canRunHub(access()))

    fun becomeMain(settings: Settings) {
        if (!mayRunHub()) return
        val code = LanPrefs.code(settings).ifBlank { LanPrefs.newCode().also { LanPrefs.setCode(settings, it) } }
        LanPrefs.setMode(settings, DeviceMode.MAIN)
        LicenceGate.update { it.copy(mode = DeviceMode.MAIN) }
        _ui.update { it.copy(code = code) }
        startServing(settings)
    }

    /** رمزِ تازه — بی بستنِ درگاه؛ گوشی‌هایی که با رمزِ کهنه بسته شده بودند باز می‌شوند. */
    fun newCode(settings: Settings) {
        if (!mayRunHub()) return
        val c = LanPrefs.newCode()
        LanPrefs.setCode(settings, c)
        hub.changeCode(c)
        _ui.update { it.copy(code = c) }
    }

    /**
     * «روشن» — و شبکه‌ای که دستگاه حالا رویش است شبکهٔ کارگاه می‌شود:
     * از این پس هر بار که برنامه روی همین شبکه باز شود، خودش روشن می‌شود.
     */
    fun startServing(settings: Settings) {
        if (!mayRunHub()) return
        val ok = hub.start(settings, lan)
        _ui.update {
            it.copy(
                mode = DeviceMode.MAIN,
                code = LanPrefs.code(settings),
                ip = lan.localIp(),
                alternatives = otherAddresses(),
                message = if (ok) "اشتراکِ کارگاه روشن شد. از این پس هر بار که برنامه روی همین شبکه باز شود، خودش روشن می‌شود."
                else hubOffText(hub.state.value.off) ?: "اشتراک روشن نشد.",
                isError = !ok
            )
        }
    }

    private fun otherAddresses(): List<String> {
        val best = lan.localIp()
        return lan.localIps().filter { it != best }
    }

    fun stopServing() {
        if (!mayRunHub()) return
        stopHub()
    }

    private fun stopHub() {
        hub.stop()
        _ui.update { it.copy(message = "اشتراکِ کارگاه خاموش شد.", isError = false) }
    }

    /**
     * نوعِ درخواست از خودِ دفتر خوانده می‌شود، نه از صفحه؛ دسترسی بر همان
     * سنجیده می‌شود ([WorkshopLinkAccess.canDecide]).
     */
    private suspend fun mayDecide(id: Long): Boolean {
        val a = access()
        if (!WorkshopLinkAccess.seesRequests(a)) return allowed(false)
        val r = repo.observePendingRequests().first().firstOrNull { it.id == id } ?: return true
        return allowed(WorkshopLinkAccess.canDecide(a, r.type))
    }

    fun approve(id: Long) = viewModelScope.launch {
        busy.once {
            if (!mayDecide(id)) return@once
            val err = repo.approveRequest(id)
            _ui.update {
                if (err == null) it.copy(message = "تأیید و ثبت شد.", isError = false)
                else it.copy(message = err, isError = true)
            }
        }
    }

    fun reject(id: Long) = viewModelScope.launch {
        busy.once {
            if (!mayDecide(id)) return@once
            repo.rejectRequest(id)
            _ui.update { it.copy(message = "درخواست رد شد.", isError = false) }
        }
    }

    // ---------------- گوشیِ کارگر ----------------

    fun becomeWorker(settings: Settings, host: String, code: String) = viewModelScope.launch {
        // نشانی **اینجا هم** وارسی می‌شود، نه فقط در صفحه.
        //
        // چون این تابع از جای دیگری هم صدا زده می‌شود (اتصالِ دوبارهٔ
        // خودکار با نشانیِ ذخیره‌شده)، و نشانیِ بدی که یک بار ذخیره شده
        // باشد وگرنه هر بار همان پیامِ گمراه‌کنندهٔ «وای‌فای را ببینید»
        // را می‌دهد.
        val clean = when (val r = HostAddress.check(host)) {
            is HostAddress.Result.Bad -> {
                _ui.update { it.copy(checking = false, message = r.reason, isError = true) }
                return@launch
            }
            is HostAddress.Result.Ok -> r.host
        }
        _ui.update { it.copy(checking = true, message = null) }
        val client = LanClient(clean, code.trim())
        when (val r = client.ping()) {
            is LanResult.Err -> _ui.update {
                it.copy(checking = false, message = r.message, isError = true)
            }
            is LanResult.Ok -> {
                LanPrefs.setMode(settings, DeviceMode.WORKER)
                LicenceGate.update { it.copy(mode = DeviceMode.WORKER) }
                LanPrefs.setHost(settings, clean)
                LanPrefs.setCode(settings, code)
                _ui.update {
                    it.copy(
                        checking = false, mode = DeviceMode.WORKER,
                        host = clean, code = code.trim(),
                        message = "وصل شد.", isError = false
                    )
                }
            }
        }
    }

    fun refreshMyWork(settings: Settings, worker: String) = viewModelScope.launch {
        val host = LanPrefs.host(settings)
        if (host.isBlank()) return@launch
        _ui.update { it.copy(checking = true) }
        when (val r = LanClient(host, LanPrefs.code(settings)).myWork(worker)) {
            is LanResult.Err -> _ui.update {
                it.copy(checking = false, message = r.message, isError = true)
            }
            is LanResult.Ok -> _ui.update {
                it.copy(checking = false, myWork = r.value, message = null, isError = false)
            }
        }
    }

    fun sendRequest(
        settings: Settings,
        worker: String,
        type: String,
        summary: String,
        refId: Long = 0,
        amount: Int = 0,
        note: String = ""
    ) = viewModelScope.launch {
        busy.once {
            val host = LanPrefs.host(settings)
            val client = LanClient(host, LanPrefs.code(settings))
            val device = LanPrefs.deviceName(settings).ifBlank { lan.deviceName }
            when (val r = client.sendRequest(device, worker, type, summary, refId, amount, note)) {
                is LanResult.Err -> _ui.update { it.copy(message = r.message, isError = true) }
                is LanResult.Ok -> _ui.update { it.copy(message = r.value, isError = false) }
            }
        }
    }

    /**
     * برگشت به حالتِ تک‌دستگاه. روی دستگاهِ اصلی فقط مدیر؛ روی گوشیِ کارگر
     * هر کس — آن گوشیِ خودِ کارگر است.
     */
    fun disconnect(settings: Settings) {
        val wasMain = LanPrefs.mode(settings) == DeviceMode.MAIN
        if (wasMain && !mayRunHub()) return
        LanPrefs.setMode(settings, DeviceMode.STANDALONE)
        LicenceGate.update { it.copy(mode = DeviceMode.STANDALONE) }
        stopHub()
        // دستگاهی که دیگر اصلی نیست شبکهٔ کارگاه را هم فراموش می‌کند؛ اگر
        // دوباره اصلی شود، همان لحظه از نو به یاد می‌سپارد.
        if (wasMain) LanPrefs.setNetwork(settings, "")
        _ui.update { it.copy(mode = DeviceMode.STANDALONE, myWork = emptyList()) }
    }

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    // `onCleared` دیگر سرور را خاموش نمی‌کند: سرور مالِ [LanHub] است، نه این صفحه.
}

/** چند گوشی از سرِ امروز (ساعتِ ۰۰:۰۰ همین دستگاه) به این دستگاه رسیده‌اند. */
fun phonesToday(seen: LanPeers.Seen, now: Long = nowMillis()): Int {
    val d = PersianDate.todayJalali(now)
    return seen.countSince(PersianDate.startOfJalaliDay(d[0], d[1], d[2]))
}

/**
 * «امروز ۳ گوشی به این کامپیوتر وصل شد؛ آخرین بار ساعتِ ۱۰:۴۲.»
 *
 * روی کامپیوتر، وقتی هنوز هیچ گوشی‌ای نرسیده، دیوارِ آتش را هم یاد می‌دهد:
 * گوشیِ کارگر فقط «وصل نشد» می‌بیند، و اینجا تنها جایی است که معلوم
 * می‌شود درخواستش اصلاً به کامپیوتر نرسیده.
 */
fun phonesSeenText(seen: LanPeers.Seen, kind: DeviceKind, now: Long = nowMillis()): String {
    val n = phonesToday(seen, now)
    val last = seen.lastAt
    if (n == 0 || last == null) {
        return "امروز هنوز هیچ گوشی‌ای به این ${kind.noun} نرسیده." +
            if (kind == DeviceKind.COMPUTER) " اگر گوشیِ کارگر «وصل نشد» می‌گوید، دیوارِ آتشِ این کامپیوتر را ببینید." else ""
    }
    return "امروز ${n.fa()} گوشی به این ${kind.noun} وصل شد؛ آخرین بار ساعتِ ${PersianDate.time(last)}."
}

/**
 * چرا اشتراک روشن نیست، به زبانِ کارفرما. `null` برای حالت‌هایی که چیزی
 * برای گفتن ندارند (روشن است، یا این دستگاه اصلی نیست).
 */
fun hubOffText(off: HubOff?): String? = when (off) {
    null, HubOff.NOT_MAIN -> null
    HubOff.WAITING -> "اشتراک در حالِ روشن شدن است…"
    HubOff.NO_CODE -> "رمزِ اتصال نیست؛ «رمز تازه» را بزنید."
    HubOff.NEW_NETWORK ->
        "شبکهٔ کارگاه هنوز شناخته نشده. یک بار «روشن» را بزنید؛ از آن پس هر بار که " +
            "برنامه روی همین شبکه باز شود، اشتراک خودش روشن می‌شود."
    HubOff.OTHER_NETWORK ->
        "این دستگاه حالا روی شبکهٔ دیگری است، نه شبکهٔ کارگاه؛ برای همین اشتراک خودش " +
            "روشن نشد. اگر همین‌جا کارگاه است، «روشن» را بزنید تا این شبکه شبکهٔ کارگاه شود."
    HubOff.NO_NETWORK ->
        "این دستگاه به وای‌فای یا شبکهٔ کارگاه وصل نیست. همین که وصل شود، اشتراک خودش روشن می‌شود."
    HubOff.PORT_BUSY -> "پورت باز نشد؛ شاید برنامهٔ دیگری آن را گرفته باشد."
    HubOff.NO_LEDGER -> "دفترِ کارگاه باز نشد؛ اشتراک بی دفتر روشن نمی‌شود."
    HubOff.STOPPED ->
        "اشتراک خاموش است. دفعهٔ بعد که برنامه روی شبکهٔ کارگاه باز شود، خودش روشن می‌شود."
}
