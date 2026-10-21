package com.afghanjama.lan

/**
 * پایه‌های رمزنگاریِ قراردادِ ۲ — **همه از خودِ سکو، هیچ‌کدام دست‌نوشته.**
 *
 * - کامپیوتر و اندروید: `javax.crypto` و `java.security` (همان که بانک‌ها و
 *   TLSِ خودِ جاوا به کار می‌برند).
 * - آیفون: CryptoKitِ اپل برای AES-GCM و P-256 (از راهِ پلی که برنامهٔ
 *   Swift نصب می‌کند، [com.afghanjama.lan.IosLanBridge])، و CommonCrypto و
 *   `SecRandomCopyBytes` برای HMAC، SHA-256 و عددِ تصادفی.
 *
 * تنها چیزی که بالای این‌ها در کاتلین نوشته شده HKDF است ([LanCrypto.hkdf])
 * — ده خط HMAC به ترتیبی که RFC 5869 می‌گوید، با هر سه بردارِ آزمونِ همان
 * سند سنجیده می‌شود. JDK ۱۷ و اندروید ۷ هیچ‌کدام HKDFِ آماده ندارند.
 *
 * **قرارداد:** کلیدِ AES همیشه ۳۲ بایت (AES-256)، nonce دوازده بایت
 * (۹۶ بیت، تصادفی برای هر پیام)، برچسبِ GCM شانزده بایت و چسبیده به انتهای
 * متنِ رمز. هر شکستِ سنجش `null` است، نه استثنا با جزئیات: پیامِ خطایی که
 * بگوید «کجا» نادرست بود، به حدس‌زننده کمک می‌کند.
 */
object LanPrims {

    /** [n] بایتِ تصادفیِ رمزنگارانه. */
    fun random(n: Int): ByteArray = primRandom(n)

    fun sha256(data: ByteArray): ByteArray = primSha256(data)

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = primHmacSha256(key, data)

    /** AES-256-GCM؛ خروجی: متنِ رمز ‖ برچسبِ ۱۶ بایتی. */
    fun gcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray =
        primGcmSeal(key, nonce, plain, aad)

    /** وارونهٔ [gcmSeal]؛ `null` اگر کلید، nonce، متن یا [aad] یک بیت فرق کند. */
    fun gcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray? =
        primGcmOpen(key, nonce, sealed, aad)

    /** یک جفت‌کلیدِ گذرای P-256 برای ECDH. */
    fun p256KeyPair(): EcKeyPair = primP256KeyPair()

    /**
     * رازِ مشترکِ ECDH (مختصِ x، ۳۲ بایت) با کلیدِ عمومیِ [peerX963]
     * (`04 ‖ X ‖ Y`، ۶۵ بایت). `null` اگر آن کلید روی منحنی نیست یا شکلش
     * درست نیست — نقطهٔ بیرونِ منحنی راهِ شناختهٔ دزدیدنِ کلیدِ خصوصی است.
     */
    fun p256Agree(own: EcKeyPair, peerX963: ByteArray): ByteArray? = primP256Agree(own, peerX963)
}

internal expect fun primRandom(n: Int): ByteArray
internal expect fun primSha256(data: ByteArray): ByteArray
internal expect fun primHmacSha256(key: ByteArray, data: ByteArray): ByteArray
internal expect fun primGcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray
internal expect fun primGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray?
internal expect fun primP256KeyPair(): EcKeyPair
internal expect fun primP256Agree(own: EcKeyPair, peerX963: ByteArray): ByteArray?

/**
 * جفت‌کلیدِ گذرای P-256. بخشِ خصوصی دستِ سکوست و بیرون داده نمی‌شود؛
 * [toString] هم چیزی از آن نمی‌گوید تا در هیچ گزارشی ننشیند.
 */
class EcKeyPair(
    /** `04 ‖ X ‖ Y`، ۶۵ بایت. */
    val publicX963: ByteArray,
    internal val secret: Any,
) {
    override fun toString(): String = "EcKeyPair(public=${publicX963.size}B, private=…)"
}
