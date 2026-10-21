package com.afghanjama

import com.afghanjama.lan.EcKeyPair
import com.afghanjama.lan.LanCrypto
import com.afghanjama.lan.LanPrims
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPrivateKeySpec

/**
 * پایه‌های رمزِ قراردادِ ۲ — با بردارهای آزمونِ منتشرشده، نه با «رفت و برگشت
 * کار کرد».
 *
 * رفت‌وبرگشتِ تنها چیزی را ثابت نمی‌کند: HKDFی که بایت‌ها را جابه‌جا کند هم
 * با خودش جور است. پس HKDF با هر سه بردارِ SHA-256ِ RFC 5869، AES-256-GCM با
 * بردارهای سندِ GCM (McGrew و Viega، مورد ۱۳ تا ۱۶)، و ECDH با بردارِ P-256ِ
 * RFC 5903 سنجیده می‌شود — همان بردارهایی که برنامهٔ آیفون با CryptoKit
 * می‌سنجد (`iosApp/CryptoInterop`).
 */
class LanCryptoTest {

    private fun hex(s: String): ByteArray =
        s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------ HKDF (RFC 5869)

    @Test
    fun `HKDF matches RFC 5869 test case 1`() {
        val okm = LanCrypto.hkdf(
            ikm = ByteArray(22) { 0x0b },
            salt = ByteArray(13) { it.toByte() },
            info = ByteArray(10) { (0xf0 + it).toByte() },
            length = 42,
        )
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.hex(),
        )
    }

    @Test
    fun `HKDF matches RFC 5869 test case 2 (long inputs, three blocks)`() {
        val okm = LanCrypto.hkdf(
            ikm = ByteArray(80) { it.toByte() },
            salt = ByteArray(80) { (0x60 + it).toByte() },
            info = ByteArray(80) { (0xb0 + it).toByte() },
            length = 82,
        )
        assertEquals(
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71" +
                "cc30c58179ec3e87c14c01d5c1f3434f1d87",
            okm.hex(),
        )
    }

    @Test
    fun `HKDF matches RFC 5869 test case 3 (empty salt and info)`() {
        val okm = LanCrypto.hkdf(ByteArray(22) { 0x0b }, ByteArray(0), ByteArray(0), 42)
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            okm.hex(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `HKDF refuses a zero length`() {
        LanCrypto.hkdf(ByteArray(1), ByteArray(0), ByteArray(0), 0)
    }

    // ------------------------------------------------------------ AES-256-GCM

    @Test
    fun `GCM matches the spec - case 13, empty text`() {
        val out = LanPrims.gcmSeal(ByteArray(32), ByteArray(12), ByteArray(0), ByteArray(0))
        assertEquals("530f8afbc74536b9a963b4f1c4cb738b", out.hex())
    }

    @Test
    fun `GCM matches the spec - case 14, one zero block`() {
        val out = LanPrims.gcmSeal(ByteArray(32), ByteArray(12), ByteArray(16), ByteArray(0))
        assertEquals("cea7403d4d606b6e074ec5d3baf39d18" + "d0d1c8a799996bf0265b98b5d48ab919", out.hex())
    }

    private val k16 = hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
    private val iv16 = hex("cafebabefacedbaddecaf888")
    private val p16 = hex(
        "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
            "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39"
    )
    private val a16 = hex("feedfacedeadbeeffeedfacedeadbeefabaddad2")
    private val c16 = "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
        "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662"
    private val t16 = "76fc6ece0f4e1768cddf8853bb2d551b"

    @Test
    fun `GCM matches the spec - case 16, with associated data`() {
        assertEquals(c16 + t16, LanPrims.gcmSeal(k16, iv16, p16, a16).hex())
        assertArrayEquals(p16, LanPrims.gcmOpen(k16, iv16, hex(c16 + t16), a16))
    }

    @Test
    fun `GCM refuses one flipped bit anywhere - text, tag, nonce, key or associated data`() {
        val sealed = hex(c16 + t16)
        for (i in sealed.indices) {
            val bad = sealed.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertNull("byte $i of the sealed text", LanPrims.gcmOpen(k16, iv16, bad, a16))
        }
        for (i in iv16.indices) {
            val bad = iv16.copyOf().also { it[i] = (it[i].toInt() xor 0x80).toByte() }
            assertNull("byte $i of the nonce", LanPrims.gcmOpen(k16, bad, sealed, a16))
        }
        val badKey = k16.copyOf().also { it[31] = (it[31].toInt() xor 1).toByte() }
        assertNull("the key", LanPrims.gcmOpen(badKey, iv16, sealed, a16))
        val badAad = a16.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertNull("the associated data", LanPrims.gcmOpen(k16, iv16, sealed, badAad))
        assertNull("missing associated data", LanPrims.gcmOpen(k16, iv16, sealed, ByteArray(0)))
        assertNull("a cut tag", LanPrims.gcmOpen(k16, iv16, sealed.copyOf(sealed.size - 1), a16))
        assertNull("shorter than a tag", LanPrims.gcmOpen(k16, iv16, ByteArray(15), a16))
    }

    @Test
    fun `GCM accepts only a 32-byte key and a 12-byte nonce`() {
        assertNull(LanPrims.gcmOpen(ByteArray(16), ByteArray(12), ByteArray(16), ByteArray(0)))
        assertNull(LanPrims.gcmOpen(ByteArray(32), ByteArray(16), ByteArray(16), ByteArray(0)))
        assertTrue(runCatching { LanPrims.gcmSeal(ByteArray(16), ByteArray(12), ByteArray(1), ByteArray(0)) }.isFailure)
    }

    @Test
    fun `seal draws a fresh nonce every time, and open needs the same associated text`() {
        val key = LanPrims.random(32)
        val a = LanCrypto.seal(key, "ورود".encodeToByteArray(), "ky2|x")
        val b = LanCrypto.seal(key, "ورود".encodeToByteArray(), "ky2|x")
        assertFalse("two messages shared a nonce", a.nonce.contentEquals(b.nonce))
        assertEquals("ورود", LanCrypto.open(key, a.nonce, a.box, "ky2|x")!!.decodeToString())
        assertNull(LanCrypto.open(key, a.nonce, a.box, "ky2|y"))
    }

    // ------------------------------------------------------------ ECDH P-256

    @Test
    fun `two fresh key pairs agree on the same 32-byte secret`() {
        val a = LanPrims.p256KeyPair()
        val b = LanPrims.p256KeyPair()
        assertEquals(65, a.publicX963.size)
        assertEquals(4.toByte(), a.publicX963[0])
        val ab = LanPrims.p256Agree(a, b.publicX963)
        val ba = LanPrims.p256Agree(b, a.publicX963)
        assertNotNull(ab)
        assertEquals(32, ab!!.size)
        assertArrayEquals(ab, ba)
        assertFalse(ab.contentEquals(LanPrims.p256Agree(a, LanPrims.p256KeyPair().publicX963)))
    }

    /** RFC 5903 §8.1 — ECDH روی P-256 با کلیدهای منتشرشده. */
    @Test
    fun `ECDH matches RFC 5903 section 8_1`() {
        val i = "C88F01F510D9AC3F70A292DAA2316DE544E9AAB8AFE84049C62A9C57862D1433"
        val gr = "04" +
            "D12DFB5289C8D4F81208B70270398C342296970A0BCCB74C736FC7554494BF63" +
            "56FBF3CA366CC23E8157854C13C58D6AAC23F046ADA30F8353E74F33039872AB"
        val gi = "04" +
            "DAD0B65394221CF9B051E1FECA5787D098DFE637FC90B9EF945D0C3772581180" +
            "5271A0461CDB8252D61F1C456FA3E59AB1F45B33ACCF5F58389E0577B8990BB3"
        val shared = "D6840F6B42F6EDAFD13116E0E12565202FEF8E9ECE7DCE03812464D04B9442DE"

        val params = (KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair().public as ECPublicKey).params
        val priv = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(i, 16), params))
        val mine = EcKeyPair(hex(gi), priv)
        assertEquals(shared.lowercase(), LanPrims.p256Agree(mine, hex(gr))!!.hex())
    }

    @Test
    fun `a public key off the curve, of the wrong shape, or the point at zero is refused`() {
        val me = LanPrims.p256KeyPair()
        val good = LanPrims.p256KeyPair().publicX963
        // یک بیت در y: نقطه از منحنی بیرون می‌افتد (حملهٔ منحنیِ نامعتبر).
        val offCurve = good.copyOf().also { it[64] = (it[64].toInt() xor 1).toByte() }
        assertNull("off the curve", LanPrims.p256Agree(me, offCurve))
        assertNull("compressed prefix", LanPrims.p256Agree(me, good.copyOf().also { it[0] = 2 }))
        assertNull("too short", LanPrims.p256Agree(me, good.copyOf(64)))
        assertNull("too long", LanPrims.p256Agree(me, good + byteArrayOf(0)))
        assertNull("all zero", LanPrims.p256Agree(me, ByteArray(65).also { it[0] = 4 }))
        // x = p: بیرون از میدان، حتی اگر y² جور شود.
        val p = "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF"
        assertNull("x = p", LanPrims.p256Agree(me, hex("04$p" + "00".repeat(32))))
    }

    @Test
    fun `a key pair never prints its private half`() {
        val kp = LanPrims.p256KeyPair()
        assertFalse(kp.toString().contains("ECPrivateKey"))
        assertTrue(kp.toString().contains("…"))
    }

    // ------------------------------------------------------------ کلیدها

    @Test
    fun `the two direction keys differ from each other and from the device key`() {
        val device = LanPrims.random(32)
        val d = LanCrypto.directional(device)
        assertEquals(32, d.request.size)
        assertFalse(d.request.contentEquals(d.response))
        assertFalse(d.request.contentEquals(device))
        // یک کلیدِ دستگاه همیشه همان دو کلید را می‌دهد (گوشی و کامپیوتر جدا می‌سازند).
        assertArrayEquals(d.request, LanCrypto.directional(device).request)
    }

    @Test
    fun `the pairing key needs both the ECDH secret and the QR secret, and the whole transcript`() {
        val shared = LanPrims.random(32)
        val secret = LanPrims.random(16)
        val k = LanCrypto.pairKey(shared, secret, "t")
        assertArrayEquals(k, LanCrypto.pairKey(shared, secret, "t"))
        assertFalse(k.contentEquals(LanCrypto.pairKey(LanPrims.random(32), secret, "t")))
        assertFalse(k.contentEquals(LanCrypto.pairKey(shared, LanPrims.random(16), "t")))
        assertFalse(k.contentEquals(LanCrypto.pairKey(shared, secret, "t2")))
        // رازِ ۱۲ رقمی و رازِ ۱۶ بایتی که پیشوندِ هم باشند به یک کلید نمی‌رسند (طول در ورودی است).
        assertFalse(LanCrypto.pairKey(shared, byteArrayOf(1, 2), "t").contentEquals(LanCrypto.pairKey(shared, byteArrayOf(1, 2, 0), "t")))
    }

    // ------------------------------------------------------------ PBKDF2 (RFC 7914 §11)

    @Test
    fun `PBKDF2-HMAC-SHA256 matches RFC 7914 - one round and two blocks`() {
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            LanPrims.pbkdf2Sha256("passwd".encodeToByteArray(), "salt".encodeToByteArray(), 1, 64).hex(),
        )
    }

    @Test
    fun `PBKDF2-HMAC-SHA256 matches RFC 7914 - eighty thousand rounds`() {
        assertEquals(
            "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56" +
                "a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d",
            LanPrims.pbkdf2Sha256("Password".encodeToByteArray(), "NaCl".encodeToByteArray(), 80_000, 64).hex(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `PBKDF2 refuses a password CommonCrypto would cut at a zero byte`() {
        LanPrims.pbkdf2Sha256(byteArrayOf(0x31, 0, 0x32), "salt".encodeToByteArray(), 1, 32)
    }

    @Test
    fun `the manual code is stretched with 2^17 rounds, salted by its window`() {
        val code = "482913572046".encodeToByteArray()
        // همان عدد از hashlib.pbkdf2_hmac('sha256', code, b'ky2/manual-code|PAIRING0001', 131072, 32)
        assertEquals(
            "d5077352754adb587c67f395bb34634a28731382367dc394580bfbd34e4c77b7",
            LanCrypto.codeSecret(code, "PAIRING0001").hex(),
        )
        assertEquals(131_072, LanCrypto.MANUAL_CODE_ROUNDS)
        assertFalse(LanCrypto.codeSecret(code, "PAIRING0001").contentEquals(LanCrypto.codeSecret(code, "PAIRING0002")))
    }

    // ------------------------------------------------------------ ابزار

    @Test
    fun `constant-time compare is still a compare`() {
        assertTrue(LanCrypto.sameBytes(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(LanCrypto.sameBytes(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(LanCrypto.sameBytes(byteArrayOf(1, 2, 3), byteArrayOf(1, 2)))
        assertTrue(LanCrypto.sameBytes(ByteArray(0), ByteArray(0)))
    }

    @Test
    fun `base32 round-trips every length and refuses letters outside the alphabet`() {
        for (n in 0..40) {
            val b = LanPrims.random(n)
            assertArrayEquals("length $n", b, LanCrypto.unb32(LanCrypto.b32(b)))
        }
        assertEquals("MZXW6YTBOI", LanCrypto.b32("foobar".encodeToByteArray()))
        assertNull(LanCrypto.unb32("MZXW6YTBO1"))
        assertNull("trailing bits that are not zero", LanCrypto.unb32("MZXW6YTBOJ"))
        assertArrayEquals("lower case is the same", "foobar".encodeToByteArray(), LanCrypto.unb32("mzxw6ytboi"))
    }

    @Test
    fun `base64 from the network never throws`() {
        assertNull(LanCrypto.unb64("@@@"))
        assertNull(LanCrypto.unb64(null))
        assertArrayEquals(byteArrayOf(1, 2, 3), LanCrypto.unb64(LanCrypto.b64(byteArrayOf(1, 2, 3))))
    }
}
