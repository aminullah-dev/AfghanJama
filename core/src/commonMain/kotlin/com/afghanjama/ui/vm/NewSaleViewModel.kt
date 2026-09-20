package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * یک ردیفِ در حالِ ویرایشِ فاکتور. `item` تا وقتی کالا انتخاب نشده null
 * است تا ردیفِ خالی هم بتواند روی صفحه بنشیند (مثلِ دفترِ کاغذی).
 */
data class DraftLine(
    val key: Long,
    val item: FinishedStock? = null,
    val qtyText: String = "1",
    val priceText: String = ""
) {
    val qty: Int get() = qtyText.toIntOrNull() ?: 0
    val unitPrice: Long get() = priceText.toLongOrNull() ?: 0L
    val total: Long get() = qty * unitPrice
    val ready: Boolean get() = item != null && qty > 0 && unitPrice > 0
}

data class NewSaleUi(
    val lines: List<DraftLine> = listOf(DraftLine(key = 1)),
    val customer: String = "",
    val receivedText: String = "",
    val usePrepay: Boolean = true,
    val prepay: Long = 0L,
    val message: String? = null,
    val isError: Boolean = false,
    val savedCode: String? = null
) {
    val subtotal: Long get() = lines.filter { it.ready }.sumOf { it.total }
    val appliedPrepay: Long get() = if (usePrepay) prepay.coerceAtMost(subtotal) else 0L

    /** خالی یعنی «باقی‌مانده را کامل نقد گرفتم» — حالتِ عادیِ پیشخوان. */
    val received: Long
        get() = (receivedText.toLongOrNull() ?: (subtotal - appliedPrepay))
            .coerceIn(0L, subtotal - appliedPrepay)

    val remaining: Long get() = subtotal - appliedPrepay - received
    val readyLines: Int get() = lines.count { it.ready }
    val canSave: Boolean get() = readyLines > 0
}

/**
 * فاکتور فروشِ چندردیفی.
 *
 * تا حالا هر فروش فقط یک طرح داشت، پس مشتری‌ای که سه جنسِ مختلف می‌برد
 * سه فاکتور جدا می‌گرفت. اینجا یک فاکتور با هر تعداد ردیف ساخته می‌شود.
 */
class NewSaleViewModel(private val repo: Repo) : ViewModel() {

    val stock: StateFlow<List<FinishedStock>> =
        repo.observeFinishedStock()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(NewSaleUi())
    /**
     * مشتریانِ ثبت‌شده — برای انتخاب به‌جای تایپِ دوباره.
     *
     * نامی که دستی تایپ می‌شود هم مجاز می‌مانَد: پیشخوان همیشه مشتریِ
     * گذری دارد و نباید مجبور شود اول در «اطلاعات پایه» ثبتش کند.
     */
    val customers: StateFlow<List<String>> =
        repo.observeCustomers()
            .map { list -> list.map { it.name }.filter { it.isNotBlank() }.sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * آیا نامِ نوشته‌شده مشتریِ تازه‌ای است؟
     *
     * تا فروشنده بداند دارد مشتریِ جدید می‌سازد یا روی حسابِ مشتریِ قبلی
     * می‌نویسد — که وقتی دو نفر نامِ نزدیک دارند مهم است.
     */
    val customerIsNew: StateFlow<Boolean> =
        combine(_ui, customers) { u, list ->
            val typed = u.customer.trim()
            typed.isNotBlank() && list.none { it.trim().equals(typed, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val ui: StateFlow<NewSaleUi> = _ui.asStateFlow()

    private var nextKey = 2L

    /** جلوی ثبتِ دوباره با دو ضربهٔ سریع را می‌گیرد. */
    val busy = Busy()

    fun addLine() = _ui.update { it.copy(lines = it.lines + DraftLine(key = nextKey++)) }

    /**
     * افزودنِ یک کالای انبار به فاکتورِ در دست — نقطهٔ ورود از صفحهٔ انبار.
     *
     * اگر همان کالا از قبل در فاکتور باشد فقط تعدادش یکی زیاد می‌شود، پس
     * دو بار زدن روی یک کالا ردیفِ تکراری نمی‌سازد. وگرنه در اولین ردیفِ
     * خالی می‌نشیند و اگر خالی نبود ردیفِ تازه ساخته می‌شود — تا فاکتورِ
     * نصفه‌کارهٔ کاربر خراب نشود.
     */
    fun addItem(item: FinishedStock) = _ui.update { u ->
        val existing = u.lines.firstOrNull { it.item?.id == item.id }
        if (existing != null) {
            val next = ((existing.qtyText.toIntOrNull() ?: 0) + 1).coerceAtMost(item.qty)
            return@update u.copy(
                lines = u.lines.map {
                    if (it.key == existing.key) it.copy(qtyText = next.toString()) else it
                }
            )
        }
        val empty = u.lines.firstOrNull { it.item == null }
        if (empty != null) {
            u.copy(
                lines = u.lines.map { if (it.key == empty.key) it.copy(item = item) else it }
            )
        } else {
            u.copy(lines = u.lines + DraftLine(key = nextKey++, item = item))
        }
    }

    /** تعدادِ قلم‌های انتخاب‌شده — برای نشانِ کوچکِ روی دکمهٔ انبار. */
    val pickedCount: StateFlow<Int> = _ui
        .map { u -> u.lines.count { it.item != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun removeLine(key: Long) = _ui.update { u ->
        val left = u.lines.filterNot { it.key == key }
        // همیشه دستِ‌کم یک ردیف بماند تا صفحه خالی و بی‌معنا نشود
        u.copy(lines = left.ifEmpty { listOf(DraftLine(key = nextKey++)) })
    }

    fun setItem(key: Long, item: FinishedStock) = _ui.update { u ->
        u.copy(
            lines = u.lines.map {
                if (it.key != key) it
                // قیمت را خالی می‌گذاریم چون قیمتِ فروش تصمیمِ فروشنده است،
                // نه بهای تمام‌شده؛ پیشنهادِ خودکار فقط گمراه می‌کرد.
                else it.copy(item = item)
            }
        )
    }

    fun setQty(key: Long, text: String) = _ui.update { u ->
        u.copy(lines = u.lines.map { if (it.key == key) it.copy(qtyText = text) else it })
    }

    fun setPrice(key: Long, text: String) = _ui.update { u ->
        u.copy(lines = u.lines.map { if (it.key == key) it.copy(priceText = text) else it })
    }

    fun setCustomer(name: String) {
        _ui.update { it.copy(customer = name) }
        viewModelScope.launch {
            val p = if (name.isBlank()) 0L else repo.customerPrepayBalance(name)
            _ui.update { it.copy(prepay = p) }
        }
    }

    fun setReceived(text: String) = _ui.update { it.copy(receivedText = text) }
    fun setUsePrepay(v: Boolean) = _ui.update { it.copy(usePrepay = v) }
    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    /** ثبتِ فاکتور. هر ردیفِ ناقص بی‌سروصدا نادیده گرفته نمی‌شود — شمرده می‌شود. */
    fun save() = viewModelScope.launch { busy.once { doSave() } }

    private suspend fun doSave() {
        val u = _ui.value
        val ready = u.lines.filter { it.ready }
        if (ready.isEmpty()) {
            _ui.update { it.copy(message = "دستِ‌کم یک ردیفِ کامل لازم است.", isError = true) }
            return
        }
        val incomplete = u.lines.size - ready.size

        val over = ready.groupBy { it.item!!.id }
            .mapNotNull { (_, rows) ->
                val item = rows.first().item!!
                val need = rows.sumOf { it.qty }
                if (need > item.qty) "${item.name}: ${need} خواسته، ${item.qty} موجود" else null
            }
        if (over.isNotEmpty()) {
            _ui.update {
                it.copy(message = "موجودی کافی نیست — ${over.joinToString("، ")}", isError = true)
            }
            return
        }

        val ok = repo.sellInvoice(
            lines = ready.map { Repo.SaleLine(it.item!!, it.qty, it.unitPrice) },
            customerName = u.customer,
            receivedNow = u.received,
            applyPrepay = u.appliedPrepay
        )
        if (ok) {
            _ui.value = NewSaleUi(
                lines = listOf(DraftLine(key = nextKey++)),
                message = "فاکتور با ${ready.size} ردیف ثبت شد." +
                    (if (u.appliedPrepay > 0) " بیعانه هم اعمال شد." else "") +
                    (if (incomplete > 0) " ($incomplete ردیفِ ناقص ثبت نشد.)" else ""),
                isError = false
            )
        } else {
            _ui.update { it.copy(message = "ثبتِ فاکتور انجام نشد.", isError = true) }
        }
    }
}
