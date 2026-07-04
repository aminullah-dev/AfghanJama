package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.CodeGen
import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.PaymentSource
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PurchaseUi(
    val designTitle: String = "",
    val qty: String = "1",

    val customerName: String = "",
    val customerPhone: String = "",

    val fabricType: String = "",
    val fabricColor: String = "",
    val size: String = "",

    // ✅ بدون پیش‌فرض: کاربر باید انتخاب کند
    val fabricUnit: String = "",
    val fabricAmount: String = "",

    val fabricPrice: String = "",
    val workCostTitle: String = "",
    val workCostPrice: Long = 0,

    val paymentSource: String = PaymentSource.WALLET.name,
    val customerPaid: String = "",

    // NEW = خرید پارچه جدید برای سفارش، STOCK = مصرف از موجودی انبار
    val fabricSource: String = "NEW",
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
    fun setQty(v: String) = _ui.update { it.copy(qty = v.filter(Char::isDigit).ifBlank { "1" }, message = null, isError = false) }

    fun setCustomerName(v: String) = _ui.update { it.copy(customerName = v, message = null, isError = false) }
    fun setCustomerPhone(v: String) = _ui.update { it.copy(customerPhone = v, message = null, isError = false) }

    fun setFabricType(v: String) = _ui.update { it.copy(fabricType = v, message = null, isError = false) }
    fun setFabricColor(v: String) = _ui.update { it.copy(fabricColor = v, message = null, isError = false) }
    fun setSize(v: String) = _ui.update { it.copy(size = v, message = null, isError = false) }

    fun setFabricUnit(v: String) = _ui.update { it.copy(fabricUnit = v.trim(), message = null, isError = false) }
    fun setFabricAmount(v: String) = _ui.update { it.copy(fabricAmount = v, message = null, isError = false) }

    fun setFabricPrice(v: String) =
        _ui.update { it.copy(fabricPrice = v.filter(Char::isDigit), message = null, isError = false) }

    fun pickWorkCost(title: String, price: Long) =
        _ui.update { it.copy(workCostTitle = title, workCostPrice = price, message = null, isError = false) }

    fun setPaymentSource(v: String) = _ui.update { it.copy(paymentSource = v.trim(), message = null, isError = false) }
    fun setFabricSource(v: String) = _ui.update { it.copy(fabricSource = v.trim(), message = null, isError = false) }
    fun setAgreedPrice(v: String) =
        _ui.update { it.copy(agreedPrice = v.filter(Char::isDigit), message = null, isError = false) }
    fun setCustomerPaid(v: String) =
        _ui.update { it.copy(customerPaid = v.filter(Char::isDigit), message = null, isError = false) }

    fun completePurchase() = viewModelScope.launch {
        val s = _ui.value
        _ui.update { it.copy(message = null, isError = false) }

        if (s.fabricUnit.isBlank()) {
            _ui.update { it.copy(message = "واحد اندازه‌گیری را انتخاب کنید (متر/یارد).", isError = true) }
            return@launch
        }

        val qty = s.qty.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val fromStock = s.fabricSource == "STOCK"
        // پارچهٔ از موجودی، قیمت خرید جداگانه ندارد
        val fabricPrice = if (fromStock) 0L else s.fabricPrice.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        val fabricAmount = s.fabricAmount.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val customerPaid = s.customerPaid.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        val agreedPrice = s.agreedPrice.toLongOrNull()?.coerceAtLeast(0) ?: 0L

        if (fabricAmount <= 0.0) {
            _ui.update { it.copy(message = "مقدار پارچه را وارد کنید (بزرگ‌تر از صفر).", isError = true) }
            return@launch
        }

        // مصرف از موجودی انبار: اول کنترل موجودی
        if (fromStock) {
            val stock = repo.getFabricStock(s.fabricType.trim(), s.fabricColor.trim(), s.fabricUnit.trim())
            val available = stock?.amount ?: 0.0
            if (available < fabricAmount) {
                _ui.update {
                    it.copy(
                        message = "موجودی پارچه کافی نیست. موجود: $available — لازم: $fabricAmount",
                        isError = true
                    )
                }
                return@launch
            }
        }

        // ✅ خرج کار برای هر عدد است و در تعداد ضرب می‌شود
        val workCostTotal = s.workCostPrice * qty
        val cost = (fabricPrice + workCostTotal).coerceAtLeast(0)

        val paySrc = runCatching { PaymentSource.valueOf(s.paymentSource.trim().uppercase()) }
            .getOrNull() ?: PaymentSource.WALLET

        val wallet = repo.observeWalletBalance().first()
        val profit = repo.observeProfitBalance().first()

        val canPay = when (paySrc) {
            PaymentSource.WALLET -> wallet >= cost
            PaymentSource.PROFIT -> profit >= cost
            PaymentSource.CUSTOMER -> true
        }

        if (!canPay) {
            val srcLabel = when (paySrc) {
                PaymentSource.WALLET -> "کیف پول"
                PaymentSource.PROFIT -> "فایده"
                PaymentSource.CUSTOMER -> "مشتری"
            }
            _ui.update { it.copy(message = "موجودی $srcLabel کافی نیست. هزینه: $cost ؋", isError = true) }
            return@launch
        }

        val orderCode = CodeGen.makeOrderCode(nextNumber = repo.nextOrderNumber())
        val shortCode = CodeGen.makeShortCode()

        val order = Order(
            orderCode = orderCode,
            shortCode = shortCode,
            designTitle = s.designTitle.trim(),
            qty = qty,
            fabricType = s.fabricType.trim(),
            fabricColor = s.fabricColor.trim(),
            size = s.size.trim(),
            fabricUnit = s.fabricUnit.trim(),
            fabricAmount = fabricAmount,
            fabricSource = s.fabricSource,
            fabricPrice = fabricPrice,
            workCost = workCostTotal,
            agreedPrice = agreedPrice,
            customerName = s.customerName.trim(),
            customerPhone = s.customerPhone.trim(),
            status = OrderStatus.IN_STOCK.name,
            stageChangedAt = System.currentTimeMillis()
        )

        if (cost > 0) {
            when (paySrc) {
                PaymentSource.WALLET -> repo.spend("WALLET", cost, "خرید/ثبت سفارش ${order.orderCode}")
                PaymentSource.PROFIT -> repo.spend("PROFIT", cost, "خرید/ثبت سفارش ${order.orderCode}")
                PaymentSource.CUSTOMER -> { /* فعلاً هیچ */ }
            }
        }

        // کسر پارچه از موجودی انبار
        if (fromStock) {
            repo.changeFabricStock(s.fabricType, s.fabricColor, s.fabricUnit, -fabricAmount)
        }

        if (customerPaid > 0) {
            repo.income("WALLET", customerPaid, "پرداخت مشتری برای سفارش ${order.orderCode}")
            // ثبت در حساب مشتری (پیش‌پرداخت)
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

        repo.createOrder(order)

        _ui.update { PurchaseUi(message = "✅ خرید ثبت شد و سفارش وارد انبار شد.", isError = false) }
    }
}
