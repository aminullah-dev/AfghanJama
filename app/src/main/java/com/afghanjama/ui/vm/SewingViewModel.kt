package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.entities.Tailor
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** یک سفارشِ آمادهٔ تحویل به خیاط، همراه با تحویل‌های ثبت‌شده و باقی‌مانده. */
data class OrderHandout(
    val order: Order,
    val assignments: List<SewingAssignment>,
    val handed: Int
) {
    val remaining: Int get() = (order.qty - handed).coerceAtLeast(0)
}

class SewingViewModel(
    private val repo: Repo
) : ViewModel() {

    private val cutDone: StateFlow<List<Order>> =
        repo.observeOrdersByStatus(OrderStatus.CUT_DONE.name)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sewing: StateFlow<List<Order>> =
        repo.observeOrdersByStatus(OrderStatus.SEWING.name)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** همهٔ تحویل‌ها — برای رسیدِ خیاط (سابقه/کارِ زیرِ دست) هم استفاده می‌شود. */
    val allAssignments: StateFlow<List<SewingAssignment>> =
        repo.observeAllAssignments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** کارمزدهای تسویه‌نشده — برای یادداشتِ مالیِ رسیدِ خیاط. */
    val pendingWages: StateFlow<List<com.afghanjama.data.entities.TailorWage>> =
        repo.observePendingWages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** سفارش‌هایی که هنوز پارچه/عدد باقی‌مانده برای تحویل دارند. */
    val handouts: StateFlow<List<OrderHandout>> =
        combine(cutDone, sewing, allAssignments) { cd, sw, assigns ->
            // هم CUT_DONE و هم SEWING ممکن است باقی‌مانده داشته باشند
            (cd + sw).map { o ->
                val list = assigns.filter { it.orderId == o.id.toString() }
                OrderHandout(o, list, list.sumOf { it.qty })
            }.filter { it.remaining > 0 || it.order.status == OrderStatus.CUT_DONE.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** تحویل‌های در حال دوخت (تب «در حال دوخت»). */
    val inProgress: StateFlow<List<SewingAssignment>> =
        repo.observeAssignmentsInProgress()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // لیست خیاط‌ها برای انتخاب هنگام تحویل
    val tailors: StateFlow<List<Tailor>> =
        repo.observeTailors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * اندازه‌های مشتریِ هر سفارش، کلید: کدِ سفارش. خیاط با کدِ سفارش کار
     * می‌کند نه با نامِ مشتری، پس همین‌جا ترجمه می‌شود تا صفحه و رسید
     * هر دو بدونِ جست‌وجوی دوباره به آن برسند.
     */
    val measurementsByOrder: StateFlow<Map<String, List<Pair<String, String>>>> =
        combine(
            repo.observeAllOrders(),
            repo.observeMeasurementsByCustomer()
        ) { orders, byCustomer ->
            orders.associate { o ->
                o.orderCode to byCustomer[o.customerName.trim()].orEmpty()
                    .map { it.label to it.value }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** تحویل بخشی از سفارش به یک خیاط با تعداد و کارمزد فی‌عدد. */
    fun handout(orderId: UUID, tailorLabel: String, qty: Int, unitWage: Long) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        if (tailorLabel.isBlank() || qty < 1) return@launch
        repo.handoutToTailor(o, tailorLabel, qty, unitWage)
    }

    /**
     * دوخت یک تحویل تمام شد. [deliveredQty] = هر مقدار که دوخته شده (تحویل
     * جزئی)؛ باقی‌مانده «در حال دوخت» می‌ماند. null یعنی کل تحویل.
     */
    fun completeAssignment(assignmentId: Long, quality: String = "", deliveredQty: Int? = null) =
        viewModelScope.launch {
            repo.completeAssignment(assignmentId, quality, deliveredQty)
        }

    /** لغو یک تحویل (اصلاح اشتباه). */
    fun cancelAssignment(assignmentId: Long) = viewModelScope.launch {
        repo.cancelAssignment(assignmentId)
    }

    /**
     * برگشت سفارش از آماده‌دوخت به برش (فقط وقتی هیچ تحویلی ثبت نشده).
     * خودِ شرط در `Repo` است، نه اینجا و نه در صفحه.
     */
    fun backToCutting(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.sendOrderBackToCutting(o)
    }

    /**
     * ارسال سفارش به نظارت در هر لحظه — بدون نیاز به تکمیل همه تحویل‌ها.
     * (هرچه آماده است جلو می‌رود؛ تحویل‌های در حال دوخت همچنان قابل تکمیل‌اند.)
     */
    /**
     * فرستادنِ سفارش به نظارت — فقط وقتی **همهٔ** عددهایش دوخته شده باشد.
     *
     * چرا این شرط: تأییدِ نظارت کلِ `order.qty` را وارد انبار محصول
     * می‌کند. تا وقتی آن اصلاح نشده، ارسالِ جزئی جنسِ خیالی می‌سازد —
     * ۱۰ عدد برش می‌خورد، خیاط ۵ تا می‌دوزد، ولی ۱۰ عدد وارد انبار
     * می‌شود و ۵ تایش وجود ندارد. بعد همان ۵ تای خیالی فروخته می‌شود.
     *
     * پس تا اصلاحِ کاملِ جریانِ جزئی، این در تنگ‌ترین حالتش بسته می‌مانَد.
     * شرحِ کامل در docs/KNOWN-ISSUE-partial-review.md.
     */
    fun sendToReview(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        if (o.status != OrderStatus.CUT_DONE.name && o.status != OrderStatus.SEWING.name) return@launch

        val sewn = repo.sewnQtyOfOrder(orderId.toString())
        if (sewn < o.qty) {
            _message.value = "هنوز همهٔ عددها دوخته نشده: ${sewn} از ${o.qty}. " +
                "تا دوختِ باقی صبر کنید — وگرنه تعدادِ نادوخته هم وارد انبار می‌شود."
            return@launch
        }
        repo.changeOrderStatus(o, OrderStatus.REVIEW.name)
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    /** چند عدد از این سفارش واقعاً دوخته و تحویل شده. */
    fun sewnQty(orderCode: String, assigns: List<SewingAssignment>): Int =
        assigns.filter { it.orderCode == orderCode && it.status == "DONE" }.sumOf { it.qty }
}
