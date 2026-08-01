package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.entities.Tailor
import com.afghanjama.data.PartialFlow
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

/** یک سفارش با کارِ دوخته‌شده‌اش و تعدادی که آمادهٔ رفتن به نظارت است. */
data class SewnGroup(
    val order: Order,
    val done: List<SewingAssignment>,
    val readyQty: Int
)

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
    /**
     * کارِ دوخته‌شده‌ای که هنوز به نظارت نرفته — تبِ «دوخته شده».
     *
     * تا امروز کارِ تمام‌شدهٔ خیاط از «در حال دوخت» ناپدید می‌شد و
     * هیچ‌جا دیده نمی‌شد؛ فقط عددی پشتِ صحنه بود. کارگاه باید ببیند کدام
     * خیاط چند عدد تحویل داده و همان‌ها را — نه کلِ سفارش — به نظارت
     * بفرستد.
     *
     * `readyToSend` تعدادی را می‌دهد که هنوز نه دستِ نظارت است نه در
     * انبار؛ همان قاعده‌ای که خودِ ارسال هم از آن پیروی می‌کند، پس
     * صفحه و عمل هرگز از هم دور نمی‌افتند.
     */
    val sewnReady: StateFlow<List<SewnGroup>> =
        combine(cutDone, sewing, allAssignments) { cd, sw, assigns ->
            (cd + sw).mapNotNull { o ->
                val mine = assigns.filter {
                    it.orderId == o.id.toString() && it.status == "DONE"
                }
                if (mine.isEmpty()) return@mapNotNull null
                val ready = PartialFlow.readyToSend(
                    qty = o.qty,
                    sewn = mine.sumOf { it.qty },
                    inReview = o.reviewQty,
                    stored = o.storedQty
                )
                if (ready <= 0) null else SewnGroup(o, mine, ready)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
     * فرستادنِ هرچه تا این لحظه دوخته شده به نظارت — یک عدد، دو عدد، هر
     * چند تا که آماده است. باقی در دوخت می‌مانَد و بعداً با همین دکمه
     * جلو می‌رود.
     *
     * تعداد را `Repo` می‌شمارد نه اینجا: قاعده‌ای که در ViewModel زندگی
     * کند با هر صفحهٔ تازه یا مسیرِ همگام‌سازی دور زده می‌شود.
     */
    fun sendToReview(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        val problem = repo.sendOrderToReview(o)
        _message.value = problem
        if (problem == null) _goReview.value = true
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    /**
     * ارسال انجام شد، صفحه می‌تواند به نظارت برود.
     *
     * تا امروز صفحه بی‌قیدوشرط جابه‌جا می‌شد؛ وقتی ارسال انجام نمی‌شد،
     * کاربر در صفحهٔ نظارت می‌ماند و پیامِ دلیل را — که در تبِ دوخت
     * نوشته می‌شد — هرگز نمی‌دید.
     */
    private val _goReview = MutableStateFlow(false)
    val goReview: StateFlow<Boolean> = _goReview.asStateFlow()
    fun consumeGoReview() { _goReview.value = false }

    /** چند عدد از این سفارش واقعاً دوخته و تحویل شده. */
    fun sewnQty(orderCode: String, assigns: List<SewingAssignment>): Int =
        assigns.filter { it.orderCode == orderCode && it.status == "DONE" }.sumOf { it.qty }
}
