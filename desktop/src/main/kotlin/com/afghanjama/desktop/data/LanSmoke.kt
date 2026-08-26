package com.afghanjama.desktop.data

import com.afghanjama.lan.Lan

/*
 * دودآزماییِ مرزِ شبکه روی ویندوز.
 *
 * دو اشکالِ واقعی که اینجا بسته شد و هیچ‌کدام سرِ ساخت دیده نمی‌شدند —
 * چون هر دو **داده‌ی خاموشِ غلط** برمی‌گرداندند، نه استثنا:
 *
 * ۱. `DesktopServer.address()` نشانی را با درگاه می‌داد
 *    (`192.168.1.5:8797`) ولی `LanClient` خودش درگاه را می‌چسباند. هر
 *    کس آن نشانی را در گوشی وارد می‌کرد به
 *    `http://192.168.1.5:8797:8797/ping` می‌رسید و فقط می‌دید «وصل
 *    نشد».
 *
 * ۲. `DesktopServer` دیتابیس را خودش باز می‌کرد — یک اتصالِ دومِ نویسنده
 *    به همان فایل. نوشتنِ گوشی `Flow`های پنجره را بی‌اعتبار نمی‌کرد، پس
 *    خیاط کار را جلو می‌برد و صفحهٔ پی‌سی همان‌جا می‌ماند.
 *
 * سرور روشن نمی‌شود (درگاه گرفتن روی ماشینِ CI کارِ درستی نیست)؛ آنچه
 * سنجیده می‌شود قراردادهاست.
 */

private class LanFailure(msg: String) : RuntimeException(msg)

private fun lanNeed(cond: Boolean, msg: String) {
    if (!cond) throw LanFailure(msg)
}

private fun lanCheck(name: String, body: () -> Unit): Boolean =
    try {
        body()
        println("OK    $name")
        true
    } catch (t: Throwable) {
        println("FAIL  $name")
        println("        ${t::class.java.name}: ${t.message}")
        false
    }

fun main() {
    println("lan smoke - KhayatYar Windows")
    println("=".repeat(52))
    var ok = true

    ok = lanCheck("the shown address carries no port (LanClient adds it)") {
        val shown = DesktopServer.address()
        val ip = Lan.localIp()
        if (ip == null) {
            // روی ماشینی بی کارتِ شبکه هر دو باید `null` باشند؛ سکوت
            // بدترین جواب است.
            lanNeed(shown == null, "no local IP, so the address must be null too")
            println("        (no network interface here - null on both sides)")
            return@lanCheck
        }
        lanNeed(shown != null, "there is a local IP but address() returned null")
        lanNeed(
            !shown!!.contains(":"),
            "address() must not include the port, got '$shown' - " +
                "LanClient builds http://\$host:${Lan.PORT}"
        )
        lanNeed(shown == ip, "address() should be exactly the local IP, got '$shown'")
    } && ok

    ok = lanCheck("the LAN host serves the SAME ledger the window uses") {
        val fromLedger = DesktopLedger.db().getOrNull()
        lanNeed(fromLedger != null, "the ledger did not open")
        val fromHost = DesktopLanHost().ledger()
        // برابریِ **هویتی**، نه ساختاری: دو نمونهٔ جدا یعنی دو اتصال.
        lanNeed(
            fromHost === fromLedger,
            "the server would open a second connection - phone writes would " +
                "not refresh the PC's screen"
        )
    } && ok

    ok = lanCheck("the device name is never blank") {
        val name = DesktopLanHost().deviceName
        lanNeed(name.isNotBlank(), "deviceName must not be blank")
        println("        (this machine reports: $name)")
    } && ok

    println("=".repeat(52))
    println(if (ok) "lan path verified" else "lan smoke FAILED")
    kotlin.system.exitProcess(if (ok) 0 else 1)
}
