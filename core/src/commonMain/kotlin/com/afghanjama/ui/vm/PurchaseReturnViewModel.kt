package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PurchaseReturnUi(
    val materials: List<MaterialStock> = emptyList(),
    val suppliers: List<String> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false,
    val done: Boolean = false
)

/**
 * برگشتِ مواد به فروشنده — قلمِ معیوب یا اضافی از انبار خارج و
 * ارزشش یا از بدهیِ ما کم می‌شود یا نقد پس گرفته می‌شود.
 */
class PurchaseReturnViewModel(private val repo: Repo) : ViewModel() {

    /** جلوی ثبتِ دوباره با دو ضربهٔ سریع را می‌گیرد. */
    val busy = Busy()

    private val _state = MutableStateFlow(PurchaseReturnUi())

    val ui: StateFlow<PurchaseReturnUi> = combine(
        repo.observeMaterialStock(),
        repo.observeLedgerBalances(),
        _state
    ) { materials, balances, s ->
        s.copy(
            materials = materials.filter { it.amount > 0.0 }.sortedBy { it.name },
            suppliers = balances.filter { it.type == "SUPPLIER" }.map { it.name }.sorted()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchaseReturnUi())

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, isError = false, done = false)
    }

    fun submit(
        supplier: String,
        name: String,
        unit: String,
        qty: Double,
        amount: Long,
        refundCash: Boolean,
        cashBox: String
    ) =
        viewModelScope.launch {
            busy.once {
                doSubmit(
                    supplier = supplier,
                    name = name,
                    unit = unit,
                    qty = qty,
                    amount = amount,
                    refundCash = refundCash,
                    cashBox = cashBox
                )
            }
        }

    private suspend fun doSubmit(
        supplier: String,
        name: String,
        unit: String,
        qty: Double,
        amount: Long,
        refundCash: Boolean,
        cashBox: String
    ) {
        if (name.isBlank() || qty <= 0.0 || amount <= 0) {
            _state.value = _state.value.copy(
                message = "قلم، مقدار و مبلغ را درست وارد کنید.", isError = true
            )
            return
        }
        val ok = repo.recordPurchaseReturn(
            supplier = supplier, name = name, unit = unit,
            qty = qty, amount = amount, refundCash = refundCash, cashBox = cashBox
        )
        _state.value = _state.value.copy(
            message = if (ok) "برگشت از خرید ثبت و سندش صادر شد."
            else "موجودیِ انبار برای این مقدار کافی نیست.",
            isError = !ok,
            done = ok
        )
    }
}
