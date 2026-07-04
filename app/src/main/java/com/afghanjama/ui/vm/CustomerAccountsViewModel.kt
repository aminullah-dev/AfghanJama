package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** خلاصه حساب یک فروشگاه/مشتری. */
data class CustomerAccount(
    val name: String,
    val phone: String,
    val ordersCount: Int,
    val openOrdersCount: Int,   // سفارش‌هایی که هنوز ارسال نشده‌اند
    val totalCost: Long,        // مجموع هزینه سفارش‌ها (پارچه + خرج کار)
    val totalPaid: Long         // مجموع دریافتی‌ها از این مشتری
) {
    /** باقی‌مانده (برآوردی): هزینه سفارش‌ها منهای دریافتی‌ها. */
    val balance: Long get() = totalCost - totalPaid
}

class CustomerAccountsViewModel(private val repo: Repo) : ViewModel() {

    val accounts: StateFlow<List<CustomerAccount>> =
        combine(
            repo.observeAllOrders(),
            repo.observeCustomerPayments()
        ) { orders, payments ->
            // نگاشت سفارش → نام مشتری برای پرداخت‌های قدیمی که نام ندارند
            val orderName = orders.associate { it.id.toString() to it.customerName }
            val paidByName = payments.groupBy { p ->
                p.customerName.ifBlank { orderName[p.orderId] ?: "" }
            }

            orders
                .filter { it.customerName.isNotBlank() }
                .groupBy { it.customerName }
                .map { (name, customerOrders) ->
                    CustomerAccount(
                        name = name,
                        phone = customerOrders
                            .mapNotNull { it.customerPhone.takeIf(String::isNotBlank) }
                            .firstOrNull() ?: "",
                        ordersCount = customerOrders.size,
                        openOrdersCount = customerOrders.count { it.status != "SENT" },
                        totalCost = customerOrders.sumOf { it.fabricPrice + it.workCost },
                        totalPaid = paidByName[name]?.sumOf { it.amount } ?: 0L
                    )
                }
                .sortedByDescending { it.balance }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * ثبت دریافتی دستی از مشتری (مثلاً تسویه قرض فروشگاه).
     * مبلغ وارد کیف پول شده و در حساب مشتری ثبت می‌شود.
     */
    fun addManualPayment(customerName: String, amount: Long, note: String) = viewModelScope.launch {
        if (customerName.isBlank() || amount <= 0L) return@launch
        val n = note.trim().ifBlank { "دریافتی از $customerName" }
        repo.income("WALLET", amount, n)
        repo.addCustomerPayment(
            CustomerPayment(
                orderId = "",
                customerName = customerName,
                amount = amount,
                source = "MANUAL",
                note = n
            )
        )
    }
}
