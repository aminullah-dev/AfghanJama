package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class InventoryUiState(
    val message: String? = null,
    val isError: Boolean = false,
    val navigateToCutting: Boolean = false
)

class InventoryViewModel(private val repo: Repo) : ViewModel() {

    val ordersInStock: StateFlow<List<Order>> =
        repo.observeOrdersByStatus(OrderStatus.IN_STOCK.name)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(InventoryUiState())
    val state: StateFlow<InventoryUiState> = _state

    fun sendToCutting(orderId: UUID, selectedDesign: String) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch

        if (o.fabricAmount <= 0.0) {
            _state.update { it.copy(message = "مقدار پارچه برای این سفارش ثبت نشده است.", isError = true) }
            return@launch
        }
        if (o.qty < 1) {
            _state.update { it.copy(message = "تعداد سفارش معتبر نیست.", isError = true) }
            return@launch
        }

        repo.changeOrderStatus(o, OrderStatus.CUTTING.name) {
            it.copy(designTitle = selectedDesign.ifBlank { it.designTitle })
        }
        _state.update { it.copy(navigateToCutting = true, message = null, isError = false) }
    }

    fun clearMessage() = _state.update { it.copy(message = null, isError = false) }
    fun clearNavigation() = _state.update { it.copy(navigateToCutting = false) }

    fun deleteOrder(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        // اگر پارچه از موجودی رزرو شده بود و هنوز برش نخورده، برگردان
        if (o.fabricSource == "STOCK" && o.status == OrderStatus.IN_STOCK.name) {
            repo.changeFabricStock(o.fabricType, o.fabricColor, o.fabricUnit, o.fabricAmount)
        }
        repo.deleteOrder(o)
    }

    fun deleteOrder(order: Order) = viewModelScope.launch {
        repo.deleteOrder(order)
    }
}
