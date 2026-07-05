package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** آمار زنده کارگاه برای داشبورد گزارش‌ها. */
data class DashboardStats(
    val inStock: Int = 0,
    val cutting: Int = 0,        // در حال برش + برش تمام
    val sewing: Int = 0,
    val review: Int = 0,
    val readyForSale: Int = 0,
    val sentTotal: Int = 0,
    val sales7: Long = 0,        // فروش ۷ روز اخیر
    val sales30: Long = 0,       // فروش ۳۰ روز اخیر
    val profitNet7: Long = 0,    // تغییر خالص فایده ۷ روز اخیر
    val profitNet30: Long = 0,
    val openWagesTotal: Long = 0,
    val openWagesCount: Int = 0,
    val expenses30: Long = 0,    // هزینه‌های عمومی ۳۰ روز اخیر
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

class DashboardViewModel(repo: Repo) : ViewModel() {

    val stats: StateFlow<DashboardStats> =
        combine(
            repo.observeAllOrders(),
            repo.observeCustomerPayments(),
            repo.observeTx(),
            repo.observeAllWages()
        ) { orders, payments, tx, allWages ->
            val wages = allWages.filter { !it.settled }
            val now = System.currentTimeMillis()
            val d7 = now - 7L * 24 * 3600 * 1000
            val d30 = now - 30L * 24 * 3600 * 1000

            fun salesSince(t: Long) = payments
                .filter { it.source == "SALE" && it.createdAt >= t }
                .sumOf { it.amount }

            fun profitNetSince(t: Long) = tx
                .filter { it.source == "PROFIT" && it.createdAt >= t }
                .sumOf { if (it.type == "IN") it.amount else -it.amount }

            val expenses30 = tx
                .filter { it.type == "OUT" && it.category.isNotBlank() && it.createdAt >= d30 }
                .sumOf { it.amount }

            val saleByOrder = payments
                .filter { it.source == "SALE" }
                .groupBy { it.orderId }

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

            val recentSales = orders
                .filter { it.status == OrderStatus.SENT.name }
                .sortedByDescending { if (it.stageChangedAt > 0) it.stageChangedAt else it.createdAt }
                .take(5)
                .map { o ->
                    RecentSale(
                        orderCode = o.orderCode,
                        designTitle = o.designTitle,
                        revenue = saleByOrder[o.id.toString()]?.sumOf { it.amount } ?: 0L,
                        cost = o.fabricPrice + o.workCost,
                        soldAt = if (o.stageChangedAt > 0) o.stageChangedAt else o.createdAt
                    )
                }

            DashboardStats(
                inStock = orders.count { it.status == OrderStatus.IN_STOCK.name },
                cutting = orders.count {
                    it.status == OrderStatus.CUTTING.name || it.status == OrderStatus.CUT_DONE.name
                },
                sewing = orders.count { it.status == OrderStatus.SEWING.name },
                review = orders.count { it.status == OrderStatus.REVIEW.name },
                readyForSale = orders.count { it.status == OrderStatus.SALES.name },
                sentTotal = orders.count { it.status == OrderStatus.SENT.name },
                sales7 = salesSince(d7),
                sales30 = salesSince(d30),
                profitNet7 = profitNetSince(d7),
                profitNet30 = profitNetSince(d30),
                openWagesTotal = wages.sumOf { it.amount },
                openWagesCount = wages.size,
                expenses30 = expenses30,
                recentSales = recentSales,
                tailorStats = tailorStats,
                oldestPendingWageDays = oldestPendingDays
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStats())
}
