package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.lan.DebtLine
import com.afghanjama.lan.HubClientState
import com.afghanjama.lan.HubCommand
import com.afghanjama.lan.HubProtocol
import com.afghanjama.lan.HubRole
import com.afghanjama.lan.HubSession
import com.afghanjama.lan.PhoneInfo
import com.afghanjama.lan.PhoneName
import com.afghanjama.lan.RequestLine
import com.afghanjama.lan.WorkLine
import com.afghanjama.licence.LicenceGate
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import com.afghanjama.prefs.Settings
import com.afghanjama.ui.format.fa
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * دفترِ همین گوشی، وقتی به کامپیوترِ کارگاه وصل می‌شود (طرح، B.6 قاعدهٔ ۶).
 *
 * - [EMPTY]: گوشی دفتری ندارد؛ پیروِ لایسنسِ کامپیوتر است.
 * - [MOVE]: «دادهٔ این گوشی را به کامپیوتر ببر» — پس از جفت شدن دفترِ گوشی
 *   فقط‌خواندنی می‌شود (`ReadOnlyReason.LEDGER_MOVED`)، تا دو دفتر از هم جدا
 *   نیفتند (نقدِ ۷).
 * - [SEPARATE]: «جدا بماند» — دفترِ گوشی مثلِ قبل و با لایسنسِ خودش؛ با
 *   کامپیوتر هم‌گام نمی‌شود.
 */
enum class LedgerChoice { EMPTY, MOVE, SEPARATE }

/**
 * صفحهٔ گوشیِ «کارگاهِ وصل» — هر سه نقش. کارِ واقعی در [HubSession] است؛ اینجا
 * فقط دکمه‌ها به کارِ صف تبدیل می‌شوند و همگام‌سازی بلافاصله پس از هر دکمه
 * و هر ۱۵ ثانیه تا وقتی صفحه باز است.
 */
class HubClientViewModel(
    private val session: HubSession,
    private val settings: Settings,
    /** دفترِ این گوشی داده دارد؟ — برای پرسیدنِ «ببر یا جدا بماند». */
    private val hasLocalData: suspend () -> Boolean = { false },
    /** سکو پس از جفت شدن: مثلاً روی اندروید اگر این گوشی اصلی بود، سرورش خاموش می‌شود. */
    private val onPaired: (LedgerChoice) -> Unit = {},
) : ViewModel() {

    val state: StateFlow<HubClientState> = session.state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** پیامِ کوتاه: متن و اینکه خطاست یا نه. */
    private val _message = MutableStateFlow<Pair<String, Boolean>?>(null)
    val message: StateFlow<Pair<String, Boolean>?> = _message.asStateFlow()

    /** `null` تا پرسیده نشده. */
    private val _localData = MutableStateFlow<Boolean?>(null)
    val localData: StateFlow<Boolean?> = _localData.asStateFlow()

    init {
        viewModelScope.launch { _localData.value = runCatching { hasLocalData() }.getOrDefault(false) }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun say(text: String, error: Boolean = false) {
        _message.value = text to error
    }

    /** تا وقتی صفحه باز است — صداکننده در `LaunchedEffect` اجرایش می‌کند. */
    suspend fun poll() {
        session.retryNow()
        while (true) {
            delay(HubProtocol.POLL_MS)
            session.sync()
        }
    }

    fun refresh() = viewModelScope.launch { session.retryNow() }

    // ------------------------------------------------------------ جفت شدن

    private fun info(role: HubRole) = PhoneInfo(PhoneName.current, "", com.afghanjama.AppVersion.current, role)

    fun pairWithLink(raw: String, role: HubRole, choice: LedgerChoice) = pair(choice) {
        session.pairWithLink(raw, info(role))
    }

    fun pairWithCode(host: String, code: String, role: HubRole, choice: LedgerChoice) = pair(choice) {
        session.pairWithCode(host, code, info(role))
    }

    private fun pair(choice: LedgerChoice, run: suspend () -> String?) = viewModelScope.launch {
        if (_busy.value) return@launch
        _busy.value = true
        _message.value = null
        try {
            val err = run()
            if (err != null) {
                say(err, error = true)
                return@launch
            }
            applyLedgerChoice(choice)
            onPaired(choice)
            say("جفت شد. از این پس کارهای این گوشی به کامپیوترِ کارگاه می‌رود.")
        } finally {
            _busy.value = false
        }
    }

    /** پس از جفت شدن: لایسنس پیروِ کامپیوتر، مگر «جدا بماند». */
    private fun applyLedgerChoice(choice: LedgerChoice) {
        val moved = choice == LedgerChoice.MOVE
        val mode = if (choice == LedgerChoice.SEPARATE) DeviceMode.STANDALONE else DeviceMode.WORKER
        LanPrefs.setLedgerMoved(settings, moved)
        LanPrefs.setMode(settings, mode)
        LicenceGate.update { it.copy(mode = mode, ledgerMoved = moved) }
    }

    fun cancelPairing() = session.cancelPairing()

    /** «جدا کردنِ این گوشی» — صفِ ارسال می‌ماند؛ دفترِ گوشی دوباره باز. */
    fun forget() {
        session.forget()
        LanPrefs.setLedgerMoved(settings, false)
        LanPrefs.setMode(settings, DeviceMode.STANDALONE)
        LicenceGate.update { it.copy(mode = DeviceMode.STANDALONE, ledgerMoved = false) }
        say("این گوشی از کامپیوترِ کارگاه جدا شد.")
    }

    // ------------------------------------------------------------ کارِ کارگر

    private fun queue(cmd: HubCommand, payload: Map<String, Any?>, summary: String, done: String) {
        val err = session.enqueue(cmd, payload, summary)
        if (err != null) {
            say(err, error = true)
            return
        }
        say(done)
        viewModelScope.launch { session.sync() }
    }

    fun checkIn() = queue(HubCommand.ATTENDANCE_IN, emptyMap(), "ورود", "«ورود» در صف نشست و فرستاده می‌شود.")

    fun checkOut() = queue(HubCommand.ATTENDANCE_OUT, emptyMap(), "خروج", "«خروج» در صف نشست و فرستاده می‌شود.")

    fun reportDone(w: WorkLine, qty: Int) {
        if (qty !in 1..w.qty) {
            say("شمار باید از ۱ تا ${w.qty.fa()} باشد.", error = true)
            return
        }
        queue(
            HubCommand.SEWING_DONE,
            mapOf("assignmentId" to w.id, "qty" to qty.toLong()),
            "${w.orderCode} — ${qty.fa()} عدد",
            "اعلامِ تحویل در صف نشست؛ تا کارفرما تأیید نکند در کارمزد نمی‌نشیند.",
        )
    }

    fun sendNote(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        queue(HubCommand.NOTE, mapOf("text" to t.take(300)), "پیام: ${t.take(40)}", "پیام در صف نشست.")
    }

    // ------------------------------------------------------------ کارِ صاحب‌کار

    /** [time]: `null` معمولی؛ «PHONE» یا «ARRIVAL» وقتی ساعتِ گوشیِ کارگر معتبر نبود. */
    fun approve(r: RequestLine, time: String? = null) = queue(
        HubCommand.APPROVE_REQUEST,
        buildMap {
            put("requestId", r.id)
            time?.let { put("time", it) }
        },
        "تأیید: ${r.worker} — ${r.summary}",
        "تأیید فرستاده شد.",
    )

    fun reject(r: RequestLine) = queue(
        HubCommand.REJECT_REQUEST,
        mapOf("requestId" to r.id, "note" to "رد از گوشیِ صاحبِ کار"),
        "رد: ${r.worker} — ${r.summary}",
        "رد فرستاده شد.",
    )

    fun followUp(d: DebtLine, outcome: String, days: Int = 0) = queue(
        HubCommand.DEBT_FOLLOW_UP,
        mapOf("customer" to d.customer, "outcome" to outcome, "untilDays" to days.toLong()),
        "پیگیری: ${d.customer}",
        "نتیجهٔ پیگیری فرستاده شد.",
    )

    fun clearFinished() = session.clearFinished()

}

/**
 * پیوندِ جفت شدن که سیستم به برنامه داد — آیفون وقتی QR را دوربینِ خودِ گوشی
 * می‌خوانَد و «باز کن در خیاط‌یار» زده می‌شود. صفحهٔ «کارگاهِ وصل» آن را
 * برمی‌دارد و **پیش از هر اتصال** از کاربر می‌پرسد؛ هیچ پیوندی خودبه‌خود جفت
 * نمی‌کند.
 */
object HubDeepLink {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(raw: String) {
        if (com.afghanjama.lan.PairingV2.looksLikeOurs(raw)) _pending.value = raw.trim()
    }

    fun take(): String? = _pending.value.also { _pending.value = null }
}
