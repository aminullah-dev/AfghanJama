package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class CuttingViewModel(private val repo: Repo) : ViewModel() {

    val ordersCutting: StateFlow<List<Order>> =
        repo.observeOrdersByStatus(OrderStatus.CUTTING.name)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markCutDone(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.updateOrder(
            o.copy(status = OrderStatus.CUT_DONE.name, stageChangedAt = System.currentTimeMillis())
        )
    }

    /** برگشت به انبار (اصلاح اشتباه). */
    fun backToStock(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.updateOrder(
            o.copy(status = OrderStatus.IN_STOCK.name, stageChangedAt = System.currentTimeMillis())
        )
    }
}
