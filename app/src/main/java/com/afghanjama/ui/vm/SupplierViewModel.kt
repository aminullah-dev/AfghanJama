package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.SupplierLedger
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** مانده بدهی به یک فروشنده. */
data class SupplierBalance(val supplier: String, val owed: Long)

data class SupplierUi(
    val message: String? = null,
    val isError: Boolean = false
)

/** دفتر حساب فروشنده‌ها (نسیه) و تسویهٔ بدهی. */
class SupplierViewModel(private val repo: Repo) : ViewModel() {

    val ledger: StateFlow<List<SupplierLedger>> =
        repo.observeSupplierLedger()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val balances: StateFlow<List<SupplierBalance>> =
        repo.observeSupplierLedger()
            .map { rows ->
                rows.groupBy { it.supplier }
                    .map { (supplier, list) ->
                        val owed = list.sumOf { if (it.type == "CREDIT") it.amount else -it.amount }
                        SupplierBalance(supplier, owed)
                    }
                    .filter { it.owed > 0 }
                    .sortedByDescending { it.owed }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(SupplierUi())
    val ui: StateFlow<SupplierUi> = _ui

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    fun settle(supplier: String, amount: Long, paySource: String) = viewModelScope.launch {
        if (amount <= 0) {
            _ui.update { it.copy(message = "مبلغ تسویه معتبر نیست.", isError = true) }
            return@launch
        }
        val available = when (paySource) {
            "PROFIT" -> repo.observeProfitBalance().first()
            "BANK" -> repo.observeBankBalance().first()
            else -> repo.observeWalletBalance().first()
        }
        if (available < amount) {
            _ui.update { it.copy(message = "موجودی صندوق کافی نیست.", isError = true) }
            return@launch
        }
        repo.settleSupplier(supplier, amount, paySource, "تسویه قرض $supplier")
        _ui.update { it.copy(message = "✅ تسویه ثبت شد.", isError = false) }
    }
}
