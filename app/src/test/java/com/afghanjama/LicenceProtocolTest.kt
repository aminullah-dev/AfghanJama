package com.afghanjama

import com.afghanjama.licence.LicenceKeys
import com.afghanjama.licence.Lnm1
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * پروتکلِ LNM1 در برابرِ نمونه‌های رسمیِ `test-vectors.json`.
 *
 * این همان نمونه‌هایی است که ابزارِ صدور (`linumic_license.py`) و
 * MediFlow هم با آن سنجیده می‌شوند. اگر یکی از این‌ها بشکند، کلیدی که
 * لینومیک صادر می‌کند در خیاط‌یار پذیرفته نمی‌شود — یا بدتر، کلیدِ
 * دست‌کاری‌شده پذیرفته می‌شود.
 *
 * کلیدِ عمومیِ **آزمایشی** تزریق می‌شود؛ کلیدِ اپ فقط در آزمونِ آخر دیده
 * می‌شود، و آنجا باید همهٔ کلیدهای آزمایشی را رد کند.
 */
class LicenceProtocolTest {

    private val testKey = Lnm1.pemToDer(LicenceVectors.TEST_PUBLIC_PEM)

    private fun verify(product: String, case: String, asProduct: String = product) =
        Lnm1.verify(LicenceVectors.key(product, case), testKey, asProduct)

    private fun ok(r: Lnm1.Result) = (r as? Lnm1.Result.Ok)?.licence
        ?: fail("پذیرفته نشد: $r") as Nothing

    private fun err(r: Lnm1.Result) = (r as? Lnm1.Result.Err)?.problem
        ?: fail("نباید پذیرفته می‌شد: $r") as Nothing

    // ---------------- کدِ دستگاه ----------------

    @Test
    fun `every machine-code vector matches`() {
        LicenceVectors.MACHINE_CODES.forEach { (product, platformId, code) ->
            assertEquals("$product|$platformId", code, Lnm1.machineCode(platformId, product))
        }
    }

    @Test
    fun `machine code is grouped 4-4-4-4 in the Crockford alphabet`() {
        val code = Lnm1.machineCode("any-device")
        assertTrue(code, Regex("[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}").matches(code))
    }

    @Test
    fun `typed machine codes are normalised like the issuing tool`() {
        assertEquals("BDZ2-PWKS-XGAW-3YGB", Lnm1.normalizeMachineCode("bdz2 pwks xgaw 3ygb"))
        assertEquals("1000-0000-0000-0000", Lnm1.normalizeMachineCode("LOOO0000OOOO0000"))
        assertNull(Lnm1.normalizeMachineCode("BDZ2-PWKS-XGAW"))
        assertNull(Lnm1.normalizeMachineCode("UUUU-PWKS-XGAW-3YGB"))
    }

    // ---------------- کلیدهای خیاط‌یار ----------------

    @Test
    fun `valid perpetual key`() {
        val l = ok(verify("khayatyar", "valid_perpetual"))
        assertEquals("KY-TEST-0001", l.id)
        assertEquals("BDZ2-PWKS-XGAW-3YGB", l.machine)
        assertNull(l.expires)
        assertEquals(LocalDate(2026, 10, 8), l.issued)
        assertEquals("standard", l.edition)
        assertEquals(listOf("*"), l.features)
        assertTrue(l.customer, l.customer.contains("Test"))
        assertTrue("نامِ دری درست خوانده شود", l.customer.startsWith("کلینیک"))
    }

    @Test
    fun `valid key with an expiry date`() {
        val l = ok(verify("khayatyar", "valid_expires_2026_12_31"))
        assertEquals(LocalDate(2026, 12, 31), l.expires)
    }

    @Test
    fun `valid key for any machine`() {
        val l = ok(verify("khayatyar", "valid_any_machine"))
        assertEquals("*", l.machine)
        assertTrue(l.anyMachine)
    }

    @Test
    fun `a key for another product is rejected`() {
        assertEquals(Lnm1.Problem.WRONG_PRODUCT, err(verify("khayatyar", "wrong_product")))
    }

    @Test
    fun `a tampered payload is rejected by the signature`() {
        assertEquals(Lnm1.Problem.BAD_SIGNATURE, err(verify("khayatyar", "tampered_payload")))
    }

    @Test
    fun `a key of another protocol version is rejected`() {
        assertEquals(Lnm1.Problem.WRONG_VERSION, err(verify("khayatyar", "wrong_version")))
    }

    @Test
    fun `spaces and newlines anywhere in a pasted key are ignored`() {
        val key = LicenceVectors.key("khayatyar", "with_whitespace_valid")
        assertTrue("نمونه واقعاً فاصله دارد", key.any { it.isWhitespace() })
        val l = ok(verify("khayatyar", "with_whitespace_valid"))
        assertEquals("BDZ2-PWKS-XGAW-3YGB", l.machine)
    }

    // ---------------- همهٔ نمونه‌های MediFlow ----------------

    /**
     * کلیدهای MediFlow در خیاط‌یار: هیچ‌کدام نباید بنشیند، جز همان
     * «wrong_product» که بارش `p = khayatyar` است و امضایش درست.
     */
    @Test
    fun `MediFlow keys are refused by KhayatYar`() {
        listOf("valid_perpetual", "valid_expires_2026_12_31", "valid_any_machine").forEach {
            assertEquals(it, Lnm1.Problem.WRONG_PRODUCT, err(verify("mediflow", it, asProduct = "khayatyar")))
        }
        assertEquals(Lnm1.Problem.BAD_SIGNATURE, err(verify("mediflow", "tampered_payload", "khayatyar")))
        assertEquals(Lnm1.Problem.WRONG_VERSION, err(verify("mediflow", "wrong_version", "khayatyar")))
    }

    /** همان سنجش با محصولِ MediFlow — تا ثابت شود سنجشگر عمومی است نه سفت‌شده. */
    @Test
    fun `MediFlow vectors behave as specified for their own product`() {
        assertEquals("4HDG-0NK5-EAKX-GQVR", ok(verify("mediflow", "valid_perpetual")).machine)
        assertEquals(LocalDate(2026, 12, 31), ok(verify("mediflow", "valid_expires_2026_12_31")).expires)
        assertTrue(ok(verify("mediflow", "valid_any_machine")).anyMachine)
        assertEquals(Lnm1.Problem.WRONG_PRODUCT, err(verify("mediflow", "wrong_product")))
        assertEquals(Lnm1.Problem.BAD_SIGNATURE, err(verify("mediflow", "tampered_payload")))
        assertEquals(Lnm1.Problem.WRONG_VERSION, err(verify("mediflow", "wrong_version")))
        assertEquals("4HDG-0NK5-EAKX-GQVR", ok(verify("mediflow", "with_whitespace_valid")).machine)
    }

    // ---------------- متن‌های خراب ----------------

    @Test
    fun `text that is not a key`() {
        listOf("", "hello", "LNM2.a.b", "LNM1.only-two", "LNM1.a.b.c").forEach {
            assertEquals(it, Lnm1.Problem.NOT_A_KEY, err(Lnm1.verify(it, testKey)))
        }
    }

    @Test
    fun `broken base64 is malformed, not a crash`() {
        assertEquals(Lnm1.Problem.MALFORMED, err(Lnm1.verify("LNM1.!!!!.@@@@", testKey)))
        assertEquals(Lnm1.Problem.MALFORMED, err(Lnm1.verify("LNM1..", testKey)))
    }

    /** کلیدِ عمومیِ اپ کلیدهای آزمایشی را نمی‌پذیرد — کلیدِ آزمون به کدِ ارسالی نرسیده. */
    @Test
    fun `the shipped public key rejects every test-signed key`() {
        LicenceVectors.KEYS.filter { it.product == "khayatyar" }.forEach {
            val r = Lnm1.verify(it.key, LicenceKeys.production)
            assertEquals(it.case, Lnm1.Problem.BAD_SIGNATURE, err(r))
        }
    }

    @Test
    fun `the shipped public key is the KhayatYar production key`() {
        val der = LicenceKeys.production
        assertEquals("SPKI یک کلیدِ P-256", 91, der.size)
        assertTrue(LicenceKeys.KHAYATYAR_PUBLIC_PEM.contains("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEmO5oWyI7lvDaIgR1"))
    }
}
