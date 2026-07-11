package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** خلاصهٔ زندهٔ کارگاه برای داشبورد صفحهٔ اصلی. */
data class HomeSummary(
    val materialItems: Int = 0,          // تعداد اقلام انبار مواد
    val materialValue: Long = 0,         // ارزش تقریبی انبار مواد
    val lowStockCount: Int = 0,          // اقلام زیر حد هشدار
    val inProduction: Int = 0,           // سفارش‌های در جریان تولید
    val inStock: Int = 0,                // در انبار سفارش‌ها
    val readyForSale: Int = 0,           // آماده فروش
    val finishedPieces: Int = 0,         // مجموع عددهای انبار محصول نهایی
    val wallet: Long = 0,
    val bank: Long = 0,
    val profit: Long = 0
)

class HomeViewModel(repo: Repo) : ViewModel() {

    private val base: kotlinx.coroutines.flow.Flow<HomeSummary> =
        combine(
            repo.observeMaterialStock(),
            repo.observeAllOrders(),
            repo.observeWalletBalance(),
            repo.observeBankBalance(),
            repo.observeProfitBalance()
        ) { materials, orders, wallet, bank, profit ->
            val productionStatuses = setOf(
                OrderStatus.CUTTING.name,
                OrderStatus.CUT_DONE.name,
                OrderStatus.SEWING.name,
                OrderStatus.REVIEW.name
            )
            HomeSummary(
                materialItems = materials.size,
                materialValue = materials.sumOf { (it.amount * it.avgPrice) }.toLong(),
                lowStockCount = materials.count { it.minLevel > 0.0 && it.amount <= it.minLevel },
                inProduction = orders.count { it.status in productionStatuses },
                inStock = orders.count { it.status == OrderStatus.IN_STOCK.name },
                readyForSale = orders.count { it.status == OrderStatus.SALES.name },
                wallet = wallet,
                bank = bank,
                profit = profit
            )
        }

    val summary: StateFlow<HomeSummary> =
        combine(base, repo.observeFinishedStock()) { s, finished ->
            s.copy(finishedPieces = finished.sumOf { it.qty })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSummary())

    /** بازه‌های بازِ حضور (کارمندانِ داخل کارگاه) برای ساعتِ شیفتِ داشبورد. */
    val insideNow: StateFlow<List<com.afghanjama.data.entities.AttendanceRecord>> =
        repo.observeAttendance()
            .map { list -> list.filter { it.checkOut == null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
