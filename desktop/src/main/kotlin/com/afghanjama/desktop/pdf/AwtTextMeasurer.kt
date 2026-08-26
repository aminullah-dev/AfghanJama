package com.afghanjama.desktop.pdf

import com.afghanjama.pdf.TextMeasurer
import com.afghanjama.pdf.Weight
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.text.AttributedString

/**
 * مترِ ویندوز — با همان موتوری که بعداً متن را می‌کشد.
 *
 * مهم است که اندازه‌گیری و کشیدن از یک منبع بیایند: اگر متر از یک جا
 * بیاید و رسم از جای دیگر، متن دقیقاً به اندازهٔ اختلافشان از ستون
 * بیرون می‌زند.
 */
class AwtTextMeasurer(private val fonts: SheetFonts) : TextMeasurer {

    override fun width(text: String, size: Float, weight: Weight): Float {
        if (text.isEmpty()) return 0f
        val f = fonts.of(weight).deriveFont(size)
        // همان چیدمانی که `SheetPdf` می‌کشد — پس پهنا و رسم
        // نمی‌توانند از هم بیفتند (نشانهٔ ؋ جایگزین دارد).
        return if (text.isEmpty()) 0f else RtlText.layout(text, f).advance
    }

    override fun wrap(text: String, size: Float, maxWidth: Float, weight: Weight): List<String> {
        if (text.isBlank()) return emptyList()
        if (maxWidth <= 0f) return listOf(text)
        val f = fonts.of(weight).deriveFont(size)

        // جهتِ راست‌به‌چپ و قلمِ جایگزین، از همان جایی که رسم‌کننده
        // می‌گیرد.
        val measurer = LineBreakMeasurer(RtlText.attributed(text, f).iterator, FRC)
        val out = mutableListOf<String>()
        var start = 0
        while (measurer.position < text.length) {
            val end = measurer.nextOffset(maxWidth)
            out += text.substring(start, end)
            measurer.position = end
            start = end
        }
        return out
    }

    override fun lineHeight(size: Float, weight: Weight): Float {
        val f = fonts.of(weight).deriveFont(size)
        val m = f.getLineMetrics("آ", FRC)
        return m.ascent + m.descent + m.leading
    }

    private companion object {
        val FRC = FontRenderContext(null, true, true)
    }
}
