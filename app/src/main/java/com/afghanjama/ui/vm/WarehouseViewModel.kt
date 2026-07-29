package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.data.entities.StockMovement
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WarehouseUi(
    val message: String? = null,
    val isError: Boolean = false
)

/** انبار عمومی مواد خام: نمایش موجودی، اصلاح دستی، حد هشدار. */
class WarehouseViewModel(private val repo: Repo) : ViewModel() {

    val materials: StateFlow<List<MaterialStock>> =
        repo.observeMaterialStock()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** کاردکس/گردش انبار (رد حسابرسی هر تغییر موجودی). */
    val movements: StateFlow<List<StockMovement>> =
        repo.observeStockMovements()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(WarehouseUi())
    val ui: StateFlow<WarehouseUi> = _ui

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    /** اصلاح دستی موجودی (شمارش انبار): مقدار به رقم دقیقِ واردشده تنظیم می‌شود. */
    fun setAmount(item: MaterialStock, newAmount: Double) = viewModelScope.launch {
        val delta = newAmount - item.amount
        repo.adjustMaterialStock(item.name, item.unit, delta, reason = "اصلاح موجودی")
        _ui.update { it.copy(message = "موجودی «${item.name}» به‌روزرسانی شد.", isError = false) }
    }

    /** ثبت ضایعات: مقدار مشخص از انبار خارج و در کاردکس با دلیلِ «ضایعات» ثبت می‌شود. */
    fun recordWaste(item: MaterialStock, amount: Double) = viewModelScope.launch {
        if (amount <= 0.0) return@launch
        repo.adjustMaterialStock(item.name, item.unit, -amount, reason = "ضایعات")
        _ui.update { it.copy(message = "ضایعاتِ «${item.name}» ثبت شد.", isError = false) }
    }

    fun setMinLevel(item: MaterialStock, minLevel: Double) = viewModelScope.launch {
        repo.setMaterialMinLevel(item.name, item.unit, minLevel)
        _ui.update { it.copy(message = "حد هشدار «${item.name}» ثبت شد.", isError = false) }
    }
}
