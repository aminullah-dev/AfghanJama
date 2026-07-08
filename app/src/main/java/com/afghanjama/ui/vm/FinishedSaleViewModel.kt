package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.FinishedSale
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FinishedSaleUi(
    val message: String? = null,
    val isError: Boolean = false
)

/** فروش جزئی از انبار محصول نهایی. */
class FinishedSaleViewModel(private val repo: Repo) : ViewModel() {

    val items: StateFlow<List<FinishedStock>> =
        repo.observeFinishedStock()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSales: StateFlow<List<FinishedSale>> =
        repo.observeFinishedSales()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(FinishedSaleUi())
    val ui: StateFlow<FinishedSaleUi> = _ui

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    fun sell(item: FinishedStock, qty: Int, unitPrice: Long, customerName: String) =
        viewModelScope.launch {
            if (qty <= 0 || qty > item.qty) {
                _ui.update { it.copy(message = "تعداد فروش نامعتبر است. موجودی: ${item.qty}", isError = true) }
                return@launch
            }
            if (unitPrice <= 0) {
                _ui.update { it.copy(message = "قیمت هر عدد را وارد کنید.", isError = true) }
                return@launch
            }
            val ok = repo.sellFinished(item, qty, unitPrice, customerName)
            _ui.update {
                if (ok) it.copy(message = "✅ فروش ثبت شد.", isError = false)
                else it.copy(message = "فروش ناموفق بود.", isError = true)
            }
        }
}
