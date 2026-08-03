package com.afghanjama.pdf

import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.afghanjama.pdf.TextMeasurer
import com.afghanjama.pdf.Weight

/**
 * مترِ اندروید — با همان `StaticLayout`ی که امروز هم متن را می‌کشد.
 *
 * عمداً همان موتور است نه یک محاسبهٔ جداگانه: اگر اندازه‌گیری و کشیدن
 * از دو راه بیایند، متن دقیقاً به اندازهٔ اختلافشان از ستون بیرون
 * می‌زند — و آن روی فاکتور دیده می‌شود.
 */
class AndroidTextMeasurer(private val fonts: PdfKit.Fonts) : TextMeasurer {

    private fun paint(size: Float, weight: Weight): TextPaint =
        PdfKit.paint(
            size,
            PdfKit.INK,
            if (weight == Weight.Bold) fonts.bold else fonts.regular
        )

    private fun layout(text: String, size: Float, maxWidth: Float, weight: Weight): StaticLayout {
        val w = if (maxWidth > 0f) maxWidth.toInt() else Int.MAX_VALUE / 2
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint(size, weight), w)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
    }

    override fun width(text: String, size: Float, weight: Weight): Float {
        if (text.isEmpty()) return 0f
        return paint(size, weight).measureText(text)
    }

    override fun wrap(text: String, size: Float, maxWidth: Float, weight: Weight): List<String> {
        if (text.isBlank()) return emptyList()
        if (maxWidth <= 0f) return listOf(text)
        val l = layout(text, size, maxWidth, weight)
        return (0 until l.lineCount).map {
            text.substring(l.getLineStart(it), l.getLineEnd(it))
        }
    }

    override fun lineHeight(size: Float, weight: Weight): Float {
        val fm = paint(size, weight).fontMetrics
        return fm.descent - fm.ascent + fm.leading
    }

    /**
     * ارتفاع مستقیم از خودِ `StaticLayout` می‌آید، نه از ضربِ
     * «تعدادِ خط × ارتفاعِ خط».
     *
     * دلیل: چیدمانِ امروزِ اپ روی همین عدد بنا شده و هر اختلافِ کوچکی
     * سطرهای فاکتور را نسبت به نسخهٔ تحویل‌شده جابه‌جا می‌کند.
     */
    override fun height(text: String, size: Float, maxWidth: Float, weight: Weight): Float {
        if (text.isBlank()) return 0f
        return layout(text, size, maxWidth, weight).height.toFloat()
    }
}
