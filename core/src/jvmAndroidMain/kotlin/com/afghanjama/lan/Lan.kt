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
     * روی ویندوز و لینوکس همین کافی است. اندروید یک راهِ دقیق‌ترِ خودش
     * دارد (`WifiManager`) که در `:app` روی همین سوار شده — چون آنجا
     * ممکن است چند کارتِ مجازی هم بالا باشد و انتخابِ اولی درست نباشد.
     */
    fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()
}
