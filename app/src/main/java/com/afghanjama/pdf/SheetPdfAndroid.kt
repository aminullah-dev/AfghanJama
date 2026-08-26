package com.afghanjama.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.afghanjama.pdf.Align
import com.afghanjama.pdf.DrawOp
import com.afghanjama.pdf.Sheet
import com.afghanjama.pdf.SheetDoc
import com.afghanjama.pdf.Weight
import com.afghanjama.util.ShareUtil
import java.io.File
import kotlin.math.ceil

/**
 * کشیدنِ [SheetDoc]ِ مشترک روی اندروید.
 *
 * **نیمهٔ گم‌شدهٔ فازِ ۵.** آن فاز چیدمانِ هفت سند را به `:core` برد و
 * سمتِ ویندوز (`SheetPdf` با PDFBox) را نوشت، ولی سمتِ اندروید هرگز
 * نوشته نشد. نتیجه‌اش این بود که هر سند **دو چیدمان** داشت: یکی مشترک
 * که فقط ویندوز استفاده می‌کرد، و یکی قدیمی در `PdfKit` که گوشی
 * استفاده می‌کرد. اندازه‌ها دستی یکسان نگه داشته شده بودند — یعنی روزی
 * که یکی عوض می‌شد، کاغذِ گوشی با کاغذِ پی‌سی فرق می‌کرد و هیچ‌کس تا
 * دیدنِ فاکتورِ چاپ‌شده نمی‌فهمید.
 *
 * `AndroidTextMeasurer` از همان فاز مانده بود و **هیچ‌جا استفاده
 * نمی‌شد**؛ حالا جفتش آمد.
 *
 * مختصات اینجا وارونه نمی‌شود: مبدأ `PdfDocument`ِ اندروید هم گوشهٔ
 * بالا-چپ است، برخلافِ PDF خام که پایین-چپ است و در `SheetPdf` هر `y`
 * باید برگردانده شود.
 */
object SheetPdfAndroid {

    /**
     * متن با همان موتوری کشیده می‌شود که [AndroidTextMeasurer] با آن
     * اندازه می‌گیرد — `StaticLayout`.
     *
     * وسوسه‌اش هست که `Canvas.drawText` ساده صدا زده شود (کوتاه‌تر است)،
     * ولی آن‌وقت اندازه‌گیری و کشیدن دو موتورِ متفاوت می‌شدند و متن دقیقاً
     * به اندازهٔ اختلافشان از ستون بیرون می‌زد.
     *
     * `DocChrome` خودش متن را شکسته و هر سطر را جدا می‌فرستد، پس اینجا
     * همیشه یک خط است و شکستنِ دوباره لازم نیست.
     */
    private fun drawText(c: Canvas, op: DrawOp.Text, fonts: PdfKit.Fonts) {
        if (op.text.isBlank()) return
        val tp: TextPaint = PdfKit.paint(
            op.size,
            op.color,
            if (op.weight == Weight.Bold) fonts.bold else fonts.regular
        )
        val w = ceil(tp.measureText(op.text).toDouble()).toInt().coerceAtLeast(1)

        // `x` در این مدل لبه است، نه گوشهٔ کادر:
        // Start لبهٔ راست (متن به چپ می‌رود)، End لبهٔ چپ، Center وسط.
        val startX = when (op.align) {
            Align.Start -> op.x - w
            Align.Center -> op.x - w / 2f
            Align.End -> op.x
        }

        val l = StaticLayout.Builder.obtain(op.text, 0, op.text.length, tp, w)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

        // `op.y` بالای کادر است و `StaticLayout` هم از بالا می‌کشد، پس
        // حسابِ خطِ کرسی لازم نیست — همان کاری که `PdfKit.rtl` می‌کند.
        c.save()
        c.translate(startX, op.y)
        l.draw(c)
        c.restore()
    }

    private fun draw(c: Canvas, op: DrawOp, fonts: PdfKit.Fonts) {
        when (op) {
            is DrawOp.Rect -> {
                val p = Paint().apply { color = op.color; isAntiAlias = true }
                val r = RectF(op.left, op.top, op.right, op.bottom)
                if (op.radius > 0f) c.drawRoundRect(r, op.radius, op.radius, p)
                else c.drawRect(r, p)
            }

            is DrawOp.Line -> {
                val p = Paint().apply {
                    color = op.color
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = op.width
                    strokeCap = Paint.Cap.ROUND
                }
                c.drawLine(op.x1, op.y1, op.x2, op.y2, p)
            }

            is DrawOp.Image -> {
                val bmp = BitmapFactory.decodeByteArray(op.bytes, 0, op.bytes.size)
                // تصویرِ خراب نباید کلِ فاکتور را بیندازد؛ لوگوی نیامده
                // بهتر از سندِ ساخته‌نشده است.
                if (bmp != null) {
                    c.drawBitmap(
                        bmp,
                        Rect(0, 0, bmp.width, bmp.height),
                        RectF(op.left, op.top, op.left + op.width, op.top + op.height),
                        Paint().apply { isAntiAlias = true; isFilterBitmap = true }
                    )
                }
            }

            is DrawOp.Text -> drawText(c, op, fonts)
        }
    }

    private fun page(doc: PdfDocument, sheet: Sheet, no: Int, fonts: PdfKit.Fonts) {
        val info = PdfDocument.PageInfo.Builder(sheet.paper.w, sheet.paper.h, no).create()
        val p = doc.startPage(info)
        sheet.ops.forEach { draw(p.canvas, it, fonts) }
        doc.finishPage(p)
    }

    /** سند را در پوشهٔ اشتراکِ اپ می‌نویسد و همان فایل را برمی‌گرداند. */
    fun write(context: Context, doc: SheetDoc, fileName: String): File {
        val fonts = PdfKit.fonts(context)
        val out = PdfDocument()
        try {
            doc.pages.forEachIndexed { i, s -> page(out, s, i + 1, fonts) }
            val file = File(ShareUtil.sharedDir(context), fileName)
            file.outputStream().use { out.writeTo(it) }
            return file
        } finally {
            // بدونِ این، هر ساختِ PDF حافظهٔ بومی را نگه می‌دارد و بعد از
            // چند فاکتور اپ کند می‌شود.
            out.close()
        }
    }
}
