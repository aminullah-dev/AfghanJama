package com.afghanjama.licence

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * سهمِ ویندوز و مک (و لینوکس، برای رانرِ CI).
 *
 * هر سه از ابزارِ خودِ سیستم خوانده می‌شوند، با سقفِ زمانی: اگر ابزار
 * جواب نداد، `null` برمی‌گردد و کدِ جایگزین ساخته می‌شود — برنامه
 * هرگز منتظرِ یک فرمانِ گیرکرده نمی‌ماند.
 */
actual fun platformMachineId(): String? {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return runCatching {
        when {
            os.contains("win") -> windowsMachineGuid()
            os.contains("mac") || os.contains("darwin") -> macPlatformUuid()
            else -> File("/etc/machine-id").takeIf { it.canRead() }?.readText()?.trim()
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

/** `reg query HKLM\SOFTWARE\Microsoft\Cryptography /v MachineGuid` */
private fun windowsMachineGuid(): String? {
    val out = run("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid")
        ?: return null
    return parseRegValue(out, "MachineGuid")
}

/** `ioreg -rd1 -c IOPlatformExpertDevice` ← `"IOPlatformUUID" = "…"` */
private fun macPlatformUuid(): String? {
    val out = run("ioreg", "-rd1", "-c", "IOPlatformExpertDevice") ?: return null
    return parseIoregUuid(out)
}

/** سطرِ `    MachineGuid    REG_SZ    3f25…` */
internal fun parseRegValue(out: String, name: String): String? =
    out.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith(name, ignoreCase = true) && "REG_SZ" in it }
        ?.substringAfter("REG_SZ")?.trim()
        ?.takeIf { it.isNotEmpty() }

internal fun parseIoregUuid(out: String): String? =
    Regex("\"IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\"").find(out)?.groupValues?.get(1)

private fun run(vararg cmd: String): String? {
    val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    // اول صبر، بعد خواندن: خواندنِ پیش از پایان تا بسته شدنِ جریان
    // می‌ایستد و سقفِ زمانی را بی‌اثر می‌کرد. خروجیِ هر دو فرمان چند
    // کیلوبایت است و در بافرِ لوله جا می‌شود.
    if (!p.waitFor(5, TimeUnit.SECONDS)) {
        p.destroyForcibly()
        return null
    }
    val text = p.inputStream.bufferedReader().use { it.readText() }
    return if (p.exitValue() == 0) text else null
}
