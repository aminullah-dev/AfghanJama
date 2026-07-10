package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.dao.PartyBalance
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** دفتر کلِ یکپارچه: ماندهٔ هر طرف حساب + گردشِ همهٔ اسناد. */
class LedgerViewModel(private val repo: Repo) : ViewModel() {

    val balances: StateFlow<List<PartyBalance>> =
        repo.observeLedgerBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<LedgerEntry>> =
        repo.observeAllLedgerEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
