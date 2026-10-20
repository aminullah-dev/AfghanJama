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
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val raw = wifi?.connectionInfo?.ipAddress ?: 0
        if (raw != 0) {
            val ip = String.format(
                Locale.ROOT, "%d.%d.%d.%d",
                raw and 0xFF, raw shr 8 and 0xFF, raw shr 16 and 0xFF, raw shr 24 and 0xFF
            )
            if (HostAddress.isPrivate(ip)) return ip
        }
    }
    return Lan.localIp()
}
