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
    val isError: Boolean = false
)

/** جزئیات کامل یک سفارش: مشخصات + تایم‌لاین مراحل + پرداخت‌ها + برگشت فروش. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModel(private val repo: Repo) : ViewModel() {

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
     * برگشت فروش: مبلغ برگشتی از کیف پول خارج، در حساب مشتری با مقدار
     * منفی ثبت و سفارش دوباره به مرحله فروش برمی‌گردد.
     */
    fun returnSale(refund: Long) = viewModelScope.launch {
        val id = orderId.value ?: return@launch
        val o = repo.getOrder(id) ?: return@launch

        if (o.status != OrderStatus.SENT.name) {
            _ui.update { it.copy(message = "فقط سفارش تحویل‌شده قابل برگشت است.", isError = true) }
            return@launch
        }
        if (refund <= 0L) {
            _ui.update { it.copy(message = "مبلغ برگشتی معتبر نیست.", isError = true) }
            return@launch
        }

        repo.spend("WALLET", refund, "برگشتی فروش سفارش ${o.orderCode}", category = "برگشتی فروش")
        repo.addCustomerPayment(
            CustomerPayment(
                orderId = o.id.toString(),
                customerName = o.customerName,
                amount = -refund,
                source = "RETURN",
                note = "برگشتی فروش سفارش ${o.orderCode}"
            )
        )
        repo.changeOrderStatus(o, OrderStatus.SALES.name)

        _ui.update {
            it.copy(
                message = "✅ برگشت فروش ثبت شد. اگر سود این فروش قبلاً به فایده منتقل شده، آن را از تب کیف پول اصلاح کنید.",
                isError = false
            )
        }
    }
}
