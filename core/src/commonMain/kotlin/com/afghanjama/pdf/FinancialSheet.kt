package com.afghanjama.pdf

import com.afghanjama.util.nowMillis
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.BalanceSheet
import com.afghanjama.ui.vm.IncomeStatement
import com.afghanjama.ui.vm.StatementLine

/**
 * صورت‌های مالی — سود و زیانِ دوره، و ترازنامه تا امروز.
 *
 * بلندترین سندِ اپ و همیشه چندبرگه‌ای. سطرِ آخرش («ترازنامه متوازن
 * است؟») مهم‌ترین جمله است: اگر متوازن نباشد یعنی جایی در دفتر
 * اشکالی هست، و کارفرما باید همان‌جا ببیندش نه در گزارشی جدا.
 */
fun financialSheets(
    income: IncomeStatement,
    balance: BalanceSheet,
    periodLabel: String,
    shop: ShopInfo,
    measurer: TextMeasurer,
    now: Long = nowMillis(),
    paper: Paper = Paper.A4
): SheetDoc {
    val pages = mutableListOf<Sheet>()
    var b = SheetBuilder(paper)
    var c = DocChrome(b, paper, measurer, shop)
    var pageNo = 1
    var y = c.header("صورت‌های مالی", periodLabel, now)

    fun breakIfNeeded(needed: Float) {
        if (y + needed <= paper.bodyBottom) return
        c.footer(pageNo)
        pages += b.build()
        pageNo++
        b = SheetBuilder(paper)
        c = DocChrome(b, paper, measurer, shop)
        y = c.header("صورت‌های مالی (ادامه)", periodLabel, now)
    }

    fun lines(list: List<StatementLine>, emptyText: String) {
        if (list.isEmpty()) {
            breakIfNeeded(20f)
            y = c.note(emptyText, y)
        } else list.forEach {
            breakIfNeeded(22f)
            y = c.kv("${it.label} (${it.code})", it.amount.afn(), y)
        }
    }

    fun sub(title: String) {
        breakIfNeeded(26f)
        y = c.kv(title, "", y, strong = true)
    }

    // ---------- سود و زیان ----------
    y = c.section("صورتِ سود و زیان — $periodLabel", y)
    lines(income.revenues, "درآمدی در این دوره ثبت نشده است.")
    breakIfNeeded(24f)
    y = c.kv("جمعِ درآمد", income.totalRevenue.afn(), y, strong = true)
    y = c.kv("کسر: بهای تمام‌شدهٔ فروش", income.cogs.afn(), y)
    y = c.rule(y + 2f)
    y = c.kv(
        "سودِ ناخالص", income.grossProfit.afn(), y,
        strong = true, danger = income.grossProfit < 0
    )

    sub("هزینه‌های عملیاتی")
    lines(income.expenses, "هزینه‌ای در این دوره ثبت نشده است.")
    breakIfNeeded(24f)
    y = c.kv("جمعِ هزینه‌ها", income.totalExpense.afn(), y, strong = true)

    breakIfNeeded(60f)
    y += 4f
    y = c.totalBox(
        if (income.netProfit >= 0) "سودِ خالصِ دوره" else "زیانِ خالصِ دوره",
        income.netProfit.afn(), y
    )
    if (income.totalRevenue > 0) {
        y = c.note("حاشیهٔ سودِ خالص: ${income.marginPercent}٪".toPersianDigits(), y)
    }

    // ---------- ترازنامه ----------
    breakIfNeeded(120f)
    y += 10f
    y = c.section("ترازنامه — تا ${PersianDate.short(now)}", y)

    sub("دارایی‌ها")
    lines(balance.assets, "دارایی ثبت نشده است.")
    breakIfNeeded(24f)
    y = c.kv("جمعِ دارایی‌ها", balance.totalAssets.afn(), y, strong = true)

    sub("بدهی‌ها")
    lines(balance.liabilities, "بدهی ثبت نشده است.")
    breakIfNeeded(24f)
    y = c.kv("جمعِ بدهی‌ها", balance.totalLiabilities.afn(), y, strong = true)

    sub("سرمایه")
    breakIfNeeded(70f)
    y = c.kv("سرمایهٔ اولیه", balance.capital.afn(), y)
    y = c.kv("سودِ انباشته", balance.retained.afn(), y, danger = balance.retained < 0)
    y = c.kv("جمعِ سرمایه", balance.totalEquity.afn(), y, strong = true)
    y = c.rule(y + 2f)
    y = c.kv(
        "جمعِ بدهی‌ها و سرمایه",
        (balance.totalLiabilities + balance.totalEquity).afn(), y, strong = true
    )

    breakIfNeeded(40f)
    y = c.note(
        if (balance.balanced) "✓ ترازنامه متوازن است — دارایی = بدهی + سرمایه"
        else "⚠ ترازنامه متوازن نیست — دفترها را بررسی کنید",
        y + 4f
    )

    breakIfNeeded(70f)
    c.signatures(y + 10f, "مهر و امضای کارگاه", "تأیید حسابدار")

    c.footer(pageNo)
    pages += b.build()
    return SheetDoc(pages)
}
