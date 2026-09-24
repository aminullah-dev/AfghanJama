package com.afghanjama.ui.vm

import com.afghanjama.util.nowMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.FinancialHealth
import com.afghanjama.data.entities.Accounts
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** آمار زنده کارگاه برای داشبورد گزارش‌ها. */
data class DashboardStats(
    val inStock: Int = 0,
    val cutting: Int = 0,        // در حال برش + برش تمام
    val sewing: Int = 0,
    val review: Int = 0,
    /** عددِ آمادهٔ فروش در انبار محصول (نه تعدادِ سفارش). */
    val readyPieces: Int = 0,
    /** تعدادِ فروش‌های ثبت‌شده تا امروز. */
    val salesCount: Int = 0,
    val sales7: Long = 0,        // فروش ۷ روز اخیر
    val sales30: Long = 0,       // فروش ۳۰ روز اخیر
    /** فروشِ ۳۰ روزِ پیش از آن — برای روند، نه برای نمایشِ مستقیم. */
    val salesPrev30: Long = 0,
    val profitNet7: Long = 0,    // تغییر خالص فایده ۷ روز اخیر
    val profitNet30: Long = 0,
    val openWagesTotal: Long = 0,
    val openWagesCount: Int = 0,
    /**
     * هزینه‌های عمومیِ ۳۰ روزِ اخیر — **از ژورنال**، همان عددِ صورتِ سود
     * و زیان.
     *
     * تا امروز جمعِ هر خروجیِ نقدی بود که دسته داشت، و این یعنی خریدِ
     * مواد، تسویهٔ قرضِ تأمین‌کننده، برگشتیِ فروش و حتی انتقال از صندوق
     * به بانک هم «هزینهٔ کارگاه» شمرده می‌شد. کارگاهی که یک بار پارچهٔ
     * ماهش را می‌خرید، هزینه‌اش چند برابرِ واقعی دیده می‌شد.
     */
    val expenses30: Long = 0,
    val recentSales: List<RecentSale> = emptyList(),
    val tailorStats: List<TailorStat> = emptyList(),
    /** قدیمی‌ترین کارمزد باز چند روز است؟ (برای یادآوری تسویه هفتگی) */
    val oldestPendingWageDays: Long = 0
) {
    val wageReminderDue: Boolean get() = oldestPendingWageDays >= 7
}

/** بهره‌وری خیاط در ۳۰ روز اخیر. */
data class TailorStat(
    val label: String,
    val ordersDone: Int,
    val piecesDone: Int,
    val earned: Long
)

/** فروش اخیر با سود واقعی. */
data class RecentSale(
    val orderCode: String,
    val designTitle: String,
    val revenue: Long,
    val cost: Long,
    val soldAt: Long
) {
    val profit: Long get() = revenue - cost
}

/** یک روزِ ۲۴ ساعته به میلی‌ثانیه. */
private const val DAY_MS = 24L * 3600 * 1000

class DashboardViewModel(repo: Repo) : ViewModel() {

    private val base: kotlinx.coroutines.flow.Flow<DashboardStats> =
        combine(
            repo.observeAllOrders(),
            repo.observeFinishedSales(),
            repo.observeTx(),
            repo.observeAllWages()
        ) { orders, sales, tx, allWages ->
            val wages = allWages.filter { !it.settled }
            val now = nowMillis()
            val d7 = now - 7L * DAY_MS
            val d30 = now - 30L * DAY_MS
            val d60 = now - 60L * DAY_MS

            // فروش از دفترِ خودِ فروش‌ها خوانده می‌شود (منبعِ واحد) و
            // مرجوعی‌ها از آن کم می‌شوند تا رقم، فروشِ واقعی باشد.
            fun salesSince(t: Long) = sales
                .filter { it.createdAt >= t }
                .sumOf { it.netTotal }

            val salesPrev30 = sales
                .filter { it.createdAt in d60 until d30 }
                .sumOf { it.netTotal }

            fun profitNetSince(t: Long) = tx
                .filter { it.source == "PROFIT" && it.createdAt >= t }
                .sumOf { if (it.type == "IN") it.amount else -it.amount }

            // بهره‌وری خیاط‌ها در ۳۰ روز اخیر (بر اساس دوخت‌های تمام‌شده)
            val qtyByOrder = orders.associate { it.id.toString() to it.qty }
            val tailorStats = allWages
                .filter { it.createdAt >= d30 }
                .groupBy { it.tailorLabel }
                .map { (label, ws) ->
                    TailorStat(
                        label = label,
                        ordersDone = ws.size,
                        piecesDone = ws.sumOf { qtyByOrder[it.orderId] ?: 1 },
                        earned = ws.sumOf { it.amount }
                    )
                }
                .sortedByDescending { it.piecesDone }

            val oldestPendingDays = wages.minOfOrNull { it.createdAt }
                ?.let { (now - it) / 86_400_000L } ?: 0L

            // فروشِ اخیر از دفترِ فروشِ انبار محصول می‌آید — فروش از همان‌جا
            // انجام می‌شود و مرحلهٔ «فروشِ سفارش» دیگر وجود ندارد.
            val recentSales = sales
                .sortedByDescending { it.createdAt }
                .take(5)
                .map { s ->
                    RecentSale(
                        orderCode = s.code,
                        designTitle = s.productName +
                            (if (s.size.isNotBlank()) " • ${s.size}" else ""),
                        revenue = s.netTotal,
                        cost = s.netCost,
                        soldAt = s.createdAt
                    )
                }

            DashboardStats(
                inStock = orders.count { it.status == OrderStatus.IN_STOCK.name },
                cutting = orders.count {
                    it.status == OrderStatus.CUTTING.name || it.status == OrderStatus.CUT_DONE.name
                },
                sewing = orders.count { it.status == OrderStatus.SEWING.name },
                review = orders.count { it.status == OrderStatus.REVIEW.name },
                // readyPieces از موجودیِ انبار محصول در combine بعدی پر می‌شود
                salesCount = sales.count(),
                sales7 = salesSince(d7),
                sales30 = salesSince(d30),
                salesPrev30 = salesPrev30,
                profitNet7 = profitNetSince(d7),
                profitNet30 = profitNetSince(d30),
                openWagesTotal = wages.sumOf { it.amount },
                openWagesCount = wages.size,
                recentSales = recentSales,
                tailorStats = tailorStats,
                oldestPendingWageDays = oldestPendingDays
            )
        }

    /**
     * صورتِ سود و زیانِ ۳۰ روزِ اخیر — همان سازنده‌ای که «گزارش‌ها» دارد.
     *
     * سرِ بازه `Long.MAX_VALUE` است نه «الان»: این جریان تا وقتی صفحه باز
     * است زنده می‌ماند، و «الانِ» لحظهٔ ساخت، هزینه‌ای را که یک ساعت بعد
     * ثبت می‌شود بیرون می‌گذاشت. تهِ بازه هم ساعتی یک بار جلو می‌رود؛
     * کمپیوترِ دفتر روزها باز می‌ماند و پنجرهٔ «۳۰ روز» نباید کش بیاید.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val income30 =
        flow {
            while (true) {
                emit(nowMillis())
                delay(60L * 60 * 1000)
            }
        }.flatMapLatest { now ->
            repo.observeAccountBalancesBetween(now - 30L * DAY_MS, Long.MAX_VALUE)
        }

    val stats: StateFlow<DashboardStats> =
        combine(base, repo.observeFinishedStock(), income30) { s, finished, journal ->
            // «آماده» یعنی چیزی که واقعاً در انبار هست؛ ردیفِ کسری از آن
            // کم نمی‌شود، وگرنه یک کسری، موجودیِ طرحِ دیگری را پنهان می‌کند.
            s.copy(
                readyPieces = finished.sumOf { it.qty.coerceAtLeast(0) },
                expenses30 = incomeStatementOf(journal).totalExpense,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStats())

    /**
     * «وضعِ مالی» — ترازنامهٔ همین لحظه + هزینه و فروشِ ۳۰ روزه.
     *
     * `null` تا وقتی اولین عددها برسند؛ کارتِ خالی بهتر از کارتی است که
     * یک لحظه «همه‌چیز صفر است» نشان دهد.
     */
    val health: StateFlow<FinancialHealth.Snapshot?> =
        combine(stats, repo.observeAccountBalances()) { s, all ->
            FinancialHealth.assess(healthFigures(balanceSheetOf(all), s))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** ترازنامه → عددهای وضعِ مالی. جدا، تا آزمون بدون ViewModel بسنجدش. */
fun healthFigures(bs: BalanceSheet, s: DashboardStats): FinancialHealth.Figures {
    fun sum(vararg codes: String) = bs.assets.filter { it.code in codes }.sumOf { it.amount }
    val cash = sum(Accounts.CASH, Accounts.BANK, Accounts.PROFIT_BOX)
    val receivable = sum(Accounts.RECEIVABLE, Accounts.STAFF_ADVANCE)
    val stock = sum(Accounts.MATERIALS, Accounts.WIP, Accounts.FINISHED)
    return FinancialHealth.Figures(
        cash = cash,
        receivable = receivable,
        stock = stock,
        otherAssets = bs.totalAssets - cash - receivable - stock,
        debts = bs.totalLiabilities,
        expense30 = s.expenses30,
        sales30 = s.sales30,
        salesPrev30 = s.salesPrev30,
    )
}
