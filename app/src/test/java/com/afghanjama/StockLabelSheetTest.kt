package com.afghanjama

import com.afghanjama.data.StockLabel
import com.afghanjama.pdf.DrawOp
import com.afghanjama.pdf.StockLabelInfo
import com.afghanjama.pdf.TextMeasurer
import com.afghanjama.pdf.Weight
import com.afghanjama.pdf.stockLabelSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * برگهٔ چاپیِ برچسبِ قفسه: کد و نام رویش هست، QR کشیده می‌شود، و **بها
 * نیست** — مشتری نباید سودِ کارگاه را روی قفسه بخوانَد.
 */
class StockLabelSheetTest {

    /** مترِ ساده: هر نویسه ۵ نقطه به ازای هر ۱۰ نقطه قلم. */
    private val m = object : TextMeasurer {
        override fun width(text: String, size: Float, weight: Weight) = text.length * size * 0.5f
        override fun wrap(text: String, size: Float, maxWidth: Float, weight: Weight): List<String> =
            if (text.isEmpty()) emptyList() else text.chunked((maxWidth / (size * 0.5f)).toInt().coerceAtLeast(1))
        override fun lineHeight(size: Float, weight: Weight) = size * 1.4f
    }

    private val code = StockLabel.encode(42)

    private fun texts(doc: com.afghanjama.pdf.SheetDoc) =
        doc.pages.flatMap { p -> p.ops.filterIsInstance<DrawOp.Text>().map { it.text } }

    @Test
    fun `one page with the code, the name and the size`() {
        val doc = stockLabelSheet(StockLabelInfo("پیراهن مردانه", "L", code), "کارگاه نمونه", m)
        assertEquals(1, doc.pages.size)
        val t = texts(doc)
        assertTrue(t.toString(), code in t)
        assertTrue(t.toString(), t.any { it.contains("پیراهن") })
        assertTrue(t.toString(), "سایز L" in t)
        assertTrue(t.toString(), t.any { it.contains("کارگاه نمونه") })
    }

    @Test
    fun `the QR is drawn`() {
        val doc = stockLabelSheet(StockLabelInfo("شال", "", code), "کارگاه", m)
        // QR از مستطیل‌های پرشده کشیده می‌شود؛ دست‌کم ده‌ها خانه.
        assertTrue(doc.pages.single().ops.count { it is DrawOp.Rect } > 30)
    }

    @Test
    fun `no size line when the item has no size`() {
        val doc = stockLabelSheet(StockLabelInfo("شال", "", code), "کارگاه", m)
        assertTrue(texts(doc).none { it.startsWith("سایز") })
    }

    @Test
    fun `no price on the label`() {
        val t = texts(stockLabelSheet(StockLabelInfo("پیراهن", "M", code), "کارگاه", m))
        assertTrue(t.toString(), t.none { it.contains("؋") || it.contains("افغانی") || it.contains("بها:") })
    }
}
