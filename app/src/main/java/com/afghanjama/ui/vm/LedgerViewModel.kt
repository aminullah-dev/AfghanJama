package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.dao.PartyBalance
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** دفتر کلِ یکپارچه: ماندهٔ هر طرف حساب + گردشِ همهٔ اسناد. */
class LedgerViewModel(private val repo: Repo) : ViewModel() {

    val balances: StateFlow<List<PartyBalance>> =
        repo.observeLedgerBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<LedgerEntry>> =
        repo.observeAllLedgerEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    /** ثبتِ دستیِ پرداخت (isPayment=true) یا دریافت روی حسابِ یک طرف. */
    fun recordManual(type: String, name: String, amount: Long, isPayment: Boolean, note: String) =
        viewModelScope.launch {
            val ok = repo.recordManualLedger(type, name, amount, isPayment, "WALLET", note)
            _message.value =
                if (ok) null
                else "ثبت نشد — موجودی صندوق برای این پرداخت کافی نیست."
        }
}
