package com.afghanjama.ui.vm

import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.util.nowMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.CustomerMeasurement
import com.afghanjama.data.Installments
import com.afghanjama.data.PersonEdit
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
    val totalPaid: Long = 0,
    /**
     * ماندهٔ مشتری از **دفتر کل** — همان عددی که «پیگیری طلب» نشان می‌دهد.
     * مثبت یعنی بدهکار است، منفی یعنی پیش‌پرداخت نزدِ ما دارد.
     */
    val owed: Long = 0,
    /** ارزشِ سفارش‌هایی که هنوز تحویل نشده‌اند؛ هنگامِ تحویل به حساب می‌آیند. */
    val pendingOrders: Long = 0
) {
    /**
     * مانده‌ای که بالای پرونده نشان داده می‌شود.
     *
     * **تا ۱.۱۳ «جمعِ قیمتِ سفارش‌ها منهای همهٔ دریافتی‌ها» بود.** ولی
     * دریافتی‌ها پولِ خرید از انبار را هم دارند و «جمعِ سفارش‌ها» آن خریدها
     * را ندارد: مشتری‌ای که از انبار ۵٬۰۰۰ نسیه خریده و ۲٬۰۰۰ داده بود
     * اینجا «حساب تسویه است» می‌دید، در حالی که دفتر و «پیگیری طلب» ۳٬۰۰۰
     * بدهی نشان می‌دادند. حالا هر دو صفحه یک عدد را از یک جا می‌خوانند.
     */
    val balance: Long get() = owed
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
        // بقیه از همان منبعِ واحدِ دفتر کل — نوع‌های امروزی (SALE_BILLING،
        // PREPAY_APPLIED…) پیش‌تر با کدِ خام روی کارتِ مشتری می‌آمدند.
        else -> com.afghanjama.data.entities.ledgerRefLabel(refType)
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
            repo.observePaidByCustomer(),
            repo.observeCustomerAccounts()
        ) { id, customers, orders, paidBy, accounts ->
            val customer = customers.firstOrNull { it.id == id } ?: return@combine CustomerSummary()
            val myOrders = orders.filter { it.customerName == customer.name }
            CustomerSummary(
                customer = customer,
                orders = myOrders.sortedByDescending { it.createdAt },
                totalDue = myOrders.sumOf { o -> if (o.agreedPrice > 0) o.agreedPrice else o.fabricPrice + o.workCost },
                // قاعدهٔ «نامِ پرداختِ وصل به سفارش از خودِ سفارش می‌آید»
                // حالا در `Repo` است، تا هشدارِ قسط هم دقیقاً همین عدد را
                // ببیند و دو صفحه دربارهٔ یک پول دو حرف نزنند.
                totalPaid = paidBy[customer.name.trim()] ?: 0L,
                owed = accounts[customer.name.trim()]?.owed ?: 0L,
                pendingOrders = myOrders
                    .filter { it.status != OrderStatus.SENT.name && it.qty > 0 }
                    .sumOf { o ->
                        val price = if (o.agreedPrice > 0) o.agreedPrice else o.fabricPrice + o.workCost
                        price * (o.qty - o.deliveredQty).coerceAtLeast(0) / o.qty
                    }
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

    /** پیامِ ویرایش یا حذفی که انجام نشد — دلیلش را می‌گوید. */
    private val _editError = MutableStateFlow<String?>(null)
    val editError: StateFlow<String?> = _editError

    fun clearEditError() { _editError.value = null }

    /**
     * نام و تلفن. تغییرِ نام سفارش‌ها و حسابِ مشتری را هم با خودش می‌برد؛
     * فقط به نامی که از قبل هست نمی‌رود — [PersonEdit]. [onDone] فقط وقتی صدا زده می‌شود که ثبت شد.
     */
    fun edit(name: String, phone: String, address: String, onDone: () -> Unit) = viewModelScope.launch {
        val id = customerId.value ?: return@launch
        val r = repo.editCustomer(id, name, phone, address)
        if (r == PersonEdit.DONE) onDone() else _editError.value = r.message("مشتری")
    }

    /** فقط مشتری‌ای که هنوز در کار و حساب نیامده. */
    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        val id = customerId.value ?: return@launch
        val r = repo.deleteCustomer(id)
        if (r == PersonEdit.DONE) onDeleted() else _editError.value = r.message("مشتری")
    }
}
