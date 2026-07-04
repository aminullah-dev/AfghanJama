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
    val totalDue: Long,         // مجموع مبلغ سفارش‌ها (قیمت توافقی؛ اگر نبود، هزینه)
    val totalPaid: Long,        // مجموع دریافتی‌ها از این مشتری
    val payments: List<CustomerPayment> = emptyList()  // تاریخچه پرداخت‌ها
) {
    /** باقی‌مانده: مبلغ سفارش‌ها منهای دریافتی‌ها. */
    val balance: Long get() = totalDue - totalPaid
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
                    val customerPayments = paidByName[name].orEmpty()
                    CustomerAccount(
                        name = name,
                        phone = customerOrders
                            .mapNotNull { it.customerPhone.takeIf(String::isNotBlank) }
                            .firstOrNull() ?: "",
                        ordersCount = customerOrders.size,
                        openOrdersCount = customerOrders.count { it.status != "SENT" },
                        // اگر قیمت توافقی ثبت شده باشد ملاک آن است، وگرنه هزینه سفارش
                        totalDue = customerOrders.sumOf { o ->
                            if (o.agreedPrice > 0) o.agreedPrice else o.fabricPrice + o.workCost
                        },
                        totalPaid = customerPayments.sumOf { it.amount },
                        payments = customerPayments
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
