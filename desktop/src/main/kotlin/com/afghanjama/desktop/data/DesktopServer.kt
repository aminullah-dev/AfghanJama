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

    /**
     * نشانی‌ای که باید روی گوشیِ خیاط وارد شود — **بی درگاه**.
     *
     * تا دیروز `"$ip:${Lan.PORT}"` برمی‌گشت و این یک اشکالِ واقعی بود:
     * `LanClient` خودش درگاه را می‌چسباند
     * (`URL("http://$host:${Lan.PORT}$path")`). یعنی هر کس نشانیِ
     * نشان‌داده‌شده را در گوشی وارد می‌کرد، به
     * `http://192.168.1.5:8797:8797/ping` می‌رسید و اتصال هرگز برقرار
     * نمی‌شد — بی هیچ پیامِ روشنی که چرا.
     *
     * `LanHost.localIp()` هم همین قرارداد را دارد: نشانیِ برهنه.
     */
    fun address(): String? = Lan.localIp()

    /**
     * روشن کردنِ سرور با رمزِ اتصال.
     *
     * دیتابیس **از `DesktopLedger`** می‌آید، نه `openDatabase()`ِ تازه.
     * با اتصالِ جدا، نوشتنِ گوشی `Flow`های پنجره را بی‌اعتبار نمی‌کرد و
     * صفحهٔ پی‌سی از کارِ کارگاه عقب می‌ماند.
     *
     * @return `false` اگر رمز خالی باشد یا درگاه گرفته باشد
     */
    fun start(pairCode: String): Boolean {
        val db = DesktopLedger.db().getOrNull() ?: return false
        val s = server ?: LanServer(db).also { server = it }
        return s.start(pairCode)
    }

    fun stop() {
        server?.stop()
        server = null
    }
}
