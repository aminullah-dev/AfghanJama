package com.afghanjama

import com.afghanjama.data.EmployeeCard
import com.afghanjama.data.StockLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * کدِ برچسبِ قفسه: رفت‌وبرگشت، رقمِ کنترلی، و مهم‌تر از همه اینکه کدِ کالا
 * و کارتِ کارمند هرگز هم‌دیگر را اشتباه نخوانند.
 */
class StockLabelTest {

    @Test
    fun `encode then parse returns the same id`() {
        for (id in listOf(1L, 7L, 42L, 142L, 999L, 1_000_742L, Long.MAX_VALUE)) {
            assertEquals("id=$id", id, StockLabel.parse(StockLabel.encode(id)))
        }
    }

    @Test
    fun `code starts with the ks prefix and the stock marker`() {
        val code = StockLabel.encode(742)
        assertTrue(code.startsWith("ks9"))
    }

    @Test
    fun `a wrong check digit is rejected`() {
        val good = StockLabel.encode(742)              // ks9742xx
        val broken = good.dropLast(2) + "00"           // رقمِ کنترلیِ خراب
        assertNull(StockLabel.parse(broken))
    }

    @Test
    fun `a single bad digit is caught by the check digits`() {
        val good = StockLabel.encode(5)
        // یک رقمِ بدنه را عوض کن؛ mod-97 باید بگیردش
        val i = good.indexOf('9')
        val bad = good.substring(0, i) + '8' + good.substring(i + 1)
        assertNull(StockLabel.parse(bad))
    }

    @Test
    fun `persian and arabic digits are accepted`() {
        val code = StockLabel.encode(742)
        val persian = code.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
        assertEquals(742L, StockLabel.parse(persian))
    }

    @Test
    fun `garbage is not a stock code`() {
        assertNull(StockLabel.parse(""))
        assertNull(StockLabel.parse("hello"))
        assertNull(StockLabel.parse("ks"))
        assertNull(StockLabel.parse("1234567"))        // بی پیشوندِ حرفی
    }

    // ---- جداییِ کالا از کارمند: قلبِ ایمنی ----

    @Test
    fun `an employee card is never read as a stock code`() {
        for (kind in EmployeeCard.Kind.entries) {
            for (id in listOf(1L, 42L, 142L, 999L)) {
                val card = EmployeeCard.encode(kind, id)
                assertNull("card $card leaked as stock", StockLabel.parse(card))
            }
        }
    }

    @Test
    fun `a stock code is never read as an employee card`() {
        // به‌ویژه شناسه‌هایی که با ۱/۲/۳ شروع می‌شوند — همان‌ها که می‌توانستند
        // با رقمِ نوعِ کارمند اشتباه شوند.
        for (id in listOf(1L, 2L, 3L, 12L, 142L, 231L, 300L)) {
            val code = StockLabel.encode(id)
            assertNull("stock $code leaked as employee", EmployeeCard.parse(code))
        }
    }
}
