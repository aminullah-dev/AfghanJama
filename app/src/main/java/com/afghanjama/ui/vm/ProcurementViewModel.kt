package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.CodeGen
import com.afghanjama.data.entities.PaymentSource
import com.afghanjama.data.entities.PurchaseInvoice
import com.afghanjama.data.entities.PurchaseItem
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.decimalOnly
import com.afghanjama.ui.format.digitsOnly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** یک قلمِ خریدِ افزوده‌شده به فاکتور. */
data class MaterialLine(
    val name: String,
    val unit: String,
    val qty: Double,
    val unitPrice: Long,
    /** چند عدد داخلِ هر بسته؛ ۰ یا ۱ یعنی بسته‌بندی نیست. */
    val perPack: Int = 0
) {
    val total: Long get() = (qty * unitPrice).toLong()

    val packed: Boolean get() = perPack > 1

    /**
     * آنچه واقعاً وارد انبار می‌شود.
     *
     * بسته واحدِ خرید است، نه واحدِ مصرف: ۵ بستهٔ ۱۰۰تایی دکمه یعنی ۵۰۰
     * عدد در انبار، چون برداشتِ روزانه عددی است نه بسته‌ای.
     */
    val stockUnit: String get() = if (packed) "عدد" else unit
    val stockQty: Double get() = if (packed) qty * perPack else qty
}

data class ProcurementUi(
    // ---- ویرایشگر قلم فعلی ----
    val name: String = "",
    val unit: String = "",
    val qty: String = "",
    val unitPrice: String = "",
    /** خالی یا ۱ یعنی این قلم بسته‌بندی ندارد. */
    val perPack: String = "",

    // ---- اقلام افزوده‌شده ----
    val items: List<MaterialLine> = emptyList(),

    // ---- اطلاعات فاکتور ----
    val supplier: String = "",
    val note: String = "",
    val paymentSource: String = PaymentSource.WALLET.name,

    val message: String? = null,
    val isError: Boolean = false
) {
    val itemsTotal: Long get() = items.sumOf { it.total }
}

/**
 * خرید مواد خام: کاربر هر قلم را با نام، واحد، تعداد و قیمتِ دلخواه
 * وارد می‌کند و با «+» به فاکتور می‌افزاید. با اتمام خرید، همهٔ اقلام
 * وارد انبار عمومی می‌شوند و مبلغ کل از صندوق انتخابی کم می‌شود.
 */
class ProcurementViewModel(private val repo: Repo) : ViewModel() {

    private val _ui = MutableStateFlow(ProcurementUi())
    val ui: StateFlow<ProcurementUi> = _ui

    private fun clear() = _ui.update { it.copy(message = null, isError = false) }

    fun setName(v: String) = _ui.update { it.copy(name = v, message = null, isError = false) }
    fun setUnit(v: String) = _ui.update { it.copy(unit = v, message = null, isError = false) }
    fun setQty(v: String) = _ui.update { it.copy(qty = v.decimalOnly(), message = null, isError = false) }
    fun setUnitPrice(v: String) = _ui.update { it.copy(unitPrice = v.digitsOnly(), message = null, isError = false) }
    fun setPerPack(v: String) = _ui.update { it.copy(perPack = v.digitsOnly(), message = null, isError = false) }

    fun setSupplier(v: String) = _ui.update { it.copy(supplier = v, message = null, isError = false) }
    fun setNote(v: String) = _ui.update { it.copy(note = v, message = null, isError = false) }
    fun setPaymentSource(v: String) = _ui.update { it.copy(paymentSource = v.trim(), message = null, isError = false) }

    private fun buildLine(s: ProcurementUi): MaterialLine? {
        if (s.name.isBlank() || s.unit.isBlank()) return null
        val qty = s.qty.toDoubleOrNull() ?: return null
        if (qty <= 0.0) return null
        val price = s.unitPrice.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        return MaterialLine(
            s.name.trim(), s.unit.trim(), qty, price,
            perPack = s.perPack.toIntOrNull() ?: 0
        )
    }

    /** قلمِ فعلی را به فاکتور اضافه و ویرایشگر را پاک می‌کند. */
    fun addLine() {
        val line = buildLine(_ui.value) ?: run {
            _ui.update { it.copy(message = "برای افزودن قلم: نام، واحد و تعداد را کامل کنید.", isError = true) }
            return
        }
        _ui.update {
            it.copy(
                items = it.items + line,
                name = "", unit = "", qty = "", unitPrice = "", perPack = "",
                message = null, isError = false
            )
        }
    }

    fun removeLine(index: Int) = _ui.update {
        if (index in it.items.indices) it.copy(items = it.items.toMutableList().apply { removeAt(index) })
        else it
    }

    /** اتمام خرید و ارسال به انبار. */
    fun completePurchase() = viewModelScope.launch {
        clear()
        val s = _ui.value

        // اقلام: لیست + در صورت خالی‌بودن، ویرایشگر فعلی
        val lines = s.items.toMutableList()
        if (lines.isEmpty()) buildLine(s)?.let { lines.add(it) }
        if (lines.isEmpty()) {
            _ui.update { it.copy(message = "حداقل یک قلم به فاکتور اضافه کنید.", isError = true) }
            return@launch
        }

        val total = lines.sumOf { it.total }
        val src = s.paymentSource.trim().uppercase()

        // خرید نسیه: نام فروشنده لازم است (برای دفتر حساب)
        if (src == "CREDIT" && s.supplier.isBlank()) {
            _ui.update { it.copy(message = "برای خرید نسیه، نام فروشنده را وارد کنید.", isError = true) }
            return@launch
        }

        val invoice = PurchaseInvoice(
            code = CodeGen.makePurchaseCode(),
            supplier = s.supplier.trim(),
            note = s.note.trim(),
            total = total,
            paySource = src
        )
        val rows = lines.map {
            PurchaseItem(
                invoiceId = invoice.id.toString(),
                name = it.name,
                unit = it.unit,
                qty = it.qty,
                unitPrice = it.unitPrice,
                total = it.total,
                perPack = it.perPack
            )
        }
        // کنترلِ موجودی یک‌جا انجام می‌شود — داخلِ خودِ ثبت — تا صفحهٔ خرید و
        // بقیهٔ مسیرهای پول یک قاعده داشته باشند و از هم دور نیفتند.
        // تنها دلیلِ ردشدنِ منبع‌های قابلِ انتخاب (کیف پول/بانک/فایده)
        // کم‌بودنِ موجودی است؛ «نسیه» هرگز رد نمی‌شود.
        if (!repo.recordPurchaseInvoice(invoice, rows)) {
            val label = when (src) {
                "PROFIT" -> "فایده"
                "BANK" -> "بانک"
                else -> "کیف پول"
            }
            _ui.update {
                ProcurementUi(
                    message = "موجودی $label کافی نیست. مبلغ خرید: $total ؋ — " +
                        "مبلغ را کم کنید یا «نسیه (قرض)» را انتخاب کنید.",
                    isError = true
                )
            }
            return@launch
        }

        _ui.update {
            ProcurementUi(message = "✅ خرید ثبت شد و ${rows.size} قلم وارد انبار شد.", isError = false)
        }
    }
}
