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

class SewingViewModel(
    private val repo: Repo
) : ViewModel() {

    val ordersCutDone: StateFlow<List<Order>> =
        repo.observeOrdersByStatus(OrderStatus.CUT_DONE.name)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ordersSewing: StateFlow<List<Order>> =
        repo.observeOrdersByStatus(OrderStatus.SEWING.name)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startSewing(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.updateOrder(o.copy(status = OrderStatus.SEWING.name))
    }

    // ✅ جدید: از دوخت -> بازرسی
    fun sendToReview(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.updateOrder(o.copy(status = OrderStatus.REVIEW.name))
    }
}
