package com.afghanjama.lan

import android.content.Context
import android.net.wifi.WifiManager
import java.util.Locale

/**
 * نشانیِ این گوشی در شبکهٔ محلی.
 *
 * اول از `WifiManager` می‌پرسیم چون روی گوشی ممکن است چند کارتِ شبکه
 * بالا باشد و «اولین کارتِ فعال» جوابِ درستی نباشد. اگر جواب نداد
 * (هات‌اسپات یا اترنت) به راهِ مشترکِ `:core` برمی‌گردیم.
 *
 * نشانیِ وای‌فای هم همان قاعدهٔ گوشیِ کارگر را می‌گذرد
 * ([HostAddress.isPrivate])، مثلِ [Lan.score]؛ وگرنه QR نشانی‌ای را نشان
 * می‌داد که هر گوشیِ کارگر ردش می‌کند. ارقام با `Locale.ROOT` ساخته
 * می‌شوند: وقتی زبانِ گوشی دری است `%d` رقمِ فارسی می‌دهد، و
 * [HostAddress.isPrivate] فقط رقمِ لاتین را می‌شناسد.
 */
fun androidLocalIp(context: Context): String? {
    runCatching {
        @Suppress("DEPRECATION")
        val ip = wifiIp(wifi(context)?.connectionInfo?.ipAddress ?: 0)
        if (ip != null && HostAddress.isPrivate(ip)) return ip
    }
    return Lan.localIp()
}

/**
 * دروازه (مودمِ) وای‌فای‌ای که گوشی رویش است، یا `null`.
 *
 * از `DhcpInfo` خوانده می‌شود که فقط مجوزِ `ACCESS_WIFI_STATE` می‌خواهد —
 * همانی که برنامه از قبل دارد. نشانیِ سخت‌افزاریِ مودم را اندروید از
 * نسخهٔ ۱۰ به برنامه‌ها نمی‌دهد، پس گوشی فقط زیرشبکه و دروازه را می‌شناسد.
 * گوشی‌ای که خودش هات‌اسپات است وای‌فایِ وصل ندارد و `null` می‌گیرد.
 */
fun androidWifiGateway(context: Context): Pair<String, String>? = runCatching {
    val w = wifi(context) ?: return null
    @Suppress("DEPRECATION")
    val ip = wifiIp(w.connectionInfo?.ipAddress ?: 0) ?: return null
    @Suppress("DEPRECATION")
    val gw = wifiIp(w.dhcpInfo?.gateway ?: 0) ?: return null
    ip to gw
}.getOrNull()

private fun wifi(context: Context): WifiManager? =
    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

/** عددِ نشانیِ `WifiManager` (کم‌ارزش‌ترین بایت اول) → متن؛ صفر یعنی هیچ. */
private fun wifiIp(raw: Int): String? =
    if (raw == 0) null
    else String.format(
        Locale.ROOT, "%d.%d.%d.%d",
        raw and 0xFF, raw shr 8 and 0xFF, raw shr 16 and 0xFF, raw shr 24 and 0xFF
    )
