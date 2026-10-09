package com.afghanjama.licence

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/*
 * سهمِ ویندوز/مک و اندروید — `java.security`ِ خودِ سکو.
 *
 * `SHA256withECDSA` امضای DER می‌خواهد، همان شکلی که کلید دارد؛ پس
 * هیچ تبدیلی لازم نیست. روی اندروید ۷ (API ۲۴) هم هست.
 */
internal actual fun verifyEcdsaP256Sha256(
    spkiDer: ByteArray,
    message: ByteArray,
    derSignature: ByteArray,
): Boolean = runCatching {
    val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spkiDer))
    Signature.getInstance("SHA256withECDSA").run {
        initVerify(key)
        update(message)
        verify(derSignature)
    }
}.getOrDefault(false)
