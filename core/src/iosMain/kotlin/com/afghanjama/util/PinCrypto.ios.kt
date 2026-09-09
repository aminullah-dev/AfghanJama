package com.afghanjama.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA1
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/*
 * سهمِ iOS — با CommonCrypto، همان کتابخانه‌ای که خودِ سیستم برای
 * Keychain به کار می‌برد.
 *
 * **چرا CommonCrypto و نه پیاده‌سازیِ دستیِ PBKDF2 در کاتلین.** نوشتنش
 * سخت نیست، ولی ۱۰۰٬۰۰۰ تکرارِ HMAC-SHA256 در کدِ تفسیرنشدهٔ عمومی
 * کُند است و این تابع دقیقاً سرِ ورودِ کاربر اجرا می‌شود. نسخهٔ سیستمی
 * از دستوراتِ رمزنگاریِ خودِ پردازنده استفاده می‌کند.
 *
 * **ترجمهٔ نامِ الگوریتم** — و این بندِ سازگاری است، نه تزیین: رشتهٔ
 * ذخیره‌شده نامِ جاوایی را در خود دارد، پس آیفون باید همان نام را
 * بشناسد تا رمزی که روی گوشیِ اندرویدی ساخته شده اینجا هم باز شود.
 * نامِ ناشناخته `null` برمی‌گرداند، نه یک حدسِ خاموش.
 */

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(n: Int): ByteArray {
    val out = ByteArray(n)
    val status = out.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, n.convert(), pinned.addressOf(0))
    }
    // `errSecSuccess` صفر است. اگر مولدِ سیستم نداد، نمکِ قابل‌حدس
    // نمی‌سازیم — بلند می‌شکنیم.
    check(status == 0) { "SecRandomCopyBytes نگرفت: $status" }
    return out
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun pbkdf2(
    pin: String,
    salt: ByteArray,
    iterations: Int,
    keyBits: Int,
    algo: String,
): ByteArray? {
    val prf = when (algo) {
        "PBKDF2WithHmacSHA256" -> kCCPRFHmacAlgSHA256
        "PBKDF2WithHmacSHA1" -> kCCPRFHmacAlgSHA1
        else -> return null
    }
    val pinBytes = pin.encodeToByteArray()
    val out = ByteArray(keyBits / 8)
    // `password` در امضای cinterop رشته است (`const char*`)، نه
    // اشاره‌گرِ بایت؛ ولی طولش باید طولِ بایتیِ UTF-8 باشد نه تعدادِ
    // کاراکترها — وگرنه رمزِ غیرلاتین کلیدِ دیگری می‌ساخت.
    val status =
        salt.usePinned { pinnedSalt ->
            out.usePinned { pinnedOut ->
                CCKeyDerivationPBKDF(
                    algorithm = kCCPBKDF2,
                    password = pin,
                    passwordLen = pinBytes.size.convert(),
                    salt = pinnedSalt.addressOf(0).reinterpret(),
                    saltLen = salt.size.convert(),
                    prf = prf,
                    rounds = iterations.convert(),
                    derivedKey = pinnedOut.addressOf(0).reinterpret(),
                    derivedKeyLen = out.size.convert(),
                )
            }
        }
    return if (status == 0) out else null
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256(data: ByteArray): ByteArray {
    val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
    data.usePinned { pinnedIn ->
        out.usePinned { pinnedOut ->
            CC_SHA256(
                pinnedIn.addressOf(0),
                data.size.convert(),
                pinnedOut.addressOf(0).reinterpret(),
            )
        }
    }
    return out
}
