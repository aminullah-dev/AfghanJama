package com.afghanjama.desktop.pdf

import com.afghanjama.pdf.Align
import com.afghanjama.pdf.DrawOp
import com.afghanjama.pdf.Sheet
import com.afghanjama.pdf.Weight
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.PathIterator
import java.io.File

/**
 * کشیدنِ یک [Sheet] روی کاغذِ PDF، برای ویندوز.
 *
 * ### چرا متن به‌صورتِ خطوطِ برداری کشیده می‌شود
 *
 * PDFBox شکل‌دهیِ متن ندارد: حروف را همان‌طور که در رشته‌اند پشتِ هم
 * می‌گذارد. برای فارسی یعنی حروف **به هم نمی‌چسبند** و «سلام» می‌شود
 * «س ل ا م» — روی فاکتورِ مشتری فاجعه است.
 *
 * جاوا خودش این کار را بلد است: [TextLayout] فارسی را درست شکل می‌دهد و
 * راست‌به‌چپ می‌چیند. پس شکلِ نهایی از آن گرفته می‌شود و همان مسیرِ
 * برداری به PDF داده می‌شود.
 *
 * بها: متنِ فایل قابلِ انتخاب و جست‌وجو نیست. برای فاکتور و رسیدی که
 * چاپ می‌شود و دستِ مشتری می‌ماند، معاملهٔ درستی است — و در عوض هیچ
 * دردسرِ جاسازیِ فونت و هیچ خطرِ نچسبیدنِ حروفی نمی‌ماند.
 */
class SheetPdf(private val fonts: SheetFonts) {

    fun write(page: Sheet, target: File): File {
        PDDocument().use { doc ->
            val pdPage = PDPage(
                PDRectangle(page.paper.w.toFloat(), page.paper.h.toFloat())
            )
            doc.addPage(pdPage)
            PDPageContentStream(doc, pdPage).use { cs ->
                page.ops.forEach { draw(doc, cs, it, page.paper.h.toFloat()) }
            }
            target.parentFile?.mkdirs()
            doc.save(target)
        }
        return target
    }

    /**
     * مبدأ در `Sheet` گوشهٔ بالا-چپ است ولی در PDF پایین-چپ، پس هر `y`
     * وارونه می‌شود. اگر این یک جا فراموش شود، برگه سر و ته چاپ می‌شود.
     */
    private fun flip(y: Float, pageH: Float) = pageH - y

    private fun draw(doc: PDDocument, cs: PDPageContentStream, op: DrawOp, pageH: Float) {
        when (op) {
            is DrawOp.Rect -> {
                setFill(cs, op.color)
                cs.addRect(
                    op.left,
                    flip(op.bottom, pageH),
                    op.right - op.left,
                    op.bottom - op.top
                )
                cs.fill()
            }

            is DrawOp.Line -> {
                setStroke(cs, op.color)
                cs.setLineWidth(op.width)
                cs.moveTo(op.x1, flip(op.y1, pageH))
                cs.lineTo(op.x2, flip(op.y2, pageH))
                cs.stroke()
            }

            is DrawOp.Image -> {
                val img = PDImageXObject.createFromByteArray(doc, op.bytes, null)
                cs.drawImage(
                    img, op.left, flip(op.top + op.height, pageH), op.width, op.height
                )
            }

            is DrawOp.Text -> drawText(cs, op, pageH)
        }
    }

    private fun drawText(cs: PDPageContentStream, op: DrawOp.Text, pageH: Float) {
        if (op.text.isBlank()) return
        val font = fonts.of(op.weight).deriveFont(op.size)
        val layout = TextLayout(op.text, font, FRC)
        val width = layout.advance

        val startX = when (op.align) {
            Align.Start -> op.x - width      // شروع از راست، چون متن فارسی است
            Align.Center -> op.x - width / 2f
            Align.End -> op.x
        }

        // `op.y` بالای کادر است، پس خطِ کرسی به اندازهٔ صعودِ قلم
        // پایین‌تر می‌نشیند. بدونِ این، هر سطر یک خط بالاتر چاپ می‌شود.
        val baseline = op.y + layout.ascent
        // خطوطِ برداریِ همین متن، با حروفِ به‌هم‌چسبیده — کارِ خودِ جاوا.
        // مقیاسِ y منفی است چون محورِ PDF بالا-به-پایین نیست.
        val at = AffineTransform.getTranslateInstance(
            startX.toDouble(), flip(baseline, pageH).toDouble()
        ).apply { scale(1.0, -1.0) }
        val outline = layout.getOutline(null)

        setFill(cs, op.color)
        // مسیر صاف می‌شود: منحنی‌ها به پاره‌خط تبدیل می‌شوند، پس فقط
        // `moveTo`/`lineTo` لازم است. اینها در هر نسخهٔ PDFBox هستند،
        // برخلافِ `curveTo1` که ممکن است نباشد — و امضای کتابخانه اینجا
        // قابلِ دیدن نیست. در ۰٫۰۵ نقطه، چشم تفاوتی نمی‌بیند.
        fillPath(cs, at.createTransformedShape(outline).getPathIterator(null, 0.05))
    }

    /** ریختنِ یک مسیرِ جاوا در دلِ محتوای PDF. */
    private fun fillPath(cs: PDPageContentStream, it: PathIterator) {
        val c = FloatArray(6)
        var wrote = false
        while (!it.isDone) {
            when (it.currentSegment(c)) {
                PathIterator.SEG_MOVETO -> { cs.moveTo(c[0], c[1]); wrote = true }
                PathIterator.SEG_LINETO -> { cs.lineTo(c[0], c[1]); wrote = true }
                PathIterator.SEG_CLOSE -> cs.closePath()
            }
            it.next()
        }
        // بدونِ این نگهبان، متنِ خالی یک `fill` بی‌مسیر می‌فرستد و
        // PDFBox خطا می‌دهد.
        if (wrote) cs.fillEvenOdd()
    }

    private fun setFill(cs: PDPageContentStream, argb: Int) {
        val (r, g, b) = rgb(argb)
        cs.setNonStrokingColor(r, g, b)
    }

    private fun setStroke(cs: PDPageContentStream, argb: Int) {
        val (r, g, b) = rgb(argb)
        cs.setStrokingColor(r, g, b)
    }

    /** ARGB به سه عددِ ۰ تا ۱ — امضایی که در هر نسخهٔ PDFBox هست. */
    private fun rgb(argb: Int) = Triple(
        ((argb shr 16) and 0xFF) / 255f,
        ((argb shr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f
    )

    private companion object {
        val FRC = FontRenderContext(null, true, true)
    }
}

/**
 * قلم‌های برگه.
 *
 * از همان فایلِ وزیرمتنی خوانده می‌شوند که اپِ اندروید دارد — کپی نمی‌شود.
 * اگر فایل نبود، قلمِ پیش‌فرضِ سیستم می‌آید: برگهٔ بدشکل بهتر از برگهٔ
 * چاپ‌نشده است.
 */
class SheetFonts(private val regular: Font, private val bold: Font) {

    fun of(weight: Weight): Font = if (weight == Weight.Bold) bold else regular

    companion object {
        fun load(): SheetFonts {
            fun read(name: String, fallbackStyle: Int): Font =
                runCatching {
                    SheetFonts::class.java.getResourceAsStream("/$name")!!.use {
                        Font.createFont(Font.TRUETYPE_FONT, it)
                    }
                }.getOrElse { Font("SansSerif", fallbackStyle, 12) }

            return SheetFonts(
                regular = read("vazirmatn_regular.ttf", Font.PLAIN),
                bold = read("vazirmatn_bold.ttf", Font.BOLD)
            )
        }
    }
}
