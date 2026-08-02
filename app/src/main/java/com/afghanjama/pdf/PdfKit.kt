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
import com.afghanjama.AppInfo
import com.afghanjama.R
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa

/**
 * زبانِ بصریِ مشترکِ همهٔ اسنادِ چاپی — فاکتور، رسید، سند و
 * صورت‌های مالی. سربرگ، بدنه و پاصفحه از یک‌جا می‌آیند تا هر برگه‌ای که
 * از کارگاه بیرون می‌رود یک‌شکل و رسمی باشد.
 *
 * لوگو با خطوط رسم می‌شود نه با فایلِ تصویر یا ایموجی: ایموجیِ قیچی در
 * فونتِ وزیرمتن گلیف ندارد و به‌جای نشان، مربعِ خالی چاپ می‌شد.
 */
object PdfKit {

    // A4 در ۷۲dpi — نامِ مستعار، تا اسنادی که فقط A4 چاپ می‌شوند
    // دست‌نخورده بمانند.
    val PAGE_W = Paper.A4.w
    val PAGE_H = Paper.A4.h
    val MARGIN = Paper.A4.margin
    val CONTENT_W = Paper.A4.contentW

    const val BRAND = 0xFF1F6E5C.toInt()
    const val BRAND_DEEP = 0xFF17594A.toInt()
    const val INK = 0xFF1B1C1A.toInt()
    const val MUTED = 0xFF61605A.toInt()
    const val LINE = 0xFFE1DFD8.toInt()
    const val SOFT = 0xFFF4F7F5.toInt()      // زمینهٔ ردیف‌های یک‌درمیان
    const val DANGER = 0xFFB3261E.toInt()

    private fun headerH(paper: Paper) = if (paper.narrow) 62f else 104f
    private fun footerTop(paper: Paper) = paper.h - (if (paper.narrow) 26f else 46f)

    /** فضای امنِ بدنه — پایین‌تر از این باید صفحهٔ جدید باز شود. */
    val BODY_BOTTOM = Paper.A4.bodyBottom

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
        docDate: Long = System.currentTimeMillis(),
        paper: Paper = Paper.A4
    ): Float {
        val coName = CompanyPrefs.shopName(context)
        val addr = CompanyPrefs.address(context)
        val phone = CompanyPrefs.phone(context)
        val meta = buildString {
            if (docNumber.isNotBlank()) append("شمارهٔ $docNumber • ")
            append(PersianDate.long(docDate))
        }

        // رولِ حرارتی سیاه‌وسفید است و زمینهٔ پررنگ فقط جوهر/حرارت هدر
        // می‌دهد؛ آنجا سربرگ ساده و بی‌زمینه چاپ می‌شود.
        if (paper.narrow) {
            drawLogo(c, (paper.w - 26f) / 2f, 6f, 26f, badge = Color.WHITE, mark = INK)
            var y = 36f
            y += rtlCenter(c, coName, paper.margin, y, paint(11f, INK, f.bold), paper.contentW)
            val sub = listOf(phone, addr).filter { it.isNotBlank() }.joinToString(" • ")
            if (sub.isNotBlank()) {
                y += rtlCenter(c, sub, paper.margin, y, paint(7.5f, MUTED, f.regular), paper.contentW)
            }
            y += 4f
            c.drawLine(paper.margin, y, paper.w - paper.margin, y,
                Paint().apply { color = INK; strokeWidth = 0.8f })
            y += 6f
            y += rtlCenter(c, docTitle, paper.margin, y, paint(10.5f, INK, f.bold), paper.contentW)
            y += rtlCenter(c, meta, paper.margin, y, paint(7.5f, MUTED, f.regular), paper.contentW)
            return y + 8f
        }

        val h = headerH(paper)
        c.drawRect(0f, 0f, paper.w.toFloat(), h, fill(BRAND))
        // نوارِ باریکِ تیره برای عمق
        c.drawRect(0f, h - 4f, paper.w.toFloat(), h, fill(BRAND_DEEP))

        val logoSize = 44f
        drawLogo(c, paper.w - paper.margin - logoSize, 20f, logoSize)

        val nameW = paper.contentW - logoSize.toInt() - 14
        rtl(c, coName, paper.margin, 24f, paint(17f, Color.WHITE, f.bold), nameW)
        val sub = listOf(addr, phone).filter { it.isNotBlank() }.joinToString(" • ")
        if (sub.isNotBlank()) {
            rtl(c, sub, paper.margin, 48f, paint(9f, 0xFFD7EBE3.toInt(), f.regular), nameW)
        }

        // عنوانِ سند در سمتِ مقابل
        rtlEnd(c, docTitle, paper.margin, 66f, paint(14f, Color.WHITE, f.bold), paper.contentW)
        rtlEnd(c, meta, paper.margin, 84f, paint(9f, 0xFFD7EBE3.toInt(), f.regular), paper.contentW)

        return h + 22f
    }

    // ==================================================
    // پاصفحه
    // ==================================================
    fun drawFooter(
        c: Canvas,
        context: Context,
        f: Fonts,
        pageNo: Int,
        note: String = "",
        paper: Paper = Paper.A4
    ) {
        val top = footerTop(paper)
        c.drawLine(paper.margin, top, paper.w - paper.margin, top,
            Paint().apply { color = LINE; strokeWidth = 0.8f })

        val credit = note.ifBlank { "صادرشده با اپلیکیشن ${AppInfo.NAME}" }

        // روی رول صفحه‌شماری معنا ندارد و جا هم نیست
        if (paper.narrow) {
            rtlCenter(c, credit, paper.margin, top + 6f, paint(7f, MUTED, f.regular), paper.contentW)
            return
        }

        val small = paint(8.5f, MUTED, f.regular)
        val phone = CompanyPrefs.phone(context)
        val right = buildString {
            append(CompanyPrefs.shopName(context))
            if (phone.isNotBlank()) append(" • $phone")
        }
        rtl(c, right, paper.margin, top + 8f, small, paper.contentW)
        rtlEnd(c, "صفحهٔ ${pageNo.fa()}", paper.margin, top + 8f, small, paper.contentW)
        rtl(c, credit, paper.margin, top + 22f, paint(8f, MUTED, f.regular), paper.contentW)
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
    fun rule(c: Canvas, y: Float, paper: Paper = Paper.A4): Float {
        c.drawLine(paper.margin, y, paper.w - paper.margin, y,
            Paint().apply { color = LINE; strokeWidth = 0.8f })
        return y + 12f
    }

    /** عنوانِ بخش با یک نشانهٔ کوچکِ برند در ابتدای آن. */
    fun section(c: Canvas, title: String, y: Float, f: Fonts, paper: Paper = Paper.A4): Float {
        val ink = if (paper.narrow) INK else BRAND
        c.drawRoundRect(
            RectF(paper.w - paper.margin - 3f, y + 2f, paper.w - paper.margin, y + 15f),
            1.5f, 1.5f, fill(ink)
        )
        rtl(c, title, paper.margin, y, paint(12.5f, ink, f.bold), paper.contentW - 8)
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
        danger: Boolean = false,
        paper: Paper = Paper.A4
    ): Float {
        val size = if (paper.narrow) 8.5f else 10.5f
        val lp = paint(size, MUTED, f.regular)
        val vp = paint(
            if (strong) size + 1.5f else size,
            if (danger) DANGER else INK,
            if (strong) f.bold else f.regular
        )
        val h1 = rtl(c, label, paper.margin, y, lp, (paper.contentW * 0.58f).toInt())
        val h2 = rtlEnd(c, value, paper.margin, y, vp, paper.contentW)
        return y + maxOf(h1, h2, 15).toFloat() + 5f
    }

    /** سرستونِ جدول. */
    fun tableHeader(
        c: Canvas,
        cells: List<String>,
        weights: List<Float>,
        y: Float,
        f: Fonts,
        paper: Paper = Paper.A4
    ): Float {
        c.drawRoundRect(
            RectF(paper.margin, y - 4f, paper.w - paper.margin, y + 17f), 3f, 3f, fill(SOFT)
        )
        drawCells(c, cells, weights, y, paint(paper.cellTextSize, if (paper.narrow) INK else BRAND, f.bold), paper)
        return y + 24f
    }

    /** ردیفِ جدول؛ [zebra] زمینهٔ ملایم برای خوانایی سطرهای بلند. */
    fun tableRow(
        c: Canvas,
        cells: List<String>,
        weights: List<Float>,
        y: Float,
        f: Fonts,
        zebra: Boolean = false,
        paper: Paper = Paper.A4
    ): Float {
        if (zebra) {
            c.drawRect(paper.margin, y - 3f, paper.w - paper.margin, y + 16f, fill(SOFT))
        }
        drawCells(c, cells, weights, y, paint(paper.cellTextSize, INK, f.regular), paper)
        return y + 21f
    }

    /**
     * ستون‌ها از راست چیده می‌شوند (اولین عنوان راست‌ترین ستون است) و
     * آخرین ستون — که معمولاً مبلغ است — چپ‌چین می‌شود.
     */
    private fun drawCells(
        c: Canvas,
        cells: List<String>,
        weights: List<Float>,
        y: Float,
        tp: TextPaint,
        paper: Paper = Paper.A4
    ) {
        val total = weights.sum().takeIf { it > 0f } ?: 1f
        var right = paper.w - paper.margin
        cells.forEachIndexed { i, text ->
            val w = paper.contentW * (weights.getOrElse(i) { 1f } / total)
            val left = right - w
            if (i == cells.lastIndex) rtlEnd(c, text, left, y, tp, w.toInt())
            else rtl(c, text, left, y, tp, w.toInt())
            right = left
        }
    }

    /** جعبهٔ جمعِ نهایی — پررنگ‌ترین عددِ برگه. */
    fun totalBox(
        c: Canvas,
        label: String,
        value: String,
        y: Float,
        f: Fonts,
        paper: Paper = Paper.A4
    ): Float {
        val h = if (paper.narrow) 28f else 34f
        val inner = paper.contentW - 24
        // روی رول، زمینهٔ پر جوهر/حرارتِ زیادی می‌برد — کادرِ خطی می‌زنیم
        if (paper.narrow) {
            c.drawRoundRect(
                RectF(paper.margin, y, paper.w - paper.margin, y + h), 4f, 4f,
                stroke(INK, 1.2f)
            )
            rtl(c, label, paper.margin + 8f, y + 7f, paint(9f, INK, f.regular), inner)
            rtlEnd(c, value, paper.margin + 8f, y + 6f, paint(11f, INK, f.bold), inner)
            return y + h + 10f
        }
        c.drawRoundRect(RectF(paper.margin, y, paper.w - paper.margin, y + h), 6f, 6f, fill(BRAND))
        rtl(c, label, paper.margin + 12f, y + 9f, paint(11f, Color.WHITE, f.regular), inner)
        rtlEnd(c, value, paper.margin + 12f, y + 7f, paint(14f, Color.WHITE, f.bold), inner)
        return y + h + 14f
    }

    /** جای امضا — رسیدِ بدونِ امضا در کارگاه اعتبار ندارد. */
    fun signatures(
        c: Canvas,
        y: Float,
        f: Fonts,
        right: String,
        left: String,
        paper: Paper = Paper.A4
    ): Float {
        val tp = paint(if (paper.narrow) 7.5f else 9.5f, MUTED, f.regular)
        val half = paper.contentW / 2f
        val lineY = y + 26f
        val gap = if (paper.narrow) 6f else 20f
        c.drawLine(paper.w - paper.margin - half + gap, lineY, paper.w - paper.margin, lineY,
            Paint().apply { color = LINE; strokeWidth = 0.8f })
        c.drawLine(paper.margin, lineY, paper.margin + half - gap, lineY,
            Paint().apply { color = LINE; strokeWidth = 0.8f })
        rtl(c, right, paper.w - paper.margin - half, lineY + 6f, tp, half.toInt())
        rtl(c, left, paper.margin, lineY + 6f, tp, (half - gap).toInt())
        return lineY + 26f
    }

    /** یادداشتِ کم‌رنگ (شرایط، توضیح، سلبِ مسئولیت). */
    fun note(c: Canvas, text: String, y: Float, f: Fonts, paper: Paper = Paper.A4): Float {
        val h = rtl(c, text, paper.margin, y, paint(8.5f, MUTED, f.regular), paper.contentW)
        return y + h + 8f
    }

    /**
     * کادرِ متنی با حاشیه — برای پیامِ پایانِ فاکتور. مقدارِ برگشتی، y ِ
     * بعد از کادر است.
     */
    fun boxedNote(
        c: Canvas,
        lines: List<String>,
        y: Float,
        f: Fonts,
        paper: Paper = Paper.A4
    ): Float {
        val tp = paint(if (paper.narrow) 7.5f else 9f, INK, f.regular)
        val innerW = paper.contentW - 20
        var inner = y + 8f
        lines.filter { it.isNotBlank() }.forEach {
            inner += rtl(c, it, paper.margin + 10f, inner, tp, innerW)
        }
        val bottom = inner + 8f
        c.drawRoundRect(
            RectF(paper.margin, y, paper.w - paper.margin, bottom), 4f, 4f, stroke(LINE, 1f)
        )
        return bottom + 10f
    }
}
