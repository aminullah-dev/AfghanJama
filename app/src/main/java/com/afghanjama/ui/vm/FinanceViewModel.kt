package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Transaction
import com.afghanjama.data.repo.Repo
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

    fun spendProfit(amount: Long, note: String) =
        viewModelScope.launch {
            repo.spend("PROFIT", amount, note)
        }
}
