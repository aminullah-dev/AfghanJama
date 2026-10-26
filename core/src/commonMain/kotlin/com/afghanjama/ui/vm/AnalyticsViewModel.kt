package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.PartyProfit
import com.afghanjama.data.entities.Order
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * «تحلیل» — سود به‌تفکیکِ مشتری و خیاط، که گزارش‌ها (محصول/سفارش) ندارند.
 *
 * درآمد و بها از خودِ سفارش‌اند؛ فقط سفارشِ قیمت‌خورده شمرده می‌شود. سودِ
 * خیاط به نسبتِ دستِ کارش از هر سفارش شکسته می‌شود ([PartyProfit]).
 */
class AnalyticsViewModel(repo: Repo) : ViewModel() {

    /** بازهٔ زمانی — همان [ReportRange] گزارش‌ها، تا یک‌جور فیلتر شود. */
    private val _range = MutableStateFlow(ReportRange.all())
    val range: StateFlow<ReportRange> = _range

    /** `null` = همه؛ وگرنه N روزِ اخیر. */
    fun setPeriod(days: Int?) {
        _range.value = days?.let { ReportRange.lastDays(it) } ?: ReportRange.all()
    }

    val ui: StateFlow<AnalyticsUi> =
        combine(repo.observeAllOrders(), repo.observeAllAssignments(), _range) { orders, assignments, range ->
            fun costOf(o: Order): Long = o.fabricPrice + o.workCost + o.sewingCost

            // فقط سفارشِ قیمت‌خورده و داخلِ بازه — سفارشِ بی‌قیمت سودِ قلابی نسازد.
            val priced = orders.filter { it.agreedPrice > 0 && it.createdAt in range }

            val customers = PartyProfit.summarize(
                priced.map { PartyProfit.Entry(it.customerName, it.qty, it.agreedPrice, costOf(it)) },
                unknown = "بی‌نام",
            )

            // سودِ خیاط: اگر سفارش ردیفِ دوخت دارد به نسبتِ دستِ هر خیاط،
            // وگرنه به خیاطِ گمارده‌شده (اگر هست)، وگرنه کنار.
            val assignmentsByOrder = assignments.groupBy { it.orderId }
            val sewn = priced.map { o ->
                val sewers = assignmentsByOrder[o.id.toString()]
                    ?.map { it.tailorLabel to it.qty }
                    ?.takeIf { it.isNotEmpty() }
                    ?: o.assignedTailor?.takeIf { it.isNotBlank() }?.let { listOf(it to o.qty) }
                    ?: emptyList()
                PartyProfit.SewnOrder(revenue = o.agreedPrice, cost = costOf(o), sewers = sewers)
            }
            val tailors = PartyProfit.summarize(PartyProfit.tailorEntries(sewn), unknown = "بی‌خیاط")

            AnalyticsUi(
                orderCount = priced.size,
                revenue = priced.sumOf { it.agreedPrice },
                cost = priced.sumOf { costOf(it) },
                customers = customers,
                tailors = tailors,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUi())
}

/** حالتِ صفحهٔ «تحلیل». */
data class AnalyticsUi(
    val orderCount: Int = 0,
    val revenue: Long = 0,
    val cost: Long = 0,
    val customers: List<PartyProfit.Row> = emptyList(),
    val tailors: List<PartyProfit.Row> = emptyList(),
) {
    val profit: Long get() = revenue - cost
    val marginPercent: Int? get() = if (revenue > 0) ((profit * 100) / revenue).toInt() else null
    val hasData: Boolean get() = orderCount > 0
    val bestCustomer: PartyProfit.Row? get() = customers.firstOrNull()
    val bestTailor: PartyProfit.Row? get() = tailors.firstOrNull()
}
