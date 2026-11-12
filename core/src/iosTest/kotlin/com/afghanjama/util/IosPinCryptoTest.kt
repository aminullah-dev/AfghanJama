package com.afghanjama.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * سهمِ iOSِ رمزِ عددی (`PinCrypto.ios.kt`) روی CommonCrypto.
 *
 * بردارها با PBKDF2ِ پایتون محاسبه شده‌اند، نه از خروجیِ خودِ این کد؛
 * چون رشتهٔ ذخیره‌شدهٔ گوشیِ اندرویدی باید اینجا هم باز شود. نمکِ همه
 * بایت‌های ۰ تا ۱۵ است.
 */
class IosPinCryptoTest {

    private val salt = ByteArray(16) { it.toByte() }

    private fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun pbkdf2Sha256MatchesReferenceVector() {
        assertEquals(
            "2240ea2a22754a3c4610356ef8e4d7acf25af2ac3d2d11866de97a8b7812c52c",
            pbkdf2("1234", salt, 1000, 256, "PBKDF2WithHmacSHA256")!!.hex(),
        )
    }

    @Test
    fun pbkdf2Sha1MatchesReferenceVector() {
        assertEquals(
            "344f41e9c76e040a42494c5f76b74ec04dc011c059a9aeca784aedf7bbeaed3d",
            pbkdf2("1234", salt, 1000, 256, "PBKDF2WithHmacSHA1")!!.hex(),
        )
    }

    /** طولِ رمز باید بایتِ UTF-8 باشد نه تعدادِ نویسه. */
    @Test
    fun pbkdf2UsesUtf8ByteLengthForNonLatinPin() {
        assertEquals(
            "fe973bccc4d580f91851921f5ce682fc2ad6de8d1ffd6a4557127bac58a6cf67",
            pbkdf2("۱۲۳۴", salt, 1000, 256, "PBKDF2WithHmacSHA256")!!.hex(),
        )
    }

    @Test
    fun unknownAlgorithmReturnsNull() {
        assertNull(pbkdf2("1234", salt, 1000, 256, "PBKDF2WithHmacMD5"))
    }

    @Test
    fun sha256MatchesReferenceVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256(ByteArray(0)).hex())
        assertEquals(
            "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
            sha256("1234".encodeToByteArray()).hex(),
        )
    }

    @Test
    fun secureRandomBytesHasRequestedLengthAndVaries() {
        val a = secureRandomBytes(16)
        val b = secureRandomBytes(16)
        assertEquals(16, a.size)
        assertEquals(0, secureRandomBytes(0).size)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun pinHashRoundTripsAndRejectsWrongPin() {
        val stored = PinHash.hash("4321")
        assertTrue(stored.startsWith("v2\$PBKDF2WithHmacSHA256\$"))
        assertTrue(PinHash.verify("4321", stored))
        assertFalse(PinHash.verify("4322", stored))
        assertFalse(PinHash.needsUpgrade(stored))
    }

    @Test
    fun pinHashUsesFreshSaltEachTime() {
        assertNotEquals(PinHash.hash("4321"), PinHash.hash("4321"))
    }

    /** رشتهٔ ساخته‌شده با نمکِ ثابت (از پایتون) باید باز شود. */
    @Test
    fun pinHashVerifiesStringBuiltElsewhere() {
        val stored = "v2\$PBKDF2WithHmacSHA256\$1000\$${salt.hex()}\$" +
            "2240ea2a22754a3c4610356ef8e4d7acf25af2ac3d2d11866de97a8b7812c52c"
        assertTrue(PinHash.verify("1234", stored))
        assertFalse(PinHash.verify("1235", stored))
    }

    @Test
    fun legacyFormatsStillVerifyAndAskForUpgrade() {
        val legacyHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
        assertTrue(PinHash.verify("1234", legacyHash))
        assertTrue(PinHash.needsUpgrade(legacyHash))
        assertTrue(PinHash.verify("1234", "1234"))
        assertFalse(PinHash.verify("1234", ""))
    }
}
