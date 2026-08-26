package com.afghanjama.pdf

import android.content.Context
import android.graphics.pdf.PdfDocument
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.afnBare
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.util.ShareUtil
import java.io.File

/*
 * `InvoiceLine`, `InvoiceData` و `qtyFa` به `:core` رفتند تا چیدمانِ
 * مشترکِ فاکتورِ چندردیفی هم بتواند از آن‌ها استفاده کند.
 */

object LineInvoicePdf {

    fun create(
        context: Context,
        data: InvoiceData,
        paper: Paper = Paper.A4,
        fileName: String = "${data.number}.pdf"
    ): File {
        val f = PdfKit.fonts(context)
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(paper.w, paper.h, pageNo).create())
        var c = page.canvas

        var y = PdfKit.drawHeader(c, context, f, data.title, data.number, data.at, paper)

        fun newPageIfNeeded(needed: Float) {
            // رول ته ندارد؛ صفحهٔ دوم روی کاغذِ حرارتی بی‌معناست
            if (paper.narrow || y + needed <= paper.bodyBottom) return
            PdfKit.drawFooter(c, context, f, pageNo, paper = paper)
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(paper.w, paper.h, pageNo).create())
            c = page.canvas
            y = PdfKit.drawHeader(c, context, f, "${data.title} (ادامه)", data.number, data.at, paper)
        }

        // ---------- طرفِ حساب ----------
        if (data.partyName.isNotBlank()) {
            y = PdfKit.section(c, data.partyLabel, y, f, paper)
            y = PdfKit.kv(c, "نام", data.partyName, y, f, paper = paper)
            if (data.partyPhone.isNotBlank()) {
                y = PdfKit.kv(c, "تلفن", data.partyPhone.toPersianDigits(), y, f, paper = paper)
            }
            y += 4f
        }

        // ---------- اقلام ----------
        val cols = invoiceColumns(paper)
        y = PdfKit.section(c, "کالا یا خدمات", y, f, paper)
        y = PdfKit.tableHeader(c, cols.titles, cols.weights, y, f, paper)

        data.lines.forEachIndexed { i, line ->
            newPageIfNeeded(24f)
            y = PdfKit.tableRow(c, cells(line, i + 1, paper), cols.weights, y, f, i % 2 == 1, paper)
        }

        // سطرِ مجموع، با همان ستون‌بندی تا اعداد زیرِ هم بیفتند
        newPageIfNeeded(30f)
        y = PdfKit.tableRow(c, totalCells(data, paper), cols.weights, y, f, false, paper)
        y = PdfKit.rule(c, y + 2f, paper)

        // ---------- خلاصهٔ پول ----------
        newPageIfNeeded(120f)
        if (data.discount > 0) {
            // جمعِ ردیف‌ها از قبل تخفیف‌خورده است؛ این سطر فقط می‌گوید چقدر
            // کم شده، تا مشتری ببیند چه گرفته.
            y = PdfKit.kv(c, "تخفیف", data.discount.afn(), y, f, paper = paper)
        }
        if (data.paid != 0L) y = PdfKit.kv(c, "پرداخت", data.paid.afn(), y, f, paper = paper)
        if (data.previousDue != 0L) {
            y = PdfKit.kv(
                c,
                if (data.previousDue > 0) "بدهی قبلی" else "بستانکاریِ قبلی",
                kotlin.math.abs(data.previousDue).afn(), y, f,
                danger = data.previousDue > 0, paper = paper
            )
        }
        y += 4f
        y = PdfKit.totalBox(
            c,
            if (data.payable > 0) "مبلغ قابل پرداخت" else "تسویه‌شده",
            kotlin.math.abs(data.payable).afn(),
            y, f, paper
        )

        // ---------- امضا و پیام ----------
        if (!paper.narrow) {
            newPageIfNeeded(110f)
            y = PdfKit.signatures(c, y, f, "مهر و امضای فروشنده", "امضای خریدار", paper)
            y += 6f
        }
        newPageIfNeeded(50f)
        PdfKit.boxedNote(
            c,
            listOfNotNull(
                data.note.takeIf { it.isNotBlank() },
                "ستاسو له اعتماده مننه — بیا هم راشئ!",
                "از اعتماد شما سپاسگزاریم — همیشه در خدمت‌تان هستیم."
            ),
            y, f, paper
        )

        PdfKit.drawFooter(c, context, f, pageNo, paper = paper)
        doc.finishPage(page)

        val file = File(ShareUtil.sharedDir(context), fileName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** خانه‌های یک ردیف، متناسب با ستون‌هایی که این کاغذ دارد. */
    private fun cells(line: InvoiceLine, rowNo: Int, paper: Paper): List<String> = when {
        paper.narrow -> listOf(line.name, line.qtyText, line.total.afnBare())
        paper.w < Paper.A4.w -> listOf(
            line.code, line.name, line.qtyText, line.unitPrice.afnBare(), line.total.afnBare()
        )
        else -> listOf(
            rowNo.fa(), line.code, line.name, line.qtyText,
            line.unit, line.unitPrice.afnBare(), line.total.afnBare()
        )
    }

    private fun totalCells(data: InvoiceData, paper: Paper): List<String> {
        val sum = data.subtotal.afnBare()
        return when {
            paper.narrow -> listOf("مجموع", totalQty(data), sum)
            paper.w < Paper.A4.w -> listOf("", "مجموع", totalQty(data), "", sum)
            else -> listOf("*", "", "مجموع", totalQty(data), "", "", sum)
        }
    }

    /**
     * جمعِ تعداد فقط وقتی نوشته می‌شود که واحدِ همهٔ ردیف‌ها یکی باشد.
     * «۳ متر + ۲ عدد = ۵» عددی است که هیچ معنایی ندارد و کارگاه را سرِ
     * شمارش اشتباه می‌اندازد؛ آنجا خطِ تیره می‌گذاریم.
     */
    private fun totalQty(data: InvoiceData): String {
        if (data.lines.isEmpty()) return ""
        val units = data.lines.map { it.unit.trim() }.distinct()
        if (units.size > 1) return "—"
        return data.lines.sumOf { it.qty }.qtyFa()
    }
}
