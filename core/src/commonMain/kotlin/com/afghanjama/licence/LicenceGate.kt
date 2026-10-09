package com.afghanjama.licence

import com.afghanjama.prefs.DeviceMode
import com.afghanjama.util.nowMillis
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * درِ نوشتن — جایی که `Repo` پیش از هر ثبت و تغییری می‌پرسد «مجاز است؟».
 *
 * **چرا یک شیءِ سراسری، مثلِ `SalePolicy`.** `Repo` در چهار جا ساخته
 * می‌شود (گوشی، ویندوز/مک، آیفون، و کارهای پس‌زمینه) و هیچ‌کدام
 * تنظیمات را به آن نمی‌دهند. همان الگوی `SalePolicy`: سکو حقیقت را
 * می‌خوانَد و اینجا می‌گذارد ([install])، و منطقِ کارگاه فقط از اینجا
 * می‌پرسد. حقیقت خودش در [LicenceFacts] است و تصمیم هر بار با ساعتِ همان
 * لحظه از نو گرفته می‌شود — پس اگر اپ از شب تا صبح باز بماند و دورهٔ
 * آزمایشی وسطش تمام شود، اولین ثبتِ صبح همان را می‌بیند.
 *
 * **قاعدهٔ پروتکل:** تصمیم در لایهٔ سرویس است (`Repo`)، صفحه فقط
 * بازتابش می‌دهد. دکمه‌ای که در صفحه پنهان نشده باشد هم به همین در
 * می‌خورد.
 *
 * **پیش از اولین [install] همه‌چیز باز است.** سکو در ثانیهٔ اولِ اجرا
 * وضعیت را می‌خوانَد؛ بستنِ ثبت در آن یک ثانیه فقط کارِ درست را رد
 * می‌کرد. آزمون‌های خالص هم بی‌لایسنس اجرا می‌شوند.
 */
object LicenceGate {

    @Volatile
    private var facts: LicenceFacts? = null

    /**
     * ساعتِ تصمیم. [Licensing] همان ساعتِ خودش را اینجا می‌گذارد؛ آزمون‌ها
     * (که در ماژولِ `:app`اند و `internal` را نمی‌بینند) ساعتِ ساختگی.
     */
    @Volatile
    var clock: () -> Long = { nowMillis() }

    private val _status = MutableStateFlow<LicenceStatus?>(null)

    /** آخرین وضعیت برای صفحه‌ها؛ `null` یعنی هنوز خوانده نشده. */
    val status: StateFlow<LicenceStatus?> = _status.asStateFlow()

    private val _refusal = MutableStateFlow<String?>(null)

    /**
     * پیامِ آخرین ثبتی که رد شد — ریشهٔ هر سکو آن را در یک پنجره نشان
     * می‌دهد، با دکمهٔ «صفحهٔ لایسنس».
     */
    val refusal: StateFlow<String?> = _refusal.asStateFlow()

    fun dismissRefusal() {
        _refusal.value = null
    }

    /** گیرندهٔ گزارشِ دستگاهِ اصلی (روی کارگر) — [Licensing] می‌گذاردش تا ماندگارش کند. */
    @Volatile
    var onMainReport: ((MainLicence) -> Unit)? = null

    fun install(f: LicenceFacts) {
        facts = f
        publish()
    }

    /** یک تکه از حقیقت عوض شد (حالتِ دستگاه، شمارِ سفارش، گزارشِ اصلی). */
    fun update(change: (LicenceFacts) -> LicenceFacts) {
        val f = facts ?: return
        facts = change(f)
        publish()
    }

    /** دوباره سنجیدن با ساعتِ همین لحظه — برای بنری که روزها باز می‌ماند. */
    fun publish() {
        _status.value = current()
    }

    fun current(): LicenceStatus? = facts?.let { LicencePolicy.evaluate(it, clock()) }

    fun facts(): LicenceFacts? = facts

    /** نوشتن مجاز است؟ پیش از اولین خواندن، بله. */
    fun canWrite(): Boolean = current()?.canWrite ?: true

    /** نشانِ «آزمایشی» روی کاغذ؟ پیش از اولین خواندن، نه. */
    fun watermark(): Boolean = current()?.watermark ?: false

    /**
     * پیش از هر کاری که سند می‌سازد یا عوض می‌کند.
     *
     * اگر بسته باشد [LicenceRefused] پرتاب می‌شود و پیامش در [refusal]
     * می‌نشیند. چون آن استثنا از نوعِ لغو است، کوروتینِ ViewModel بی‌صدا
     * تمام می‌شود — نه اپ می‌ترکد، نه پیامِ گمراه‌کننده‌ای مثلِ «موجودیِ
     * صندوق کافی نیست» نشان داده می‌شود (ادامهٔ همان تابع اصلاً اجرا
     * نمی‌شود).
     */
    fun requireWrite() {
        val s = current() ?: return
        if (!s.canWrite) refuse(s)
    }

    /**
     * پیش از ساختنِ سفارشِ تازه — همان [requireWrite] به‌علاوهٔ سقفِ
     * دورهٔ آزمایشی.
     *
     * [countSince] شمارِ سفارش‌هایی را می‌دهد که از شروعِ دوره ساخته
     * شده‌اند؛ از دیتابیس خوانده می‌شود، نه از حافظه، تا حذف و بازیابیِ
     * پشتیبان هم درست شمرده شوند.
     */
    suspend fun requireOrderSlot(countSince: suspend (Long) -> Int) {
        val f = facts ?: return
        if (f.licence == null && f.mode != DeviceMode.WORKER) {
            val n = countSince(f.firstRunMillis)
            if (n != f.ordersSinceTrialStart) update { it.copy(ordersSinceTrialStart = n) }
        }
        requireWrite()
    }

    /** پس از ساختن یا حذفِ سفارش، تا بنر «چند سفارش مانده» را درست بگوید. */
    suspend fun recount(countSince: suspend (Long) -> Int) {
        val f = facts ?: return
        if (f.licence != null || f.mode == DeviceMode.WORKER) return
        val n = countSince(f.firstRunMillis)
        update { it.copy(ordersSinceTrialStart = n) }
    }

    private fun refuse(s: LicenceStatus): Nothing {
        val msg = LicenceText.refusal(s)
        _refusal.value = msg
        throw LicenceRefused(s, msg)
    }

    /** فقط برای آزمون‌ها: برگشت به حالتِ «هنوز خوانده نشده». */
    fun resetForTest() {
        facts = null
        clock = { nowMillis() }
        onMainReport = null
        _status.value = null
        _refusal.value = null
    }
}

/**
 * ثبتی که لایسنس اجازه‌اش را نداد.
 *
 * **چرا از `CancellationException`.** حدودِ صد تابعِ نویسندهٔ `Repo` هر
 * کدام نوعِ برگشتِ خودشان را دارند (`Boolean`، `String?`، `Unit`،
 * `PersonEdit`…) و پنجاه ViewModel هر کدام پیامِ خودشان را می‌سازند.
 * عوض کردنِ همهٔ آن امضاها یعنی دست زدن به هر صفحه — و هر جا که فراموش
 * شود، «false» یعنی پیامِ غلطی مثلِ «موجودی کافی نیست».
 *
 * استثنای لغو را کوروتین «پایانِ عادی» می‌خوانَد: کارِ ViewModel همان‌جا
 * می‌ایستد، اپ نمی‌ترکد، و پیامِ درست از [LicenceGate.refusal] در یک
 * پنجرهٔ مشترک نشان داده می‌شود. جایی هم که `runCatching` آن را بگیرد،
 * `message`ش همان متنِ فارسیِ روشن است.
 */
class LicenceRefused(val status: LicenceStatus, message: String) : CancellationException(message)
