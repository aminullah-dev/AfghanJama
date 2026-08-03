package com.afghanjama.desktop.data

import com.afghanjama.lan.Lan
import com.afghanjama.lan.LanServer

/**
 * دفترِ کارگاه روی پی‌سی، در دسترسِ گوشی‌ها.
 *
 * **این وارونهٔ چیزی است که تا دیروز بود.** سرور روی گوشیِ کارفرما
 * اجرا می‌شد؛ یعنی اگر آن گوشی خاموش بود یا از کارگاه بیرون می‌رفت،
 * هیچ خیاطی کارش را نمی‌دید. پی‌سی همیشه روشن است — همان تصمیمی که
 * کارفرما گرفت.
 *
 * خودِ `LanServer` عوض نشد؛ فقط دیگر دیتابیس را خودش نمی‌سازد و
 * می‌گیرد. همان کدِ سرور روی هر دو سکو اجرا می‌شود.
 */
object DesktopServer {

    private var server: LanServer? = null

    val running: Boolean get() = server?.running == true

    /** نشانی‌ای که باید روی گوشیِ خیاط وارد شود. */
    fun address(): String? = Lan.localIp()?.let { "$it:${Lan.PORT}" }

    /**
     * روشن کردنِ سرور با رمزِ اتصال.
     *
     * @return `false` اگر رمز خالی باشد یا درگاه گرفته باشد
     */
    fun start(pairCode: String): Boolean {
        val s = server ?: LanServer(openDatabase()).also { server = it }
        return s.start(pairCode)
    }

    fun stop() {
        server?.stop()
        server = null
    }
}
