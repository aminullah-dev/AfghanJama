package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** یک سفارشِ آماده که هنوز دستِ مشتری نرسیده. */
data class DeliveryRow(
    val order: Order,
    /** چند روز است در انبار محصول منتظر مانده. */
    val waitingDays: Int,
    /** ماندهٔ حسابِ همین مشتری؛ مثبت یعنی او به ما بدهکار است. */
    val customerBalance: Long,
    /** بیعانهٔ استفاده‌نشدهٔ او. */
    val prepay: Long,
    /** موجودیِ انبار برای طرحِ این سفارش — مشترک بین سفارش‌های هم‌طرح. */
    val available: Int = 0
) {
    val remainingQty: Int get() = (order.qty - order.deliveredQty).coerceAtLeast(0)
    /** انبار برای تحویلِ کاملِ باقی‌مانده کم است. */
    val short: Boolean get() = available < remainingQty
}

data class DeliveryQueueUi(
    val rows: List<DeliveryRow> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false
)

/**
 * صفِ تحویل: هرچه دوخته و تأیید شده و صاحبش هنوز نبرده.
 *
 * این حالت تا حالا جایی دیده نمی‌شد؛ لباس در انبار می‌ماند، پولِ باقی‌مانده
 * وصول نمی‌شد و کسی خبر نداشت چند روز است معطل است. مرتب‌سازی از
 * قدیمی‌ترین است، چون همان است که باید زنگ زد.
 */
class DeliveryQueueViewModel(private val repo: Repo) : ViewModel() {

    /** جلوی ثبتِ دوباره با دو ضربهٔ سریع را می‌گیرد. */
    val busy = Busy()

    private val _ui = MutableStateFlow(DeliveryQueueUi())

    /** بیعانه‌های استفاده‌نشده به تفکیکِ نامِ مشتری. */
    private val prepays = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** موجودیِ انبار به تفکیکِ «طرح|سایز» — برای نشان‌دادن پیش از تحویل. */
    private val stock = MutableStateFlow<Map<String, Int>>(emptyMap())

    val ui: StateFlow<DeliveryQueueUi> =
        combine(
            repo.observeOrdersByStatus(OrderStatus.STORED.name),
            repo.observeLedgerBalances(),
            repo.observeFinishedStock(),
            prepays,
            _ui
        ) { orders, balances, stockRows, prepayMap, base ->
            val byDesign = stockRows.associate { "${it.name}|${it.size}" to it.qty }
            val now = System.currentTimeMillis()
            val byCustomer = balances.filter { it.type == "CUSTOMER" }
                .associate { it.name.trim() to it.net }

            val rows = orders
                .filter { it.customerName.isNotBlank() }
                .map { o ->
                    val since = if (o.stageChangedAt > 0) o.stageChangedAt else o.createdAt
                    DeliveryRow(
                        order = o,
                        waitingDays = ((now - since) / 86_400_000L).toInt().coerceAtLeast(0),
                        customerBalance = byCustomer[o.customerName.trim()] ?: 0L,
                        prepay = prepayMap[o.customerName.trim()] ?: 0L,
                        available = byDesign["${o.designTitle}|${o.size}"] ?: 0
                    )
                }
                .sortedByDescending { it.waitingDays }

            base.copy(rows = rows)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeliveryQueueUi())

    /**
     * بیعانهٔ یک مشتری را می‌خواند. عمداً فقط هنگامِ بازکردنِ دیالوگ صدا
     * زده می‌شود، نه برای کلِ صف — صفِ بلند نباید برای هر سطر یک کوئریِ
     * جداگانه بزند.
     */
    fun lookupPrepay(customerName: String) = viewModelScope.launch {
        val name = customerName.trim()
        if (name.isBlank()) return@launch
        prepays.update { it + (name to repo.customerPrepayBalance(name)) }
    }

    fun deliver(
        orderId: UUID,
        qty: Int,
        unitPrice: Long,
        receivedNow: Long,
        applyPrepay: Long
    ) =
        viewModelScope.launch {
            busy.once {
                doDeliver(
                    orderId = orderId,
                    qty = qty,
                    unitPrice = unitPrice,
                    receivedNow = receivedNow,
                    applyPrepay = applyPrepay
                )
            }
        }

    private suspend fun doDeliver(
        orderId: UUID,
        qty: Int,
        unitPrice: Long,
        receivedNow: Long,
        applyPrepay: Long
    ) {
        val o = repo.getOrder(orderId) ?: return
        val err = repo.deliverOrderToCustomer(o, qty, unitPrice, receivedNow, applyPrepay)
        _ui.update {
            if (err == null) it.copy(message = "✅ ${o.customerName} تحویل گرفت.", isError = false)
            else it.copy(message = err, isError = true)
        }
        if (err == null) prepays.update { it - o.customerName.trim() }
    }

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    /** برای دیالوگ: بیعانهٔ خوانده‌شدهٔ همین مشتری. */
    val prepayOf: StateFlow<Map<String, Long>> = prepays.asStateFlow()
}
