package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Inspector
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ReviewViewModel(
    private val repo: Repo
) : ViewModel() {

    val ordersInReview: StateFlow<List<Order>> =
        repo.observeOrdersByStatus(OrderStatus.REVIEW.name)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val inspectors: StateFlow<List<Inspector>> =
        repo.observeInspectors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * تایید نهایی: ناظر ثبت می‌شود + وضعیت به SALES
     */
    fun approve(orderId: UUID, inspectorLabel: String, note: String = "") = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.approveQc(o, inspectorLabel, note)
    }

    /**
     * برگشت برای اصلاح: مشکل + ناظر ثبت و وضعیت به SEWING.
     */
    fun backToSewing(orderId: UUID, inspectorLabel: String, problem: String = "") = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.rejectQc(o, inspectorLabel, problem)
    }
}
