package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.CustomerMeasurement
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Order
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomerSummary(
    val customer: Customer? = null,
    val orders: List<Order> = emptyList(),
    val totalDue: Long = 0,
    val totalPaid: Long = 0
) {
    val balance: Long get() = totalDue - totalPaid
}

/** پروندهٔ کامل یک مشتری: مشخصات، مانده، تاریخچهٔ سفارش و اندازه‌ها. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDetailViewModel(private val repo: Repo) : ViewModel() {

    private val customerId = MutableStateFlow<Long?>(null)

    fun open(id: Long) { customerId.value = id }

    val measurements: StateFlow<List<CustomerMeasurement>> =
        customerId.filterNotNull()
            .flatMapLatest { repo.observeMeasurements(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<CustomerSummary> =
        combine(
            customerId,
            repo.observeCustomers(),
            repo.observeAllOrders(),
            repo.observeCustomerPayments()
        ) { id, customers, orders, payments ->
            val customer = customers.firstOrNull { it.id == id } ?: return@combine CustomerSummary()
            val myOrders = orders.filter { it.customerName == customer.name }
            val orderName = orders.associate { it.id.toString() to it.customerName }
            val myPayments = payments.filter {
                (it.customerName.ifBlank { orderName[it.orderId] ?: "" }) == customer.name
            }
            CustomerSummary(
                customer = customer,
                orders = myOrders.sortedByDescending { it.createdAt },
                totalDue = myOrders.sumOf { o -> if (o.agreedPrice > 0) o.agreedPrice else o.fabricPrice + o.workCost },
                totalPaid = myPayments.sumOf { it.amount }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerSummary())

    fun addMeasurement(label: String, value: String) = viewModelScope.launch {
        val id = customerId.value ?: return@launch
        if (label.isBlank() || value.isBlank()) return@launch
        repo.upsertMeasurement(CustomerMeasurement(customerId = id, label = label.trim(), value = value.trim()))
    }

    fun updateMeasurement(row: CustomerMeasurement, value: String) = viewModelScope.launch {
        repo.upsertMeasurement(row.copy(value = value.trim()))
    }

    fun deleteMeasurement(id: Long) = viewModelScope.launch { repo.deleteMeasurement(id) }

    /** ثبت دریافتی از مشتری (به کیف پول). */
    fun recordPayment(amount: Long) = viewModelScope.launch {
        val name = summary.value.customer?.name ?: return@launch
        if (amount <= 0) return@launch
        repo.income("WALLET", amount, "دریافتی از $name")
        repo.addCustomerPayment(
            CustomerPayment(orderId = "", customerName = name, amount = amount, source = "MANUAL", note = "دریافتی از $name")
        )
    }
}
