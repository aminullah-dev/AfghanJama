package com.afghanjama.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/*
 * سهمِ JVM — همان کدی که تا امروز در `PinHash` بود، بی یک کلمه تغییر.
 * فقط جایش عوض شد تا `:core` برای iOS هم کامپایل شود.
 */

internal actual fun secureRandomBytes(n: Int): ByteArray =
    ByteArray(n).also { SecureRandom().nextBytes(it) }

internal actual fun pbkdf2(
    pin: String,
    salt: ByteArray,
    iterations: Int,
    keyBits: Int,
    algo: String,
): ByteArray? {
    val factory = runCatching { SecretKeyFactory.getInstance(algo) }.getOrNull() ?: return null
    val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyBits)
    return runCatching { factory.generateSecret(spec).encoded }.getOrNull()
}

internal actual fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)
