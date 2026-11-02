package com.afghanjama.licence

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * تأییدِ امضای لایسنس روی آیفون (`Security.framework`).
 *
 * بردارها با `openssl` ساخته و با خودِ openssl تأیید شده‌اند:
 * کلیدِ P-256، امضای DER روی SHA-256 پیامِ «LNM1 test message».
 * کلیدِ دوم یک کلیدِ نامرتبط است.
 */
class IosEcdsaVerifyTest {

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val keyA = hex(
        "3059301306072a8648ce3d020106082a8648ce3d030107034200044d252c223a0e2487516ca2d597866527" +
            "dda249991c39b9f9b2aa9d57214b62889901446588e6dee548bcd813eedf177ae5027d74481428d414e15bf9929397d0"
    )
    private val keyB = hex(
        "3059301306072a8648ce3d020106082a8648ce3d03010703420004abefde0e5c5fb429ee53c76955d20b0a" +
            "ab82f8441ccaf24d1283b9162b7ffd53d5fb96d82442c82b5ba7d13e312bf4485cabf1a1e5b76b420e84023d4b0584a3"
    )
    private val sig = hex(
        "3043022061a65a2c5d89c88f70829a1c509249a77f2ca8c635c141c34a385855cd0671e3021f3ee38b6669108f" +
            "7d9810b1577d905ae22ac8934665d04ebd38ddd453ee39e7"
    )
    private val msg = "LNM1 test message".encodeToByteArray()

    @Test
    fun validSignatureVerifies() {
        assertTrue(verifyEcdsaP256Sha256(keyA, msg, sig))
    }

    @Test
    fun changedMessageIsRejected() {
        assertFalse(verifyEcdsaP256Sha256(keyA, "LNM1 test messagf".encodeToByteArray(), sig))
    }

    @Test
    fun changedSignatureIsRejected() {
        val bad = sig.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        assertFalse(verifyEcdsaP256Sha256(keyA, msg, bad))
    }

    @Test
    fun otherKeyIsRejected() {
        assertFalse(verifyEcdsaP256Sha256(keyB, msg, sig))
    }

    @Test
    fun degenerateInputsAreRejectedWithoutCrashing() {
        assertFalse(verifyEcdsaP256Sha256(ByteArray(0), msg, sig))
        assertFalse(verifyEcdsaP256Sha256(ByteArray(64), msg, sig))
        assertFalse(verifyEcdsaP256Sha256(keyA, ByteArray(0), sig))
        assertFalse(verifyEcdsaP256Sha256(keyA, msg, ByteArray(0)))
        // آخرین ۶۵ بایت با 0x04 شروع نمی‌شود
        val notUncompressed = keyA.copyOf().also { it[it.size - 65] = 0x02 }
        assertFalse(verifyEcdsaP256Sha256(notUncompressed, msg, sig))
    }
}
