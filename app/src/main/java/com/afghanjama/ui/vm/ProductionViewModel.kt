package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.CodeGen
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.decimalOnly
import com.afghanjama.ui.format.digitsOnly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

data class ProductionUi(
    val designTitle: String = "",
    val qty: String = "",              // خالی؛ کاربر وارد می‌کند
    val size: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val agreedPrice: String = "",

    // ویرایشگر ماده فعلی (انتخاب از انبار)
    val pickedName: String = "",
    val pickedUnit: String = "",
    val pickedAvgPrice: Double = 0.0,
    val amount: String = "",
    val amountPerPiece: Boolean = false,

    val lines: List<ConsumeLine> = emptyList(),

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

    private val _ui = MutableStateFlow(ProductionUi())
    val ui: StateFlow<ProductionUi> = _ui

    fun setDesignTitle(v: String) = _ui.update { it.copy(designTitle = v, message = null, isError = false) }
    fun setQty(v: String) = _ui.update { it.copy(qty = v.digitsOnly(), message = null, isError = false) }
    fun setSize(v: String) = _ui.update { it.copy(size = v, message = null, isError = false) }
    fun setCustomerName(v: String) = _ui.update { it.copy(customerName = v, message = null, isError = false) }
    fun setCustomerPhone(v: String) = _ui.update { it.copy(customerPhone = v, message = null, isError = false) }
    fun setAgreedPrice(v: String) = _ui.update { it.copy(agreedPrice = v.digitsOnly(), message = null, isError = false) }

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
            workCost = 0,
            agreedPrice = agreedPrice,
            customerName = s.customerName.trim(),
            customerPhone = s.customerPhone.trim(),
            status = OrderStatus.IN_STOCK.name,
            stageChangedAt = System.currentTimeMillis()
        )

        // کسر مواد از انبار (مواد قبلاً پرداخت شده‌اند؛ پول دوباره کم نمی‌شود)
        resolved.forEach {
            repo.changeMaterialStock(
                it.fabricType, it.fabricUnit, -it.amount,
                reason = "مصرف تولید", note = "سفارش ${order.orderCode}"
            )
        }

        repo.createOrder(order, resolved, emptyList())

        _ui.update {
            ProductionUi(message = "✅ سفارش تولید ثبت شد و مواد از انبار کسر و وارد مرحلهٔ انبار شد.", isError = false)
        }
    }
}
