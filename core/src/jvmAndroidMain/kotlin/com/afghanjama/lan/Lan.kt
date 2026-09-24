package com.afghanjama.lan

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * قراردادِ سادهٔ بینِ دفترِ کارگاه و گوشیِ کارگران، روی وای‌فای کارگاه.
 *
 * عمداً هیچ کتابخانهٔ سنگینی اضافه نشده: سرور با `ServerSocket` و کلاینت
 * با `HttpURLConnection` که هر دو در خودِ جاواست، و JSON با `org.json`
 * که روی اندروید در خودِ سیستم هست و روی ویندوز یک jarِ کوچک. یک ERPِ
 * کارگاهی نباید برای فرستادنِ چند خط متن، ده مگابایت کتابخانه بیاورد.
 *
 * **این فایل در `:core` است، و همین نکتهٔ فاز ۶ است.** تا دیروز
 * «سرور» یعنی گوشیِ کارفرما. حالا هر چیزی که کاتلین اجرا کند می‌تواند
 * سرور باشد — و تصمیمِ کارفرما این بود که پی‌سی دفتر شود.
 */
object Lan {
    const val PORT = 8797

    // مسیرها
    const val PATH_PING = "/ping"
    const val PATH_MY_WORK = "/my-work"
    const val PATH_REQUEST = "/request"
    const val PATH_BOARD = "/board"

    /** هدرِ رمزِ اتصال. سرور هر درخواستِ بدونِ رمزِ درست را رد می‌کند. */
    const val HEADER_CODE = "X-Workshop-Code"

    /** نسخهٔ قرارداد؛ اگر دو سو نسخهٔ متفاوت داشته باشند صریح می‌گوییم. */
    const val PROTOCOL = 1

    /**
     * نشانیِ این ماشین در شبکهٔ محلی، از روی کارت‌های شبکه.
     *
     * **دیگر «اولین نشانی» نیست، بهترین نشانی است.** تا امروز اولین
     * نشانیِ غیرِ loopback برمی‌گشت و ترتیبِ کارت‌ها دستِ سیستم است:
     * روی ویندوزی که WSL، VirtualBox یا VPN دارد اولی اغلب کارتِ مجازی
     * بود، و روی گوشی‌ای که هات‌اسپات است کارتِ اینترنتِ سیم‌کارت. در هر
     * دو حالت صفحه نشانی‌ای نشان می‌داد که گوشیِ خیاط هرگز به آن نمی‌رسید.
     * حالا همهٔ نشانی‌ها با [score] مرتب می‌شوند.
     */
    fun localIp(): String? = localIps().firstOrNull()

    /** همهٔ نشانی‌های قابلِ اتصال، بهترین اول — برای نشان دادنِ جایگزین. */
    fun localIps(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic ->
                nic.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { a ->
                        a.hostAddress?.let { Candidate(nic.name.orEmpty(), nic.displayName.orEmpty(), it) }
                    }
            }
            .let { rank(it) }
    }.getOrDefault(emptyList())

    /** یک نشانی روی یک کارتِ شبکه. */
    data class Candidate(val iface: String, val display: String, val address: String)

    /** مرتب‌سازیِ نشانی‌ها؛ آنهایی که هرگز قابلِ اتصال نیستند حذف می‌شوند. */
    fun rank(all: List<Candidate>): List<String> =
        all.mapNotNull { c -> score(c)?.let { it to c.address } }
            .sortedBy { it.first }
            .map { it.second }
            .distinct()

    /**
     * امتیازِ یک نشانی — کمتر یعنی بهتر؛ `null` یعنی «اصلاً نه».
     *
     * سه چیز سنجیده می‌شود: بازهٔ نشانی (شبکهٔ خانگی/کارگاهی بهترین است،
     * ۱۰۰.۶۴/۱۰ مالِ اپراتورِ موبایل است)، نامِ کارت (وای‌فای و سیم بهتر،
     * مجازی و تونل بدتر)، و آنچه هرگز کار نمی‌کند (loopback و ۱۶۹.۲۵۴ که
     * یعنی کارت اصلاً نشانی نگرفته).
     */
    fun score(c: Candidate): Int? {
        val o = c.address.split('.').mapNotNull { it.toIntOrNull() }
        if (o.size != 4 || o.any { it !in 0..255 }) return null
        if (o[0] == 127 || o[0] == 0) return null
        if (o[0] == 169 && o[1] == 254) return null

        var s = when {
            o[0] == 192 && o[1] == 168 -> 0
            o[0] == 10 -> 1
            o[0] == 172 && o[1] in 16..31 -> 2
            o[0] == 100 && o[1] in 64..127 -> 50 // CGNAT: اینترنتِ سیم‌کارت
            else -> 20
        }

        val name = c.iface.lowercase()
        val display = c.display.lowercase()
        val virtualHints = listOf(
            "vethernet", "virtual", "vbox", "vmware", "vmnet", "hyper-v", "wsl",
            "docker", "veth", "tun", "tap", "utun", "ppp", "wg", "zerotier",
            "tailscale", "hamachi", "vpn", "rmnet", "ccmni", "pdp", "bridge",
        )
        if (virtualHints.any { name.startsWith(it) || display.contains(it) } || name.startsWith("br-")) {
            s += 100
        }
        val lanPrefixes = listOf("wlan", "swlan", "ap", "softap", "eth", "en", "wl")
        val lanWords = listOf("wi-fi", "wifi", "wireless", "ethernet")
        if (s < 100 && (lanPrefixes.any { name.startsWith(it) } || lanWords.any { display.contains(it) })) {
            s -= 10
        }
        return s
    }
}
