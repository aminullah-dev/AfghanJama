package com.afghanjama

import com.afghanjama.util.PinHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * رمز باید با نمک و تکرار ذخیره شود — و **هیچ‌کس از اپ بیرون نماند**.
 *
 * دو قالبِ قدیمی در گوشی‌های کارگاه هست: رمزِ خامِ صفحهٔ ورود، و هشِ
 * `SHA-256`ِ بی‌نمکِ قفلِ اپ. اگر ارتقا آن‌ها را نشناسد، کارفرما پشتِ
 * درِ بستهٔ اپِ خودش می‌ماند — بدتر از خودِ اشکالِ امنیتی.
 */
class PinHashTest {

    @Test
    fun `new format verifies`() {
        val h = PinHash.hash("1379")
        assertTrue(h.startsWith("v2$"))
        assertTrue(PinHash.verify("1379", h))
        assertFalse(PinHash.verify("1380", h))
        assertFalse(PinHash.verify("", h))
    }

    /** نمک یعنی دو نفر با یک رمز، دو مقدارِ متفاوت ذخیره می‌کنند. */
    @Test
    fun `same pin hashes differently every time`() {
        val a = PinHash.hash("1234")
        val b = PinHash.hash("1234")
        assertNotEquals("نمک تصادفی نیست", a, b)
        assertTrue(PinHash.verify("1234", a))
        assertTrue(PinHash.verify("1234", b))
    }

    /** رمز نباید هیچ‌جای رشتهٔ ذخیره‌شده پیدا شود. */
    @Test
    fun `stored value does not contain the pin`() {
        val h = PinHash.hash("4321")
        assertFalse(h.contains("4321"))
    }

    /** قالبِ قدیمیِ صفحهٔ ورود: رمزِ خام. */
    @Test
    fun `legacy plaintext still opens the app`() {
        assertTrue(PinHash.verify("1379", "1379"))
        assertFalse(PinHash.verify("1111", "1379"))
        assertTrue("باید ارتقا بخواهد", PinHash.needsUpgrade("1379"))
    }

    /** قالبِ قدیمیِ قفلِ اپ: SHA-256 بی‌نمک. */
    @Test
    fun `legacy sha256 still opens the app`() {
        val legacy = MessageDigest.getInstance("SHA-256")
            .digest("1379".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(64, legacy.length)
        assertTrue(PinHash.verify("1379", legacy))
        assertFalse(PinHash.verify("9999", legacy))
        assertTrue("باید ارتقا بخواهد", PinHash.needsUpgrade(legacy))
    }

    @Test
    fun `new format does not ask for upgrade`() {
        assertFalse(PinHash.needsUpgrade(PinHash.hash("1379")))
    }

    /** ورودیِ خراب نباید بترکاند — فقط رد شود. */
    @Test
    fun `malformed stored values are refused, not crashing`() {
        for (bad in listOf("v2$", "v2\$x\$y\$z", "v2\$PBKDF2WithHmacSHA256\$abc\$zz\$zz", "")) {
            assertFalse("«$bad» نباید قبول شود", PinHash.verify("1379", bad))
        }
    }

    /** فاصلهٔ اضافه نباید کاربر را بیرون بگذارد. */
    @Test
    fun `surrounding spaces are ignored`() {
        val h = PinHash.hash(" 1379 ")
        assertTrue(PinHash.verify("1379", h))
        assertTrue(PinHash.verify("  1379  ", h))
    }
}
