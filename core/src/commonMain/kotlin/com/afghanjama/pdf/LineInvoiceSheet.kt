package com.afghanjama.pdf

import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.afnBare
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import kotlin.math.abs

/**
 * فاکتورِ چندردیفی — یک چیدمان که هم فروش را چاپ می‌کند هم خرید.
 *
 * ستون‌ها با اندازهٔ کاغذ کم و زیاد می‌شوند: روی رول سه ستون، روی A5
 * پنج، و روی A4 هفت. `invoiceColumns` در `Paper.kt` این را می‌داند و
 * از قبل هم مشترک بود.
 */
fun lineInvoiceSheets(
    data: InvoiceData,
    shop: ShopInfo,
    measurer: TextMeasurer,
    paper: Paper = Paper.A4
): SheetDoc {
    val pages = mutableListOf<Sheet>()
    var b = SheetBuilder(paper)
    var c = DocChrome(b, paper, measurer, shop)
    var pageNo = 1
    var y = c.header(data.title, data.number, data.at)

    fun breakIfNeeded(needed: Float) {
        if (paper.narrow || y + needed <= paper.bodyBottom) return
        c.footer(pageNo)
        pages += b.build()
        pageNo++
        b = SheetBuilder(paper)
        c = DocChrome(b, paper, measurer, shop)
        y = c.header("${data.title} (ادامه)", data.number, data.at)
    }

    if (data.partyName.isNotBlank()) {
        y = c.section(data.partyLabel, y)
        y = c.kv("نام", data.partyName, y)
        if (data.partyPhone.isNotBlank()) {
            y = c.kv("تلفن", data.partyPhone.toPersianDigits(), y)
        }
        y += 4f
    }

    val cols = invoiceColumns(paper)
    y = c.section("کالا یا خدمات", y)
    y = c.tableHeader(cols.titles, cols.weights, y)

    data.lines.forEachIndexed { i, line ->
        breakIfNeeded(24f)
        y = c.tableRow(rowCells(line, i + 1, paper), cols.weights, y, zebra = i % 2 == 1)
    }

    breakIfNeeded(30f)
    y = c.tableRow(totalCells(data, paper), cols.weights, y)
    y = c.rule(y + 2f)

    breakIfNeeded(120f)
    if (data.discount > 0) y = c.kv("تخفیف", data.discount.afn(), y)
    if (data.paid != 0L) y = c.kv("پرداخت", data.paid.afn(), y)
    if (data.previousDue != 0L) {
        y = c.kv(
            if (data.previousDue > 0) "بدهی قبلی" else "بستانکاریِ قبلی",
            abs(data.previousDue).afn(), y,
            danger = data.previousDue > 0
        )
    }

    y += 4f
    y = c.totalBox(
        if (data.payable > 0) "مبلغ قابل پرداخت" else "تسویه‌شده",
        abs(data.payable).afn(),
        y
    )

    // روی رول جای امضا نیست و پرینترِ حرارتی هم کاغذ را هدر می‌دهد.
    if (!paper.narrow) {
        breakIfNeeded(110f)
        y = c.signatures(y, "مهر و امضای فروشنده", "امضای خریدار")
        y += 6f
    }

    breakIfNeeded(50f)
    c.boxedNote(
        listOfNotNull(
            data.note.takeIf { it.isNotBlank() },
            "ستاسو له اعتماده مننه — بیا هم راشئ!",
            "از اعتماد شما سپاسگزاریم — همیشه در خدمت‌تان هستیم."
        ),
        y
    )

    c.footer(pageNo)
    pages += b.build()
    return SheetDoc(pages)
}

/** خانه‌های یک ردیف، متناسب با ستون‌هایی که این کاغذ دارد. */
private fun rowCells(line: InvoiceLine, rowNo: Int, paper: Paper): List<String> = when {
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
