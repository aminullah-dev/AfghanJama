package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Transaction
import com.afghanjama.data.repo.Repo
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FinanceViewModel(private val repo: Repo) : ViewModel() {

    // ================================
    // Balances
    // ================================

    val walletBalance: StateFlow<Long> =
        repo.observeWalletBalance()
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val profitBalance: StateFlow<Long> =
        repo.observeProfitBalance()
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val bankBalance: StateFlow<Long> =
        repo.observeBankBalance()
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val tx: StateFlow<List<Transaction>> =
        repo.observeTx()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * پیامِ رد شدن. سکوت بدترین حالت بود: کاربر مبلغ را می‌زد، هیچ اتفاقی
     * نمی‌افتاد و فکر می‌کرد ثبت شد.
     */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() { _message.value = null }

    private fun refuse(source: String) {
        _message.value = "موجودیِ ${boxName(source)} برای این مبلغ کافی نیست؛ چیزی ثبت نشد."
    }

    private fun boxName(source: String): String = when (source) {
        "BANK" -> "بانک"
        "PROFIT" -> "صندوق فایده"
        else -> "کیف پول"
    }

    // ================================
    // Actions
    // ================================

    fun incomeWallet(amount: Long, note: String) =
        viewModelScope.launch {
            repo.recordManualCash("WALLET", amount, isIn = true, note = note)
        }

    fun incomeProfit(amount: Long, note: String) =
        viewModelScope.launch {
            repo.recordManualCash("PROFIT", amount, isIn = true, note = note)
        }

    fun spendWallet(amount: Long, note: String) =
        viewModelScope.launch {
            if (!repo.recordManualCash("WALLET", amount, isIn = false, note = note)) {
                refuse("WALLET")
            }
        }

    /** حذف تراکنش (اصلاح اشتباه). */
    fun deleteTx(id: UUID) = viewModelScope.launch { repo.deleteTx(id) }

    /** انتقال بین صندوق‌ها (WALLET / BANK / PROFIT). */
    fun transfer(from: String, to: String, amount: Long, note: String = "") =
        viewModelScope.launch {
            if (amount <= 0L || from == to) return@launch
            if (!repo.transfer(from, to, amount, note.trim().ifBlank { "انتقال بین صندوق‌ها" })) {
                refuse(from)
            }
        }

    /** ثبت هزینه عمومی کارگاه (کرایه، برق و آب، معاش، ...) از کیف پول. */
    fun addExpense(category: String, amount: Long, note: String) =
        viewModelScope.launch {
            if (amount <= 0L || category.isBlank()) return@launch
            val ok = repo.recordExpense(
                source = "WALLET",
                category = category,
                amount = amount,
                note = note.trim().ifBlank { "هزینه: $category" }
            )
            if (!ok) refuse("WALLET")
        }

    fun spendProfit(amount: Long, note: String) =
        viewModelScope.launch {
            if (!repo.recordManualCash("PROFIT", amount, isIn = false, note = note)) {
                refuse("PROFIT")
            }
        }
}
