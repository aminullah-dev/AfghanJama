package com.afghanjama.lan

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * قراردادِ سادهٔ بینِ گوشیِ اصلی و گوشی‌های کارگران، روی وای‌فای کارگاه.
 *
 * عمداً هیچ کتابخانهٔ تازه‌ای اضافه نشده: سرور با `ServerSocket` و کلاینت
 * با `HttpURLConnection` که هر دو در خودِ جاوا هستند، و JSON با
 * `org.json` که در خودِ اندروید هست. یک ERPِ کارگاهی نباید برای
 * فرستادنِ چند خط متن، ده مگابایت کتابخانه بیاورد.
 */
object Lan {
    const val PORT = 8797

    // مسیرها
    const val PATH_PING = "/ping"
    const val PATH_MY_WORK = "/my-work"
    const val PATH_REQUEST = "/request"

    /** هدرِ رمزِ اتصال. سرور هر درخواستِ بدونِ رمزِ درست را رد می‌کند. */
    const val HEADER_CODE = "X-Workshop-Code"

    /** نسخهٔ قرارداد؛ اگر دو گوشی نسخهٔ متفاوت داشته باشند صریح می‌گوییم. */
    const val PROTOCOL = 1

    /**
     * نشانیِ این گوشی در شبکهٔ محلی. اول از WifiManager می‌پرسیم و اگر
     * جواب نداد (مثلاً هات‌اسپات یا اترنت) از خودِ کارت‌های شبکه.
     */
    fun localIp(context: Context): String? {
        runCatching {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val raw = wifi?.connectionInfo?.ipAddress ?: 0
            if (raw != 0) {
                return "%d.%d.%d.%d".format(
                    raw and 0xFF, raw shr 8 and 0xFF, raw shr 16 and 0xFF, raw shr 24 and 0xFF
                )
            }
        }
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
