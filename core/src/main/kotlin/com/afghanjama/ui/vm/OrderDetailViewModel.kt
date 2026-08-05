package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStageLog
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class OrderDetailUi(
    val message: String? = null,
    val isError: Boolean = false,
    val deleted: Boolean = false
)

/** جزئیات کامل یک سفارش: مشخصات + تایم‌لاین مراحل + پرداخت‌ها + برگشت فروش. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModel(private val repo: Repo) : ViewModel() {

    /**
     * کدِ طرحِ هر سفارش (DIP-12) — کلید: کدِ سفارش.
     * تعریفش یک‌جا در `Repo` است تا صفحه‌ها از هم جدا نیفتند.
     */
    val designCodeByOrder: StateFlow<Map<String, String>> =
        repo.observeDesignCodeByOrder()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val orderId = MutableStateFlow<UUID?>(null)

    fun open(id: UUID) {
        orderId.value = id
        _ui.value = OrderDetailUi()
    }

    val order: StateFlow<Order?> =
        orderId.filterNotNull()
            .flatMapLatest { repo.observeOrderById(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stageLogs: StateFlow<List<OrderStageLog>> =
        orderId.filterNotNull()
            .flatMapLatest { repo.observeStageLogs(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cuttingRecords: StateFlow<List<com.afghanjama.data.entities.CuttingRecord>> =
        orderId.filterNotNull()
            .flatMapLatest { repo.observeCuttingForOrder(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** اندازه‌های مشتریِ همین سفارش — تا برای دیدن‌شان لازم نباشد صفحه عوض شود. */
    val measurements: StateFlow<List<Pair<String, String>>> =
        combine(order, repo.observeMeasurementsByCustomer()) { o, byCustomer ->
            byCustomer[o?.customerName?.trim().orEmpty()].orEmpty()
                .map { it.label to it.value }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photos: StateFlow<List<com.afghanjama.data.entities.OrderPhoto>> =
        orderId.filterNotNull()
            .flatMapLatest { repo.observeOrderPhotos(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addPhoto(fileName: String) = viewModelScope.launch {
        val o = order.value ?: return@launch
        repo.addOrderPhoto(o, fileName)
    }

    fun deletePhoto(
        photo: com.afghanjama.data.entities.OrderPhoto,
        deleteFile: (String) -> Unit
    ) = viewModelScope.launch {
        repo.deleteOrderPhoto(photo, deleteFile)
    }

    val qcRecords: StateFlow<List<com.afghanjama.data.entities.QcRecord>> =
        orderId.filterNotNull()
            .flatMapLatest { repo.observeQcForOrder(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fabrics: StateFlow<List<com.afghanjama.data.entities.OrderFabric>> =
        orderId.filterNotNull()
            .flatMapLatest { repo.observeOrderFabrics(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workItems: StateFlow<List<com.afghanjama.data.entities.OrderWorkItem>> =
        orderId.filterNotNull()
            .flatMapLatest { repo.observeOrderWorkItems(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val assignments: StateFlow<List<com.afghanjama.data.entities.SewingAssignment>> =
        orderId.filterNotNull()
            .flatMapLatest { repo.observeAssignmentsForOrder(it.toString()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val payments: StateFlow<List<CustomerPayment>> =
        combine(orderId.filterNotNull(), repo.observeCustomerPayments()) { id, all ->
            all.filter { it.orderId == id.toString() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(OrderDetailUi())
    val ui: StateFlow<OrderDetailUi> = _ui

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    /**
     * ویرایش مشخصات سفارش (فقط مدیر). مرحله، مالیات‌ها و جدول‌های فرزند
     * دست نمی‌خورند — فقط مشخصات ظاهری/قراردادی.
     */
    fun updateDetails(
        designTitle: String,
        size: String,
        customerName: String,
        customerPhone: String,
        agreedPrice: Long,
        dueDate: Long
    ) = viewModelScope.launch {
        val id = orderId.value ?: return@launch
        val o = repo.getOrder(id) ?: return@launch

        if (designTitle.isBlank()) {
            _ui.update { it.copy(message = "نام طرح نمی‌تواند خالی باشد.", isError = true) }
            return@launch
        }
        repo.updateOrder(
            o.copy(
                designTitle = designTitle.trim(),
                size = size.trim(),
                customerName = customerName.trim(),
                customerPhone = customerPhone.trim(),
                agreedPrice = agreedPrice,
                dueDate = dueDate
            )
        )

        // همگام‌سازیِ دفتر کل: بدهیِ مشتری بابتِ سفارش (SALE_BILLING) با
        // نام/قیمتِ جدید — سندِ قبلی خنثی و سندِ جدید ثبت می‌شود.
        val newName = customerName.trim()
        if (o.customerName != newName || o.agreedPrice != agreedPrice) {
            if (o.customerName.isNotBlank() && o.agreedPrice > 0) {
                repo.postLedger(
                    "CUSTOMER", o.customerName, 0, o.agreedPrice,
                    "SALE_ADJUST", o.orderCode, "اصلاح مشخصات سفارش"
                )
            }
            if (newName.isNotBlank() && agreedPrice > 0) {
                repo.postLedger(
                    "CUSTOMER", newName, agreedPrice, 0,
                    "SALE_BILLING", o.orderCode, "اصلاح مشخصات سفارش"
                )
            }
        }
        _ui.update { it.copy(message = "مشخصات سفارش ذخیره شد.", isError = false) }
    }

    /**
     * حذف امن سفارش (فقط مدیر و فقط در مرحله انبار).
     * پارچه‌های «از موجودی» به انبار برمی‌گردند؛ تراکنش‌های مالی ثبت‌شده
     * (خرید پارچه، پیش‌پرداخت) عمداً حذف نمی‌شوند و در صورت نیاز باید
     * از تب کیف پول اصلاح شوند.
     */
    fun deleteOrder() = viewModelScope.launch {
        val id = orderId.value ?: return@launch
        val o = repo.getOrder(id) ?: return@launch

        if (o.status != OrderStatus.IN_STOCK.name) {
            _ui.update {
                it.copy(
                    message = "فقط سفارشی که هنوز در انبار است قابل حذف است. سفارش واردشده به خط تولید را نمی‌توان حذف کرد.",
                    isError = true
                )
            }
            return@launch
        }
        repo.deleteOrderWithStockReturn(o)
        _ui.update { it.copy(deleted = true) }
    }

    /** بیعانهٔ استفاده‌نشدهٔ مشتریِ همین سفارش — برای پیشنهادِ خودکار در تحویل. */
    private val _prepay = MutableStateFlow(0L)
    val prepay: StateFlow<Long> = _prepay

    /** موجودیِ انبار برای طرحِ همین سفارش. */
    private val _stockAvailable = MutableStateFlow(0)
    val stockAvailable: StateFlow<Int> = _stockAvailable

    fun lookupStock() = viewModelScope.launch {
        _stockAvailable.value = order.value?.let { repo.stockForOrder(it) } ?: 0
    }

    fun lookupPrepay() = viewModelScope.launch {
        val name = order.value?.customerName.orEmpty()
        _prepay.value = if (name.isBlank()) 0L else repo.customerPrepayBalance(name)
    }

    /**
     * تحویلِ سفارش به مشتری: فروش + خروج از انبار + وضعیتِ «تحویل شد».
     * پیغامِ خطا (اگر باشد) همان‌جا روی صفحه نشان داده می‌شود، چون تحویل
     * لحظه‌ای است که مشتری جلوی پیشخوان ایستاده و باید بداند چه شد.
     */
    fun deliverToCustomer(
        qty: Int,
        unitPrice: Long,
        receivedNow: Long,
        applyPrepay: Long
    ) = viewModelScope.launch {
        val o = order.value ?: return@launch
        val err = repo.deliverOrderToCustomer(o, qty, unitPrice, receivedNow, applyPrepay)
        _ui.update {
            if (err == null) it.copy(message = "سفارش تحویل مشتری شد.", isError = false)
            else it.copy(message = err, isError = true)
        }
        if (err == null) _prepay.value = 0L
    }

}
