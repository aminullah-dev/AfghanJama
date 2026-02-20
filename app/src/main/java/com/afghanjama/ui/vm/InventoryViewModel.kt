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

class InventoryViewModel(private val repo: Repo) : ViewModel() {

    val ordersInStock: StateFlow<List<Order>> =
        repo.observeOrdersByStatus(OrderStatus.IN_STOCK.name)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun sendToCutting(orderId: UUID, selectedDesign: String) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.updateOrder(
            o.copy(
                designTitle = selectedDesign.ifBlank { o.designTitle },
                status = OrderStatus.CUTTING.name
            )
        )
    }

    // ✅ حذف سفارش با id (بدون نیاز به deleteOrderById در Repo)
    fun deleteOrder(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.deleteOrder(o)
    }

    // (اختیاری) اگر جایی order کامل داری:
    fun deleteOrder(order: Order) = viewModelScope.launch {
        repo.deleteOrder(order)
    }
}
