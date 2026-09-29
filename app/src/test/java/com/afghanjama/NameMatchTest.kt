package com.afghanjama

import com.afghanjama.util.NameMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * پیدا کردنِ نامِ موجود هنگامِ تایپ — تا یک نفر دو طرفِ حساب نشود.
 *
 * نام کلیدِ حساب است؛ «حاجی نصیر» با ی و ک عربیِ کیبوردِ دیگر، یا با
 * نیم‌فاصله، از نظرِ دیتابیس آدمِ دیگری است و بدهیِ او دو تکه می‌شود.
 */
class NameMatchTest {

    private val names = listOf(
        "حاجی نصیر", "بوتیک آرزو", "مکتب نور", "شرکت ساختمانی کابل", "[T10] احمد نوری"
    )

    @Test
    fun `arabic ya and kaf are the same person`() {
        assertEquals("حاجی نصیر", NameMatch.exact("حاجي نصير", names))
        assertEquals("مکتب نور", NameMatch.exact("مكتب نور", names))
    }

    @Test
    fun `zero width joiner and extra spaces do not make a new person`() {
        assertEquals("حاجی نصیر", NameMatch.exact("حاجی‌نصیر", names))
        assertEquals("حاجی نصیر", NameMatch.exact("  حاجی   نصیر ", names))
    }

    @Test
    fun `suggestions start from the first letter, closest first`() {
        val s = NameMatch.suggest("ح", names)
        assertEquals("حاجی نصیر", s.first())
        // «نو» آغازِ «نور» و «نوری» است — هر دو، و نامِ کوتاه‌تر اول
        assertEquals(listOf("مکتب نور", "[T10] احمد نوری"), NameMatch.suggest("نو", names))
    }

    @Test
    fun `a word inside a tailor label is found`() {
        assertEquals(listOf("[T10] احمد نوری"), NameMatch.suggest("احمد", names))
    }

    @Test
    fun `nothing typed suggests nothing`() {
        assertTrue(NameMatch.suggest("", names).isEmpty())
        assertTrue(NameMatch.suggest("   ", names).isEmpty())
        assertNull(NameMatch.exact("", names))
    }

    @Test
    fun `alef with madda matches plain alef`() {
        assertEquals("بوتیک آرزو", NameMatch.exact("بوتیک ارزو", names))
    }

    @Test
    fun `duplicates in the source list are offered once`() {
        assertEquals(listOf("مکتب نور"), NameMatch.suggest("مکتب", names + "مكتب نور"))
    }
}
