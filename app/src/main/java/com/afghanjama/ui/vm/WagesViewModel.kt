package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.TailorWage
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** مجموع کارمزدهای باز یک خیاط. */
data class TailorWageGroup(
    val tailorLabel: String,
    val total: Long,
    val items: List<TailorWage>
)

class WagesViewModel(private val repo: Repo) : ViewModel() {

    /** کارمزدهای تسویه‌نشده، گروه‌بندی‌شده بر اساس خیاط. */
    val pendingGroups: StateFlow<List<TailorWageGroup>> =
        repo.observePendingWages()
            .map { list ->
                list.groupBy { it.tailorLabel }
                    .map { (tailor, items) ->
                        TailorWageGroup(
                            tailorLabel = tailor,
                            total = items.sumOf { it.amount },
                            items = items
                        )
                    }
                    .sortedByDescending { it.total }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** تاریخچه کارمزدهای تسویه‌شده. */
    val settledWages: StateFlow<List<TailorWage>> =
        repo.observeAllWages()
            .map { list -> list.filter { it.settled } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * تسویه هفتگی: همه کارمزدهای باز خیاط بسته و مبلغ از کیف پول
     * پرداخت و در تراکنش‌های مالی عمومی ثبت می‌شود.
     */
    fun settle(group: TailorWageGroup) = viewModelScope.launch {
        if (group.total <= 0L) return@launch
        repo.settleTailorWages(group.tailorLabel, group.total)
    }
}
