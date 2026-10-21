package com.afghanjama.lan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * پنجرهٔ «افزودنِ گوشی» روی کامپیوتر — ده دقیقه، **یک گوشی**.
 *
 * - با باز شدن یک جفت‌کلیدِ گذرای P-256، یک رازِ ۱۲۸ بیتی (برای QR) و یک رمزِ
 *   دستیِ ۱۲ رقمی ساخته می‌شود. هیچ‌کدام روی دیسک نمی‌رود؛ با بسته شدنِ
 *   پنجره همه فراموش می‌شوند.
 * - **بیرون از پنجره هیچ** (طرح، B.4): «سلام»، درخواست و پرسشِ «اجازه»
 *   همه ۴۰۴ می‌گیرند.
 * - **ده درخواستِ نادرست و پنجره بسته** ([HubProtocol.PAIR_MAX_FAILURES]).
 *   حدسِ رمزِ دستیِ ۴۰ بیتی با ده تلاش ناشدنی است؛ کسی که پنجره را با
 *   درخواستِ نادرست ببندد، فقط کارفرما را وادار می‌کند دوباره باز کند — و
 *   صفحه می‌گوید چرا بسته شد.
 * - **یک گوشی در هر پنجره:** تا وقتی یکی منتظرِ «اجازه» است، دومی
 *   «گوشیِ دیگری منتظر است» می‌گیرد. پس از «اجازه» پنجره درِ تازه را
 *   می‌بندد ولی جوابِ همان گوشی را تا پایانِ ده دقیقه نگه می‌دارد (اگر
 *   جوابِ اول به گوشی نرسید، پرسشِ دوباره همان کلید را می‌گیرد). پس از
 *   «رد» درِ پنجره برای تلاشِ دیگر باز می‌ماند.
 * - **گوشیِ صاحب‌کار فقط با QR.** رمزِ دستی برای کارگر و تابلوست.
 */
class HubPairing(private val clock: () -> Long = { System.currentTimeMillis() }) {

    /** گوشی‌ای که درخواست داده و منتظرِ کارفرماست. */
    class Pending(
        val requestId: String,
        internal val key: ByteArray,
        val via: PairVia,
        val info: PhoneInfo,
        val ip: String,
        val at: Long,
    ) {
        override fun toString(): String = "Pending(requestId=$requestId, via=$via, info=$info, ip=$ip, key=…)"
    }

    sealed interface Outcome {
        data class Approved(val grant: PairGrant) : Outcome
        data class Rejected(val reason: String) : Outcome
    }

    private class Window(
        val pairingId: String,
        val secret: ByteArray,
        val code: String,
        val hubPair: EcKeyPair,
        val qrText: String,
        val expiresAt: Long,
        val allowCode: Boolean,
    ) {
        var failures = 0
        var pending: Pending? = null
        var outcome: Outcome? = null
    }

    /** آنچه صفحه نشان می‌دهد. رازها فقط در [qrText] و [code] — و فقط تا پنجره باز است. */
    data class View(
        val open: Boolean = false,
        val qrText: String = "",
        val code: String = "",
        val expiresAt: Long = 0L,
        val allowCode: Boolean = true,
        /** گوشی‌ای که منتظرِ «اجازه» است. */
        val waiting: PhoneInfo? = null,
        val waitingVia: PairVia? = null,
        val waitingIp: String = "",
        /** گوشیِ پذیرفته‌شده، تا وقتی پنجره هست. */
        val approvedName: String = "",
        /** چرا بسته شد — برای صفحه. */
        val closedReason: String = "",
    ) {
        override fun toString(): String =
            "View(open=$open, expiresAt=$expiresAt, waiting=$waiting, approved=$approvedName, closed=$closedReason)"
    }

    private var window: Window? = null
    private val _view = MutableStateFlow(View())
    val view: StateFlow<View> = _view.asStateFlow()

    /** هر بار که چیزی عوض شد — پرسش‌های «اجازه» با همین بیدار می‌شوند. */
    private val tick = MutableStateFlow(0L)

    @Synchronized
    fun open(host: String, port: Int, hubId: String, allowCode: Boolean = true): View {
        val pairingId = LanCrypto.randomId(8)
        val secret = LanPrims.random(PairingV2.SECRET_BYTES)
        val w = Window(
            pairingId = pairingId,
            secret = secret,
            code = PairingV2.newManualCode(),
            hubPair = LanPrims.p256KeyPair(),
            qrText = PairingV2.encode(PairingV2.Link(host, port, hubId, pairingId, secret)),
            expiresAt = clock() + HubProtocol.PAIR_WINDOW_MS,
            allowCode = allowCode,
        )
        window = w
        publish()
        return _view.value
    }

    @Synchronized
    fun close(reason: String = "") {
        window = null
        _view.value = View(closedReason = reason)
        tick.value++
    }

    /** پنجرهٔ زنده، یا `null` — پنجرهٔ کهنه همین‌جا بسته می‌شود. */
    private fun live(): Window? {
        val w = window ?: return null
        if (clock() >= w.expiresAt) {
            window = null
            _view.value = View(closedReason = EXPIRED)
            tick.value++
            return null
        }
        return w
    }

    @Synchronized
    fun isOpen(): Boolean = live()?.let { it.outcome !is Outcome.Approved } == true

    private fun publish() {
        val w = window ?: return
        val p = w.pending
        _view.value = View(
            open = true,
            qrText = w.qrText,
            code = w.code,
            expiresAt = w.expiresAt,
            allowCode = w.allowCode,
            waiting = p?.info?.takeIf { w.outcome == null },
            waitingVia = p?.via?.takeIf { w.outcome == null },
            waitingIp = p?.ip?.takeIf { w.outcome == null }.orEmpty(),
            approvedName = (w.outcome as? Outcome.Approved)?.let { p?.info?.name.orEmpty() }.orEmpty(),
        )
        tick.value++
    }

    // ------------------------------------------------------------ سمتِ سرور

    @Synchronized
    fun hello(hubId: String): PairHello? {
        val w = live() ?: return null
        if (w.outcome is Outcome.Approved) return null
        return PairHello(hubId, w.pairingId, w.hubPair.publicX963, w.expiresAt - clock())
    }

    sealed interface Submit {
        data class Ok(val requestId: String) : Submit
        data object Closed : Submit
        data object Busy : Submit
        data object OwnerNeedsQr : Submit
        /** راز نادرست بود؛ [closed] یعنی همین یکی پنجره را بست. */
        data class Wrong(val closed: Boolean) : Submit
    }

    @Synchronized
    fun submit(body: Map<String, Any?>, ip: String): Submit {
        val w = live() ?: return Submit.Closed
        if (w.outcome is Outcome.Approved) return Submit.Closed
        if (w.pending != null && w.outcome == null) return Submit.Busy
        val opened = HubEnvelope.openPairRequest(body, w.pairingId, w.hubPair) { via ->
            when (via) {
                PairVia.QR -> w.secret
                PairVia.CODE -> if (w.allowCode) w.code.encodeToByteArray() else null
            }
        }
        if (opened == null) {
            w.failures++
            if (w.failures >= HubProtocol.PAIR_MAX_FAILURES) {
                window = null
                _view.value = View(closedReason = TOO_MANY)
                tick.value++
                return Submit.Wrong(closed = true)
            }
            return Submit.Wrong(closed = false)
        }
        if (opened.via == PairVia.CODE && opened.info.wantRole == HubRole.OWNER) return Submit.OwnerNeedsQr
        val p = Pending(LanCrypto.randomId(10), opened.key, opened.via, opened.info, ip, clock())
        w.pending = p
        w.outcome = null
        publish()
        return Submit.Ok(p.requestId)
    }

    /** کلیدِ درخواستِ [requestId]، اگر هنوز همان درخواستِ پنجره است. */
    @Synchronized
    internal fun keyFor(requestId: String): ByteArray? =
        live()?.pending?.takeIf { it.requestId == requestId }?.key

    /** تا [timeoutMs] منتظرِ تصمیمِ کارفرما. `null` یعنی هنوز هیچ (یا پنجره رفت). */
    suspend fun await(requestId: String, timeoutMs: Long): Outcome? {
        val first = synchronized(this) { outcomeFor(requestId) }
        if (first != null || synchronized(this) { keyFor(requestId) == null }) return first
        return withTimeoutOrNull(timeoutMs) {
            tick.first { synchronized(this@HubPairing) { outcomeFor(requestId) != null || keyFor(requestId) == null } }
            synchronized(this@HubPairing) { outcomeFor(requestId) }
        }
    }

    private fun outcomeFor(requestId: String): Outcome? =
        live()?.takeIf { it.pending?.requestId == requestId }?.outcome

    /** پنجره هنوز هست؟ — برای «کهنه شد» به‌جای «منتظر». */
    @Synchronized
    fun alive(requestId: String): Boolean = keyFor(requestId) != null

    // ------------------------------------------------------------ سمتِ کارفرما

    /** گوشیِ منتظر، اگر هست — برای ساختنِ ردیفِ دستگاه. */
    @Synchronized
    fun waiting(): Pending? = live()?.let { w -> w.pending?.takeIf { w.outcome == null } }

    @Synchronized
    fun decide(requestId: String, outcome: Outcome): Boolean {
        val w = live() ?: return false
        val p = w.pending ?: return false
        if (p.requestId != requestId || w.outcome != null) return false
        w.outcome = outcome
        if (outcome is Outcome.Rejected) {
            // درِ پنجره برای گوشیِ دیگر باز می‌ماند؛ همین یکی جوابِ «رد» را می‌گیرد.
            publish()
            _view.value = _view.value.copy(waiting = null, waitingVia = null, waitingIp = "")
        } else {
            publish()
        }
        return true
    }

    /** پس از «رد»، گوشیِ بعدی بتواند درخواست بدهد. */
    @Synchronized
    internal fun clearRejected() {
        val w = live() ?: return
        if (w.outcome is Outcome.Rejected) {
            w.pending = null
            w.outcome = null
            publish()
        }
    }

    companion object {
        const val EXPIRED = "ده دقیقه گذشت و پنجرهٔ «افزودنِ گوشی» بسته شد."
        const val TOO_MANY =
            "چند رمزِ نادرست پشتِ سرِ هم آمد و پنجرهٔ «افزودنِ گوشی» برای امنیت بسته شد. اگر خودتان نبودید، " +
                "کسی روی همین وای‌فای حدس می‌زد؛ دوباره باز کنید."
    }
}
