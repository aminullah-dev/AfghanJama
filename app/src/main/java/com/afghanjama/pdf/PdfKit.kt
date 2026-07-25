package com.afghanjama.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.afghanjama.R
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa

/**
 * زبانِ بصریِ مشترکِ همهٔ اسنادِ چاپیِ افغان‌جامه — فاکتور، رسید، سند و
 * صورت‌های مالی. سربرگ، بدنه و پاصفحه از یک‌جا می‌آیند تا هر برگه‌ای که
 * از کارگاه بیرون می‌رود یک‌شکل و رسمی باشد.
 *
 * لوگو با خطوط رسم می‌شود نه با فایلِ تصویر یا ایموجی: ایموجیِ قیچی در
 * فونتِ وزیرمتن گلیف ندارد و به‌جای نشان، مربعِ خالی چاپ می‌شد.
 */
object PdfKit {

    // A4 در ۷۲dpi (استاندارد PDF)
    const val PAGE_W = 595
    const val PAGE_H = 842
    const val MARGIN = 40f
    val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()

    const val BRAND = 0xFF1F6E5C.toInt()
    const val BRAND_DEEP = 0xFF17594A.toInt()
    const val INK = 0xFF1B1C1A.toInt()
    const val MUTED = 0xFF61605A.toInt()
    const val LINE = 0xFFE1DFD8.toInt()
    const val SOFT = 0xFFF4F7F5.toInt()      // زمینهٔ ردیف‌های یک‌درمیان
    const val DANGER = 0xFFB3261E.toInt()

    private const val HEADER_H = 104f
    private const val FOOTER_TOP = PAGE_H - 46f

    /** فضای امنِ بدنه — پایین‌تر از این باید صفحهٔ جدید باز شود. */
    const val BODY_BOTTOM = PAGE_H - 74f

    class Fonts(val regular: Typeface, val bold: Typeface)

    fun fonts(context: Context) = Fonts(
        regular = ResourcesCompat.getFont(context, R.font.vazirmatn_regular) ?: Typeface.DEFAULT,
        bold = ResourcesCompat.getFont(context, R.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD
    )

    fun paint(size: Float, color: Int, tf: Typeface): TextPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = size
        this.color = color
        typeface = tf
    }

    private fun fill(c: Int) = Paint().apply { color = c; isAntiAlias = true }

    private fun stroke(c: Int, w: Float) = Paint().apply {
        color = c; isAntiAlias = true
        style = Paint.Style.STROKE; strokeWidth = w
        strokeCap = Paint.Cap.ROUND
    }

    // ==================================================
    // لوگو: سوزن و نخ — نشانِ کارگاه خیاطی، کاملاً برداری
    // ==================================================
    fun drawLogo(
        c: Canvas,
        left: Float,
        top: Float,
        size: Float,
        badge: Int = Color.WHITE,
        mark: Int = BRAND
    ) {
        val r = size * 0.26f
        c.drawRoundRect(RectF(left, top, left + size, top + size), r, r, fill(badge))

        val cx = left + size / 2f
        val cy = top + size / 2f

        // بدنهٔ سوزن
        c.drawLine(
            cx + size * 0.20f, cy + size * 0.24f,
            cx - size * 0.06f, cy - size * 0.20f,
            stroke(mark, size * 0.075f)
        )
        // چشمِ سوزن
        c.drawCircle(
            cx - size * 0.10f, cy - size * 0.27f, size * 0.055f,
            stroke(mark, size * 0.045f)
        )
        // نخ
        c.drawPath(
            Path().apply {
                moveTo(cx - size * 0.17f, cy - size * 0.30f)
                quadTo(cx - size * 0.34f, cy - size * 0.02f, cx - size * 0.14f, cy + size * 0.12f)
                quadTo(cx + size * 0.02f, cy + size * 0.24f, cx - size * 0.10f, cy + size * 0.32f)
            },
            stroke(mark, size * 0.05f)
        )
    }

    // ==================================================
    // سربرگ
    // ==================================================
    /**
     * سربرگِ رسمی: نوارِ برند + لوگو + نامِ کارگاه + عنوانِ سند و
     * شماره/تاریخِ آن. مقدارِ برگشتی، y ِ شروعِ بدنه است.
     */
    fun drawHeader(
        c: Canvas,
        context: Context,
        f: Fonts,
        docTitle: String,
        docNumber: String = "",
        docDate: Long = System.currentTimeMillis()
    ): Float {
        c.drawRect(0f, 0f, PAGE_W.toFloat(), HEADER_H, fill(BRAND))
        // نوارِ باریکِ تیره برای عمق
        c.drawRect(0f, HEADER_H - 4f, PAGE_W.toFloat(), HEADER_H, fill(BRAND_DEEP))

        val logoSize = 44f
        drawLogo(c, PAGE_W - MARGIN - logoSize, 20f, logoSize)

        val coName = CompanyPrefs.name(context).ifBlank { "افغان‌جامه" }
        val addr = CompanyPrefs.address(context)
        val phone = CompanyPrefs.phone(context)

        val nameW = CONTENT_W - logoSize.toInt() - 14
        rtl(c, coName, MARGIN, 24f, paint(17f, Color.WHITE, f.bold), nameW)
        val sub = listOf(addr, phone).filter { it.isNotBlank() }.joinToString(" • ")
        if (sub.isNotBlank()) {
            rtl(c, sub, MARGIN, 48f, paint(9f, 0xFFD7EBE3.toInt(), f.regular), nameW)
        }

        // عنوانِ سند در سمتِ مقابل
        rtlEnd(c, docTitle, MARGIN, 66f, paint(14f, Color.WHITE, f.bold), CONTENT_W)
        val meta = buildString {
            if (docNumber.isNotBlank()) append("شمارهٔ $docNumber • ")
            append(PersianDate.long(docDate))
        }
        rtlEnd(c, meta, MARGIN, 84f, paint(9f, 0xFFD7EBE3.toInt(), f.regular), CONTENT_W)

        return HEADER_H + 22f
    }

    // ==================================================
    // پاصفحه
    // ==================================================
    fun drawFooter(
        c: Canvas,
        context: Context,
        f: Fonts,
        pageNo: Int,
        note: String = ""
    ) {
        c.drawLine(MARGIN, FOOTER_TOP, PAGE_W - MARGIN, FOOTER_TOP,
            Paint().apply { color = LINE; strokeWidth = 0.8f })

        val small = paint(8.5f, MUTED, f.regular)
        val phone = CompanyPrefs.phone(context)
        val right = buildString {
            append(CompanyPrefs.name(context).ifBlank { "افغان‌جامه" })
            if (phone.isNotBlank()) append(" • $phone")
        }
        rtl(c, right, MARGIN, FOOTER_TOP + 8f, small, CONTENT_W)
        rtlEnd(c, "صفحهٔ ${pageNo.fa()}", MARGIN, FOOTER_TOP + 8f, small, CONTENT_W)

        val credit = note.ifBlank { "صادرشده با اپلیکیشن افغان‌جامه" }
        rtl(c, credit, MARGIN, FOOTER_TOP + 22f, paint(8f, MUTED, f.regular), CONTENT_W)
    }

    // ==================================================
    // متنِ راست‌به‌چپ
    // ==================================================
    /** متن راست‌چین؛ ارتفاعِ رسم‌شده را برمی‌گرداند. */
    fun rtl(c: Canvas, text: String, x: Float, top: Float, tp: TextPaint, width: Int): Int {
        val l = StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        c.save(); c.translate(x, top); l.draw(c); c.restore()
        return l.height
    }

    /** متن در سمتِ مقابلِ سطر (چپ در چیدمانِ راست‌به‌چپ). */
    fun rtlEnd(c: Canvas, text: String, x: Float, top: Float, tp: TextPaint, width: Int): Int {
        val l = StaticLayout.Builder.obtain(text, 0, text.length, TextPaint(tp), width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE).build()
        c.save(); c.translate(x, top); l.draw(c); c.restore()
        return l.height
    }

    /** متنِ وسط‌چین. */
    fun rtlCenter(c: Canvas, text: String, x: Float, top: Float, tp: TextPaint, width: Int): Int {
        val l = StaticLayout.Builder.obtain(text, 0, text.length, TextPaint(tp), width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        c.save(); c.translate(x, top); l.draw(c); c.restore()
        return l.height
    }

    // ==================================================
    // اجزای بدنه
    // ==================================================
    fun rule(c: Canvas, y: Float): Float {
        c.drawLine(MARGIN, y, PAGE_W - MARGIN, y,
            Paint().apply { color = LINE; strokeWidth = 0.8f })
        return y + 12f
    }

    /** عنوانِ بخش با یک نشانهٔ کوچکِ برند در ابتدای آن. */
    fun section(c: Canvas, title: String, y: Float, f: Fonts): Float {
        c.drawRoundRect(
            RectF(PAGE_W - MARGIN - 3f, y + 2f, PAGE_W - MARGIN, y + 15f),
            1.5f, 1.5f, fill(BRAND)
        )
        rtl(c, title, MARGIN, y, paint(12.5f, BRAND, f.bold), CONTENT_W - 8)
        return y + 24f
    }

    /** ردیفِ «برچسب … مقدار». */
    fun kv(
        c: Canvas,
        label: String,
        value: String,
        y: Float,
        f: Fonts,
        strong: Boolean = false,
        danger: Boolean = false
    ): Float {
        val lp = paint(10.5f, MUTED, f.regular)
        val vp = paint(
            if (strong) 12f else 10.5f,
            if (danger) DANGER else INK,
            if (strong) f.bold else f.regular
        )
        val h1 = rtl(c, label, MARGIN, y, lp, (CONTENT_W * 0.58f).toInt())
        val h2 = rtlEnd(c, value, MARGIN, y, vp, CONTENT_W)
        return y + maxOf(h1, h2, 15).toFloat() + 5f
    }

    /** سرستونِ جدول. */
    fun tableHeader(c: Canvas, cells: List<String>, weights: List<Float>, y: Float, f: Fonts): Float {
        c.drawRoundRect(
            RectF(MARGIN, y - 4f, PAGE_W - MARGIN, y + 17f), 3f, 3f, fill(SOFT)
        )
        drawCells(c, cells, weights, y, paint(9.5f, BRAND, f.bold))
        return y + 24f
    }

    /** ردیفِ جدول؛ [zebra] زمینهٔ ملایم برای خوانایی سطرهای بلند. */
    fun tableRow(
        c: Canvas,
        cells: List<String>,
        weights: List<Float>,
        y: Float,
        f: Fonts,
        zebra: Boolean = false
    ): Float {
        if (zebra) {
            c.drawRect(MARGIN, y - 3f, PAGE_W - MARGIN, y + 16f, fill(SOFT))
        }
        drawCells(c, cells, weights, y, paint(9.5f, INK, f.regular))
        return y + 21f
    }

    /**
     * ستون‌ها از راست چیده می‌شوند (اولین عنوان راست‌ترین ستون است) و
     * آخرین ستون — که معمولاً مبلغ است — چپ‌چین می‌شود.
     */
    private fun drawCells(c: Canvas, cells: List<String>, weights: List<Float>, y: Float, tp: TextPaint) {
        val total = weights.sum().takeIf { it > 0f } ?: 1f
        var right = PAGE_W - MARGIN
        cells.forEachIndexed { i, text ->
            val w = CONTENT_W * (weights.getOrElse(i) { 1f } / total)
            val left = right - w
            if (i == cells.lastIndex) rtlEnd(c, text, left, y, tp, w.toInt())
            else rtl(c, text, left, y, tp, w.toInt())
            right = left
        }
    }

    /** جعبهٔ جمعِ نهایی — پررنگ‌ترین عددِ برگه. */
    fun totalBox(c: Canvas, label: String, value: String, y: Float, f: Fonts): Float {
        val h = 34f
        c.drawRoundRect(RectF(MARGIN, y, PAGE_W - MARGIN, y + h), 6f, 6f, fill(BRAND))
        rtl(c, label, MARGIN + 12f, y + 9f, paint(11f, Color.WHITE, f.regular), CONTENT_W - 24)
        rtlEnd(c, value, MARGIN + 12f, y + 7f, paint(14f, Color.WHITE, f.bold), CONTENT_W - 24)
        return y + h + 14f
    }

    /** جای امضا — رسیدِ بدونِ امضا در کارگاه اعتبار ندارد. */
    fun signatures(c: Canvas, y: Float, f: Fonts, right: String, left: String): Float {
        val tp = paint(9.5f, MUTED, f.regular)
        val half = CONTENT_W / 2f
        val lineY = y + 26f
        c.drawLine(PAGE_W - MARGIN - half + 20f, lineY, PAGE_W - MARGIN, lineY,
            Paint().apply { color = LINE; strokeWidth = 0.8f })
        c.drawLine(MARGIN, lineY, MARGIN + half - 20f, lineY,
            Paint().apply { color = LINE; strokeWidth = 0.8f })
        rtl(c, right, PAGE_W - MARGIN - half, lineY + 6f, tp, half.toInt())
        rtl(c, left, MARGIN, lineY + 6f, tp, (half - 20f).toInt())
        return lineY + 26f
    }

    /** یادداشتِ کم‌رنگ (شرایط، توضیح، سلبِ مسئولیت). */
    fun note(c: Canvas, text: String, y: Float, f: Fonts): Float {
        val h = rtl(c, text, MARGIN, y, paint(8.5f, MUTED, f.regular), CONTENT_W)
        return y + h + 8f
    }
}
