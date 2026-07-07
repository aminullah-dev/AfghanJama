package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Transaction
import com.afghanjama.data.repo.Repo
import java.util.UUID
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

    // ================================
    // Actions
    // ================================

    fun incomeWallet(amount: Long, note: String) =
        viewModelScope.launch {
            repo.income("WALLET", amount, note)
        }

    fun incomeProfit(amount: Long, note: String) =
        viewModelScope.launch {
            repo.income("PROFIT", amount, note)
        }

    fun spendWallet(amount: Long, note: String) =
        viewModelScope.launch {
            repo.spend("WALLET", amount, note)
        }

    /** حذف تراکنش (اصلاح اشتباه). */
    fun deleteTx(id: UUID) = viewModelScope.launch { repo.deleteTx(id) }

    /** انتقال بین صندوق‌ها (WALLET / BANK / PROFIT). */
    fun transfer(from: String, to: String, amount: Long, note: String = "") =
        viewModelScope.launch {
            if (amount <= 0L || from == to) return@launch
            repo.transfer(from, to, amount, note.trim().ifBlank { "انتقال بین صندوق‌ها" })
        }

    /** ثبت هزینه عمومی کارگاه (کرایه، برق و آب، معاش، ...) از کیف پول. */
    fun addExpense(category: String, amount: Long, note: String) =
        viewModelScope.launch {
            if (amount <= 0L || category.isBlank()) return@launch
            repo.spend(
                source = "WALLET",
                amount = amount,
                note = note.trim().ifBlank { "هزینه: $category" },
                category = category
            )
        }

    fun spendProfit(amount: Long, note: String) =
        viewModelScope.launch {
            repo.spend("PROFIT", amount, note)
        }
}
