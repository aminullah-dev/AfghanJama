package com.afghanjama

import com.afghanjama.licence.Licence
import com.afghanjama.licence.LicenceFacts
import com.afghanjama.licence.LicenceGate
import com.afghanjama.licence.LicencePolicy.DAY_MILLIS
import com.afghanjama.licence.LicenceText
import com.afghanjama.pdf.DrawOp
import com.afghanjama.pdf.Paper
import com.afghanjama.pdf.Sheet
import com.afghanjama.pdf.SheetDoc
import com.afghanjama.pdf.Watermark
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نشانِ «نسخهٔ آزمایشی — بدون لایسنس» روی کاغذ.
 *
 * به خودِ برگه افزوده می‌شود، پس PDFِ گوشی و ویندوز و تصویرِ واتساپ
 * (که از همان PDF ساخته می‌شود) همه یک نشان دارند.
 */
class LicenceWatermarkTest {

    private val t0 = 1_791_000_000_000L
    private val page = Sheet(
        Paper.A4,
        listOf(DrawOp.Text("فاکتور", 500f, 40f, 12f, 0xFF000000.toInt()))
    )

    @After
    fun reset() = LicenceGate.resetForTest()

    private fun texts(s: Sheet) = s.ops.filterIsInstance<DrawOp.Text>().map { it.text }

    @Test
    fun `the exact protocol text, under the content and again above the footer`() {
        val w = Watermark.apply(page, on = true)
        assertEquals("نسخهٔ آزمایشی — بدون لایسنس", LicenceText.WATERMARK)
        assertEquals(LicenceText.WATERMARK, texts(w).first())
        assertEquals(LicenceText.WATERMARK, texts(w).last())
        assertTrue("محتوای اصلی دست نخورد", w.ops.containsAll(page.ops))
    }

    @Test
    fun `every page and every paper size gets it`() {
        val doc = SheetDoc(listOf(page, page.copy(paper = Paper.A5), page.copy(paper = Paper.ROLL80)))
        Watermark.apply(doc, on = true).pages.forEach {
            assertEquals(it.paper.label, 2, texts(it).count { t -> t == LicenceText.WATERMARK })
        }
    }

    @Test
    fun `no watermark when licensed`() {
        LicenceGate.install(
            LicenceFacts(
                licence = Licence("KY-1", "خیاطی", "*", LocalDate(2026, 10, 8), null, "standard", listOf("*")),
                machineCode = "X", firstRunMillis = t0, lastSeenMillis = t0,
            )
        )
        LicenceGate.clock = { t0 }
        assertSame(page, Watermark.apply(page))
    }

    @Test
    fun `trial and read-only print with the watermark, and printing still works`() {
        LicenceGate.install(LicenceFacts(null, "X", t0, t0))
        LicenceGate.clock = { t0 }
        assertTrue(texts(Watermark.apply(page)).contains(LicenceText.WATERMARK))
        LicenceGate.clock = { t0 + 30 * DAY_MILLIS }
        // فقط‌خواندنی: ساختنِ برگه هیچ درِ لایسنسی ندارد و رد نمی‌شود.
        assertTrue(texts(Watermark.apply(page)).contains(LicenceText.WATERMARK))
    }
}
