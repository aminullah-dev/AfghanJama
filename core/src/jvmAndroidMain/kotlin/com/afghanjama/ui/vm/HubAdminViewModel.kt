package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.AppVersion
import com.afghanjama.data.entities.LanDevice
import com.afghanjama.data.entities.LanHubRow
import com.afghanjama.data.entities.LegacyMode
import com.afghanjama.data.entities.RequestTime
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.entities.clockSuspect
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.HubPairing
import com.afghanjama.lan.HubRole
import com.afghanjama.lan.LanHub
import com.afghanjama.lan.LanPeers
import com.afghanjama.ui.format.fa
import com.afghanjama.util.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * «کارگاهِ وصل» از چشمِ مرکز — کامپیوتر، یا گوشیِ اصلیِ اندرویدی.
 *
 * پنجرهٔ «افزودنِ گوشی»، فهرستِ دستگاه‌ها، صندوقِ درخواست‌ها و حالتِ گوشی‌های
 * قدیم. هر دکمه دوباره دسترسی را می‌سنجد ([WorkshopLinkAccess])، حتی اگر صفحه
 * پنهانش نکرده باشد: جفت کردن، قطعِ دسترسی و تغییرِ نقش فقط با مدیر؛ تأییدِ هر
 * درخواست با کسی که تیکِ همان کار را دارد.
 */
class HubAdminViewModel(
    private val repo: Repo,
    private val hub: LanHub = LanHub.shared,
    private val access: () -> Access = { CurrentAccess.value },
    private val clock: () -> Long = { nowMillis() },
) : ViewModel() {

    val hubState: StateFlow<LanHub.State> = hub.state
    val pairing: StateFlow<HubPairing.View> = hub.pairing
    val seen: StateFlow<LanPeers.Seen> = hub.seen

    val devices: StateFlow<List<LanDevice>> =
        repo.observeLanDevices().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pending: StateFlow<List<SyncRequest>> =
        repo.observePendingRequests().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hubRow: StateFlow<LanHubRow?> =
        repo.observeHubRow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * کسانی که گوشیِ کارگر می‌تواند به نامشان باشد: خیاط‌ها، ناظرها و
     * **کارمندها** (نقدِ ۲۴ — بی کارمند، حضورِ گوشیِ کارمند با قراردادِ ۲ گم
     * می‌شد).
     */
    val people: StateFlow<List<String>> =
        combine(repo.observeTailors(), repo.observeInspectors(), repo.observeStaff()) { t, i, s ->
            (t.map { "[${it.code}] ${it.name}" } + i.map { "[${it.code}] ${it.name}" } + s.map { it.name.trim() })
                .filter { it.isNotBlank() }.distinct()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** ساعتِ صفحه — نقطهٔ «آنلاین» و شمارشِ معکوسِ پنجره با آن زنده می‌مانند. */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(clock())
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), clock())

    private val _message = MutableStateFlow<Pair<String, Boolean>?>(null)
    val message: StateFlow<Pair<String, Boolean>?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    private fun say(text: String, error: Boolean = false) {
        _message.value = text to error
    }

    private fun manager(): Boolean {
        val ok = WorkshopLinkAccess.canRunHub(access())
        if (!ok) say(WorkshopLinkAccess.NOT_YOURS, error = true)
        return ok
    }

    private fun work(block: suspend () -> Unit) = viewModelScope.launch {
        if (_busy.value) return@launch
        _busy.value = true
        try {
            block()
        } finally {
            _busy.value = false
        }
    }

    // ------------------------------------------------------------ پنجرهٔ «افزودنِ گوشی»

    fun openPairing(allowCode: Boolean = true) {
        if (!manager()) return
        if (hub.openPairing(allowCode) == null) {
            say("اول «کارگاهِ وصل» باید روشن باشد (و این دستگاه به وای‌فای یا شبکهٔ کارگاه وصل).", error = true)
        }
    }

    fun closePairing() {
        if (manager()) hub.closePairing()
    }

    fun approvePairing(role: HubRole, person: String) = work {
        if (!manager()) return@work
        val err = hub.approvePairing(role, person)
        if (err == null) say("جفت شد. گوشی همین حالا کلیدش را می‌گیرد.") else say(err, error = true)
    }

    fun rejectPairing() {
        if (!manager()) return
        if (hub.rejectPairing()) say("درخواستِ جفت شدن رد شد.")
    }

    // ------------------------------------------------------------ دستگاه‌ها

    fun revoke(d: LanDevice) = work {
        if (!manager()) return@work
        if (hub.revokeDevice(d.deviceId)) say("دسترسیِ «${d.name}» برداشته شد؛ همگام‌سازیِ بعدی‌اش رد می‌شود.")
        else say("دسترسیِ «${d.name}» برداشته نشد (شاید پیش‌تر برداشته شده).", error = true)
    }

    fun changeRole(d: LanDevice, role: HubRole, person: String) = work {
        if (!manager()) return@work
        if (role == HubRole.OWNER && HubRole.of(d.role) != HubRole.OWNER) {
            // گوشیِ صاحب‌کار فقط با QR جفت می‌شود؛ ارتقای گوشیِ رمزِ دستی به
            // صاحب‌کار همان دری است که QR بسته بود.
            say("گوشیِ صاحبِ کار را از نو با QR جفت کنید؛ نقشِ گوشیِ دیگر صاحب‌کار نمی‌شود.", error = true)
            return@work
        }
        if (repo.changeLanDeviceRole(d.deviceId, role, person)) say("نقشِ «${d.name}» عوض شد.")
        else say("نقش عوض نشد — برای گوشیِ کارگر، کارگرش را انتخاب کنید.", error = true)
    }

    /** گوشی‌های نسخهٔ قدیم (رمزِ شش‌رقمی). */
    fun setLegacy(mode: LegacyMode) = work {
        if (!manager()) return@work
        val svc = hub.hubService()
        if (svc != null) svc.updateIdentity { it.copy(legacyV1 = mode.name) }
        else repo.updateHubRow { it.copy(legacyV1 = mode.name) }
        say(
            when (mode) {
                LegacyMode.ON -> "گوشی‌های نسخهٔ قدیم پذیرفته می‌شوند."
                LegacyMode.OFF -> "گوشی‌های نسخهٔ قدیم دیگر پذیرفته نمی‌شوند و پیامِ «به‌روز کنید» می‌گیرند."
                LegacyMode.AUTO -> "خودکار: تا ۳۰ روز پس از آخرین گوشیِ نسخهٔ قدیم."
            }
        )
    }

    /** پس از بازیابیِ دفتر روی این دستگاه: «مرکز اینجاست». */
    fun confirmHere() = work {
        if (!manager()) return@work
        val twin = hub.confirmHere()
        if (twin == null) say("این دستگاه حالا مرکزِ کارگاه است؛ گوشی‌ها خودشان دوباره پیدایش می‌کنند.")
        else say("دستگاهِ دیگری در نشانیِ $twin هنوز با همین دفتر جواب می‌دهد؛ اول آن را خاموش کنید.", error = true)
    }

    // ------------------------------------------------------------ صندوقِ درخواست‌ها

    // همان درِ داشبورد ([RequestDecisions]): اجازه پیش از هر کاری پرسیده می‌شود.
    fun approve(r: SyncRequest, time: RequestTime = RequestTime.AUTO) = work {
        val err = RequestDecisions.approve(repo, access(), r, time)
        if (err == null) say("تأیید و ثبت شد.") else say(err, error = true)
    }

    fun reject(r: SyncRequest) = work {
        val err = RequestDecisions.reject(repo, access(), r)
        if (err == null) say("درخواست رد شد.") else say(err, error = true)
    }

    /**
     * «تأییدِ همه» برای ورود و خروج — به ترتیبِ زدنِ دکمه. آنهایی که ساعتِ
     * گوشی‌شان معتبر نیست جدا می‌مانند تا کارفرما یکی‌یکی انتخاب کند؛ هر کدام
     * که ثبت‌شدنی نبود هم با دلیل می‌ماند.
     */
    fun approveAllAttendance(worker: String?) = work {
        if (!WorkshopLinkAccess.canDecide(access(), "ATTENDANCE_IN")) {
            say(WorkshopLinkAccess.NOT_YOURS, error = true)
            return@work
        }
        val list = repo.observePendingRequests().first()
            .filter { (it.type == "ATTENDANCE_IN" || it.type == "ATTENDANCE_OUT") && !it.clockSuspect() }
            .filter { worker == null || it.worker == worker }
            .sortedWith(compareBy({ it.clientAt ?: it.createdAt }, { it.id }))
        var ok = 0
        var left = 0
        for (r in list) {
            // ممکن است با تأییدِ «خروجِ» بعدی، «ورودِ» قبلی خودش تأیید شده باشد.
            if (repo.observePendingRequests().first().none { it.id == r.id }) continue
            if (repo.approveRequest(r.id) == null) ok++ else left++
        }
        say(
            if (left == 0) "${ok.fa()} حضور تأیید شد."
            else "${ok.fa()} حضور تأیید شد؛ ${left.fa()} تا ثبت‌شدنی نبود و در صندوق ماند (دلیل روی کارتش).",
            error = left > 0,
        )
    }

    companion object {
        /** نسخهٔ گوشی از نسخهٔ این دستگاه عقب‌تر است؟ (سه عدد؛ ناخوانا یعنی نه.) */
        fun outdated(phone: String, here: String = AppVersion.current): Boolean = AppVersion.isBehind(phone, here)
    }
}
