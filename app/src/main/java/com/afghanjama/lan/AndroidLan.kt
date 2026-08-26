package com.afghanjama.lan

import android.content.Context
import android.net.wifi.WifiManager

/**
 * نشانیِ این گوشی در شبکهٔ محلی.
 *
 * اول از `WifiManager` می‌پرسیم چون روی گوشی ممکن است چند کارتِ شبکه
 * بالا باشد و «اولین کارتِ فعال» جوابِ درستی نباشد. اگر جواب نداد
 * (هات‌اسپات یا اترنت) به راهِ مشترکِ `:core` برمی‌گردیم.
 */
fun androidLocalIp(context: Context): String? {
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
    return Lan.localIp()
}
