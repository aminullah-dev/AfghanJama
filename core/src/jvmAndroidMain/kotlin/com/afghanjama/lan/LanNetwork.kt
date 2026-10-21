package com.afghanjama.lan

import com.afghanjama.prefs.DeviceMode

/**
 * «شبکهٔ کارگاه» — آنچه دستگاهِ اصلی از شبکه‌ای که رویش «اصلی» شد به یاد
 * می‌سپارد، تا اشتراک را **فقط همان‌جا** خودش روشن کند.
 *
 * **چرا لازم شد.** از ۱.۹.۱ اشتراک سرِ باز شدنِ برنامه خودش روشن می‌شود
 * ([LanHub]). دستگاهِ اصلی اما اغلب لپ‌تاپ یا گوشیِ خودِ کارفرماست و با
 * او به خانه و چایخانه می‌رود؛ آنجا هم دستگاه‌های دیگر نشانیِ خصوصی دارند
 * و مرزِ [HostAddress.isPrivate] جلویشان را نمی‌گیرد. سروری که روی هر
 * وای‌فایی خودش روشن شود، رمزِ شش‌رقمی را جلوی هر غریبه‌ای می‌گذارد.
 *
 * سه نشانه، از ضعیف به قوی:
 *
 * - [subnet]: «۱۹۲.۱۶۸.۱.۰/۲۴». همیشه هست، ولی بسیاری از مودم‌ها همین را
 *   دارند.
 * - [gateway]: نشانیِ مودم، مثلاً «۱۹۲.۱۶۸.۱.۱». آن هم میانِ مودم‌ها رایج
 *   است.
 * - [gatewayMac]: نشانیِ سخت‌افزاریِ همان مودم. این است که چایخانه‌ای با
 *   همان مدلِ مودم را از کارگاه جدا می‌کند. فقط روی کامپیوتر خوانده
 *   می‌شود؛ اندروید از نسخهٔ ۱۰ آن را به برنامه‌ها نمی‌دهد.
 *
 * نشانه‌ای که یک سو ندارد (`null`) داوری نمی‌کند؛ تصمیم با بقیه است.
 * بی اندروید و بی `org.json` است تا آزمونِ JVMِ `:app` بسنجدش.
 */
data class LanNetwork(
    val subnet: String,
    val gateway: String? = null,
    val gatewayMac: String? = null,
) {

    /** همین شبکه است؟ [now] شبکه‌ای است که دستگاه حالا رویش است. */
    fun sameAs(now: LanNetwork): Boolean =
        subnet == now.subnet &&
            (gateway == null || now.gateway == null || gateway == now.gateway) &&
            (gatewayMac == null || now.gatewayMac == null || gatewayMac == now.gatewayMac)

    /** برای تنظیمات: `1|subnet|gateway|mac`. */
    fun encode(): String = listOf(FORMAT, subnet, gateway.orEmpty(), gatewayMac.orEmpty()).joinToString("|")

    companion object {
        private const val FORMAT = "1"

        /** خواندن از تنظیمات؛ `null` اگر چیزی ذخیره نشده یا خراب است. */
        fun decode(raw: String): LanNetwork? {
            val f = raw.trim().split('|')
            if (f.size != 4 || f[0] != FORMAT) return null
            val (ip, prefix) = f[1].split('/').takeIf { it.size == 2 } ?: return null
            val p = prefix.toIntOrNull() ?: return null
            if (subnetOf(ip, p) != f[1]) return null
            val gw = f[2].ifBlank { null }?.takeIf { octets(it) != null }
            val mac = f[3].ifBlank { null }?.let { normalizeMac(it) }
            return LanNetwork(f[1], gw, mac)
        }

        /**
         * زیرشبکهٔ [ip] با طولِ پیشوندِ [prefix]، مثلِ `192.168.1.0/24`.
         *
         * پیشوندِ بی‌معنا (صفر یا بیش از ۳۲؛ بعضی کارت‌ها روی ویندوز چنین
         * گزارش می‌کنند) `/32` می‌شود: یعنی «همین نشانی». حدسِ `/24` ممکن
         * بود دو شبکهٔ جدا را یکی کند؛ `/32` در بدترین حال فقط روشن‌شدنِ
         * خودکار را تا «روشن»ِ دستی عقب می‌اندازد.
         */
        fun subnetOf(ip: String, prefix: Int): String? {
            val o = octets(ip) ?: return null
            val p = if (prefix in 1..32) prefix else 32
            val value = o.fold(0L) { acc, b -> (acc shl 8) or b.toLong() }
            val mask = if (p == 32) 0xFFFF_FFFFL else (0xFFFF_FFFFL shl (32 - p)) and 0xFFFF_FFFFL
            val net = value and mask
            val text = (3 downTo 0).joinToString(".") { ((net shr (8 * it)) and 0xFF).toString() }
            return "$text/$p"
        }

        /** آیا [ip] درونِ [subnet] است؟ */
        fun contains(subnet: String, ip: String): Boolean {
            val (_, prefix) = subnet.split('/').takeIf { it.size == 2 } ?: return false
            val p = prefix.toIntOrNull() ?: return false
            return subnetOf(ip, p) == subnet
        }

        /** `a4-2b-b0-2-34-56` یا `A4:2B:B0:02:34:56` → `a4:2b:b0:02:34:56`؛ همه‌صفر و همه‌یک `null`. */
        fun normalizeMac(raw: String): String? {
            val parts = raw.trim().split(':', '-')
            if (parts.size != 6) return null
            if (parts.any { it.isEmpty() || it.length > 2 || it.any { c -> c.lowercaseChar() !in HEX } }) return null
            val mac = parts.joinToString(":") { it.lowercase().padStart(2, '0') }
            return mac.takeUnless { it == "00:00:00:00:00:00" || it == "ff:ff:ff:ff:ff:ff" }
        }

        private const val HEX = "0123456789abcdef"

        internal fun octets(ip: String): IntArray? {
            val parts = ip.trim().split('.')
            if (parts.size != 4) return null
            val out = IntArray(4)
            for ((i, p) in parts.withIndex()) {
                if (p.isEmpty() || p.length > 3 || p.any { it !in '0'..'9' }) return null
                val n = p.toInt()
                if (n > 255) return null
                out[i] = n
            }
            return out
        }
    }
}

/** یک نشانیِ این دستگاه، روی کدام کارتِ شبکه، در کدام شبکه. */
data class LanLink(val address: String, val iface: String, val network: LanNetwork)

/**
 * چرا اشتراک الان روشن نیست — آنچه صفحهٔ «اشتراکِ کارگاه» به کارفرما
 * می‌گوید. `null` یعنی روشن است.
 */
enum class HubOff {
    /** برنامه تازه باز شده و وضعیتِ لایسنس هنوز خوانده نشده. */
    WAITING,

    /** این دستگاه «اصلی» نیست. */
    NOT_MAIN,

    /** رمزِ اتصال ذخیره نشده. */
    NO_CODE,

    /** شبکهٔ کارگاه هنوز به یاد سپرده نشده (نصبِ پیش از ۱.۹.۱). */
    NEW_NETWORK,

    /** دستگاه روی شبکهٔ دیگری است. */
    OTHER_NETWORK,

    /** دستگاه به هیچ شبکهٔ محلی‌ای وصل نیست. */
    NO_NETWORK,

    /** درگاه را برنامهٔ دیگری گرفته. */
    PORT_BUSY,

    /** دفترِ کارگاه باز نشد. */
    NO_LEDGER,

    /** کارفرما خودش خاموش کرد. */
    STOPPED,

    /**
     * دفتر آخرین بار روی دستگاهِ دیگری مرکز بود (بازیابی روی کامپیوترِ تازه)؛
     * تا کارفرما نگوید «مرکز اینجاست»، هیچ گوشی‌ای جواب نمی‌گیرد.
     */
    MOVED,

    /** دستگاهِ دیگری هنوز با همین دفتر (همان hubId) جواب می‌دهد. */
    DUPLICATE_HUB,
}

/**
 * قاعدهٔ روشن‌شدنِ خودکار — خالص، تا بی شبکه و بی ساعت آزموده شود.
 *
 * `null` یعنی «روشن کن». ترتیب مهم است: اول نقش و رمز، بعد اینکه اصلاً
 * شبکه‌ای به یاد سپرده شده، و آخر اینکه دستگاه حالا رویش است.
 */
object AutoStartRule {
    fun decide(
        mode: DeviceMode,
        code: String,
        remembered: LanNetwork?,
        current: Collection<LanNetwork>,
    ): HubOff? = when {
        mode != DeviceMode.MAIN -> HubOff.NOT_MAIN
        code.isBlank() -> HubOff.NO_CODE
        remembered == null -> HubOff.NEW_NETWORK
        current.isEmpty() -> HubOff.NO_NETWORK
        current.any { remembered.sameAs(it) } -> null
        else -> HubOff.OTHER_NETWORK
    }
}

/** یک مسیرِ پیش‌فرض از جدولِ مسیریابیِ سیستم: از [gateway]، روی کارتِ [iface] یا کارتی که [ifaceIp] دارد. */
data class DefaultRoute(val gateway: String, val iface: String? = null, val ifaceIp: String? = null)

/**
 * خواندنِ جدولِ مسیریابی و جدولِ ARP از خروجیِ ابزارهای خودِ سیستم.
 *
 * جاوا نشانیِ مودم را نمی‌دهد؛ کامپیوتر آن را از `netstat` (مک)،
 * `route print` (ویندوز) یا `ip route` (لینوکس) می‌خوانَد. خواندن اینجاست و
 * اجرای دستور در `:desktop`، تا خواندن با نمونه‌های واقعی آزموده شود.
 * سرخط‌های این خروجی‌ها در ویندوزِ غیرِانگلیسی ترجمه می‌شوند، پس فقط
 * سطرهای عددی خوانده می‌شوند، نه سرخط‌ها.
 */
object RouteTable {

    private val IFACE = Regex("^[a-z][a-z0-9]*[0-9]$")

    /** مک: `netstat -rn -f inet`. */
    fun parseNetstat(text: String): List<DefaultRoute> = text.lines().mapNotNull { line ->
        val t = line.trim().split(Regex("\\s+"))
        if (t.size < 3 || t[0] != "default" || LanNetwork.octets(t[1]) == null) return@mapNotNull null
        // ستونِ Netif: نخستین نامِ کارت پس از Flags (نسخه‌های قدیمیِ مک Refs و Use هم دارند).
        val iface = t.drop(2).firstOrNull { IFACE.matches(it) }
        DefaultRoute(t[1], iface = iface)
    }

    /** ویندوز: `route print -4`. «On-link» و ستونِ «Default»ِ مسیرهای ماندگار نشانی نیستند و کنار می‌روند. */
    fun parseRoutePrint(text: String): List<DefaultRoute> = text.lines().mapNotNull { line ->
        val t = line.trim().split(Regex("\\s+"))
        if (t.size < 4 || t[0] != "0.0.0.0" || t[1] != "0.0.0.0" || LanNetwork.octets(t[2]) == null) return@mapNotNull null
        DefaultRoute(t[2], ifaceIp = t[3].takeIf { LanNetwork.octets(it) != null })
    }

    /** لینوکس: `ip -4 route show default`. */
    fun parseIpRoute(text: String): List<DefaultRoute> = text.lines().mapNotNull { line ->
        val t = line.trim().split(Regex("\\s+"))
        if (t.firstOrNull() != "default") return@mapNotNull null
        fun after(word: String) = t.indexOf(word).takeIf { it >= 0 && it + 1 < t.size }?.let { t[it + 1] }
        val gw = after("via")?.takeIf { LanNetwork.octets(it) != null } ?: return@mapNotNull null
        DefaultRoute(gw, iface = after("dev"), ifaceIp = after("src")?.takeIf { LanNetwork.octets(it) != null })
    }

    private val MAC = Regex("(?i)(?<![0-9a-f:-])(?:[0-9a-f]{1,2}[:-]){5}[0-9a-f]{1,2}(?![0-9a-f:-])")

    /**
     * نشانیِ سخت‌افزاریِ [ip] از خروجیِ `arp -n IP` (مک)، `arp -a IP`
     * (ویندوز) یا `ip neigh show IP` (لینوکس). فقط سطری که خودِ همین
     * نشانی در آن آمده خوانده می‌شود.
     */
    fun parseArp(text: String, ip: String): String? {
        val me = Regex("(?<![0-9.])" + Regex.escape(ip) + "(?![0-9.])")
        return text.lines()
            .filter { me.containsMatchIn(it) }
            .firstNotNullOfOrNull { line -> MAC.find(line)?.value?.let { LanNetwork.normalizeMac(it) } }
    }

    /**
     * دروازهٔ هر نشانیِ این دستگاه: مسیرِ پیش‌فرضی که روی همان کارت است و
     * دروازه‌اش درونِ همان زیرشبکه. کارتی که مسیرِ پیش‌فرض ندارد (مثلاً
     * کابلِ کارگاهِ بی‌اینترنت کنارِ وای‌فایِ خانه) دروازه نمی‌گیرد.
     */
    fun attach(links: List<LanLink>, routes: List<DefaultRoute>, mac: (String) -> String?): Map<String, LanNetwork> =
        links.associate { link ->
            val r = routes.firstOrNull { it.ifaceIp == link.address } ?: routes.firstOrNull { it.iface == link.iface }
            val net = if (r != null && LanNetwork.contains(link.network.subnet, r.gateway)) {
                link.network.copy(gateway = r.gateway, gatewayMac = mac(r.gateway))
            } else link.network
            link.address to net
        }
}
