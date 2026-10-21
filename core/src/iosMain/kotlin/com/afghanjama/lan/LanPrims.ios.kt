package com.afghanjama.lan

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/*
 * سهمِ آیفون: عددِ تصادفی، SHA-256 و HMAC از خودِ سیستم (Security و
 * CommonCrypto)؛ AES-GCM و P-256 از CryptoKit، از راهِ [IosLanBridge].
 */
@OptIn(ExperimentalForeignApi::class)
internal object PlatformPrims {

    fun random(n: Int): ByteArray {
        val out = ByteArray(n)
        if (n == 0) return out
        val status = out.usePinned { SecRandomCopyBytes(kSecRandomDefault, n.convert(), it.addressOf(0)) }
        check(status == 0) { "SecRandomCopyBytes نگرفت: $status" }
        return out
    }

    fun sha256(data: ByteArray): ByteArray {
        val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
        val input = if (data.isEmpty()) ByteArray(1) else data
        input.usePinned { d ->
            out.usePinned { o -> CC_SHA256(d.addressOf(0), data.size.convert(), o.addressOf(0).reinterpret()) }
        }
        return out
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        require(key.isNotEmpty()) { "HMAC بی کلید" }
        val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
        val input = if (data.isEmpty()) ByteArray(1) else data
        key.usePinned { k ->
            input.usePinned { d ->
                out.usePinned { o ->
                    CCHmac(kCCHmacAlgSHA256, k.addressOf(0), key.size.convert(), d.addressOf(0), data.size.convert(), o.addressOf(0))
                }
            }
        }
        return out
    }

    /** PBKDF2-HMAC-SHA256 از CommonCrypto (`CCKeyDerivationPBKDF`). */
    fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val out = ByteArray(length)
        // CommonCrypto رمز را `const char *` می‌گیرد؛ LanPrims آن را به ASCIIِ چاپی
        // محدود کرده، پس رشته همان بایت‌هاست.
        val text = password.decodeToString()
        val status = salt.usePinned { s ->
            out.usePinned { o ->
                CCKeyDerivationPBKDF(
                    kCCPBKDF2, text, password.size.convert(),
                    s.addressOf(0).reinterpret(), salt.size.convert(),
                    kCCPRFHmacAlgSHA256, iterations.convert(),
                    o.addressOf(0).reinterpret(), length.convert(),
                )
            }
        }
        check(status == 0) { "PBKDF2 نگرفت: $status" }
        return out
    }

    fun gcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == LanCrypto.KEY_BYTES && nonce.size == LanCrypto.NONCE_BYTES)
        return IosLanBridge.require()
            .gcmSeal(key.toNSData(), nonce.toNSData(), plain.toNSData(), aad.toNSData())
            ?.nsBytes()
            ?: throw IllegalStateException("AES-GCM نبست")
    }

    fun gcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray? {
        if (key.size != LanCrypto.KEY_BYTES || nonce.size != LanCrypto.NONCE_BYTES) return null
        if (sealed.size < LanCrypto.TAG_BYTES) return null
        return IosLanBridge.require()
            .gcmOpen(key.toNSData(), nonce.toNSData(), sealed.toNSData(), aad.toNSData())
            ?.nsBytes()
    }

    fun p256KeyPair(): EcKeyPair {
        val both = IosLanBridge.require().p256Generate()?.nsBytes()
            ?: throw IllegalStateException("کلیدِ P-256 ساخته نشد")
        check(both.size == 32 + 65 && both[32] == 4.toByte()) { "کلیدِ P-256 نادرست" }
        return EcKeyPair(both.copyOfRange(32, 97), both.copyOfRange(0, 32))
    }

    fun p256Agree(own: EcKeyPair, peerX963: ByteArray): ByteArray? {
        val priv = own.secret as? ByteArray ?: return null
        if (peerX963.size != 65 || peerX963[0] != 4.toByte()) return null
        return IosLanBridge.require().p256Agree(priv.toNSData(), peerX963.toNSData())
            ?.nsBytes()
            ?.takeIf { s -> s.size == 32 && s.any { it != 0.toByte() } }
    }
}

internal actual fun primRandom(n: Int): ByteArray = PlatformPrims.random(n)
internal actual fun primSha256(data: ByteArray): ByteArray = PlatformPrims.sha256(data)
internal actual fun primHmacSha256(key: ByteArray, data: ByteArray): ByteArray = PlatformPrims.hmacSha256(key, data)
internal actual fun primPbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray =
    PlatformPrims.pbkdf2Sha256(password, salt, iterations, length)
internal actual fun primGcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray =
    PlatformPrims.gcmSeal(key, nonce, plain, aad)
internal actual fun primGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray? =
    PlatformPrims.gcmOpen(key, nonce, sealed, aad)
internal actual fun primP256KeyPair(): EcKeyPair = PlatformPrims.p256KeyPair()
internal actual fun primP256Agree(own: EcKeyPair, peerX963: ByteArray): ByteArray? = PlatformPrims.p256Agree(own, peerX963)
