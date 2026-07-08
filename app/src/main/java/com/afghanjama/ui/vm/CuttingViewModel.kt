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

    /** خیاط‌ها برای پیشنهادِ «مسئول برش». */
    val tailors: StateFlow<List<com.afghanjama.data.entities.Tailor>> =
        repo.observeTailors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** ثبت رکورد برش (مسئول/تعداد/ضایعات) و انتقال به «برش تمام». */
    fun markCutDone(
        orderId: UUID,
        cutter: String,
        pieces: Int,
        waste: String,
        note: String
    ) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.completeCutting(o, cutter, pieces.coerceAtLeast(0), waste, note)
    }

    /** برگشت به انبار (اصلاح اشتباه). */
    fun backToStock(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.changeOrderStatus(o, OrderStatus.IN_STOCK.name)
    }
}
