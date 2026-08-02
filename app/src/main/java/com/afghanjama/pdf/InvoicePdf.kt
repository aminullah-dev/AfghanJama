package com.afghanjama.pdf

import android.content.Context
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.FabricUnit
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.pdf.PdfKit.BODY_BOTTOM
import com.afghanjama.pdf.PdfKit.CONTENT_W
import com.afghanjama.pdf.PdfKit.MARGIN
import com.afghanjama.pdf.PdfKit.PAGE_H
import com.afghanjama.pdf.PdfKit.PAGE_W
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.util.ShareUtil
import java.io.File

/**
 * فاکتورِ رسمیِ یک سفارش — A4 راست‌به‌چپ با سربرگ، بدنه و پاصفحهٔ
 * مشترک. QR کدِ کوتاهِ سفارش برای اسکن در خطِ تولید.
 */
object InvoicePdf {

    fun create(
        context: Context,
        order: Order,
        fabrics: List<OrderFabric>,
        workItems: List<OrderWorkItem>,
        payments: List<CustomerPayment>
    ): File {
        val f = PdfKit.fonts(context)
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var c = page.canvas

        var y = PdfKit.drawHeader(c, context, f, "فاکتور سفارش", order.orderCode)

        fun newPageIfNeeded(needed: Float) {
            if (y + needed <= BODY_BOTTOM) return
            PdfKit.drawFooter(c, context, f, pageNo)
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            c = page.canvas
            y = PdfKit.drawHeader(c, context, f, "فاکتور سفارش (ادامه)", order.orderCode)
        }

        // ---------- مشتری و QR ----------
        PdfKit.section(c, "مشخصات", y, f).also { y = it }

        com.afghanjama.util.QrGen.bitmap(order.shortCode)?.let { qr ->
            c.drawBitmap(qr, null, RectF(MARGIN, y - 4f, MARGIN + 62f, y + 58f), null)
            PdfKit.rtl(
                c, order.shortCode, MARGIN, y + 60f,
                PdfKit.paint(8f, PdfKit.MUTED, f.regular), 62
            )
        }

        y = PdfKit.kv(c, "کد سفارش", order.orderCode, y, f)
        y = PdfKit.kv(c, "تاریخ صدور", PersianDate.long(System.currentTimeMillis()), y, f)
        if (order.customerName.isNotBlank()) {
            y = PdfKit.kv(
                c, "مشتری",
                order.customerName +
                    (order.customerPhone.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                y, f
            )
        }
        y = PdfKit.kv(c, "طرح / محصول", order.designTitle.ifBlank { "-" }, y, f)
        y = PdfKit.kv(c, "تعداد", "${order.qty.fa()} عدد", y, f)
        if (order.size.isNotBlank()) y = PdfKit.kv(c, "سایز", order.size, y, f)
        if (order.dueDate > 0) {
            y = PdfKit.kv(c, "مهلت تحویل", PersianDate.long(order.dueDate), y, f)
        }
        y = maxOf(y, 190f) + 6f

        // ---------- اقلام ----------
        if (fabrics.isNotEmpty()) {
            newPageIfNeeded(70f)
            y = PdfKit.section(c, "پارچه و مواد", y, f)
            val w = listOf(3.2f, 1.6f, 1.4f, 1.8f)
            y = PdfKit.tableHeader(c, listOf("قلم", "رنگ", "مقدار", "مبلغ"), w, y, f)
            fabrics.forEachIndexed { i, fab ->
                newPageIfNeeded(24f)
                val unitFa = when (fab.fabricUnit.uppercase()) {
                    FabricUnit.METER.name -> "متر"
                    FabricUnit.YARD.name -> "یارد"
                    else -> fab.fabricUnit
                }
                val amount = fab.amount.toString().trimEnd('0').trimEnd('.')
                y = PdfKit.tableRow(
                    c,
                    listOf(fab.fabricType, fab.fabricColor, "$amount $unitFa", fab.price.afn()),
                    w, y, f, zebra = i % 2 == 1
                )
            }
            y += 8f
        }

        if (workItems.isNotEmpty()) {
            newPageIfNeeded(70f)
            y = PdfKit.section(c, "خرج‌کارها (فی‌عدد)", y, f)
            val w = listOf(5f, 2f)
            y = PdfKit.tableHeader(c, listOf("شرح", "مبلغ"), w, y, f)
            workItems.forEachIndexed { i, item ->
                newPageIfNeeded(24f)
                y = PdfKit.tableRow(c, listOf(item.title, item.price.afn()), w, y, f, i % 2 == 1)
            }
            y += 8f
        }

        // ---------- خلاصهٔ مالی ----------
        newPageIfNeeded(150f)
        y = PdfKit.section(c, "خلاصهٔ مالی", y, f)
        y = PdfKit.kv(c, "جمع پارچه و مواد", order.fabricPrice.afn(), y, f)
        if (order.workCost > 0) y = PdfKit.kv(c, "جمع خرج کار", order.workCost.afn(), y, f)
        if (order.sewingCost > 0) y = PdfKit.kv(c, "دستمزد دوخت", order.sewingCost.afn(), y, f)

        val gross = if (order.agreedPrice > 0) order.agreedPrice
        else order.fabricPrice + order.workCost + order.sewingCost
        val paid = payments.sumOf { it.amount }
        if (paid != 0L) y = PdfKit.kv(c, "دریافتی تا امروز", paid.afn(), y, f)

        y += 4f
        val due = gross - paid
        y = PdfKit.totalBox(
            c,
            if (due > 0) "قابل پرداخت" else "تسویه‌شده",
            (if (due > 0) due else gross).afn(),
            y, f
        )
        if (paid != 0L && due > 0) {
            y = PdfKit.kv(c, "جمع کل فاکتور", gross.afn(), y, f)
        }

        // ---------- امضا و یادداشت ----------
        newPageIfNeeded(90f)
        y += 10f
        y = PdfKit.signatures(c, y, f, "مهر و امضای فروشنده", "امضای مشتری")
        y += 6f
        PdfKit.note(
            c,
            "کالای فروخته‌شده مطابق مشخصات این فاکتور تحویل می‌گردد. " +
                "برای پیگیری، کد سفارش را همراه داشته باشید.",
            y, f
        )

        PdfKit.drawFooter(c, context, f, pageNo)
        doc.finishPage(page)

        val file = File(ShareUtil.sharedDir(context), "فاکتور-${order.orderCode}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
