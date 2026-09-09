package com.afghanjama.ui.vm

import com.afghanjama.util.nowMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.CustomerMeasurement
import com.afghanjama.data.Installments
import com.afghanjama.data.entities.CustomerInstallment
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
            repo.observePaidByCustomer()
        ) { id, customers, orders, paidBy ->
            val customer = customers.firstOrNull { it.id == id } ?: return@combine CustomerSummary()
            val myOrders = orders.filter { it.customerName == customer.name }
            CustomerSummary(
                customer = customer,
                orders = myOrders.sortedByDescending { it.createdAt },
                totalDue = myOrders.sumOf { o -> if (o.agreedPrice > 0) o.agreedPrice else o.fabricPrice + o.workCost },
                // قاعدهٔ «نامِ پرداختِ وصل به سفارش از خودِ سفارش می‌آید»
                // حالا در `Repo` است، تا هشدارِ قسط هم دقیقاً همین عدد را
                // ببیند و دو صفحه دربارهٔ یک پول دو حرف نزنند.
                totalPaid = paidBy[customer.name.trim()] ?: 0L
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

    /**
     * قسط‌های این مشتری، هر کدام با سهمی که از پرداخت‌هایش رسیده.
     *
     * قول از `customer_installments` می‌آید و پول از همان `totalPaid`ِ
     * [summary] — یعنی از دفتر، نه از جای دوم. اگر کارفرما امروز
     * دریافتی ثبت کند، قسطِ عقب‌افتاده همین‌جا خودش بسته می‌شود بی
     * آنکه کسی چیزی را «پرداخت‌شده» علامت بزند.
     */
    val installments: StateFlow<List<Installments.Row>> =
        combine(summary, repo.observeInstallments()) { s, all ->
            val name = s.customer?.name ?: return@combine emptyList()
            Installments.allocate(all.filter { it.customerName == name }, s.totalPaid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** بستنِ قسط: مبلغ، و مهلت به «چند روز از امروز» — مثلِ مهلتِ سفارش. */
    fun addInstallment(amount: Long, days: Long, note: String) = viewModelScope.launch {
        val name = summary.value.customer?.name ?: return@launch
        if (amount <= 0L) return@launch
        repo.addInstallment(
            CustomerInstallment(
                customerName = name,
                amount = amount,
                dueDate = nowMillis() + days.coerceAtLeast(0L) * 86_400_000L,
                note = note.trim()
            )
        )
    }

    fun deleteInstallment(row: CustomerInstallment) = viewModelScope.launch {
        repo.deleteInstallment(row)
    }

    /** ثبت دریافتی از مشتری (به کیف پول + حساب مشتری + ژورنال). */
    fun recordPayment(amount: Long) = viewModelScope.launch {
        val name = summary.value.customer?.name ?: return@launch
        repo.recordCustomerReceipt(name, amount)
    }
}
