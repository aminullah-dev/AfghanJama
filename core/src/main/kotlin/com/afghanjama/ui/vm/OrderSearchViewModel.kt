package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Order
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** جستجو و پیگیری سفارش با کد سفارش، کد کوتاه، نام مشتری یا طرح. */
class OrderSearchViewModel(repo: Repo) : ViewModel() {

    /**
     * کدِ طرحِ هر سفارش (DIP-12) — کلید: کدِ سفارش.
     * تعریفش یک‌جا در `Repo` است تا صفحه‌ها از هم جدا نیفتند.
     */
    val designCodeByOrder: StateFlow<Map<String, String>> =
        repo.observeDesignCodeByOrder()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val results: StateFlow<List<Order>> =
        combine(repo.observeAllOrders(), _query) { orders, q ->
            val t = q.trim()
            if (t.isBlank()) emptyList()
            else orders.filter {
                it.orderCode.contains(t, ignoreCase = true) ||
                    it.shortCode.contains(t, ignoreCase = true) ||
                    it.customerName.contains(t) ||
                    it.designTitle.contains(t)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(v: String) {
        _query.value = v
    }
}
