package com.afghanjama.ui.vm

import com.afghanjama.data.dao.AccountBalance
import com.afghanjama.data.entities.Accounts

/** یک سطرِ صورتِ مالی: حساب و مبلغِ طبیعی‌اش (همیشه مثبت‌خوان). */
data class StatementLine(
    val code: String,
    val label: String,
    val amount: Long
)

/**
 * صورتِ سود و زیانِ دوره: درآمد − بهای تمام‌شده = سود ناخالص،
 * و سود ناخالص − هزینه‌های عمومی = سود خالص.
 */
data class IncomeStatement(
    val revenues: List<StatementLine> = emptyList(),
    val totalRevenue: Long = 0,
    val cogs: Long = 0,
    val grossProfit: Long = 0,
    val expenses: List<StatementLine> = emptyList(),
    val totalExpense: Long = 0,
    val netProfit: Long = 0
) {
    val hasData: Boolean get() = totalRevenue != 0L || cogs != 0L || totalExpense != 0L

    /** حاشیهٔ سودِ خالص به درصد (۰ وقتی درآمدی نیست). */
    val marginPercent: Int
        get() = if (totalRevenue > 0) ((netProfit * 100) / totalRevenue).toInt() else 0
}

/**
 * ترازنامه در همین لحظه (همیشه تجمعی — از ابتدای کار تا امروز):
 * دارایی = بدهی + سرمایه + سودِ انباشته.
 */
data class BalanceSheet(
    val assets: List<StatementLine> = emptyList(),
    val totalAssets: Long = 0,
    val liabilities: List<StatementLine> = emptyList(),
    val totalLiabilities: Long = 0,
    val capital: Long = 0,
    val retained: Long = 0
) {
    val totalEquity: Long get() = capital + retained
    val hasData: Boolean
        get() = totalAssets != 0L || totalLiabilities != 0L || totalEquity != 0L

    /** معادلهٔ حسابداری برقرار است؟ (اگر ژورنال تراز باشد همیشه بله) */
    val balanced: Boolean get() = totalAssets == totalLiabilities + totalEquity
}

/*
 * سازندهٔ صورت‌ها — **یک جا**، تا «گزارش‌ها» و داشبوردِ «مالی» از یک
 * فرمول بخوانند. تا امروز این دو تابع خصوصیِ `ReportsViewModel` بودند و
 * داشبورد هزینه را از راهِ دیگری (جمعِ خروجی‌های دسته‌دار) حساب می‌کرد
 * که خریدِ مواد و انتقالِ بینِ صندوق‌ها را هم «هزینه» می‌شمرد.
 */

/** درآمد (۴xxx) بستانکارِ طبیعی است؛ هزینه (۵xxx) بدهکارِ طبیعی. */
fun incomeStatementOf(list: List<AccountBalance>): IncomeStatement {
    val revenues = list
        .filter { it.account.startsWith("4") }
        .map { StatementLine(it.account, Accounts.label(it.account), -it.net) }
        .filter { it.amount != 0L }
    val totalRevenue = revenues.sumOf { it.amount }

    val cogs = list.filter { it.account == Accounts.COGS }.sumOf { it.net }

    val expenses = list
        .filter { it.account.startsWith("5") && it.account != Accounts.COGS }
        .map { StatementLine(it.account, Accounts.label(it.account), it.net) }
        .filter { it.amount != 0L }
    val totalExpense = expenses.sumOf { it.amount }

    val gross = totalRevenue - cogs
    return IncomeStatement(
        revenues = revenues,
        totalRevenue = totalRevenue,
        cogs = cogs,
        grossProfit = gross,
        expenses = expenses,
        totalExpense = totalExpense,
        netProfit = gross - totalExpense
    )
}

/** دارایی (۱xxx) بدهکارِ طبیعی؛ بدهی (۲xxx) و سرمایه (۳xxx) بستانکارِ طبیعی. */
fun balanceSheetOf(list: List<AccountBalance>): BalanceSheet {
    val assets = list
        .filter { it.account.startsWith("1") }
        .map { StatementLine(it.account, Accounts.label(it.account), it.net) }
        .filter { it.amount != 0L }
    val liabilities = list
        .filter { it.account.startsWith("2") }
        .map { StatementLine(it.account, Accounts.label(it.account), -it.net) }
        .filter { it.amount != 0L }
    val capital = list.filter { it.account.startsWith("3") }.sumOf { -it.net }
    // سودِ انباشته از ابتدای کار = کلِ درآمد − کلِ هزینه (شاملِ بهای تمام‌شده)
    val retained = list.filter { it.account.startsWith("4") }.sumOf { -it.net } -
        list.filter { it.account.startsWith("5") }.sumOf { it.net }

    return BalanceSheet(
        assets = assets,
        totalAssets = assets.sumOf { it.amount },
        liabilities = liabilities,
        totalLiabilities = liabilities.sumOf { it.amount },
        capital = capital,
        retained = retained
    )
}
