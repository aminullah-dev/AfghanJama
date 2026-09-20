package com.afghanjama.pdf

import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.toPersianDigits
import kotlin.math.abs

/**
 * کارتِ حسابِ مشتری — چیدمانِ مشترک.
 *
 * ماندهٔ حساب **سطر‌به‌سطر** جلو می‌رود تا مشتری ببیند هر رقم از کجا
 * آمده، نه اینکه فقط یک عددِ آخر دستش باشد. همان قاعدهٔ نسخهٔ اندروید.
 *
 * روی رول (`narrow`) صفحه شکسته نمی‌شود — رول انتها ندارد — و ستون‌ها
 * به سه تا کم می‌شوند. این هم از `StatementPdf` آمده.
 */
fun statementSheets(
    data: StatementData,
    shop: ShopInfo,
    measurer: TextMeasurer,
    paper: Paper = Paper.A4
): SheetDoc {
    val pages = mutableListOf<Sheet>()
    var b = SheetBuilder(paper)
    var c = DocChrome(b, paper, measurer, shop)
    var pageNo = 1
    val title = "کارت حساب"
    var y = c.header(title, data.customerName, data.at)

    fun breakIfNeeded(needed: Float) {
        if (paper.narrow || y + needed <= paper.bodyBottom) return
        c.footer(pageNo)
        pages += b.build()
        pageNo++
        b = SheetBuilder(paper)
        c = DocChrome(b, paper, measurer, shop)
        y = c.header("$title (ادامه)", data.customerName, data.at)
    }

    // ---------- مشتری ----------
    y = c.section("مشتری", y)
    y = c.kv("نام", data.customerName, y)
    if (data.customerPhone.isNotBlank()) {
        y = c.kv("تلفن", data.customerPhone.toPersianDigits(), y)
    }
    y += 4f

    // ---------- گردشِ حساب ----------
    val cols = statementColumns(paper)
    y = c.section("گردش حساب", y)
    y = c.tableHeader(cols.titles, cols.weights, y)

    var running = 0L
    data.rows.forEachIndexed { i, r ->
        breakIfNeeded(24f)
        running += r.debit - r.credit
        val cells = if (paper.narrow) {
            listOf(r.title, (r.debit - r.credit).afn(), running.afn())
        } else {
            listOf(
                r.date,
                r.title,
                if (r.debit > 0) r.debit.afn() else "—",
                if (r.credit > 0) r.credit.afn() else "—",
                running.afn()
            )
        }
        y = c.tableRow(cells, cols.weights, y, zebra = i % 2 == 1)
    }

    y = c.rule(y + 2f)

    // ---------- جمع ----------
    breakIfNeeded(90f)
    y = c.kv("جمعِ بدهکار", data.totalDebit.afn(), y)
    y = c.kv("جمعِ پرداختی", data.totalCredit.afn(), y)
    c.kv(
        when {
            data.balance > 0 -> "مانده (بدهیِ مشتری)"
            data.balance < 0 -> "مانده (بستانکاریِ مشتری)"
            else -> "مانده"
        },
        abs(data.balance).afn(),
        y
    )

    c.footer(pageNo)
    pages += b.build()
    return SheetDoc(pages)
}
