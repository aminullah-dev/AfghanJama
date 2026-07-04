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
    val openWagesCount: Int = 0
)

class DashboardViewModel(repo: Repo) : ViewModel() {

    val stats: StateFlow<DashboardStats> =
        combine(
            repo.observeAllOrders(),
            repo.observeCustomerPayments(),
            repo.observeTx(),
            repo.observePendingWages()
        ) { orders, payments, tx, wages ->
            val now = System.currentTimeMillis()
            val d7 = now - 7L * 24 * 3600 * 1000
            val d30 = now - 30L * 24 * 3600 * 1000

            fun salesSince(t: Long) = payments
                .filter { it.source == "SALE" && it.createdAt >= t }
                .sumOf { it.amount }

            fun profitNetSince(t: Long) = tx
                .filter { it.source == "PROFIT" && it.createdAt >= t }
                .sumOf { if (it.type == "IN") it.amount else -it.amount }

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
                openWagesCount = wages.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStats())
}
