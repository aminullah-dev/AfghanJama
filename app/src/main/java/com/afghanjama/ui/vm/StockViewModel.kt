package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.FabricColor
import com.afghanjama.data.entities.FabricStock
import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StockUi(
    val message: String? = null,
    val isError: Boolean = false
)

/** مدیریت موجودی پارچه انبار: خرید، اصلاح دستی، حد هشدار. */
class StockViewModel(private val repo: Repo) : ViewModel() {

    val stocks: StateFlow<List<FabricStock>> =
        repo.observeFabricStock()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // برای انتخاب نوع/رنگ در دیالوگ خرید پارچه
    val fabricTypes: StateFlow<List<FabricType>> =
        repo.observeFabricTypes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fabricColors: StateFlow<List<FabricColor>> =
        repo.observeFabricColors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(StockUi())
    val ui: StateFlow<StockUi> = _ui

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    /**
     * خرید پارچه برای انبار: مبلغ از منبع انتخابی پرداخت و
     * موجودی پارچه زیاد می‌شود.
     */
    fun buyFabric(
        type: String,
        color: String,
        unit: String,
        amount: Double,
        totalPrice: Long,
        paySource: String
    ) = viewModelScope.launch {
        if (type.isBlank() || color.isBlank() || unit.isBlank()) {
            _ui.update { it.copy(message = "نوع، رنگ و واحد پارچه را انتخاب کنید.", isError = true) }
            return@launch
        }
        if (amount <= 0.0) {
            _ui.update { it.copy(message = "مقدار پارچه باید بیشتر از صفر باشد.", isError = true) }
            return@launch
        }

        if (totalPrice > 0) {
            repo.spend(
                source = paySource.ifBlank { "WALLET" },
                amount = totalPrice,
                note = "خرید پارچه: $type $color — $amount",
                category = "خرید پارچه"
            )
        }
        // موجودی + قیمت میانگین (بهای تمام‌شده) به‌روزرسانی می‌شود
        repo.addFabricPurchase(type, color, unit, amount, totalPrice)
        _ui.update { it.copy(message = "✅ خرید پارچه ثبت و موجودی به‌روز شد.", isError = false) }
    }

    /** اصلاح دستی موجودی (شمارش انبار). */
    fun adjustStock(stock: FabricStock, newAmount: Double) = viewModelScope.launch {
        repo.changeFabricStock(
            stock.fabricType, stock.fabricColor, stock.fabricUnit,
            newAmount - stock.amount
        )
        _ui.update { it.copy(message = "موجودی اصلاح شد.", isError = false) }
    }

    /** تنظیم حد هشدار کمبود. */
    fun setMinLevel(stock: FabricStock, minLevel: Double) = viewModelScope.launch {
        repo.setFabricMinLevel(stock.fabricType, stock.fabricColor, stock.fabricUnit, minLevel)
    }
}
