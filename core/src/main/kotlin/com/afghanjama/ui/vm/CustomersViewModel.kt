package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** یک ردیف در فهرست مشتریان با مانده‌حساب. */
data class CustomerRow(
    val customer: Customer,
    val ordersCount: Int,
    val balance: Long            // طلبِ ما از مشتری (مثبت = بدهکار)
)

/** فهرست مشتریان (پرونده) با مانده‌حساب و افزودن مشتری. */
class CustomersViewModel(private val repo: Repo) : ViewModel() {

    val rows: StateFlow<List<CustomerRow>> =
        combine(
            repo.observeCustomers(),
            repo.observeAllOrders(),
            repo.observeCustomerPayments()
        ) { customers, orders, payments ->
            val ordersByName = orders.filter { it.customerName.isNotBlank() }.groupBy { it.customerName }
            val orderName = orders.associate { it.id.toString() to it.customerName }
            val paidByName = payments.groupBy { p -> p.customerName.ifBlank { orderName[p.orderId] ?: "" } }

            customers
                .map { c ->
                    val myOrders = ordersByName[c.name].orEmpty()
                    val due = myOrders.sumOf { o ->
                        if (o.agreedPrice > 0) o.agreedPrice else o.fabricPrice + o.workCost
                    }
                    val paid = paidByName[c.name].orEmpty().sumOf { it.amount }
                    CustomerRow(c, myOrders.size, due - paid)
                }
                .sortedWith(compareByDescending<CustomerRow> { it.balance }.thenBy { it.customer.name })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCustomer(name: String, phone: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        repo.addCustomer(Customer(id = 0L, name = name.trim(), phone = phone.trim().ifBlank { null }))
    }
}
