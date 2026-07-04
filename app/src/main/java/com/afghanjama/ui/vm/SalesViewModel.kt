// app/src/main/java/com/afghanjama/ui/vm/SalesViewModel.kt
package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class SalesUi(
    val message: String? = null,
    val isError: Boolean = false,
    val earningSoundKey: Int = 0
)

class SalesViewModel(private val repo: Repo) : ViewModel() {

    val ordersInSales: StateFlow<List<Order>> =
        repo.observeOrdersByStatus(OrderStatus.SALES.name)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val walletBalance: StateFlow<Long> =
        repo.observeWalletBalance()
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val profitBalance: StateFlow<Long> =
        repo.observeProfitBalance()
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _ui = MutableStateFlow(SalesUi())
    val ui: StateFlow<SalesUi> = _ui

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null, isError = false)
    }

    fun consumeEarningSound() {
        _ui.value = _ui.value.copy(earningSoundKey = 0)
    }

    /** برگشت به نظارت (اصلاح اشتباه). */
    fun backToReview(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.updateOrder(
            o.copy(status = OrderStatus.REVIEW.name, stageChangedAt = System.currentTimeMillis())
        )
    }

    fun completeSale(orderId: UUID, revenue: Long) = viewModelScope.launch {
        clearMessage()

        val order = repo.getOrder(orderId)
        if (order == null) {
            _ui.value = _ui.value.copy(message = "سفارش پیدا نشد.", isError = true)
            return@launch
        }

        val rev = revenue.coerceAtLeast(0)
        if (rev <= 0L) {
            _ui.value = _ui.value.copy(message = "مبلغ فروش باید بیشتر از صفر باشد.", isError = true)
            return@launch
        }

        val cost = (order.fabricPrice + order.workCost).coerceAtLeast(0)
        val profit = rev - cost

        // 1) پول مشتری وارد WALLET
        repo.income("WALLET", rev, "فروش سفارش ${order.orderCode}")

        // ثبت در حساب مشتری (دریافتی فروش)
        repo.addCustomerPayment(
            CustomerPayment(
                orderId = order.id.toString(),
                customerName = order.customerName,
                amount = rev,
                source = "SALE",
                note = "دریافتی فروش سفارش ${order.orderCode}"
            )
        )

        // 2) سود -> انتقال از WALLET به PROFIT
        if (profit > 0L) {
            repo.spend("WALLET", profit, "انتقال سود سفارش ${order.orderCode} به فایده")
            repo.income("PROFIT", profit, "سود سفارش ${order.orderCode}")
        }

        // 3) وضعیت نهایی
        repo.updateOrder(
            order.copy(status = OrderStatus.SENT.name, stageChangedAt = System.currentTimeMillis())
        )

        // 4) پیام + صدا
        _ui.value = if (profit > 0L) {
            _ui.value.copy(
                message = "✅ فروش ثبت شد. سود: $profit ؋",
                isError = false,
                earningSoundKey = _ui.value.earningSoundKey + 1
            )
        } else {
            _ui.value.copy(message = "✅ فروش ثبت شد.", isError = false)
        }
    }
}
