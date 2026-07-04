package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.Tailor
import com.afghanjama.data.entities.TailorWage
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

    // لیست خیاط‌ها برای انتخاب هنگام شروع دوخت
    val tailors: StateFlow<List<Tailor>> =
        repo.observeTailors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** شروع دوخت با تعیین خیاط (برای محاسبه کارمزد). */
    fun startSewing(orderId: UUID, tailorLabel: String) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.updateOrder(
            o.copy(
                status = OrderStatus.SEWING.name,
                assignedTailor = tailorLabel.ifBlank { o.assignedTailor }
            )
        )
    }

    /** برگشت از «آماده دوخت» به برش (اصلاح اشتباه). */
    fun backToCutting(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.updateOrder(o.copy(status = OrderStatus.CUTTING.name))
    }

    /** برگشت از «در حال دوخت» به آماده دوخت (اصلاح اشتباه). */
    fun backToCutDone(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.updateOrder(o.copy(status = OrderStatus.CUT_DONE.name))
    }

    // ✅ از دوخت -> بازرسی + ثبت کارمزد خیاط (یک‌بار برای هر سفارش)
    fun sendToReview(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.updateOrder(o.copy(status = OrderStatus.REVIEW.name))

        if (o.workCost > 0) {
            repo.addTailorWage(
                TailorWage(
                    orderId = o.id.toString(),
                    orderCode = o.orderCode,
                    tailorLabel = o.assignedTailor?.takeIf { it.isNotBlank() } ?: "نامشخص",
                    amount = o.workCost
                )
            )
        }
    }
}
