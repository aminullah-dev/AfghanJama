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

    /**
     * هر نشانیِ خصوصیِ این دستگاه با زیرشبکه‌اش — برای شناختنِ «شبکهٔ
     * کارگاه» ([LanNetwork]). دروازه را اینجا نمی‌دانیم؛ جاوا آن را
     * نمی‌دهد و هر سکو خودش اضافه می‌کند (`LanHost.networks`).
     */
    fun links(): List<LanLink> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic ->
                nic.interfaceAddresses.mapNotNull { ia ->
                    val ip = (ia.address as? Inet4Address)?.hostAddress ?: return@mapNotNull null
                    if (!HostAddress.isPrivate(ip)) return@mapNotNull null
                    val subnet = LanNetwork.subnetOf(ip, ia.networkPrefixLength.toInt()) ?: return@mapNotNull null
                    LanLink(ip, nic.name.orEmpty(), LanNetwork(subnet))
                }
            }
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
     * مجازی و تونل بدتر)، و آنچه گوشیِ کارگر هرگز نمی‌پذیرد.
     *
     * **این آخری همان قاعدهٔ گوشیِ کارگر است: [HostAddress.isPrivate].**
     * تا ۱.۹.۰ نشانیِ عمومی هم امتیاز می‌گرفت (حتی بهتر از ۱۰۰.۶۴)، پس
     * دستگاهِ اصلی‌ای که فقط نشانیِ عمومی داشت آن را در QR می‌گذاشت، و
     * گوشیِ ۱.۹.۱ همان QR را رد می‌کرد. حالا دستگاهِ اصلی چیزی نشان
     * نمی‌دهد که هیچ گوشی‌ای به آن وصل نمی‌شود؛ loopback و ۱۶۹.۲۵۴ (کارتی
     * که اصلاً نشانی نگرفته) هم از همین راه بیرون می‌مانند.
     */
    fun score(c: Candidate): Int? {
        if (!HostAddress.isPrivate(c.address)) return null
        val o = c.address.split('.').map { it.toInt() }

        var s = when {
            o[0] == 192 -> 0 // ۱۹۲.۱۶۸
            o[0] == 10 -> 1
            o[0] == 172 -> 2 // ۱۷۲.۱۶ تا ۱۷۲.۳۱
            else -> 50 // ۱۰۰.۶۴/۱۰، CGNAT: اینترنتِ سیم‌کارت
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
