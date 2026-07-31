package com.afghanjama.pdf

import android.content.Context
import android.graphics.pdf.PdfDocument
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.toPersianDigits
import java.io.File

/** یک سطرِ حسابِ مشتری. */
data class StatementRow(
    val date: String,
    val title: String,
    /** بدهکار — چیزی که مشتری برداشته. */
    val debit: Long = 0L,
    /** بستانکار — چیزی که پرداخته. */
    val credit: Long = 0L
)

data class StatementData(
    val customerName: String,
    val customerPhone: String = "",
    val at: Long = System.currentTimeMillis(),
    val rows: List<StatementRow>
) {
    val totalDebit: Long get() = rows.sumOf { it.debit }
    val totalCredit: Long get() = rows.sumOf { it.credit }

    /** مثبت یعنی مشتری بدهکار است، منفی یعنی نزدِ ما پول دارد. */
    val balance: Long get() = totalDebit - totalCredit
}

/**
 * کارتِ حسابِ مشتری — برگه‌ای که برای یادآوریِ بدهی فرستاده می‌شود.
 *
 * دفترِ مشتری در اپ بود، ولی وقتی کسی بدهکار می‌مانْد چیزی برای فرستادن
 * وجود نداشت و پیگیری شفاهی انجام می‌شد. وصولِ مطالبات کارِ روزمرهٔ هر
 * کارگاه است.
 *
 * همان موتور و همان هدر و فوترِ فاکتور استفاده می‌شود تا دو چیدمانِ جدا
 * نداشته باشیم که روزی یکی درست شود و دیگری عقب بماند.
 */
object StatementPdf {

    fun create(
        context: Context,
        data: StatementData,
        paper: Paper = Paper.A4,
        fileName: String = "statement.pdf"
    ): File {
        val f = PdfKit.fonts(context)
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(paper.w, paper.h, pageNo).create())
        var c = page.canvas

        val title = "کارت حساب"
        var y = PdfKit.drawHeader(c, context, f, title, data.customerName, data.at, paper)

        fun newPageIfNeeded(needed: Float) {
            if (paper.narrow || y + needed <= paper.bodyBottom) return
            PdfKit.drawFooter(c, context, f, pageNo, paper = paper)
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(paper.w, paper.h, pageNo).create())
            c = page.canvas
            y = PdfKit.drawHeader(c, context, f, "$title (ادامه)", data.customerName, data.at, paper)
        }

        // ---------- مشتری ----------
        y = PdfKit.section(c, "مشتری", y, f, paper)
        y = PdfKit.kv(c, "نام", data.customerName, y, f, paper = paper)
        if (data.customerPhone.isNotBlank()) {
            y = PdfKit.kv(c, "تلفن", data.customerPhone.toPersianDigits(), y, f, paper = paper)
        }
        y += 4f

        // ---------- گردشِ حساب ----------
        val cols = statementColumns(paper)
        y = PdfKit.section(c, "گردش حساب", y, f, paper)
        y = PdfKit.tableHeader(c, cols.titles, cols.weights, y, f, paper)

        // مانده سطر‌به‌سطر جلو می‌رود تا مشتری ببیند هر رقم از کجا آمده،
        // نه اینکه فقط یک عددِ آخر دستش باشد.
        var running = 0L
        data.rows.forEachIndexed { i, r ->
            newPageIfNeeded(24f)
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
            y = PdfKit.tableRow(c, cells, cols.weights, y, f, i % 2 == 1, paper)
        }

        y = PdfKit.rule(c, y + 2f, paper)

        // ---------- جمع ----------
        newPageIfNeeded(90f)
        y = PdfKit.kv(c, "جمعِ بدهکار", data.totalDebit.afn(), y, f, paper = paper)
        y = PdfKit.kv(c, "جمعِ پرداختی", data.totalCredit.afn(), y, f, paper = paper)
        y = PdfKit.kv(
            c,
            when {
                data.balance > 0 -> "مانده (بدهیِ مشتری)"
                data.balance < 0 -> "مانده (بستانکاریِ مشتری)"
                else -> "مانده"
            },
            kotlin.math.abs(data.balance).afn(),
            y, f, paper = paper
        )

        PdfKit.drawFooter(c, context, f, pageNo, paper = paper)
        doc.finishPage(page)

        val out = File(com.afghanjama.util.ShareUtil.sharedDir(context), fileName)
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }
}

/**
 * ستون‌های کارتِ حساب.
 *
 * روی رولِ حرارتی جا برای پنج ستون نیست؛ آنجا فقط شرح، مبلغ و مانده
 * می‌مانند — همان چیزی که مشتری واقعاً نگاه می‌کند.
 */
fun statementColumns(paper: Paper): InvoiceColumns = when {
    paper.narrow -> InvoiceColumns(
        listOf("شرح", "مبلغ", "مانده"),
        listOf(2.4f, 1.3f, 1.3f)
    )
    else -> InvoiceColumns(
        listOf("تاریخ", "شرح", "بدهکار", "بستانکار", "مانده"),
        listOf(1.2f, 2.6f, 1.2f, 1.2f, 1.3f)
    )
}
