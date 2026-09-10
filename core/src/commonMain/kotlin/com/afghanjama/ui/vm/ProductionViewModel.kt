package com.afghanjama.ui.vm

import com.afghanjama.util.nowMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.CodeGen
import com.afghanjama.data.entities.DesignItem
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.entities.SizeItem
import com.afghanjama.data.entities.WorkCost
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.decimalOnly
import com.afghanjama.ui.format.digitsOnly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** یک قلم ماده که از انبار برای تولید مصرف می‌شود. */
data class ConsumeLine(
    val name: String,
    val unit: String,
    val amount: Double,        // مقدار واردشده (فی‌عدد یا کل)
    val perPiece: Boolean,     // فی‌عدد است یا کل سفارش
    val avgPrice: Double       // میانگین قیمت هر واحد (برای بهای تمام‌شده)
)

/**
 * یک خرج‌کارِ انتخاب‌شده برای سفارش. [price] قیمتِ **فی‌عدد** است — همان
 * قراردادی که `OrderWorkItem` دارد و `Order.workCost` جمعِ کلش را نگه
 * می‌دارد.
 */
data class WorkLine(val title: String, val price: Long)

data class ProductionUi(
    val designTitle: String = "",
    val qty: String = "",              // خالی؛ کاربر وارد می‌کند
    val size: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val agreedPrice: String = "",
    /** مهلتِ تحویل بر حسبِ «چند روزِ دیگر» (خالی = بدون مهلت). */
    val dueDays: String = "",
    /** بیعانهٔ دریافتی هنگامِ ثبت (خالی = بدون بیعانه). */
    val deposit: String = "",
    /** خرج‌کار از کجا پرداخت می‌شود: WALLET / BANK / PROFIT / CREDIT. */
    val workCostSource: String = "WALLET",
    /** طرفِ حسابِ خرج‌کارِ نسیه — بی آن، بدهی تسویه‌نشدنی می‌مانَد. */
    val workCostPayee: String = "",

    // ویرایشگر ماده فعلی (انتخاب از انبار)
    val pickedName: String = "",
    val pickedUnit: String = "",
    val pickedAvgPrice: Double = 0.0,
    val amount: String = "",
    val amountPerPiece: Boolean = false,

    val lines: List<ConsumeLine> = emptyList(),

    /**
     * خرج‌کارهای انتخاب‌شده (دکمه، زیپ، لایی…). قیمتِ هر ردیف **فی‌عدد**
     * است، پس خرج‌کارِ کلِ سفارش = جمعِ قیمت‌ها × تعداد.
     */
    val workItems: List<WorkLine> = emptyList(),

    val message: String? = null,
    val isError: Boolean = false
)

/**
 * ثبت سفارش تولید که مواد را از انبار عمومی مصرف می‌کند.
 * چون مواد قبلاً هنگام خرید پرداخت شده‌اند، اینجا پول دوباره کم نمی‌شود؛
 * فقط موجودی انبار کاهش می‌یابد و بهای تمام‌شده در سفارش ثبت می‌گردد.
 * سفارش وارد مرحلهٔ انبار (IN_STOCK) می‌شود و در پایپلاین فعلی حرکت می‌کند.
 */
class ProductionViewModel(private val repo: Repo) : ViewModel() {

    /** مواد موجود در انبار برای انتخاب. */
    val materials: StateFlow<List<MaterialStock>> =
        repo.observeMaterialStock()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** طرح‌های ازقبل‌تعریف‌شده برای انتخابِ «نام طرح/محصول». */
    val designs: StateFlow<List<DesignItem>> =
        repo.observeDesignItems()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** خرج‌کارهای ازقبل‌تعریف‌شده در «اطلاعات پایه» (دکمه، زیپ، لایی…). */
    val workCosts: StateFlow<List<WorkCost>> =
        repo.observeWorkCosts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** سایزهای ازقبل‌تعریف‌شده (منبعِ واحدِ حقیقت برای سایز). */
    val sizes: StateFlow<List<SizeItem>> =
        repo.observeSizes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** شمارندهٔ محصولِ هر طرح تا این لحظه: نام طرح → مجموع تعداد. */
    val designCounts: StateFlow<Map<String, Int>> =
        repo.observeDesignProduction()
            .map { list -> list.associate { it.title to it.total } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** افزودنِ همان‌لحظه‌ایِ طرح تایپ‌شده به کاتالوگ (دکمهٔ +) با کدِ کارگاه. */
    fun addDesignToCatalog(title: String, code: String = "") = viewModelScope.launch {
        val t = title.trim()
        if (t.isNotBlank()) {
            repo.addDesign(DesignItem(title = t, code = code.trim()))
            _ui.update { it.copy(designTitle = t, message = null, isError = false) }
        }
    }

    /** افزودنِ همان‌لحظه‌ایِ سایز تایپ‌شده به کاتالوگ (دکمهٔ +). */
    fun addSizeToCatalog(title: String) = viewModelScope.launch {
        val t = title.trim()
        if (t.isNotBlank()) {
            repo.addSize(SizeItem(title = t))
            _ui.update { it.copy(size = t, message = null, isError = false) }
        }
    }

    private val _ui = MutableStateFlow(ProductionUi())
    val ui: StateFlow<ProductionUi> = _ui

    fun setDesignTitle(v: String) = _ui.update { it.copy(designTitle = v, message = null, isError = false) }
    fun setQty(v: String) = _ui.update { it.copy(qty = v.digitsOnly(), message = null, isError = false) }
    fun setSize(v: String) = _ui.update { it.copy(size = v, message = null, isError = false) }
    fun setCustomerName(v: String) = _ui.update { it.copy(customerName = v, message = null, isError = false) }
    fun setCustomerPhone(v: String) = _ui.update { it.copy(customerPhone = v, message = null, isError = false) }
    fun setAgreedPrice(v: String) = _ui.update { it.copy(agreedPrice = v.digitsOnly(), message = null, isError = false) }
    fun setDueDays(v: String) = _ui.update { it.copy(dueDays = v.digitsOnly(), message = null, isError = false) }
    fun setWorkCostSource(v: String) =
        _ui.update { it.copy(workCostSource = v.trim().uppercase(), message = null, isError = false) }

    fun setWorkCostPayee(v: String) =
        _ui.update { it.copy(workCostPayee = v, message = null, isError = false) }

    fun setDeposit(v: String) = _ui.update { it.copy(deposit = v.digitsOnly(), message = null, isError = false) }

    /** انتخاب یک ماده از انبار برای ویرایشگر فعلی. */
    fun pickMaterial(m: MaterialStock) = _ui.update {
        it.copy(pickedName = m.name, pickedUnit = m.unit, pickedAvgPrice = m.avgPrice, message = null, isError = false)
    }

    fun setAmount(v: String) = _ui.update { it.copy(amount = v.decimalOnly(), message = null, isError = false) }
    fun setAmountPerPiece(b: Boolean) = _ui.update { it.copy(amountPerPiece = b, message = null, isError = false) }

    fun addLine() {
        val s = _ui.value
        if (s.pickedName.isBlank() || s.pickedUnit.isBlank()) {
            _ui.update { it.copy(message = "یک ماده از انبار انتخاب کنید.", isError = true) }
            return
        }
        val amount = s.amount.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _ui.update { it.copy(message = "مقدار مصرف را وارد کنید.", isError = true) }
            return
        }
        _ui.update {
            it.copy(
                lines = it.lines + ConsumeLine(s.pickedName, s.pickedUnit, amount, s.amountPerPiece, s.pickedAvgPrice),
                pickedName = "", pickedUnit = "", pickedAvgPrice = 0.0,
                amount = "", amountPerPiece = false,
                message = null, isError = false
            )
        }
    }

    fun removeLine(index: Int) = _ui.update {
        if (index in it.lines.indices) it.copy(lines = it.lines.toMutableList().apply { removeAt(index) })
        else it
    }

    /**
     * افزودن یا برداشتنِ یک خرج‌کار از فهرستِ الگوها.
     *
     * قیمت از همان الگو گرفته می‌شود، نه از دستِ کاربر: اگر قیمتِ الگو
     * عوض شد، سفارشِ تازه قیمتِ تازه را می‌گیرد و سفارشِ قدیم قیمتِ خودش
     * را نگه می‌دارد، چون در `order_work_items` کپی می‌شود.
     */
    fun toggleWorkItem(template: WorkCost) = _ui.update { s ->
        val exists = s.workItems.any { it.title == template.title }
        s.copy(
            workItems =
                if (exists) s.workItems.filterNot { it.title == template.title }
                else s.workItems + WorkLine(template.title, template.price),
            message = null,
            isError = false
        )
    }

    /**
     * قیمتِ یک خرج‌کار **فقط برای همین سفارش**.
     *
     * فهرستِ «اطلاعات پایه» دست نمی‌خورد: `WorkLine` از اول کپیِ خودِ
     * سفارش بوده، پس عوض کردنش اینجا قیمتِ پایه را برای سفارش‌های بعدی
     * تغییر نمی‌دهد.
     *
     * چرا لازم است: قیمتِ دکمه و زیپ در بازار بالا و پایین می‌رود و
     * کارگاه نباید برای یک سفارش مجبور شود فهرستِ پایه را دستکاری کند —
     * آن کار قیمتِ سفارش‌های قبلی را هم در گزارش‌ها جابه‌جا می‌کرد.
     */
    fun setWorkItemPrice(title: String, price: Long) = _ui.update { s ->
        s.copy(
            workItems = s.workItems.map {
                if (it.title == title) it.copy(price = price.coerceAtLeast(0)) else it
            },
            message = null,
            isError = false
        )
    }

    fun removeWorkItem(title: String) = _ui.update {
        it.copy(workItems = it.workItems.filterNot { w -> w.title == title })
    }

    fun completeProduction() = viewModelScope.launch {
        val s = _ui.value
        _ui.update { it.copy(message = null, isError = false) }

        val qtyInput = s.qty.toIntOrNull()
        if (qtyInput == null || qtyInput < 1) {
            _ui.update { it.copy(message = "تعداد سفارش را وارد کنید (حداقل ۱).", isError = true) }
            return@launch
        }
        val qty = qtyInput

        if (s.designTitle.isBlank()) {
            _ui.update { it.copy(message = "نام طرح/محصول را وارد کنید.", isError = true) }
            return@launch
        }

        val lines = s.lines.toMutableList()
        if (lines.isEmpty() && s.pickedName.isNotBlank()) {
            s.amount.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                lines.add(ConsumeLine(s.pickedName, s.pickedUnit, it, s.amountPerPiece, s.pickedAvgPrice))
            }
        }
        if (lines.isEmpty()) {
            _ui.update { it.copy(message = "حداقل یک ماده برای تولید از انبار انتخاب کنید.", isError = true) }
            return@launch
        }

        // کنترل موجودی همهٔ مواد قبل از کسر
        val resolved = ArrayList<OrderFabric>(lines.size)
        for (l in lines) {
            val totalAmount = if (l.perPiece) l.amount * qty else l.amount
            val stock = repo.getMaterialStock(l.name, l.unit)
            val available = stock?.amount ?: 0.0
            if (available < totalAmount) {
                _ui.update {
                    it.copy(
                        message = "موجودی «${l.name}» کافی نیست. موجود: $available — لازم: $totalAmount",
                        isError = true
                    )
                }
                return@launch
            }
            val cost = ((stock?.avgPrice ?: l.avgPrice) * totalAmount).toLong()
            resolved.add(
                OrderFabric(
                    orderId = "",
                    fabricType = l.name,
                    fabricColor = "—",
                    fabricUnit = l.unit,
                    amount = totalAmount,
                    price = cost,
                    source = "MATERIAL"
                )
            )
        }

        val materialsCost = resolved.sumOf { it.price }
        val agreedPrice = s.agreedPrice.toLongOrNull()?.coerceAtLeast(0) ?: 0L

        // قیمتِ هر خرج‌کار فی‌عدد است، پس در تعداد ضرب می‌شود. همین جمع در
        // Order.workCost می‌نشیند تا صفحه‌های دیگر و بهای تمام‌شده بی‌تغییر
        // کار کنند، و ردیف‌هایش جدا در order_work_items می‌مانند.
        val workLines = s.workItems.filter { it.price > 0 }
        val workCostTotal = workLines.sumOf { it.price } * qty

        val first = resolved.first()
        val order = Order(
            orderCode = CodeGen.makeOrderCode(nextNumber = repo.nextOrderNumber()),
            shortCode = CodeGen.makeShortCode(),
            designTitle = s.designTitle.trim(),
            qty = qty,
            fabricType = if (resolved.size > 1) "${first.fabricType} +${resolved.size - 1}" else first.fabricType,
            fabricColor = "مواد انبار",
            size = s.size.trim(),
            fabricUnit = first.fabricUnit,
            fabricAmount = first.amount,
            fabricSource = "MATERIAL",
            fabricPrice = materialsCost,
            workCost = workCostTotal,
            workCostSource = s.workCostSource,
            workCostPayee = s.workCostPayee.trim(),
            agreedPrice = agreedPrice,
            customerName = s.customerName.trim(),
            customerPhone = s.customerPhone.trim(),
            status = OrderStatus.IN_STOCK.name,
            stageChangedAt = nowMillis(),
            dueDate = s.dueDays.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.let { nowMillis() + it * 86_400_000L }
                ?: 0L
        )

        // مواد اینجا کسر نمی‌شوند؛ کسرِ واقعی از انبار هنگام «برش» انجام
        // می‌شود. سفارش با فهرست موادش (BOM) و بهای تمام‌شدهٔ برآوردی ثبت
        // می‌گردد و وارد انبار سفارش‌ها می‌شود.
        if (workCostTotal > 0 &&
            s.workCostSource.equals("CREDIT", true) &&
            s.workCostPayee.isBlank()
        ) {
            _ui.update {
                it.copy(
                    message = "برای خرج‌کارِ نسیه، نامِ طرفِ حساب لازم است — " +
                        "وگرنه بدهی بی‌صاحب می‌مانَد و هرگز تسویه نمی‌شود.",
                    isError = true
                )
            }
            return@launch
        }

        val saved = repo.createOrder(
            order,
            resolved,
            workLines.map { OrderWorkItem(orderId = "", title = it.title, price = it.price) }
        )
        if (!saved) {
            // خرج‌کارِ نقدی بیش از موجودیِ صندوق — هیچ چیز ثبت نشده
            val box = when (s.workCostSource.uppercase()) {
                "BANK" -> "بانک"
                "PROFIT" -> "فایده"
                else -> "کیف پول"
            }
            _ui.update {
                it.copy(
                    message = "موجودیِ $box برای خرج‌کارِ ${workCostTotal} ؋ کافی نیست. " +
                        "مبلغ را کم کنید یا «نسیه» را انتخاب کنید.",
                    isError = true
                )
            }
            return@launch
        }

        // بیعانه بعد از ثبتِ سفارش گرفته می‌شود تا بدهیِ مشتری اول ثبت
        // شده باشد و این دریافت آن را کم کند، نه اینکه طلبِ منفی بسازد.
        val deposit = s.deposit.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        if (deposit > 0) repo.recordOrderDeposit(order, deposit)

        _ui.update {
            ProductionUi(
                message = "سفارش تولید ثبت و وارد انبار شد. مواد هنگام «برش» از انبار کسر می‌شود." +
                    (if (deposit > 0) " بیعانه هم دریافت و رسیدش صادر شد." else ""),
                isError = false
            )
        }
    }
}
