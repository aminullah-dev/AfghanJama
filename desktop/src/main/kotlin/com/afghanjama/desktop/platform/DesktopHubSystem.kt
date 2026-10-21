package com.afghanjama.desktop.platform

import com.afghanjama.lan.HubProtocol
import com.afghanjama.prefs.Settings
import java.util.concurrent.TimeUnit

/**
 * آنچه سیستمِ عامل می‌تواند جلویش را بگیرد — و صفحهٔ «کارگاهِ وصل» صادقانه
 * می‌گوید (طرح، B.9).
 *
 * **هیچ‌چیز بی‌صدا عوض نمی‌شود.** دیوارِ آتشِ ویندوز فقط با دکمهٔ کارفرما و
 * پرسشِ UACِ خودِ ویندوز («آیا اجازه می‌دهید…») باز می‌شود؛ مک فقط صفحهٔ
 * تنظیماتِ خودش را باز می‌کند.
 */
object DesktopHubSystem {

    private const val SINGLE = "'"
    private val os = System.getProperty("os.name").orEmpty().lowercase()
    val onWindows: Boolean = os.startsWith("windows")
    val onMac: Boolean = os.contains("mac") || os.contains("darwin")

    /** برنامهٔ اجرایی (`KhayatYar.exe`) — برای قاعدهٔ «برنامه» در دیوارِ آتش. */
    private fun exePath(): String? =
        ProcessHandle.current().info().command().orElse(null)?.takeIf { it.endsWith(".exe", ignoreCase = true) }

    /**
     * دو قاعدهٔ ورودی در «شبکه‌های خصوصی»: درگاهِ ۸۷۹۷، و خودِ برنامه. دومی
     * لازم است چون اگر کسی پرسشِ اولِ ویندوز را رد کرده باشد، ویندوز برای همین
     * برنامه قاعدهٔ «بستن» ساخته و قاعدهٔ درگاه به‌تنهایی از آن نمی‌گذرد (نقدِ ۲۳).
     *
     * یک پرسشِ UAC؛ اگر کارفرما «نه» بزند، چیزی عوض نمی‌شود و همین گفته می‌شود.
     */
    fun allowOnWindowsFirewall(port: Int = HubProtocol.PORT): Result<Unit> = runCatching {
        check(onWindows) { "فقط روی ویندوز" }
        val rules = buildList {
            add("netsh advfirewall firewall add rule name=\"KhayatYar LAN\" dir=in action=allow protocol=TCP localport=$port profile=private")
            exePath()?.let { add("netsh advfirewall firewall add rule name=\"KhayatYar App\" dir=in action=allow program=\"$it\" enable=yes profile=private") }
        }.joinToString(" & ")
        // PowerShell: نقلِ قولِ تکی، و هر ' درونش دوتایی.
        val quoted = ("/c " + rules).replace(SINGLE, SINGLE + SINGLE)
        val ps = "Start-Process -FilePath cmd.exe -ArgumentList " + SINGLE + quoted + SINGLE + " -Verb RunAs -Wait -WindowStyle Hidden"
        val shell = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps)
            .redirectErrorStream(true).start()
        if (!shell.waitFor(120, TimeUnit.SECONDS)) {
            shell.destroyForcibly()
            error("ویندوز جواب نداد.")
        }
        check(shell.exitValue() == 0) { "ویندوز اجازه نداد (پرسشِ «آیا اجازه می‌دهید» رد شد یا مدیر نیستید)." }
    }

    /** صفحهٔ «دیوارِ آتش»ِ تنظیماتِ مک. */
    fun openMacFirewallSettings(): Boolean = runCatching {
        check(onMac)
        val open = ProcessBuilder("/usr/bin/open", "x-apple.systempreferences:com.apple.Network-Settings.extension?Firewall")
            .redirectErrorStream(true).start()
        open.waitFor(10, TimeUnit.SECONDS) && open.exitValue() == 0 ||
            ProcessBuilder("/usr/bin/open", "/System/Library/PreferencePanes/Security.prefPane").start().waitFor(10, TimeUnit.SECONDS)
    }.getOrDefault(false)

    // ------------------------------------------------------------ بیدار ماندن (فقط مک)

    @Volatile private var caffeinate: Process? = null

    /**
     * «تا وقتی کارگاه روشن است، کامپیوتر نخوابد» — فقط مک (`caffeinate -i -w`
     * که با بسته شدنِ همین برنامه خودش تمام می‌شود). روی ویندوز این گزینه
     * نشان داده نمی‌شود (نقدِ ۲۳).
     */
    @Synchronized
    fun keepAwake(on: Boolean) {
        if (!onMac) return
        val running = caffeinate?.isAlive == true
        if (on && !running) {
            caffeinate = runCatching {
                ProcessBuilder("/usr/bin/caffeinate", "-i", "-w", ProcessHandle.current().pid().toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            }.getOrNull()
        } else if (!on && running) {
            caffeinate?.destroy()
            caffeinate = null
        }
    }

    val awake: Boolean get() = caffeinate?.isAlive == true
}

/** تنظیماتِ «کارگاهِ وصل» روی کامپیوتر. */
object HubDeskPrefs {
    private const val FILE = "hub_desk"

    /** بستنِ پنجره وقتی کارگاه روشن است: `KEEP` (به سینی)، `QUIT`، یا خالی = بپرس. */
    fun closeChoice(s: Settings): String = s.getString(FILE, "close_choice")
    fun setCloseChoice(s: Settings, v: String) = s.putString(FILE, "close_choice", v)

    fun keepAwake(s: Settings): Boolean = s.getBoolean(FILE, "keep_awake", false)
    fun setKeepAwake(s: Settings, on: Boolean) = s.putBoolean(FILE, "keep_awake", on)

    /** نشانیِ این کامپیوتر بارِ پیش — برای «نشانی عوض شد». */
    fun lastIp(s: Settings): String = s.getString(FILE, "last_ip")
    fun setLastIp(s: Settings, ip: String) = s.putString(FILE, "last_ip", ip)

    const val KEEP = "KEEP"
    const val QUIT = "QUIT"
}
