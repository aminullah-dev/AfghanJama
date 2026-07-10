package com.afghanjama.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.afghanjama.R
import com.afghanjama.data.entities.Document
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.util.QrGen
import com.afghanjama.util.ShareUtil
import java.io.File

/**
 * سندِ مالیِ رسمی PDF (فاکتور/رسید) — A4 راست‌به‌چپ با فونت وزیرمتن و QR.
 * از همان الگوی اثبات‌شدهٔ InvoicePdf استفاده می‌کند.
 */
object DocumentPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()

    private const val BRAND = 0xFF1F6E5C.toInt()
    private const val INK = 0xFF1B1C1A.toInt()
    private const val MUTED = 0xFF61605A.toInt()
    private const val LINE = 0xFFE1DFD8.toInt()

    private fun typeLabel(t: String): String = when (t) {
        "PURCHASE" -> "فاکتور خرید"
        "SALE" -> "فاکتور فروش"
        "SUPPLIER_PAYMENT" -> "رسید پرداخت به فروشنده"
        "WAGE_RECEIPT" -> "رسید کارمزد دوخت"
        "CUSTOMER_RECEIPT" -> "رسید دریافت از مشتری"
        "RETURN" -> "سند برگشت"
        "PROFORMA" -> "پیش‌فاکتور"
        "PAYMENT" -> "رسید پرداخت"
        "RECEIPT" -> "رسید دریافت"
        else -> "سند"
    }

    fun create(context: Context, d: Document): File {
        val regular = ResourcesCompat.getFont(context, R.font.vazirmatn_regular) ?: Typeface.DEFAULT
        val bold = ResourcesCompat.getFont(context, R.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD

        fun paint(size: Float, color: Int, tf: Typeface) = TextPaint().apply {
            isAntiAlias = true; textSize = size; this.color = color; typeface = tf
        }

        val titlePaint = paint(20f, Color.WHITE, bold)
        val headerSubPaint = paint(11f, Color.WHITE, regular)
        val labelPaint = paint(11f, MUTED, regular)
        val valuePaint = paint(12f, INK, regular)
        val strongPaint = paint(15f, INK, bold)
        val footerPaint = paint(9f, MUTED, regular)

        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c = page.canvas

        // ---------- سربرگ ----------
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 96f, Paint().apply { color = BRAND })
        val logoPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        c.drawCircle(PAGE_W - MARGIN - 24f, 48f, 24f, logoPaint)
        val logoText = paint(22f, BRAND, bold).apply { textAlign = Paint.Align.CENTER }
        c.drawText("✂", PAGE_W - MARGIN - 24f, 56f, logoText)
        c.drawRtl("AfghanJama — مدیریت کارگاه خیاطی", MARGIN, 28f, titlePaint, CONTENT_W - 60)
        c.drawRtl(typeLabel(d.type), MARGIN, 62f, headerSubPaint, CONTENT_W - 60)

        var y = 122f
        y = c.kv("شمارهٔ سند", d.number, y, labelPaint, valuePaint)
        y = c.kv("تاریخ", PersianDate.long(d.at), y, labelPaint, valuePaint)
        if (d.partyName.isNotBlank()) y = c.kv("طرف حساب", d.partyName, y, labelPaint, valuePaint)
        if (d.refId.isNotBlank()) y = c.kv("مرجع", d.refId, y, labelPaint, valuePaint)
        if (d.note.isNotBlank()) y = c.kv("توضیح", d.note, y, labelPaint, valuePaint)
        y += 6f
        y = c.rule(y)
        y = c.kv("مبلغ", d.amount.afn(), y, labelPaint, strongPaint)

        // ---------- QR ----------
        val qr = QrGen.bitmap("AJ|${d.number}|${typeLabel(d.type)}|${d.amount}|${d.partyName}")
        if (qr != null) {
            y += 18f
            val s = 140f
            val left = (PAGE_W - s) / 2f
            c.drawBitmap(qr, null, RectF(left, y, left + s, y + s), null)
            y += s + 8f
            c.drawRtl("برای بررسی، QR را اسکن کنید", MARGIN, y, footerPaint, CONTENT_W)
        }

        // ---------- پاصفحه ----------
        val footY = PAGE_H - 36f
        c.drawLine(MARGIN, footY - 10f, PAGE_W - MARGIN, footY - 10f, Paint().apply { color = LINE; strokeWidth = 0.8f })
        c.drawRtl(
            "این سند با اپلیکیشن AfghanJama صادر شده — پشتیبانی: aminhashemi979@gmail.com",
            MARGIN, footY, footerPaint, CONTENT_W
        )

        doc.finishPage(page)
        val file = File(ShareUtil.sharedDir(context), "${d.number}.pdf")
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

    private fun Canvas.kv(label: String, value: String, y: Float, labelPaint: TextPaint, valuePaint: TextPaint): Float {
        drawRtl(label, MARGIN, y, labelPaint, CONTENT_W)
        val layout = StaticLayout.Builder
            .obtain(value, 0, value.length, TextPaint(valuePaint), CONTENT_W)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .build()
        save(); translate(MARGIN, y); layout.draw(this); restore()
        return y + maxOf(layout.height.toFloat(), 18f) + 4f
    }

    private fun Canvas.rule(y: Float): Float {
        drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply { color = LINE; strokeWidth = 0.8f })
        return y + 12f
    }
}
