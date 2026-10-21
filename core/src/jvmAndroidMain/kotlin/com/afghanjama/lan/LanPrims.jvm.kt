package com.afghanjama.lan

import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * سهمِ کامپیوتر و اندروید — فقط `javax.crypto` و `java.security`.
 *
 * `GCMParameterSpec` روی اندروید از API ۱۹ هست و `KeyAgreement("ECDH")` و
 * منحنیِ `secp256r1` از API ۱۱؛ کفِ برنامه ۲۴ است. هیچ کتابخانهٔ تازه‌ای
 * لازم نشد.
 */
internal object PlatformPrims {

    private val rng = SecureRandom()

    fun random(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        // کلیدِ خالی را `SecretKeySpec` نمی‌پذیرد؛ HKDF آن را پیش‌تر به صفرها بدل کرده.
        require(key.isNotEmpty()) { "HMAC بی کلید" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher {
        require(key.size == LanCrypto.KEY_BYTES) { "کلیدِ AES باید ۳۲ بایت باشد" }
        require(nonce.size == LanCrypto.NONCE_BYTES) { "nonce باید ۱۲ بایت باشد" }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(LanCrypto.TAG_BYTES * 8, nonce))
            updateAAD(aad)
        }
    }

    fun gcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray =
        gcm(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plain)

    fun gcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray? {
        if (key.size != LanCrypto.KEY_BYTES || nonce.size != LanCrypto.NONCE_BYTES) return null
        if (sealed.size < LanCrypto.TAG_BYTES) return null
        return try {
            gcm(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(sealed)
        } catch (_: GeneralSecurityException) {
            // `AEADBadTagException` و هر شکستِ دیگرِ سنجش — یک جواب: نه.
            null
        }
    }

    /** پارامترهای P-256، از یک کلیدِ ساخته‌شده؛ روی همهٔ نسخه‌های اندروید هست. */
    private val p256: ECParameterSpec by lazy { (generator().generateKeyPair().public as ECPublicKey).params }

    private fun generator(): KeyPairGenerator =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), rng) }

    fun p256KeyPair(): EcKeyPair {
        val kp = generator().generateKeyPair()
        val w = (kp.public as ECPublicKey).w
        return EcKeyPair(encodePoint(w), kp.private)
    }

    fun p256Agree(own: EcKeyPair, peerX963: ByteArray): ByteArray? {
        val priv = own.secret as? PrivateKey ?: return null
        val point = decodePoint(peerX963) ?: return null
        return try {
            val pub = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, p256))
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(priv)
            ka.doPhase(pub, true)
            ka.generateSecret().takeIf { s -> s.size == 32 && s.any { it != 0.toByte() } }
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun encodePoint(w: ECPoint): ByteArray =
        byteArrayOf(4) + fixed32(w.affineX) + fixed32(w.affineY)

    private fun fixed32(n: BigInteger): ByteArray {
        val b = n.toByteArray()
        return when {
            b.size == 32 -> b
            b.size == 33 && b[0] == 0.toByte() -> b.copyOfRange(1, 33)
            b.size < 32 -> ByteArray(32 - b.size) + b
            else -> throw IllegalStateException("مختصِ P-256 بیش از ۳۲ بایت")
        }
    }

    /**
     * `04 ‖ X ‖ Y` → نقطه، **فقط اگر روی منحنی باشد.** جاوا خودش هم
     * می‌سنجد، ولی اینکه هر ارائه‌دهنده‌ای روی هر نسخهٔ اندروید بسنجد را
     * نمی‌دانیم؛ این سنجش حسابِ ساده است (y² = x³ + ax + b به پیمانهٔ p)،
     * نه رمزنگاری.
     */
    internal fun decodePoint(x963: ByteArray): ECPoint? {
        if (x963.size != 65 || x963[0] != 4.toByte()) return null
        val x = BigInteger(1, x963.copyOfRange(1, 33))
        val y = BigInteger(1, x963.copyOfRange(33, 65))
        val field = p256.curve.field as? ECFieldFp ?: return null
        val p = field.p
        if (x.signum() < 0 || y.signum() < 0 || x >= p || y >= p) return null
        val lhs = y.multiply(y).mod(p)
        val rhs = x.multiply(x).multiply(x).add(p256.curve.a.multiply(x)).add(p256.curve.b).mod(p)
        if (lhs != rhs) return null
        return ECPoint(x, y)
    }
}

internal actual fun primRandom(n: Int): ByteArray = PlatformPrims.random(n)
internal actual fun primSha256(data: ByteArray): ByteArray = PlatformPrims.sha256(data)
internal actual fun primHmacSha256(key: ByteArray, data: ByteArray): ByteArray = PlatformPrims.hmacSha256(key, data)
internal actual fun primGcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray =
    PlatformPrims.gcmSeal(key, nonce, plain, aad)
internal actual fun primGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray? =
    PlatformPrims.gcmOpen(key, nonce, sealed, aad)
internal actual fun primP256KeyPair(): EcKeyPair = PlatformPrims.p256KeyPair()
internal actual fun primP256Agree(own: EcKeyPair, peerX963: ByteArray): ByteArray? = PlatformPrims.p256Agree(own, peerX963)
