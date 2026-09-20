package com.afghanjama.ui.vm

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
