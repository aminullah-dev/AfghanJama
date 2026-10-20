package com.afghanjama.desktop.platform

import com.afghanjama.desktop.DeskNotices
import com.afghanjama.platform.SystemActions
import com.afghanjama.util.PhoneNumber
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLEncoder

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

    /**
     * سیستم برای `tel:` برنامه‌ای دارد؟
     *
     * روی ویندوزی که ندارد، `browse` استثنا نمی‌دهد — ویندوز پنجرهٔ «برنامه‌ای
     * برای این پیوند پیدا کنید» را باز می‌کند و ما خیال می‌کردیم تماس گرفته
     * شد. پس اول در رجیستری نگاه می‌شود (انتخابِ کاربر، بعد پیش‌فرضِ سیستم).
     * مک همیشه FaceTime را دارد.
     */
    private val telHandler: Boolean by lazy {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        if (!os.contains("win")) return@lazy true
        listOf(
            listOf("reg", "query", """HKCU\Software\Microsoft\Windows\Shell\Associations\UrlAssociations\tel\UserChoice""", "/v", "ProgId"),
            listOf("reg", "query", """HKCR\tel\shell\open\command"""),
        ).any { cmd ->
            runCatching {
                val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
                p.inputStream.readBytes()
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0
            }.getOrDefault(false)
        }
    }

    override fun shareText(title: String, text: String) {
        copy(text)
        DeskNotices.show("متن کپی شد؛ در واتساپ یا هر جای دیگر بچسبانید.")
    }

    override fun dial(phone: String) {
        val intl = PhoneNumber.international(phone)
        if (intl != null && telHandler && browse("tel:$intl")) return
        copy(phone)
        DeskNotices.show(
            if (phone.isBlank()) "شماره‌ای ثبت نشده."
            else "شماره کپی شد: $phone"
        )
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
