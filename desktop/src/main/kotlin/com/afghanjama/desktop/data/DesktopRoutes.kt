package com.afghanjama.desktop.data

import com.afghanjama.lan.DefaultRoute
import com.afghanjama.lan.LanNetwork
import com.afghanjama.lan.RouteTable
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * دروازه (مودمِ) شبکه و نشانیِ سخت‌افزاری‌اش روی کامپیوتر — برای شناختنِ
 * «شبکهٔ کارگاه» ([LanNetwork]).
 *
 * جاوا هیچ‌کدام را نمی‌دهد، پس ابزارِ خودِ سیستم اجرا می‌شود: مک
 * `netstat` و `arp`، ویندوز `route` و `arp`، لینوکس `ip`. خواندنِ خروجی
 * در `:core` است (`RouteTable`) و با نمونه‌های واقعی آزموده می‌شود.
 *
 * **هر شکستی یعنی «نمی‌دانم»، نه خطا:** ابزار نبود، دیر جواب داد (بیش از
 * دو ثانیه)، یا خروجی‌اش خوانده نشد — آن‌وقت دروازه `null` است و شبکهٔ
 * کارگاه فقط با زیرشبکه شناخته می‌شود. روشن شدنِ خودکار نباید به خاطرِ
 * یک ابزارِ سیستم بشکند.
 */
internal object DesktopRoutes {

    private val os = System.getProperty("os.name").orEmpty().lowercase()
    private val onWindows = os.startsWith("windows")
    private val onMac = os.contains("mac") || os.contains("darwin")

    fun defaultRoutes(): List<DefaultRoute> = when {
        onWindows -> run("route", "print", "-4")?.let { RouteTable.parseRoutePrint(it) }
        onMac -> run(tool("/usr/sbin/netstat", "netstat"), "-rn", "-f", "inet")?.let { RouteTable.parseNetstat(it) }
        else -> run(tool("/usr/sbin/ip", "/sbin/ip", "ip"), "-4", "route", "show", "default")?.let { RouteTable.parseIpRoute(it) }
    }.orEmpty()

    /**
     * نشانیِ سخت‌افزاریِ [ip] (مودم) از جدولِ ARP.
     *
     * **اگر نبود، یک بار مودم را صدا می‌زند و دوباره می‌خوانَد.** سیستم
     * نشانیِ مودم را فقط وقتی در جدول دارد که تازگی چیزی برایش فرستاده
     * باشد. بی این، کامپیوتری که تازه بیدار شده یا کارگاهی که اینترنت ندارد
     * نشانی را «نمی‌دانم» می‌دید: سرِ «روشن» شبکهٔ کارگاه بی آن به یاد
     * سپرده می‌شد (و دیگر هرگز چایخانهٔ هم‌مدل را جدا نمی‌کرد)، و در
     * چایخانه تا یک دقیقه، تا بارِ بعدیِ دیدن، اشتراک باز می‌ماند.
     * صدا زدن با `isReachable` است (روی ویندوز ICMP، جای دیگر اتصالِ TCP
     * به درگاهِ ۷): فقط بیرون می‌رود و درگاهی باز نمی‌کند، پس پنجرهٔ
     * دیوارِ آتشِ ویندوز را هم بالا نمی‌آورد.
     */
    fun mac(ip: String): String? {
        readMac(ip)?.let { return it }
        runCatching { InetAddress.getByName(ip).isReachable(POKE_MS) }
        return readMac(ip)
    }

    private const val POKE_MS = 300

    private fun readMac(ip: String): String? = when {
        onWindows -> run("arp", "-a", ip)
        onMac -> run(tool("/usr/sbin/arp", "arp"), "-n", ip)
        else -> run(tool("/usr/sbin/ip", "/sbin/ip", "ip"), "neigh", "show", ip)
    }?.let { RouteTable.parseArp(it, ip) }

    /** مسیرِ کاملِ ابزار اگر هست — تا `PATH`ِ برنامهٔ بسته‌بندی‌شده مهم نباشد. */
    private fun tool(vararg candidates: String): String =
        candidates.firstOrNull { it.startsWith("/") && File(it).canExecute() } ?: candidates.last()

    private fun run(vararg cmd: String): String? = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        p.outputStream.close()
        // خروجی در رشته‌ای جدا خوانده می‌شود تا ابزاری که گیر کرده برنامه را نگه ندارد.
        var text = ""
        val reader = Thread { text = runCatching { p.inputStream.readBytes().toString(Charsets.ISO_8859_1) }.getOrDefault("") }
        reader.isDaemon = true
        reader.start()
        if (!p.waitFor(2, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return null
        }
        reader.join(1_000)
        text.takeIf { p.exitValue() == 0 && it.isNotBlank() }
    }.getOrNull()
}
