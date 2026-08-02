package com.afghanjama.pdf

import android.content.Context
import android.graphics.pdf.PdfDocument
import com.afghanjama.pdf.PdfKit.BODY_BOTTOM
import com.afghanjama.pdf.PdfKit.PAGE_H
import com.afghanjama.pdf.PdfKit.PAGE_W
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.BalanceSheet
import com.afghanjama.ui.vm.IncomeStatement
import com.afghanjama.ui.vm.StatementLine
import com.afghanjama.util.ShareUtil
import java.io.File

/**
 * صورت‌های مالیِ رسمیِ کارگاه در یک PDF: صورتِ سود و زیانِ دوره +
 * ترازنامهٔ لحظه‌ای، هر دو از ژورنالِ دوطرفه — با سربرگ و پاصفحهٔ
 * مشترک. قابلِ ارائه به شریک، بانک یا حسابدار.
 */
object FinancialStatementsPdf {

    fun create(
        context: Context,
        income: IncomeStatement,
        sheet: BalanceSheet,
        periodLabel: String
    ): File {
        val f = PdfKit.fonts(context)
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var c = page.canvas
        var y = PdfKit.drawHeader(c, context, f, "صورت‌های مالی", periodLabel)

        fun newPageIfNeeded(needed: Float) {
            if (y + needed <= BODY_BOTTOM) return
            PdfKit.drawFooter(c, context, f, pageNo)
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            c = page.canvas
            y = PdfKit.drawHeader(c, context, f, "صورت‌های مالی (ادامه)", periodLabel)
        }

        fun lines(list: List<StatementLine>, emptyText: String) {
            if (list.isEmpty()) {
                newPageIfNeeded(20f)
                y = PdfKit.note(c, emptyText, y, f)
            } else list.forEach {
                newPageIfNeeded(22f)
                y = PdfKit.kv(c, "${it.label} (${it.code})", it.amount.afn(), y, f)
            }
        }

        fun sub(title: String) {
            newPageIfNeeded(26f)
            y = PdfKit.kv(c, title, "", y, f, strong = true)
        }

        // ================= صورتِ سود و زیان =================
        y = PdfKit.section(c, "صورتِ سود و زیان — $periodLabel", y, f)
        lines(income.revenues, "درآمدی در این دوره ثبت نشده است.")
        newPageIfNeeded(24f)
        y = PdfKit.kv(c, "جمعِ درآمد", income.totalRevenue.afn(), y, f, strong = true)
        y = PdfKit.kv(c, "کسر: بهای تمام‌شدهٔ فروش", income.cogs.afn(), y, f)
        y = PdfKit.rule(c, y + 2f)
        y = PdfKit.kv(
            c, "سودِ ناخالص", income.grossProfit.afn(), y, f,
            strong = true, danger = income.grossProfit < 0
        )

        sub("هزینه‌های عملیاتی")
        lines(income.expenses, "هزینه‌ای در این دوره ثبت نشده است.")
        newPageIfNeeded(24f)
        y = PdfKit.kv(c, "جمعِ هزینه‌ها", income.totalExpense.afn(), y, f, strong = true)

        newPageIfNeeded(60f)
        y += 4f
        y = PdfKit.totalBox(
            c,
            if (income.netProfit >= 0) "سودِ خالصِ دوره" else "زیانِ خالصِ دوره",
            income.netProfit.afn(), y, f
        )
        if (income.totalRevenue > 0) {
            y = PdfKit.note(
                c, "حاشیهٔ سودِ خالص: ${income.marginPercent}٪".toPersianDigits(), y, f
            )
        }

        // ================= ترازنامه =================
        newPageIfNeeded(120f)
        y += 10f
        y = PdfKit.section(
            c, "ترازنامه — تا ${PersianDate.short(System.currentTimeMillis())}", y, f
        )

        sub("دارایی‌ها")
        lines(sheet.assets, "دارایی ثبت نشده است.")
        newPageIfNeeded(24f)
        y = PdfKit.kv(c, "جمعِ دارایی‌ها", sheet.totalAssets.afn(), y, f, strong = true)

        sub("بدهی‌ها")
        lines(sheet.liabilities, "بدهی ثبت نشده است.")
        newPageIfNeeded(24f)
        y = PdfKit.kv(c, "جمعِ بدهی‌ها", sheet.totalLiabilities.afn(), y, f, strong = true)

        sub("سرمایه")
        newPageIfNeeded(70f)
        y = PdfKit.kv(c, "سرمایهٔ اولیه", sheet.capital.afn(), y, f)
        y = PdfKit.kv(c, "سودِ انباشته", sheet.retained.afn(), y, f, danger = sheet.retained < 0)
        y = PdfKit.kv(c, "جمعِ سرمایه", sheet.totalEquity.afn(), y, f, strong = true)
        y = PdfKit.rule(c, y + 2f)
        y = PdfKit.kv(
            c, "جمعِ بدهی‌ها و سرمایه",
            (sheet.totalLiabilities + sheet.totalEquity).afn(), y, f, strong = true
        )

        newPageIfNeeded(40f)
        y = PdfKit.note(
            c,
            if (sheet.balanced) "✓ ترازنامه متوازن است — دارایی = بدهی + سرمایه"
            else "⚠ ترازنامه متوازن نیست — دفترها را بررسی کنید",
            y + 4f, f
        )

        newPageIfNeeded(70f)
        PdfKit.signatures(c, y + 10f, f, "مهر و امضای کارگاه", "تأیید حسابدار")

        PdfKit.drawFooter(c, context, f, pageNo)
        doc.finishPage(page)

        val file = File(ShareUtil.sharedDir(context), "صورت‌های-مالی.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
