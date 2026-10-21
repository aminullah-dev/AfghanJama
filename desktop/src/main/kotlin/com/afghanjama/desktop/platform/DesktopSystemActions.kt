package com.afghanjama.desktop.platform

import com.afghanjama.desktop.DeskNotices
import com.afghanjama.platform.SystemActions
import com.afghanjama.util.PhoneNumber
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [SystemActions] روی مک و ویندوز.
 *
 * کامپیوتر نه «اشتراکِ اندرویدی» دارد نه خطِ تلفن. از ۱.۱۰:
 *
 * - **پیام:** واتساپ (دسکتاپ یا وب) با شماره و متنِ آماده از راهِ
 *   `https://wa.me/<شمارهٔ بین‌المللی>?text=…`. شمارهٔ «۰۷…» با
 *   [PhoneNumber] «+۹۳۷…» می‌شود.
 * - **تماس:** `tel:` — روی مک FaceTime یا آیفونِ همراه، روی ویندوز هر
 *   برنامه‌ای که برای تماس تعیین شده.
 * - **هر جا که نشد** (شمارهٔ ناروشن، ویندوزی که برای `tel:` برنامه‌ای
 *   ندارد، کامپیوترِ بی مرورگر): شماره یا متن در کلیپ‌بورد کپی می‌شود و
 *   **گفته می‌شود** — تا ۱.۹ این کپی بی‌صدا بود و کاربر نمی‌فهمید دکمه کاری
 *   کرده.
 *
 * از AWT استفاده می‌شود نه Compose، چون کلیپ‌بوردِ Compose به یک
 * `CompositionLocal` نیاز دارد و این کلاس بیرونِ درختِ رابط ساخته می‌شود.
 */
class DesktopSystemActions : SystemActions {

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun copy(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(text), null)
        }
    }

    /** `true` اگر سیستم برنامه‌ای برای این نشانی داشت و بازش کرد. */
    private fun browse(uri: String): Boolean = runCatching {
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
        desktop.browse(URI(uri))
        true
    }.getOrDefault(false)

    override fun shareText(title: String, text: String) {
        copy(text)
        DeskNotices.show("متن کپی شد؛ در واتساپ یا هر جای دیگر بچسبانید.")
    }

    /**
     * روی رشتهٔ رابط صدا زده می‌شود (کلیکِ «تماس»)، پس کارش آنجا انجام
     * نمی‌شود: پرسیدن از رجیستریِ ویندوز ([TelHandler]) تا چند ثانیه طول
     * می‌کشد و تا ۱.۱۰ بارِ اول پنجره را همان‌قدر یخ می‌زد.
     */
    override fun dial(phone: String) {
        background.launch { dialNow(phone) }
    }

    private suspend fun dialNow(phone: String) {
        val intl = PhoneNumber.international(phone)
        if (intl != null && TelHandler.available() && browse("tel:$intl")) return
        copy(phone)
        if (phone.isBlank()) DeskNotices.show("شماره‌ای ثبت نشده.")
        else DeskNotices.show("شماره کپی شد:", ltr = phone.trim())
    }

    override fun messageTo(phone: String, text: String, title: String): Boolean {
        val wa = PhoneNumber.forWhatsApp(phone)
        if (wa != null) {
            // فاصله «%20» می‌ماند نه «+»: واتساپ «+» را در متن همان «+» می‌خواند.
            val body = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
            if (browse("https://wa.me/$wa?text=$body")) return true
        }
        copy(text)
        DeskNotices.show(
            if (wa == null) "شمارهٔ این نفر روشن نیست؛ متنِ پیام کپی شد."
            else "واتساپ باز نشد؛ متنِ پیام کپی شد."
        )
        return false
    }
}

/**
 * سیستم برای `tel:` برنامه‌ای دارد؟ — یک بار در هر اجرای برنامه پرسیده می‌شود.
 *
 * روی ویندوزی که ندارد، `browse` استثنا نمی‌دهد — ویندوز پنجرهٔ «برنامه‌ای
 * برای این پیوند پیدا کنید» را باز می‌کند و ما خیال می‌کردیم تماس گرفته
 * شد. پس اول در رجیستری نگاه می‌شود (انتخابِ کاربر، بعد پیش‌فرضِ سیستم).
 * مک همیشه FaceTime را دارد.
 *
 * **بیرون از رشتهٔ رابط، با سقفِ زمان.** تا ۱.۱۰ این پرسش `lazy` بود و
 * بارِ اول روی همان رشتهٔ کلیک اجرا می‌شد؛ و چون خروجیِ `reg` تا آخر
 * خوانده می‌شد، `waitFor(5s)` هرگز به کار نمی‌آمد و `reg`ِ گیرکرده پنجره
 * را نگه می‌داشت و خودش هم کشته نمی‌شد.
 */
internal object TelHandler {

    private const val TIMEOUT_MS = 3_000L

    private val lock = Mutex()

    @Volatile
    private var known: Boolean? = null

    suspend fun available(): Boolean = known ?: lock.withLock {
        known ?: withContext(Dispatchers.IO) { lookup() }
    }

    /** پاسخِ قطعی به یاد می‌ماند؛ سقفِ زمان نه، تا بارِ بعد دوباره پرسیده شود. */
    private fun lookup(): Boolean {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        if (!os.contains("win")) return true.also { known = it }
        var timedOut = false
        val found = listOf(
            listOf("reg", "query", """HKCU\Software\Microsoft\Windows\Shell\Associations\UrlAssociations\tel\UserChoice""", "/v", "ProgId"),
            listOf("reg", "query", """HKCR\tel\shell\open\command"""),
        ).any { cmd ->
            exitsOk(cmd, TIMEOUT_MS) ?: run {
                timedOut = true
                false
            }
        }
        if (found || !timedOut) known = found
        return found
    }

    /**
     * فرمان را اجرا می‌کند و تا [timeoutMs] منتظر می‌ماند. `true`/`false`
     * کدِ خروجِ صفر یا جز آن؛ `null` یعنی به سقف رسید (یا اجرا نشد) و پروسه
     * کشته شد.
     *
     * خروجی دور ریخته می‌شود و خوانده نمی‌شود: پروسه‌ای که خروجی‌اش پر شود
     * و کسی نخوانَد گیر می‌کند، و خواندنش تا آخر یعنی منتظر ماندن بی سقف.
     */
    internal fun exitsOk(cmd: List<String>, timeoutMs: Long): Boolean? {
        val p = runCatching {
            ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrNull() ?: return null
        return try {
            if (p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) p.exitValue() == 0 else null
        } finally {
            if (p.isAlive) {
                p.destroyForcibly()
                p.waitFor(2, TimeUnit.SECONDS)
            }
        }
    }
}
