package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.AppInfo
import com.afghanjama.data.CashPolicy
import com.afghanjama.data.dao.PartyBalance
import com.afghanjama.data.entities.Accounts
import com.afghanjama.data.entities.FinishedSale
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.Transaction
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.PersianDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** سودِ برآوردیِ یک سفارش = قیمت توافقی − بهای تمام‌شده. */
data class OrderProfit(
    val code: String,
    val title: String,
    val agreed: Long,
    val cost: Long,
    val at: Long
) {
    val profit: Long get() = agreed - cost
}

data class ReportData(
    val wallet: Long = 0,
    val bank: Long = 0,
    val profitBox: Long = 0,

    val salesCount: Int = 0,
    val revenue: Long = 0,
    val cogs: Long = 0,
    val grossProfit: Long = 0,

    val incomeTotal: Long = 0,
    val expenseTotal: Long = 0,
    val expenseByCategory: List<Pair<String, Long>> = emptyList(),

    /**
     * جابه‌جاییِ پول بینِ صندوق‌های خودِ کارگاه در این بازه.
     *
     * از ورودی و خروجیِ نقد بیرون گذاشته شده چون پولی وارد یا خارجِ کارگاه
     * نمی‌کند — ولی ناپدید هم نمی‌شود، وگرنه کاربر نمی‌فهمد چرا جمعِ
     * تراکنش‌ها با گزارش نمی‌خوانَد. فقط یک سمتِ هر جفت شمرده می‌شود.
     */
    val internalMoves: Long = 0,

    val receivable: Long = 0,
    val payable: Long = 0,
    val debtorCount: Int = 0,
    val creditorCount: Int = 0,

    val orderProfits: List<OrderProfit> = emptyList()
)

private data class RawData(
    val orders: List<Order>,
    val tx: List<Transaction>,
    val sales: List<FinishedSale>,
    val ledger: List<PartyBalance>
)

/** یک سطرِ ترازِ آزمایشی: حساب و ماندهٔ طبیعی‌اش. */
data class AccountRow(
    val code: String,
    val label: String,
    /** ماندهٔ نمایشی: دارایی/هزینه = بدهکار−بستانکار؛ بقیه برعکس. */
    val shown: Long
)

data class TrialBalance(
    val rows: List<AccountRow> = emptyList(),
    val totalDebit: Long = 0,
    val totalCredit: Long = 0
) {
    val hasData: Boolean get() = totalDebit > 0 || totalCredit > 0
    val balanced: Boolean get() = totalDebit == totalCredit
}

/**
 * بازهٔ گزارش. [from] شاملِ خودش و [to] شاملِ کلِ آن روز است.
 * پیش‌فرض «از ابتدا تا امروز» — یعنی هیچ کرانی.
 */
data class ReportRange(
    val from: Long = 0L,
    val to: Long = Long.MAX_VALUE,
    val label: String = "از ابتدا تا امروز",
    /** اگر از چیپِ آماده آمده باشد، تعدادِ روزش — برای هایلایتِ چیپ. */
    val presetDays: Int? = null,
    val isCustom: Boolean = false
) {
    operator fun contains(at: Long): Boolean = at in from..to

    companion object {
        fun all() = ReportRange()

        fun lastDays(days: Int) = ReportRange(
            from = System.currentTimeMillis() - days * 24L * 3600L * 1000L,
            label = "${days} روز اخیر",
            presetDays = days
        )

        fun custom(from: Long, to: Long, label: String) =
            ReportRange(from = from, to = to, label = label, isCustom = true)
    }
}

/** سودآوریِ یک محصول در بازهٔ گزارش (از فروش‌های انبارِ محصول). */
data class ProductProfit(
    val name: String,
    val qty: Int,
    val revenue: Long,
    val cost: Long
) {
    val profit: Long get() = revenue - cost
    val marginPercent: Int get() = if (revenue > 0) ((profit * 100) / revenue).toInt() else 0
}

/** یک ماهِ شمسی در روندِ کسب‌وکار. */
data class MonthPoint(
    val key: String,
    val label: String,
    val revenue: Long,
    val cost: Long,
    val salesCount: Int
) {
    val profit: Long get() = revenue - cost
}

/**
 * روندِ ماهانه و سودآوریِ محصولات — پاسخِ دو سؤالی که مدیرِ کارگاه
 * هر روز دارد: «کدام محصول پول می‌سازد؟» و «کارم بهتر شده یا بدتر؟»
 */
data class TrendData(
    val months: List<MonthPoint> = emptyList(),
    val products: List<ProductProfit> = emptyList(),
    /**
     * تغییرِ سود نسبت به **همین بازه** در ماهِ قبل (نه کلِ ماهِ قبل).
     * مقایسهٔ «۵ روزِ گذشتهٔ این ماه» با «کلِ ماهِ قبل» گمراه‌کننده بود،
     * پس ماهِ قبل هم تا همین روزِ ماه بریده می‌شود. null = قابلِ مقایسه نیست.
     */
    val profitChangePercent: Int? = null,
    /** روزِ جاریِ ماهِ شمسی — برای توضیحِ صادقانهٔ بازهٔ مقایسه. */
    val dayOfMonth: Int = 0
) {
    val hasMonths: Boolean get() = months.any { it.revenue > 0 }
    val hasProducts: Boolean get() = products.isNotEmpty()

    /** بیشترین درآمدِ ماهانه — مقیاسِ مشترکِ میله‌ها. */
    val maxMonthRevenue: Long get() = months.maxOfOrNull { it.revenue } ?: 0
    val maxProductRevenue: Long get() = products.maxOfOrNull { it.revenue } ?: 0

    val bestProduct: ProductProfit? get() = products.maxByOrNull { it.profit }

}

/*
 * `StatementLine`, `IncomeStatement` و `BalanceSheet` به `:core` رفتند.
 * خالص بودند و فقط چون اینجا نشسته بودند، چیدمانِ صورت‌های مالی
 * نمی‌توانست مشترک شود. بسته یکی است، پس ایمپورتی عوض نشد.
 */


/** گزارش‌های مدیریتی: محاسبه از داده‌های موجود (بدون جدول جدید). */
class ReportsViewModel(private val repo: Repo) : ViewModel() {

    /** ترازِ آزمایشیِ ژورنالِ دوطرفه. */
    val trialBalance: StateFlow<TrialBalance> =
        repo.observeAccountBalances().map { list ->
            TrialBalance(
                rows = list.map { b ->
                    val natural =
                        if (b.account.startsWith("1") || b.account.startsWith("5")) b.net
                        else -b.net
                    AccountRow(b.account, Accounts.label(b.account), natural)
                },
                totalDebit = list.sumOf { it.debit },
                totalCredit = list.sumOf { it.credit }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrialBalance())

    /** بازهٔ گزارش: پیش‌فرض «از ابتدا تا امروز». */
    private val _range = MutableStateFlow(ReportRange.all())
    val range: StateFlow<ReportRange> = _range

    fun setPeriod(days: Int?) {
        _range.value = if (days == null) ReportRange.all() else ReportRange.lastDays(days)
    }

    /** بازهٔ دلخواه با تاریخِ شمسی. */
    fun setCustomRange(from: Long, to: Long, label: String) {
        _range.value = ReportRange.custom(from, to, label)
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    /**
     * خروجیِ اکسل (CSV) از گزارش‌های همین بازه — صورتِ سود و زیان،
     * ترازنامه، سودآوریِ محصولات و روندِ ماهانه در یک فایل.
     *
     * فقط **متن** می‌سازد و هیچ فایلی نمی‌نویسد. تا دیروز همین‌جا مستقیم
     * در `OutputStream`ِ اندروید نوشته می‌شد و همان یک خط بود که این
     * ViewModel را در `:app` نگه می‌داشت؛ حالا نوشتن کارِ `FileExport`ِ
     * هر سکوست و این تابع روی هر دو یکی است.
     *
     * جداکننده «،» نیست بلکه «,» است، و BOM را نویسندهٔ فایل می‌گذارد —
     * بدونش اکسل متنِ فارسی را به‌هم‌ریخته نشان می‌دهد.
     */
    fun csvText(): String = buildString {
        run {
            val r = range.value
            val inc = incomeStatement.value
            val bs = balanceSheet.value
            val tr = trend.value

            fun row(vararg cells: String) =
                appendLine(cells.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" })

            row("گزارش‌های ${AppInfo.NAME}")
            row("بازه", r.label)
            row("تاریخ خروجی", PersianDate.csv(System.currentTimeMillis()))
            row("")

            row("صورت سود و زیان")
            row("حساب", "کد", "مبلغ")
            inc.revenues.forEach { row(it.label, it.code, it.amount.toString()) }
            row("جمع درآمد", "", inc.totalRevenue.toString())
            row("بهای تمام‌شده فروش", "", inc.cogs.toString())
            row("سود ناخالص", "", inc.grossProfit.toString())
            inc.expenses.forEach { row(it.label, it.code, it.amount.toString()) }
            row("جمع هزینه‌ها", "", inc.totalExpense.toString())
            row("سود خالص", "", inc.netProfit.toString())
            row("حاشیه سود (٪)", "", inc.marginPercent.toString())
            row("")

            row("ترازنامه")
            row("گروه", "حساب", "کد", "مبلغ")
            bs.assets.forEach { row("دارایی", it.label, it.code, it.amount.toString()) }
            row("", "جمع دارایی‌ها", "", bs.totalAssets.toString())
            bs.liabilities.forEach { row("بدهی", it.label, it.code, it.amount.toString()) }
            row("", "جمع بدهی‌ها", "", bs.totalLiabilities.toString())
            row("سرمایه", "سرمایه اولیه", "", bs.capital.toString())
            row("سرمایه", "سود انباشته", "", bs.retained.toString())
            row("", "جمع سرمایه", "", bs.totalEquity.toString())
            row("", "متوازن است؟", "", if (bs.balanced) "بله" else "خیر")
            row("")

            row("سودآوری محصولات")
            row("محصول", "تعداد", "درآمد", "بهای تمام‌شده", "سود", "حاشیه (٪)")
            tr.products.forEach {
                row(
                    it.name, it.qty.toString(), it.revenue.toString(),
                    it.cost.toString(), it.profit.toString(), it.marginPercent.toString()
                )
            }
            row("")

            row("روند ماهانه")
            row("ماه", "تعداد فروش", "درآمد", "بهای تمام‌شده", "سود")
            tr.months.forEach {
                row(
                    it.label, it.salesCount.toString(), it.revenue.toString(),
                    it.cost.toString(), it.profit.toString()
                )
            }
            row("")

            row("سود هر سفارش")
            row("کد سفارش", "طرح", "قیمت توافقی", "بهای تمام‌شده", "سود")
            report.value.orderProfits.forEach {
                row(
                    it.code, it.title, it.agreed.toString(),
                    it.cost.toString(), it.profit.toString()
                )
            }
        }
    }

    /** پس از ذخیرهٔ موفق، از خودِ صفحه صدا زده می‌شود. */
    fun csvSaved() {
        _message.value = "✅ خروجی اکسل ذخیره شد."
    }

    /**
     * صورتِ سود و زیانِ دوره — با چیپِ بازه (۷/۳۰ روز/همه) هماهنگ است،
     * چون فقط اسنادِ همان بازه را جمع می‌زند.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val incomeStatement: StateFlow<IncomeStatement> =
        _range
            .flatMapLatest { r -> repo.observeAccountBalancesBetween(r.from, r.to) }
            .map { buildIncome(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomeStatement())

    /**
     * روندِ ۶ ماهِ اخیر + سودآوریِ محصولات. روند عمداً به چیپِ بازه وابسته
     * نیست (روند یعنی مقایسهٔ ماه‌ها)، اما فهرستِ محصولات بازه را رعایت می‌کند.
     */
    val trend: StateFlow<TrendData> =
        combine(repo.observeFinishedSales(), _range) { sales, range ->
            val monthsWanted = PersianDate.recentMonths(6)
            val byMonth = sales.groupBy { PersianDate.monthKey(it.createdAt) }
            val months = monthsWanted.map { (key, label) ->
                val list = byMonth[key].orEmpty()
                MonthPoint(
                    key = key,
                    label = label,
                    revenue = list.sumOf { it.total },
                    cost = list.sumOf { it.cost },
                    salesCount = list.size
                )
            }

            val products = sales
                .filter { it.createdAt in range }
                .groupBy { it.productName.ifBlank { "بدون نام" } }
                .map { (name, list) ->
                    ProductProfit(
                        name = name,
                        qty = list.sumOf { it.qty },
                        revenue = list.sumOf { it.total },
                        cost = list.sumOf { it.cost }
                    )
                }
                .sortedByDescending { it.profit }

            // مقایسهٔ منصفانه: ماهِ جاری تا امروز، در برابرِ ماهِ قبل تا همین روز
            val today = PersianDate.dayOfMonth(System.currentTimeMillis())
            val change: Int? = if (monthsWanted.size < 2) null else {
                val prevKey = monthsWanted[monthsWanted.size - 2].first
                val prevSoFar = byMonth[prevKey].orEmpty()
                    .filter { PersianDate.dayOfMonth(it.createdAt) <= today }
                val prevProfit = prevSoFar.sumOf { it.total } - prevSoFar.sumOf { it.cost }
                val currProfit = months.last().profit
                if (prevProfit <= 0L) null
                else (((currProfit - prevProfit) * 100) / prevProfit).toInt()
            }

            TrendData(
                months = months,
                products = products,
                profitChangePercent = change,
                dayOfMonth = today
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendData())

    /** ترازنامه — همیشه تجمعی (عکسِ لحظه‌ای از وضعِ مالی). */
    val balanceSheet: StateFlow<BalanceSheet> =
        repo.observeAccountBalances()
            .map { buildSheet(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BalanceSheet())

    /** درآمد (۴xxx) بستانکارِ طبیعی است؛ هزینه (۵xxx) بدهکارِ طبیعی. */
    private fun buildIncome(list: List<com.afghanjama.data.dao.AccountBalance>): IncomeStatement {
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
    private fun buildSheet(list: List<com.afghanjama.data.dao.AccountBalance>): BalanceSheet {
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

    private val raw =
        combine(
            repo.observeAllOrders(),
            repo.observeTx(),
            repo.observeFinishedSales(),
            repo.observeLedgerBalances()
        ) { o, t, s, l -> RawData(o, t, s, l) }

    private val balances =
        combine(
            repo.observeWalletBalance(),
            repo.observeBankBalance(),
            repo.observeProfitBalance()
        ) { w, b, p -> Triple(w, b, p) }

    val report: StateFlow<ReportData> =
        combine(raw, balances, _range) { r, bal, range ->
            build(r, bal, range)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportData())

    private fun build(r: RawData, bal: Triple<Long, Long, Long>, range: ReportRange): ReportData {
        val sales = r.sales.filter { it.createdAt in range }
        val revenue = sales.sumOf { it.total }
        val cogs = sales.sumOf { it.cost }

        val allTx = r.tx.filter { it.createdAt in range }
        // جابه‌جاییِ بینِ صندوق‌های خودمان پولِ کارگاه را کم یا زیاد نمی‌کند،
        // پس نه ورودیِ نقد است نه خروجیِ نقد. تا پیش از این شمرده می‌شد و
        // سودِ هر فروش را در فهرستِ هزینه‌ها زیرِ «سایر» نشان می‌داد.
        val tx = allTx.filterNot { CashPolicy.isInternalMove(it.category) }
        val moves = allTx.filter { CashPolicy.isInternalMove(it.category) }
        // فقط یک سمتِ هر جفت، وگرنه مبلغ دو برابر دیده می‌شود
        val internalMoves = moves.filter { it.type == "OUT" }.sumOf { it.amount }

        val income = tx.filter { it.type == "IN" }.sumOf { it.amount }
        val outTx = tx.filter { it.type == "OUT" }
        val expenseTotal = outTx.sumOf { it.amount }
        val byCat = outTx.groupBy { it.category.ifBlank { "سایر" } }
            .map { (cat, list) -> cat to list.sumOf { it.amount } }
            .sortedByDescending { it.second }

        val receivable = r.ledger.filter { it.net > 0 }.sumOf { it.net }
        val payable = r.ledger.filter { it.net < 0 }.sumOf { -it.net }

        val orderProfits = r.orders
            .filter { it.agreedPrice > 0 && it.createdAt in range }
            .map {
                OrderProfit(
                    code = it.orderCode,
                    title = it.designTitle,
                    agreed = it.agreedPrice,
                    cost = it.fabricPrice + it.workCost + it.sewingCost,
                    at = it.createdAt
                )
            }
            .sortedByDescending { it.at }

        return ReportData(
            wallet = bal.first, bank = bal.second, profitBox = bal.third,
            salesCount = sales.size, revenue = revenue, cogs = cogs,
            grossProfit = revenue - cogs,
            incomeTotal = income, expenseTotal = expenseTotal, expenseByCategory = byCat,
            internalMoves = internalMoves,
            receivable = receivable, payable = payable,
            debtorCount = r.ledger.count { it.net > 0 },
            creditorCount = r.ledger.count { it.net < 0 },
            orderProfits = orderProfits
        )
    }
}
