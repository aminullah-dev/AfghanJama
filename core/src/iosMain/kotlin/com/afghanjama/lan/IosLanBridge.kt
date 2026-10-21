package com.afghanjama.lan

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.concurrent.Volatile

/**
 * پلِ CryptoKit — **تنها چیزی که آیفون از Swift می‌خواهد.**
 *
 * AES-GCM و P-256 در CryptoKitِ اپل‌اند و CryptoKit فقط از Swift دیده
 * می‌شود؛ CommonCryptoِ عمومی GCM ندارد. پس برنامهٔ Swift یک
 * پیاده‌سازیِ این واسط را سرِ راه‌اندازی در [IosLanBridge.crypto]
 * می‌گذارد (`iosApp/KhayatYar/LanCryptoBridge.swift`) و کاتلین فقط همین را
 * صدا می‌زند. Keychain هم از همین راه است، چون Swift آن را بی دردسرِ
 * دیکشنری‌های CoreFoundation می‌خوانَد.
 *
 * هیچ منطقی آن‌سو نیست: هر تابع یک صدا به CryptoKit یا Keychain است.
 * قراردادِ بایت‌ها همان [LanPrims] است (کلید ۳۲، nonce ۱۲، برچسب ۱۶ بایت
 * چسبیده به انتها، کلیدِ عمومی `04 ‖ X ‖ Y`).
 */
interface IosLanCrypto {
    /** متنِ رمز ‖ برچسب، یا `nil` اگر نشد. */
    fun gcmSeal(key: NSData, nonce: NSData, plain: NSData, aad: NSData): NSData?

    /** متنِ باز، یا `nil` برای هر شکستِ سنجش. */
    fun gcmOpen(key: NSData, nonce: NSData, sealed: NSData, aad: NSData): NSData?

    /** ۳۲ بایت کلیدِ خصوصیِ خام ‖ ۶۵ بایت کلیدِ عمومیِ X9.63. */
    fun p256Generate(): NSData?

    /** رازِ مشترکِ ۳۲ بایتی، یا `nil` اگر کلیدِ آن‌سو نامعتبر است. */
    fun p256Agree(privateRaw: NSData, peerX963: NSData): NSData?

    fun secretPut(name: String, value: NSData): Boolean
    fun secretGet(name: String): NSData?
    fun secretDelete(name: String)
}

object IosLanBridge {
    /** برنامهٔ Swift سرِ راه‌اندازی می‌گذارد؛ تا آن وقت قراردادِ ۲ روی آیفون کار نمی‌کند. */
    @Volatile
    var crypto: IosLanCrypto? = null

    internal fun require(): IosLanCrypto =
        crypto ?: throw IllegalStateException("رمزنگاریِ آیفون آماده نیست (پلِ CryptoKit نصب نشده).")
}

/** انبارِ کلیدِ دستگاه روی آیفون: Keychain، از راهِ همان پل. */
class IosSecretStore : SecretStore {
    override fun put(name: String, value: ByteArray): Boolean =
        IosLanBridge.crypto?.secretPut(name, value.toNSData()) ?: false

    override fun get(name: String): ByteArray? = IosLanBridge.crypto?.secretGet(name)?.nsBytes()

    override fun remove(name: String) {
        IosLanBridge.crypto?.secretDelete(name)
    }
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData()
    else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.nsBytes(): ByteArray {
    val n = length.toInt()
    if (n == 0) return ByteArray(0)
    val out = ByteArray(n)
    out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
