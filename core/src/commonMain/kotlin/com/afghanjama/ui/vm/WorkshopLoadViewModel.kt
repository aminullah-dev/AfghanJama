package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.repo.Repo
import com.afghanjama.util.nowMillis
import com.afghanjama.work.WorkshopLoad
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
}
