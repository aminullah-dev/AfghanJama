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
        repo.changeOrderStatus(o, OrderStatus.REVIEW.name)
    }

    /**
     * تکمیل فروش. اگر [settleWithDiscount] فعال باشد و مبلغ دریافتی از
     * قیمت توافقی کمتر باشد، اختلاف به عنوان تخفیف ثبت و حساب مشتری
     * برای این سفارش کاملاً تسویه می‌شود.
     */
    fun completeSale(
        orderId: UUID,
        revenue: Long,
        settleWithDiscount: Boolean = false
    ) = viewModelScope.launch {
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

        val cost = (order.fabricPrice + order.workCost + order.sewingCost).coerceAtLeast(0)
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

        // 3) تخفیف (در صورت انتخاب): قیمت توافقی به مبلغ دریافتی کاهش می‌یابد
        val discount =
            if (settleWithDiscount && order.agreedPrice > rev) order.agreedPrice - rev else 0L

        // 4) وضعیت نهایی
        repo.changeOrderStatus(order, OrderStatus.SENT.name) {
            if (discount > 0) it.copy(agreedPrice = rev) else it
        }

        // 5) پیام + صدا
        val discountNote = if (discount > 0) " (تخفیف: $discount ؋)" else ""
        _ui.value = if (profit > 0L) {
            _ui.value.copy(
                message = "✅ فروش ثبت شد. سود: $profit ؋$discountNote",
                isError = false,
                earningSoundKey = _ui.value.earningSoundKey + 1
            )
        } else {
            _ui.value.copy(message = "✅ فروش ثبت شد.$discountNote", isError = false)
        }
    }
}
