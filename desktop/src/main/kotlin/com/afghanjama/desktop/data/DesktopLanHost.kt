package com.afghanjama.desktop.data

import com.afghanjama.data.Db
import com.afghanjama.lan.DeviceKind
import com.afghanjama.lan.Lan
import com.afghanjama.lan.LanHost
import com.afghanjama.lan.LanNetwork
import com.afghanjama.lan.RouteTable
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

    /** صفحه «این کامپیوتر…» می‌گوید، نه «این گوشی…». */
    override val kind: DeviceKind get() = DeviceKind.COMPUTER

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
     * راهِ اندروید (`WifiManager`) اینجا لازم نیست. روی ویندوزی که کارتِ
     * مجازی دارد (WSL، VirtualBox، VPN) اولین نشانیِ غیرِ loopback اغلب
     * آنی نبود که گوشیِ خیاط از آن به دفتر می‌رسد؛ حالا `Lan.score`
     * کارت‌های مجازی را عقب می‌فرستد، و اگر باز هم حدس غلط باشد
     * [localIps] بقیه را روی صفحه نشان می‌دهد.
     */
    override fun localIp(): String? = Lan.localIp()

    override fun localIps(): List<String> = Lan.localIps()

    /**
     * زیرشبکه از کارت‌های شبکه، به‌علاوهٔ مودم و نشانیِ سخت‌افزاری‌اش از
     * جدول‌های خودِ سیستم ([DesktopRoutes]). کامپیوترِ کارگاه اغلب لپ‌تاپ
     * است و چایخانه هم ممکن است همان مدلِ مودم و همان «۱۹۲.۱۶۸.۱.۱» را
     * داشته باشد؛ نشانیِ سخت‌افزاریِ مودم است که آن دو را از هم جدا می‌کند.
     */
    override fun networks(): Map<String, LanNetwork> {
        val links = Lan.links()
        if (links.isEmpty()) return emptyMap()
        return RouteTable.attach(links, DesktopRoutes.defaultRoutes(), DesktopRoutes::mac)
    }

    /**
     * **همان دفتری که پنجره باز کرده** — نه یک اتصالِ تازه.
     *
     * `AndroidLanHost` هم همین کار را با `by lazy` می‌کند. اینجا
     * `DesktopLedger` از قبل یکی‌بودنِ اتصال را تضمین می‌کند.
     */
    override fun ledger(): Db = DesktopLedger.db().getOrThrow()
}
