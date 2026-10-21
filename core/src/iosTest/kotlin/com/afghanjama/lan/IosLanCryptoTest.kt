package com.afghanjama.lan

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * «کارگاهِ وصل» روی آیفون — بخشی که در کاتلین/Native است، روی شبیه‌ساز.
 *
 * AES-GCM و P-256 از CryptoKit‌اند و با `iosApp/CryptoInterop` (Swift روی مک)
 * سنجیده می‌شوند. اینجا HKDF روی CCHmac، SHA-256، عددِ تصادفی، تبدیلِ NSData
 * و JSONِ دری — همان بردارهای RFC 5869 که JVM می‌سنجد.
 */
class IosLanCryptoTest {

    private fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun hkdfMatchesRfc5869OnCommonCrypto() {
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            LanCrypto.hkdf(ByteArray(22) { 0x0b }, ByteArray(13) { it.toByte() }, ByteArray(10) { (0xf0 + it).toByte() }, 42).hex(),
        )
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            LanCrypto.hkdf(ByteArray(22) { 0x0b }, ByteArray(0), ByteArray(0), 42).hex(),
        )
    }

    @Test
    fun pbkdf2MatchesRfc7914OnCommonCrypto() {
        // همان بردارها و همان رمزِ کُندشده‌ای که `LanCryptoTest`ِ JVM می‌سنجد:
        // گوشیِ آیفون و کامپیوتر باید بایت‌به‌بایت یک کلید بسازند.
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            LanPrims.pbkdf2Sha256("passwd".encodeToByteArray(), "salt".encodeToByteArray(), 1, 64).hex(),
        )
        assertEquals(
            "d5077352754adb587c67f395bb34634a28731382367dc394580bfbd34e4c77b7",
            LanCrypto.codeSecret("482913572046".encodeToByteArray(), "PAIRING0001").hex(),
        )
    }

    @Test
    fun sha256AndHmacOfEmptyInput() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", LanPrims.sha256(ByteArray(0)).hex())
        // RFC 4231 مورد ۲
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            LanPrims.hmacSha256("Jefe".encodeToByteArray(), "what do ya want for nothing?".encodeToByteArray()).hex(),
        )
    }

    @Test
    fun randomIsRandomAndNsDataRoundTrips() {
        val a = LanPrims.random(32)
        val b = LanPrims.random(32)
        assertEquals(32, a.size)
        assertFalse(a.contentEquals(b))
        assertContentEquals(a, a.toNSData().nsBytes())
        assertContentEquals(ByteArray(0), ByteArray(0).toNSData().nsBytes())
    }

    @Test
    fun dariJsonAndPairingTextRoundTrip() {
        val o = mapOf("متن" to "آستین‌ها دوباره دوخته شد.", "n" to 7L)
        assertEquals(o, LanJson.parse(LanJson.write(o)))
        val link = PairingV2.Link("192.168.1.7", 8797, "hub-0001-aaaa", "PAIRING0001", LanPrims.random(16))
        assertEquals(link, PairingV2.parse(PairingV2.encode(link)))
    }

    @Test
    fun withoutTheSwiftBridgeGcmFailsLoudlyNotSilently() {
        // در آزمونِ کاتلین برنامهٔ Swift نیست؛ رمز نباید «بی رمز» کار کند.
        val saved = IosLanBridge.crypto
        IosLanBridge.crypto = null
        try {
            assertTrue(runCatching { LanPrims.gcmSeal(ByteArray(32), ByteArray(12), ByteArray(1), ByteArray(0)) }.isFailure)
        } finally {
            IosLanBridge.crypto = saved
        }
    }

    @Test
    fun theWorkshopAddressIsPrivateOrUnknown() {
        val ip = phoneLanIp()
        if (ip != null) assertTrue(HostAddress.isPrivate(ip), "phoneLanIp gave $ip")
        assertNotNull(Unit)
    }
}
