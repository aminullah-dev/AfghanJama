package com.afghanjama.desktop.data

import com.afghanjama.data.Db
import com.afghanjama.lan.Lan
import com.afghanjama.lan.LanHost
import java.net.InetAddress

/**
 * [LanHost] روی ویندوز — آخرین چیزی که `WorkshopLinkScreen` را در
 * `:app` نگه داشته بود.
 *
 * این مرز روی پی‌سی **معنیِ بیشتری** دارد تا روی گوشی: طبقِ تصمیمِ
 * کارفرما پی‌سی دفترِ حساب است و گوشی‌ها به آن وصل می‌شوند، پس
 * سرویس‌دهنده در عمل همین‌جاست — نه روی گوشیِ کارفرما که ممکن است
 * خاموش یا بیرون از کارگاه باشد.
 */
class DesktopLanHost : LanHost {

    /**
     * نامِ کامپیوتر، همان‌طور که در شبکهٔ کارگاه دیده می‌شود.
     *
     * `InetAddress.getLocalHost().hostName` روی ماشینی که DNS ندارد
     * می‌تواند کند باشد یا استثنا بدهد، پس اول متغیرهای خودِ ویندوز
     * (`COMPUTERNAME`) خوانده می‌شود و آن راه فقط پشتیبان است.
     */
    override val deviceName: String
        get() = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
            ?: System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
            ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
                ?.takeIf { it.isNotBlank() }
            ?: "کامپیوترِ کارگاه"

    /**
     * نشانیِ برهنه، بی درگاه — همان قراردادی که `LanClient` می‌خواهد.
     *
     * راهِ اندروید (`WifiManager`) اینجا لازم نیست؛ ولی هشدارِ `Lan`
     * سرِ جایش است: روی ویندوزی که کارتِ مجازی دارد (WSL، VirtualBox،
     * VPN) ممکن است اولین نشانیِ غیرِ loopback آنی نباشد که گوشیِ خیاط
     * از آن به دفتر می‌رسد. تا وقتی کارگاه یک شبکهٔ ساده دارد این کافی
     * است؛ اگر روزی نبود، انتخابِ دستیِ کارت لازم می‌شود.
     */
    override fun localIp(): String? = Lan.localIp()

    /**
     * **همان دفتری که پنجره باز کرده** — نه یک اتصالِ تازه.
     *
     * `AndroidLanHost` هم همین کار را با `by lazy` می‌کند. اینجا
     * `DesktopLedger` از قبل یکی‌بودنِ اتصال را تضمین می‌کند.
     */
    override fun ledger(): Db = DesktopLedger.db().getOrThrow()
}
