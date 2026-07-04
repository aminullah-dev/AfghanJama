package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
            val paidByOrder = payments.groupBy { it.orderId }

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
                        totalPaid = customerOrders.sumOf { o ->
                            paidByOrder[o.id.toString()]?.sumOf { it.amount } ?: 0L
                        }
                    )
                }
                .sortedByDescending { it.balance }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
