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
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.FabricUnit
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.util.ShareUtil
import java.io.File

/**
 * فاکتور رسمی PDF برای یک سفارش — A4، راست‌به‌چپ، با فونت وزیرمتن.
 * از PdfDocument خود اندروید استفاده می‌کند (بدون وابستگی خارجی).
 */
object InvoicePdf {

    // A4 در ۷۲dpi (استاندارد PDF)
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private val CONTENT_W = (PAGE_W - 2 * MARGIN).toInt()

    private const val BRAND = 0xFF1F6E5C.toInt()      // سبز برند اپ
    private const val INK = 0xFF1B1C1A.toInt()
    private const val MUTED = 0xFF61605A.toInt()
    private const val LINE = 0xFFE1DFD8.toInt()

    fun create(
        context: Context,
        order: Order,
        fabrics: List<OrderFabric>,
        workItems: List<OrderWorkItem>,
        payments: List<CustomerPayment>
    ): File {
        val regular = ResourcesCompat.getFont(context, R.font.vazirmatn_regular)
            ?: Typeface.DEFAULT
        val bold = ResourcesCompat.getFont(context, R.font.vazirmatn_bold)
            ?: Typeface.DEFAULT_BOLD

        fun paint(size: Float, color: Int, tf: Typeface) = TextPaint().apply {
            isAntiAlias = true
            textSize = size
            this.color = color
            typeface = tf
        }

        val titlePaint = paint(20f, Color.WHITE, bold)
        val headerSubPaint = paint(10f, Color.WHITE, regular)
        val sectionPaint = paint(13f, BRAND, bold)
        val labelPaint = paint(11f, MUTED, regular)
        val valuePaint = paint(11f, INK, regular)
        val strongPaint = paint(13f, INK, bold)
        val footerPaint = paint(9f, MUTED, regular)

        val doc = PdfDocument()
        val page = doc.startPage(
            PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        )
        val c = page.canvas
        var y = 0f

        // ---------- سربرگ رنگی با لوگو ----------
        val headerPaint = Paint().apply { color = BRAND }
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 96f, headerPaint)

        // لوگوی دایره‌ای «قیچی»
        val logoPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        c.drawCircle(PAGE_W - MARGIN - 24f, 48f, 24f, logoPaint)
        val logoText = paint(22f, BRAND, bold)
        c.drawTextRtl("✂", PAGE_W - MARGIN - 24f, 56f, logoText, centered = true)

        val coName = com.afghanjama.prefs.CompanyPrefs.name(context)
            .ifBlank { "AfghanJama — مدیریت کارگاه خیاطی" }
        c.drawRtl(coName, MARGIN, 30f, titlePaint, CONTENT_W - 60)
        c.drawRtl("فاکتور سفارش", MARGIN, 62f, headerSubPaint, CONTENT_W - 60)

        // QR کد کوتاه سفارش (برای اسکن در بخش برش)
        com.afghanjama.util.QrGen.bitmap(order.shortCode)?.let { qr ->
            c.drawBitmap(qr, null, android.graphics.RectF(MARGIN, 16f, MARGIN + 64f, 80f), null)
        }
        y = 120f

        // ---------- مشخصات فاکتور ----------
        y = c.kv("شماره فاکتور", order.orderCode, MARGIN, y, labelPaint, valuePaint)
        y = c.kv("کد کوتاه", order.shortCode, MARGIN, y, labelPaint, valuePaint)
        y = c.kv("تاریخ", PersianDate.long(System.currentTimeMillis()), MARGIN, y, labelPaint, valuePaint)
        if (order.customerName.isNotBlank()) {
            y = c.kv(
                "مشتری",
                order.customerName +
                    (order.customerPhone.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                MARGIN, y, labelPaint, valuePaint
            )
        }
        y += 8f
        y = c.rule(y)

        // ---------- سفارش ----------
        y = c.section("مشخصات سفارش", y, sectionPaint)
        y = c.kv("طرح", order.designTitle.ifBlank { "-" }, MARGIN, y, labelPaint, valuePaint)
        y = c.kv("تعداد", "${order.qty.fa()} عدد", MARGIN, y, labelPaint, valuePaint)
        if (order.size.isNotBlank()) {
            y = c.kv("سایز", order.size, MARGIN, y, labelPaint, valuePaint)
        }
        y += 6f

        // ---------- پارچه‌ها ----------
        if (fabrics.isNotEmpty()) {
            y = c.section("پارچه‌ها", y, sectionPaint)
            fabrics.forEach { f ->
                val unitFa = when (f.fabricUnit.uppercase()) {
                    FabricUnit.METER.name -> "متر"
                    FabricUnit.YARD.name -> "یارد"
                    else -> f.fabricUnit
                }
                y = c.kv(
                    "${f.fabricType} • ${f.fabricColor}",
                    "${f.amount.toString().trimEnd('0').trimEnd('.')} $unitFa — ${f.price.afn()}",
                    MARGIN, y, labelPaint, valuePaint
                )
            }
            y += 6f
        }

        // ---------- خرج‌کارها ----------
        if (workItems.isNotEmpty()) {
            y = c.section("خرج‌کارها (فی‌عدد)", y, sectionPaint)
            workItems.forEach { w ->
                y = c.kv(w.title, w.price.afn(), MARGIN, y, labelPaint, valuePaint)
            }
            y += 6f
        }

        // ---------- جمع مالی ----------
        y = c.rule(y)
        y = c.section("خلاصه مالی", y, sectionPaint)
        y = c.kv("جمع قیمت پارچه", order.fabricPrice.afn(), MARGIN, y, labelPaint, valuePaint)
        y = c.kv("جمع خرج کار", order.workCost.afn(), MARGIN, y, labelPaint, valuePaint)
        if (order.sewingCost > 0) {
            y = c.kv("دستمزد دوخت", order.sewingCost.afn(), MARGIN, y, labelPaint, valuePaint)
        }
        if (order.agreedPrice > 0) {
            y = c.kv("قیمت توافقی", order.agreedPrice.afn(), MARGIN, y, labelPaint, strongPaint)
        }

        val paid = payments.sumOf { it.amount }
        if (paid != 0L) {
            y = c.kv("مجموع دریافتی", paid.afn(), MARGIN, y, labelPaint, valuePaint)
            val due = (if (order.agreedPrice > 0) order.agreedPrice
            else order.fabricPrice + order.workCost) - paid
            if (due > 0) {
                y = c.kv("باقی‌مانده", due.afn(), MARGIN, y, labelPaint, strongPaint)
            }
        }

        // ---------- امضاها ----------
        y += 26f
        val sigPaint = paint(10f, MUTED, regular)
        c.drawRtl("امضای فروشنده: ....................", MARGIN, y, sigPaint, CONTENT_W / 2)
        c.drawRtl("امضای مشتری: ....................", MARGIN + CONTENT_W / 2f, y, sigPaint, CONTENT_W / 2)

        // ---------- پاصفحه ----------
        val footY = PAGE_H - 36f
        c.drawLine(MARGIN, footY - 10f, PAGE_W - MARGIN, footY - 10f, Paint().apply { color = LINE; strokeWidth = 0.8f })
        c.drawRtl(
            "این فاکتور با اپلیکیشن AfghanJama صادر شده — پشتیبانی: aminhashemi979@gmail.com",
            MARGIN, footY, footerPaint, CONTENT_W
        )

        doc.finishPage(page)

        val file = File(ShareUtil.sharedDir(context), "فاکتور-${order.orderCode}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    // ------------------------------------------------
    // کمکی‌های رسم راست‌به‌چپ
    // ------------------------------------------------

    /** رسم یک خط متن RTL (راست‌چین) درون عرض داده‌شده. */
    private fun Canvas.drawRtl(text: String, x: Float, top: Float, tp: TextPaint, width: Int) {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, tp, width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        save()
        translate(x, top)
        layout.draw(this)
        restore()
    }

    /** متن تک‌کاراکتری وسط‌چین (برای لوگو). */
    private fun Canvas.drawTextRtl(text: String, cx: Float, baselineY: Float, tp: TextPaint, centered: Boolean) {
        val old = tp.textAlign
        if (centered) tp.textAlign = Paint.Align.CENTER
        drawText(text, cx, baselineY, tp)
        tp.textAlign = old
    }

    /** یک ردیف «برچسب / مقدار»: برچسب سمت راست، مقدار سمت چپ. */
    private fun Canvas.kv(
        label: String,
        value: String,
        x: Float,
        y: Float,
        labelPaint: TextPaint,
        valuePaint: TextPaint
    ): Float {
        drawRtl(label, x, y, labelPaint, CONTENT_W)
        // مقدار در سمت چپ سطر
        val vp = TextPaint(valuePaint)
        val layout = StaticLayout.Builder
            .obtain(value, 0, value.length, vp, CONTENT_W)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .build()
        save()
        translate(x, y)
        layout.draw(this)
        restore()
        return y + maxOf(layout.height.toFloat(), 16f) + 4f
    }

    /** عنوان بخش. */
    private fun Canvas.section(title: String, y: Float, tp: TextPaint): Float {
        drawRtl(title, MARGIN, y, tp, CONTENT_W)
        return y + 22f
    }

    /** خط جداکننده. */
    private fun Canvas.rule(y: Float): Float {
        drawLine(
            MARGIN, y, PAGE_W - MARGIN, y,
            Paint().apply { color = LINE; strokeWidth = 0.8f }
        )
        return y + 12f
    }
}
