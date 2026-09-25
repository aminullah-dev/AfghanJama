package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.repo.Repo
import com.afghanjama.util.nowMillis
import com.afghanjama.work.DeliveryForecast
import com.afghanjama.work.WorkshopLoad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * بارِ کارگاه — هیچ حالتِ خودش را ندارد.
 *
 * کلِ محاسبه در [WorkshopLoad] است و آنجا تابعِ خالص است. اینجا فقط
 * دو جریان را به آن می‌دهد. همان الگویی که `Margin` و `Installments`
 * دارند: قاعده جایی می‌نشیند که بشود بی اپ آزمودش.
 */
class WorkshopLoadViewModel(repo: Repo) : ViewModel() {

    val ui: StateFlow<WorkshopLoad.Result> =
        combine(
            repo.observeAllOrders(),
            repo.observeAllAssignments(),
        ) { orders, sewn ->
            WorkshopLoad.of(orders, sewn, nowMillis())
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WorkshopLoad.of(emptyList(), emptyList(), nowMillis()),
        )

    /**
     * پیش‌بینیِ هر سفارش و کارِ زیرِ دستِ هر خیاط — از همان دو جریان.
     * قاعده در [DeliveryForecast] است، مثلِ [WorkshopLoad].
     */
    val forecast: StateFlow<DeliveryForecast.Result> =
        forecastFlow(repo).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DeliveryForecast.of(emptyList(), emptyList(), nowMillis()),
        )
}

/**
 * پیش‌بینیِ تحویل، یک‌جا ساخته — صفحهٔ بارِ کارگاه، فرمِ سفارشِ تازه و
 * مرکزِ هشدار هر سه از همین می‌خوانند، تا یکی «دیر می‌شود» نگوید و
 * دیگری ساکت بماند.
 */
fun forecastFlow(repo: Repo): Flow<DeliveryForecast.Result> =
    combine(repo.observeAllOrders(), repo.observeAllAssignments()) { orders, sewn ->
        DeliveryForecast.of(orders, sewn, nowMillis())
    }
