package com.afghanjama

import com.afghanjama.data.CodeGen
import com.afghanjama.util.randomUuid
import com.afghanjama.util.uuidOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * کدهای سفارش و خرید و شناسهٔ یکتا — قالبشان روی فاکتور چاپی و جستجو
 * می‌نشیند، پس نباید بی‌صدا عوض شود.
 */
class CodeGenTest {

    @Test fun orderCodeIsPaddedToSixDigits() {
        val code = CodeGen.makeOrderCode(42)
        assertTrue(code, Regex("""AJ-\d{4}-000042""").matches(code))
    }

    @Test fun orderCodeKeepsLargeNumbersWhole() {
        val code = CodeGen.makeOrderCode(1_234_567)
        assertTrue(code, code.endsWith("-1234567"))
    }

    @Test fun purchaseCodeShape() {
        val code = CodeGen.makePurchaseCode()
        assertTrue(code, Regex("""KH-\d{4}-\d{6}""").matches(code))
    }

    @Test fun shortCodeAvoidsConfusingCharacters() {
        repeat(200) {
            val code = CodeGen.makeShortCode()
            assertTrue(code, Regex("""[A-HJ-NP-Z2-9]{3}-[A-HJ-NP-Z2-9]{3}""").matches(code))
        }
    }

    @Test fun uuidRoundTripsInLowercaseCanonicalForm() {
        val id = randomUuid()
        val text = id.toString()
        assertEquals(36, text.length)
        assertEquals(text.lowercase(), text)
        assertEquals(id, uuidOf(text))
    }

    @Test fun uuidsDiffer() {
        assertNotEquals(randomUuid(), randomUuid())
    }

    @Test(expected = Exception::class)
    fun uuidOfRejectsGarbage() {
        uuidOf("not-a-uuid")
    }
}
