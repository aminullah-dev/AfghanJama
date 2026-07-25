package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Inspector
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * خیاطانِ سفارشی که همین حالا در دیالوگِ برگشت باز است. وقتی بیش از
     * یکی باشد، ناظر باید بگوید کارِ کدام برگشت خورده — وگرنه کارنامه
     * ناچار است آن سفارش را کنار بگذارد.
     */
    private val _tailorsOfOrder = MutableStateFlow<List<String>>(emptyList())
    val tailorsOfOrder: StateFlow<List<String>> = _tailorsOfOrder.asStateFlow()

    fun loadTailorsOfOrder(orderId: UUID) = viewModelScope.launch {
        _tailorsOfOrder.value = repo.tailorsOfOrder(orderId.toString())
    }

    /**
     * برگشت برای اصلاح: مشکل + ناظر ثبت و وضعیت به SEWING.
     * [tailor] خالی یعنی ناظر نتوانست بگوید کارِ کدام خیاط بوده.
     */
    fun backToSewing(
        orderId: UUID,
        inspectorLabel: String,
        problem: String = "",
        tailor: String = ""
    ) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.rejectQc(o, inspectorLabel, problem, tailor)
    }
}
