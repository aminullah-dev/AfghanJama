package com.afghanjama.desktop.data

import com.afghanjama.licence.LicenceKeys
import com.afghanjama.licence.Lnm1
import com.afghanjama.licence.platformMachineId
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/*
 * دودآزماییِ لایسنس روی خودِ ویندوز و مک.
 *
 * **چرا جدا از آزمون‌های گوشی.** آزمون‌های `testDebugUnitTest` پروتکل را
 * با نمونه‌های رسمی می‌سنجند، ولی روی JVMِ رانرِ لینوکس. دو چیز فقط روی
 * خودِ سکو معلوم می‌شود:
 *
 * - شناسهٔ دستگاه: `reg query … MachineGuid` روی ویندوز و `ioreg` روی مک
 *   واقعاً جواب می‌دهند و خوانده می‌شوند؟ اگر نه، هر کمپیوتری کدِ
 *   «جایگزین» می‌گیرد که با نصبِ دوباره عوض می‌شود.
 * - `SHA256withECDSA` روی همان کلاس‌هایی که در بسته می‌روند کار می‌کند؟
 *
 * **بی کلیدِ آزمایشی.** این فایل در خودِ برنامه می‌رود، پس هیچ کلیدِ
 * آزمایشی‌ای در آن نیست: یک جفت‌کلیدِ موقت همین‌جا ساخته می‌شود، با آن
 * کلیدی به شکلِ پروتکل امضا می‌شود و با همان سنجشگرِ اپ سنجیده می‌شود.
 */

private class LicenceFailure(msg: String) : RuntimeException(msg)

private fun licenceNeed(cond: Boolean, msg: String) {
    if (!cond) throw LicenceFailure(msg)
}

private fun licenceCheck(name: String, body: () -> Unit): Boolean =
    try {
        body()
        println("OK    $name")
        true
    } catch (t: Throwable) {
        println("FAIL  $name")
        println("        ${t::class.java.name}: ${t.message}")
        false
    }

private fun b64u(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

fun main() {
    var ok = true
    val os = System.getProperty("os.name").orEmpty().lowercase()

    ok = licenceCheck("the platform id is read on this OS") {
        val id = platformMachineId()
        if (os.contains("win") || os.contains("mac")) {
            licenceNeed(!id.isNullOrBlank(), "no platform id on $os — every PC would get a fallback code")
        }
        if (id != null) {
            val code = Lnm1.machineCode(id)
            licenceNeed(Regex("[0-9A-Z]{4}(-[0-9A-Z]{4}){3}").matches(code), "bad code $code")
            licenceNeed(code == Lnm1.machineCode(id), "the code must be stable")
            println("        machine code: $code")
        }
    } && ok

    // یک کلیدِ پروتکلی با جفت‌کلیدِ موقت.
    val kpg = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
    val pair = kpg.generateKeyPair()
    val body = ("{\"c\":\"خیاطیِ آزمون\",\"e\":null,\"ed\":\"standard\",\"f\":[\"*\"],\"i\":\"2026-10-08\"," +
        "\"id\":\"KY-SMOKE-0001\",\"m\":\"*\",\"p\":\"khayatyar\",\"v\":1}").toByteArray(Charsets.UTF_8)
    val sig = Signature.getInstance("SHA256withECDSA").run {
        initSign(pair.private); update(body); sign()
    }
    val key = "LNM1.${b64u(body)}.${b64u(sig)}"
    val pub = pair.public.encoded

    ok = licenceCheck("a protocol key verifies with SHA256withECDSA on this JVM") {
        val r = Lnm1.verify(key, pub)
        licenceNeed(r is Lnm1.Result.Ok, "rejected: $r")
        licenceNeed((r as Lnm1.Result.Ok).licence.customer == "خیاطیِ آزمون", "customer name garbled")
    } && ok

    ok = licenceCheck("a tampered key is refused") {
        val tampered = "LNM1.${b64u(body.copyOf().also { it[10] = 'X'.code.toByte() })}.${b64u(sig)}"
        licenceNeed(Lnm1.verify(tampered, pub) == Lnm1.Result.Err(Lnm1.Problem.BAD_SIGNATURE), "tampered key accepted")
    } && ok

    ok = licenceCheck("the shipped public key refuses keys it did not sign") {
        licenceNeed(LicenceKeys.production.size == 91, "the production key is not a P-256 SPKI")
        licenceNeed(
            Lnm1.verify(key, LicenceKeys.production) == Lnm1.Result.Err(Lnm1.Problem.BAD_SIGNATURE),
            "the production key accepted a foreign signature"
        )
    } && ok

    if (!ok) {
        println("\nلایسنس روی این سکو سالم نیست.")
        kotlin.system.exitProcess(1)
    }
    println("\nلایسنس روی این سکو سالم است.")
}
