package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.CustomerMeasurement
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Order
import com.afghanjama.pdf.StatementData
import com.afghanjama.pdf.StatementRow
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

    /**
     * کدِ طرحِ هر سفارش (DIP-12) — کلید: کدِ سفارش.
     * تعریفش یک‌جا در `Repo` است تا صفحه‌ها از هم جدا نیفتند.
     */
    val designCodeByOrder: StateFlow<Map<String, String>> =
        repo.observeDesignCodeByOrder()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val customerId = MutableStateFlow<Long?>(null)

    fun open(id: Long) { customerId.value = id }

    /**
     * کارتِ حسابِ مشتری برای فرستادن.
     *
     * دفترِ مشتری در اپ بود ولی چیزی برای فرستادن نداشت و پیگیریِ بدهی
     * شفاهی انجام می‌شد. همان سطرهای دفتر اینجا به یک برگه تبدیل می‌شوند.
     */
    suspend fun statementData(name: String): StatementData {
        val entries = repo.observeLedgerEntries("CUSTOMER", name.trim()).first()
        val customer = repo.customerByName(name)
        return StatementData(
            customerName = name.trim(),
            customerPhone = customer?.phone.orEmpty(),
            rows = entries
                .sortedBy { it.at }
                .map { e ->
                    StatementRow(
                        date = PersianDate.short(e.at),
                        title = listOf(ledgerLabel(e.refType), e.refId, e.note)
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        debit = e.debit,
                        credit = e.credit
                    )
                }
        )
    }

    /** برچسبِ خواندنیِ نوعِ سند — کدِ خام به دستِ مشتری نمی‌رود. */
    private fun ledgerLabel(refType: String): String = when (refType) {
        "CUSTOMER_SALE", "SALE" -> "فروش"
        "CUSTOMER_PAYMENT", "PAYMENT" -> "پرداخت"
        "CUSTOMER_PREPAY", "PREPAY" -> "بیعانه"
        "CUSTOMER_RETURN", "RETURN" -> "برگشت از فروش"
        "MANUAL" -> "ثبت دستی"
        else -> refType
    }

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

    /** ثبت دریافتی از مشتری (به کیف پول + حساب مشتری + ژورنال). */
    fun recordPayment(amount: Long) = viewModelScope.launch {
        val name = summary.value.customer?.name ?: return@launch
        repo.recordCustomerReceipt(name, amount)
    }
}
