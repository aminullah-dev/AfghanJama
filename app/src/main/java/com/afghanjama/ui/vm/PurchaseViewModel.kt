package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.CodeGen
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.data.entities.PaymentSource
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.digitsOnly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** یک پارچهٔ افزوده‌شده به سفارش. */
data class FabricLine(
    val fabricType: String,
    val fabricColor: String,
    val fabricUnit: String,
    val amount: Double,
    val perPiece: Boolean,  // مقدار فی‌عدد است یا کل سفارش
    val price: Long,        // برای NEW: قیمت خرید
    val source: String      // NEW / STOCK
)

/** یک خرج‌کار افزوده‌شده به سفارش (دکمه، لایی چسب، ...). قیمت فی‌عدد. */
data class WorkItemLine(
    val title: String,
    val price: Long
)

data class PurchaseUi(
    val designTitle: String = "",
    val qty: String = "",   // خالی؛ کاربر باید تعداد را وارد کند

    val customerName: String = "",
    val customerPhone: String = "",

    // ---- ویرایشگر پارچهٔ فعلی (یک ردیف) ----
    val fabricType: String = "",
    val fabricColor: String = "",
    val size: String = "",
    val fabricUnit: String = "",
    val fabricAmount: String = "",
    // مقدار پارچه فی‌عدد است یا کل سفارش (پیش‌فرض: کل)
    val fabricAmountPerPiece: Boolean = false,
    val fabricPrice: String = "",
    // NEW = خرید پارچه جدید، STOCK = مصرف از موجودی انبار
    val fabricSource: String = "NEW",

    // ---- پارچه‌های افزوده‌شده به سفارش ----
    val fabrics: List<FabricLine> = emptyList(),

    // ویرایشگر خرج‌کار فعلی (انتخاب از کاتالوگ)
    val workCostTitle: String = "",
    val workCostPrice: Long = 0,
    // خرج‌کارهای افزوده‌شده به سفارش
    val workItems: List<WorkItemLine> = emptyList(),

    val paymentSource: String = PaymentSource.WALLET.name,
    val customerPaid: String = "",

    // قیمت فروش توافق‌شده با مشتری (اختیاری)
    val agreedPrice: String = "",

    val message: String? = null,
    val isError: Boolean = false
)

class PurchaseViewModel(private val repo: Repo) : ViewModel() {

    val workCosts = repo.observeWorkCosts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _ui = MutableStateFlow(PurchaseUi())
    val ui: StateFlow<PurchaseUi> = _ui

    fun setDesignTitle(v: String) = _ui.update { it.copy(designTitle = v, message = null, isError = false) }
    fun setQty(v: String) = _ui.update { it.copy(qty = v.digitsOnly(), message = null, isError = false) }

    fun setCustomerName(v: String) = _ui.update { it.copy(customerName = v, message = null, isError = false) }
    fun setCustomerPhone(v: String) = _ui.update { it.copy(customerPhone = v, message = null, isError = false) }

    fun setFabricType(v: String) = _ui.update { it.copy(fabricType = v, message = null, isError = false) }
    fun setFabricColor(v: String) = _ui.update { it.copy(fabricColor = v, message = null, isError = false) }
    fun setSize(v: String) = _ui.update { it.copy(size = v, message = null, isError = false) }

    fun setFabricUnit(v: String) = _ui.update { it.copy(fabricUnit = v.trim(), message = null, isError = false) }
    fun setFabricAmount(v: String) = _ui.update { it.copy(fabricAmount = v, message = null, isError = false) }
    fun setFabricAmountPerPiece(b: Boolean) = _ui.update { it.copy(fabricAmountPerPiece = b, message = null, isError = false) }

    fun setFabricPrice(v: String) =
        _ui.update { it.copy(fabricPrice = v.digitsOnly(), message = null, isError = false) }

    fun setFabricSource(v: String) = _ui.update { it.copy(fabricSource = v.trim(), message = null, isError = false) }

    // خرج کار: انتخاب از کاتالوگ یا ورود دستی (زنده)
    fun pickWorkCost(title: String, price: Long) =
        _ui.update { it.copy(workCostTitle = title, workCostPrice = price, message = null, isError = false) }
    fun setWorkCostTitle(v: String) = _ui.update { it.copy(workCostTitle = v, message = null, isError = false) }
    fun setWorkCostPrice(v: String) =
        _ui.update { it.copy(workCostPrice = v.digitsOnly().toLongOrNull() ?: 0L, message = null, isError = false) }

    /** خرج‌کارِ انتخاب‌شدهٔ فعلی را به لیست سفارش اضافه می‌کند. */
    fun addWorkItem() {
        val s = _ui.value
        if (s.workCostTitle.isBlank() || s.workCostPrice <= 0L) {
            _ui.update { it.copy(message = "یک خرج‌کار از لیست انتخاب کنید.", isError = true) }
            return
        }
        _ui.update {
            it.copy(
                workItems = it.workItems + WorkItemLine(s.workCostTitle, s.workCostPrice),
                workCostTitle = "",
                workCostPrice = 0,
                message = null,
                isError = false
            )
        }
    }

    fun removeWorkItem(index: Int) = _ui.update {
        if (index in it.workItems.indices) it.copy(workItems = it.workItems.toMutableList().apply { removeAt(index) })
        else it
    }

    fun setPaymentSource(v: String) = _ui.update { it.copy(paymentSource = v.trim(), message = null, isError = false) }
    fun setAgreedPrice(v: String) =
        _ui.update { it.copy(agreedPrice = v.digitsOnly(), message = null, isError = false) }
    fun setCustomerPaid(v: String) =
        _ui.update { it.copy(customerPaid = v.digitsOnly(), message = null, isError = false) }

    /** ویرایشگر پارچهٔ فعلی را به لیست پارچه‌های سفارش اضافه می‌کند. */
    fun addFabricLine() {
        val s = _ui.value
        val line = buildLineFromEditor(s) ?: run {
            _ui.update { it.copy(message = "برای افزودن پارچه: نوع، رنگ، واحد و مقدار را کامل کنید.", isError = true) }
            return
        }
        _ui.update {
            it.copy(
                fabrics = it.fabrics + line,
                // پاک‌کردن ویرایشگر برای پارچهٔ بعدی
                fabricType = "",
                fabricColor = "",
                fabricUnit = "",
                fabricAmount = "",
                fabricAmountPerPiece = false,
                fabricPrice = "",
                fabricSource = "NEW",
                message = null,
                isError = false
            )
        }
    }

    fun removeFabricLine(index: Int) = _ui.update {
        if (index in it.fabrics.indices) it.copy(fabrics = it.fabrics.toMutableList().apply { removeAt(index) })
        else it
    }

    private fun buildLineFromEditor(s: PurchaseUi): FabricLine? {
        if (s.fabricType.isBlank() || s.fabricColor.isBlank() || s.fabricUnit.isBlank()) return null
        val amount = s.fabricAmount.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null
        val price = if (s.fabricSource == "STOCK") 0L else s.fabricPrice.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        return FabricLine(
            fabricType = s.fabricType.trim(),
            fabricColor = s.fabricColor.trim(),
            fabricUnit = s.fabricUnit.trim(),
            amount = amount,
            perPiece = s.fabricAmountPerPiece,
            price = price,
            source = s.fabricSource
        )
    }

    fun completePurchase() = viewModelScope.launch {
        val s = _ui.value
        _ui.update { it.copy(message = null, isError = false) }

        // تعداد باید توسط کاربر وارد شود (بدون پیش‌فرض)
        val qtyInput = s.qty.toIntOrNull()
        if (qtyInput == null || qtyInput < 1) {
            _ui.update { it.copy(message = "تعداد سفارش را وارد کنید (حداقل ۱).", isError = true) }
            return@launch
        }
        val qty = qtyInput

        // پارچه‌ها: لیست افزوده‌شده + در صورت خالی‌بودن، ویرایشگر فعلی
        val lines = s.fabrics.toMutableList()
        if (lines.isEmpty()) {
            val editorLine = buildLineFromEditor(s)
            if (editorLine != null) lines.add(editorLine)
        }
        if (lines.isEmpty()) {
            _ui.update { it.copy(message = "حداقل یک پارچه برای سفارش اضافه کنید.", isError = true) }
            return@launch
        }

        // بهای هر پارچه: NEW از ورودی، STOCK از میانگین انبار + کنترل موجودی
        val resolved = ArrayList<OrderFabric>(lines.size)
        var payNow = 0L
        for (l in lines) {
            val fromStock = l.source == "STOCK"
            // مقدار مؤثر کل: اگر فی‌عدد باشد در تعداد ضرب می‌شود
            val totalAmount = if (l.perPiece) l.amount * qty else l.amount
            val price: Long
            if (fromStock) {
                val stock = repo.getFabricStock(l.fabricType, l.fabricColor, l.fabricUnit)
                val available = stock?.amount ?: 0.0
                if (available < totalAmount) {
                    _ui.update {
                        it.copy(
                            message = "موجودی «${l.fabricType} ${l.fabricColor}» کافی نیست. موجود: $available — لازم: $totalAmount",
                            isError = true
                        )
                    }
                    return@launch
                }
                price = ((stock?.avgPrice ?: 0.0) * totalAmount).toLong()
                // پارچهٔ از موجودی قبلاً پرداخت شده؛ به payNow اضافه نمی‌شود
            } else {
                price = l.price
                payNow += l.price
            }
            resolved.add(
                OrderFabric(
                    orderId = "",
                    fabricType = l.fabricType,
                    fabricColor = l.fabricColor,
                    fabricUnit = l.fabricUnit,
                    amount = totalAmount,   // مقدار کل مصرف ذخیره می‌شود
                    price = price,
                    source = l.source
                )
            )
        }

        val fabricPriceTotal = resolved.sumOf { it.price }

        // خرج‌کارها: لیست افزوده‌شده + ویرایشگر فعلی (اگر انتخاب شده و اضافه نشده)
        val workLines = s.workItems.toMutableList()
        if (s.workCostTitle.isNotBlank() && s.workCostPrice > 0L) {
            workLines.add(WorkItemLine(s.workCostTitle, s.workCostPrice))
        }
        // ✅ خرج کار فی‌عدد است و در تعداد ضرب می‌شود
        val workPerPiece = workLines.sumOf { it.price }
        val workCostTotal = workPerPiece * qty
        payNow += workCostTotal
        val cost = payNow

        val agreedPrice = s.agreedPrice.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        val customerPaid = s.customerPaid.toLongOrNull()?.coerceAtLeast(0) ?: 0L

        val paySrc = runCatching { PaymentSource.valueOf(s.paymentSource.trim().uppercase()) }
            .getOrNull() ?: PaymentSource.WALLET

        val wallet = repo.observeWalletBalance().first()
        val profit = repo.observeProfitBalance().first()
        val bank = repo.observeBankBalance().first()

        val canPay = when (paySrc) {
            PaymentSource.WALLET -> wallet >= cost
            PaymentSource.PROFIT -> profit >= cost
            PaymentSource.BANK -> bank >= cost
            PaymentSource.CUSTOMER -> true
        }

        if (!canPay) {
            val srcLabel = when (paySrc) {
                PaymentSource.WALLET -> "کیف پول"
                PaymentSource.PROFIT -> "فایده"
                PaymentSource.BANK -> "بانک"
                PaymentSource.CUSTOMER -> "مشتری"
            }
            _ui.update { it.copy(message = "موجودی $srcLabel کافی نیست. هزینه: $cost ؋", isError = true) }
            return@launch
        }

        val orderCode = CodeGen.makeOrderCode(nextNumber = repo.nextOrderNumber())
        val shortCode = CodeGen.makeShortCode()

        // فیلدهای تک‌پارچهٔ سفارش = خلاصه (اولین پارچه + جمع قیمت) تا صفحات دیگر کار کنند
        val first = resolved.first()
        val order = Order(
            orderCode = orderCode,
            shortCode = shortCode,
            designTitle = s.designTitle.trim(),
            qty = qty,
            fabricType = if (resolved.size > 1) "${first.fabricType} +${resolved.size - 1}" else first.fabricType,
            fabricColor = if (resolved.size > 1) "چند رنگ" else first.fabricColor,
            size = s.size.trim(),
            fabricUnit = first.fabricUnit,
            fabricAmount = first.amount,
            fabricSource = if (resolved.all { it.source == "STOCK" }) "STOCK" else "NEW",
            fabricPrice = fabricPriceTotal,
            workCost = workCostTotal,
            agreedPrice = agreedPrice,
            customerName = s.customerName.trim(),
            customerPhone = s.customerPhone.trim(),
            status = OrderStatus.IN_STOCK.name,
            stageChangedAt = System.currentTimeMillis()
        )

        if (payNow > 0) {
            when (paySrc) {
                PaymentSource.WALLET -> repo.spend("WALLET", payNow, "خرید/ثبت سفارش ${order.orderCode}")
                PaymentSource.PROFIT -> repo.spend("PROFIT", payNow, "خرید/ثبت سفارش ${order.orderCode}")
                PaymentSource.BANK -> repo.spend("BANK", payNow, "خرید/ثبت سفارش ${order.orderCode}")
                PaymentSource.CUSTOMER -> { /* فعلاً هیچ */ }
            }
        }

        // کسر پارچه‌های «از موجودی» از انبار
        resolved.filter { it.source == "STOCK" }.forEach {
            repo.changeFabricStock(it.fabricType, it.fabricColor, it.fabricUnit, -it.amount)
        }

        if (customerPaid > 0) {
            repo.income("WALLET", customerPaid, "پرداخت مشتری برای سفارش ${order.orderCode}")
            repo.addCustomerPayment(
                CustomerPayment(
                    orderId = order.id.toString(),
                    customerName = order.customerName,
                    amount = customerPaid,
                    source = "ADVANCE",
                    note = "پیش‌پرداخت سفارش ${order.orderCode}"
                )
            )
        }

        val workItemRows = workLines.map { OrderWorkItem(orderId = "", title = it.title, price = it.price) }
        repo.createOrder(order, resolved, workItemRows)

        _ui.update { PurchaseUi(message = "✅ خرید ثبت شد و سفارش وارد انبار شد.", isError = false) }
    }
}
