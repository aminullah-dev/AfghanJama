package com.afghanjama.ui.vm

import com.afghanjama.util.nowMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.StockForecast
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.STAGE_WARN_DAYS
import com.afghanjama.ui.format.stageDays
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
    val finishedPieces: Int = 0,         // مجموع عددهای انبار محصول نهایی
    val wallet: Long = 0,
    val bank: Long = 0,
    val profit: Long = 0,

    // ضربان خط تولید (تعداد سفارش در هر ایستگاه)
    val cutting: Int = 0,
    val sewing: Int = 0,
    val review: Int = 0,
    // سفارش‌های معطل‌مانده (بیش از آستانهٔ هشدار در یک مرحله)
    val stuck: Int = 0
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
                wallet = wallet,
                bank = bank,
                profit = profit,
                cutting = orders.count { it.status == OrderStatus.CUTTING.name || it.status == OrderStatus.CUT_DONE.name },
                sewing = orders.count { it.status == OrderStatus.SEWING.name },
                review = orders.count { it.status == OrderStatus.REVIEW.name },
                stuck = orders.count {
                    it.status in productionStatuses &&
                        stageDays(it.stageChangedAt, it.createdAt) >= STAGE_WARN_DAYS
                }
            )
        }

    val summary: StateFlow<HomeSummary> =
        combine(base, repo.observeFinishedStock()) { s, finished ->
            // ردیفِ کسری از شمارشِ «آماده» کم نمی‌شود — آن یک بدهیِ جنسی
            // است، نه کالایی که کم شده باشد.
            s.copy(finishedPieces = finished.sumOf { it.qty.coerceAtLeast(0) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSummary())

    /** بازه‌های بازِ حضور (کارمندانِ داخل کارگاه) برای ساعتِ شیفتِ داشبورد. */
    /**
     * موادی که دارند تمام می‌شوند — از رویِ سرعتِ مصرفِ واقعی، نه یک عددِ ثابت.
     *
     * کاردکس از اول همهٔ خروج‌ها را ثبت می‌کرده و کسی از آن چیزی نمی‌ساخت.
     * در خیاطی تمام شدنِ پارچه وسطِ سفارش یعنی خطِ تولید می‌خوابد، پس این
     * هشدار باید پیش از خوابیدنِ کار برسد.
     */
    val runningOut: StateFlow<List<StockForecast.Forecast>> = run {
        val windowMs = StockForecast.WINDOW_DAYS * 24L * 60L * 60L * 1000L
        val since = nowMillis() - windowMs
        combine(
            repo.observeMaterialStock(),
            repo.observeStockMovementsSince(since)
        ) { stock, moves ->
            val draws = moves
                // فقط خروجِ واقعی؛ اصلاحِ دستی و برگشت نرخِ مصرف نیستند
                .filter { it.delta < 0 && it.reason != "اصلاح" }
                .map { m ->
                    StockForecast.Draw(
                        name = m.name,
                        unit = m.unit,
                        qty = -m.delta,
                        atDay = ((nowMillis() - m.createdAt) / 86_400_000L)
                            .toInt().coerceAtLeast(0)
                    )
                }
            StockForecast.needsAttention(
                StockForecast.forecast(
                    stock.map { StockForecast.Item(it.name, it.unit, it.amount, it.minLevel) },
                    draws
                )
            ).take(5)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    val insideNow: StateFlow<List<com.afghanjama.data.entities.AttendanceRecord>> =
        repo.observeAttendance()
            .map { list -> list.filter { it.checkOut == null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
