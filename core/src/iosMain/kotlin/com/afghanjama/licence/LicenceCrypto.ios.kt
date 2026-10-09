package com.afghanjama.licence

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyVerifySignature
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.posix.int32_tVar

/*
 * سهمِ آیفون — `Security`ِ خودِ اپل.
 *
 * **یک تبدیل لازم است و فقط یکی.** `SecKeyCreateWithData` کلیدِ EC را
 * به شکلِ خامِ X9.63 می‌خواهد (`04 || X || Y`، ۶۵ بایت)، نه
 * SubjectPublicKeyInfo که در فایلِ PEM است. در SPKIِ منحنیِ P-256 آن
 * ۶۵ بایت دقیقاً انتهای ساختارند (۲۶ بایت سرآیند + ۶۵)، پس بریده
 * می‌شوند. امضا همان DER می‌ماند: الگوریتمِ `…X962SHA256` خودش DER
 * می‌خواهد و خودش چکیده می‌گیرد.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun verifyEcdsaP256Sha256(
    spkiDer: ByteArray,
    message: ByteArray,
    derSignature: ByteArray,
): Boolean {
    if (spkiDer.size < 65 || message.isEmpty() || derSignature.isEmpty()) return false
    val raw = spkiDer.copyOfRange(spkiDer.size - 65, spkiDer.size)
    if (raw[0] != 0x04.toByte()) return false

    return memScoped {
        val keyData = cfData(raw) ?: return@memScoped false
        val msgData = cfData(message)
        val sigData = cfData(derSignature)
        val bits = alloc<int32_tVar>().apply { value = 256 }
        val size = CFNumberCreate(null, kCFNumberIntType, bits.ptr)
        val attrs = CFDictionaryCreateMutable(
            null, 3, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr
        )
        CFDictionaryAddValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionaryAddValue(attrs, kSecAttrKeyClass, kSecAttrKeyClassPublic)
        CFDictionaryAddValue(attrs, kSecAttrKeySizeInBits, size)

        val key = SecKeyCreateWithData(keyData, attrs, null)
        val ok = key != null && msgData != null && sigData != null &&
            SecKeyVerifySignature(
                key, kSecKeyAlgorithmECDSASignatureMessageX962SHA256, msgData, sigData, null
            )

        key?.let { CFRelease(it) }
        CFRelease(attrs)
        size?.let { CFRelease(it) }
        CFRelease(keyData)
        msgData?.let { CFRelease(it) }
        sigData?.let { CFRelease(it) }
        ok
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun cfData(bytes: ByteArray): CFDataRef? =
    bytes.usePinned { pinned ->
        CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.convert())
    }
