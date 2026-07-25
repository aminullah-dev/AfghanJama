package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.dao.PartyBalance
import com.afghanjama.data.entities.Accounts
import com.afghanjama.data.entities.FinishedSale
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.Transaction
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    /** بازهٔ گزارش: null = همه، یا تعداد روزِ اخیر (مثلاً ۳۰). */
    private val _period = MutableStateFlow<Int?>(null)
    val period: StateFlow<Int?> = _period
    fun setPeriod(days: Int?) { _period.value = days }

    /**
     * صورتِ سود و زیانِ دوره — با چیپِ بازه (۷/۳۰ روز/همه) هماهنگ است،
     * چون فقط اسنادِ همان بازه را جمع می‌زند.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val incomeStatement: StateFlow<IncomeStatement> =
        _period
            .flatMapLatest { days ->
                val since = days?.let {
                    System.currentTimeMillis() - it * 24L * 3600L * 1000L
                } ?: 0L
                repo.observeAccountBalancesSince(since)
            }
            .map { buildIncome(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomeStatement())

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
        combine(raw, balances, _period) { r, bal, period ->
            build(r, bal, period)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportData())

    private fun build(r: RawData, bal: Triple<Long, Long, Long>, period: Int?): ReportData {
        val cutoff = period?.let { System.currentTimeMillis() - it * 24L * 3600L * 1000L } ?: 0L

        val sales = r.sales.filter { it.createdAt >= cutoff }
        val revenue = sales.sumOf { it.total }
        val cogs = sales.sumOf { it.cost }

        val tx = r.tx.filter { it.createdAt >= cutoff }
        val income = tx.filter { it.type == "IN" }.sumOf { it.amount }
        val outTx = tx.filter { it.type == "OUT" }
        val expenseTotal = outTx.sumOf { it.amount }
        val byCat = outTx.groupBy { it.category.ifBlank { "سایر" } }
            .map { (cat, list) -> cat to list.sumOf { it.amount } }
            .sortedByDescending { it.second }

        val receivable = r.ledger.filter { it.net > 0 }.sumOf { it.net }
        val payable = r.ledger.filter { it.net < 0 }.sumOf { -it.net }

        val orderProfits = r.orders
            .filter { it.agreedPrice > 0 && it.createdAt >= cutoff }
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
            receivable = receivable, payable = payable,
            debtorCount = r.ledger.count { it.net > 0 },
            creditorCount = r.ledger.count { it.net < 0 },
            orderProfits = orderProfits
        )
    }
}
