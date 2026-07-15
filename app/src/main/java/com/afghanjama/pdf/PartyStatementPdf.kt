package com.afghanjama.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.afghanjama.R
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.data.entities.ledgerRefLabel
import com.afghanjama.data.entities.partyTypeLabel
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.util.ShareUtil
import java.io.File

/**
 * صورت‌حسابِ رسمیِ یک طرف از دفتر کل (برای دادن به مشتری/خیاط/فروشنده):
 * A4 راست‌به‌چپ، چندصفحه‌ای، با مانده و همهٔ اسناد.
 */
object PartyStatementPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()

    private const val BRAND = 0xFF1F6E5C.toInt()
    private const val INK = 0xFF1B1C1A.toInt()
    private const val MUTED = 0xFF61605A.toInt()
    private const val LINE = 0xFFE1DFD8.toInt()

    fun create(
        context: Context,
        partyType: String,
        partyName: String,
        net: Long,
        entries: List<LedgerEntry>
    ): File {
        val regular = ResourcesCompat.getFont(context, R.font.vazirmatn_regular) ?: Typeface.DEFAULT
        val bold = ResourcesCompat.getFont(context, R.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD

        fun paint(size: Float, color: Int, tf: Typeface) = TextPaint().apply {
            isAntiAlias = true; textSize = size; this.color = color; typeface = tf
        }

        val titlePaint = paint(18f, Color.WHITE, bold)
        val headerSubPaint = paint(11f, Color.WHITE, regular)
        val labelPaint = paint(10f, MUTED, regular)
        val rowPaint = paint(10f, INK, regular)
        val strongPaint = paint(13f, INK, bold)
        val footerPaint = paint(9f, MUTED, regular)

        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var c = page.canvas
        var y: Float

        fun drawHeader(canvas: Canvas): Float {
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 86f, Paint().apply { color = BRAND })
            val coName = CompanyPrefs.name(context).ifBlank { "AfghanJama — مدیریت کارگاه خیاطی" }
            canvas.drawRtl(coName, MARGIN, 22f, titlePaint, CONTENT_W)
            canvas.drawRtl(
                "صورت‌حساب ${partyTypeLabel(partyType)} — $partyName",
                MARGIN, 54f, headerSubPaint, CONTENT_W
            )
            return 104f
        }

        fun drawFooter(canvas: Canvas) {
            val footY = PAGE_H - 32f
            canvas.drawLine(MARGIN, footY - 10f, PAGE_W - MARGIN, footY - 10f,
                Paint().apply { color = LINE; strokeWidth = 0.8f })
            val phone = CompanyPrefs.phone(context)
            canvas.drawRtl(
                "صفحهٔ $pageNo • ${PersianDate.long(System.currentTimeMillis())}" +
                    (if (phone.isNotBlank()) " • تلفن: $phone" else ""),
                MARGIN, footY, footerPaint, CONTENT_W
            )
        }

        y = drawHeader(c)

        // ---------- مانده ----------
        val balanceText = when {
            net > 0 -> "بدهکار ${net.afn()} (به ما بدهکار است)"
            net < 0 -> "بستانکار ${(-net).afn()} (ما بدهکاریم)"
            else -> "تسویه — مانده صفر"
        }
        c.drawRtl("ماندهٔ حساب: $balanceText", MARGIN, y, strongPaint, CONTENT_W)
        y += 30f
        c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply { color = LINE; strokeWidth = 0.8f })
        y += 10f

        // ---------- ردیف‌های گردش ----------
        entries.forEach { e ->
            if (y > PAGE_H - 80f) {
                drawFooter(c)
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                c = page.canvas
                y = drawHeader(c)
            }
            val amount = if (e.debit > 0) "بدهکار ${e.debit.afn()}" else "بستانکار ${e.credit.afn()}"
            c.drawRtl(
                "${PersianDate.short(e.at)} — ${ledgerRefLabel(e.refType)}" +
                    (if (e.refId.isNotBlank()) " (${e.refId})" else ""),
                MARGIN, y, rowPaint, (CONTENT_W * 0.62f).toInt()
            )
            c.drawRtlEnd(amount, MARGIN, y, rowPaint, CONTENT_W)
            y += 16f
            if (e.note.isNotBlank()) {
                c.drawRtl("   ${e.note}", MARGIN, y, labelPaint, CONTENT_W)
                y += 14f
            }
            y += 2f
        }

        if (entries.isEmpty()) {
            c.drawRtl("سندی ثبت نشده است.", MARGIN, y, rowPaint, CONTENT_W)
        }

        drawFooter(c)
        doc.finishPage(page)

        val safe = partyName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val file = File(ShareUtil.sharedDir(context), "صورتحساب-$safe.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    // ---------- کمکی‌های رسم راست‌به‌چپ ----------

    private fun Canvas.drawRtl(text: String, x: Float, top: Float, tp: TextPaint, width: Int) {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, tp, width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        save(); translate(x, top); layout.draw(this); restore()
    }

    /** متن در سمتِ مقابل سطر (چپ در چیدمان RTL). */
    private fun Canvas.drawRtlEnd(text: String, x: Float, top: Float, tp: TextPaint, width: Int) {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, TextPaint(tp), width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .build()
        save(); translate(x, top); layout.draw(this); restore()
    }
}
