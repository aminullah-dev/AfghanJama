package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.RecurringExpense
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.util.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ماهِ شمسیِ جاری به شکلِ `سال*۱۰۰+ماه` — همان چیزی که
 * [RecurringExpense.lastPostedYm] نگه می‌دارد.
 *
 * بیرونِ ViewModel است تا مرکزِ اقدام هم از همان یک تعریف بخوانَد.
 * دو جا نوشتنش یعنی روزی یکی‌شان ماهِ شمسی بگیرد و آن یکی میلادی.
 */
fun currentYm(now: Long = nowMillis()): Int {
    val j = PersianDate.todayJalali(now)
    return j[0] * 100 + j[1]
}

data class RecurringUi(
    val rows: List<RecurringExpense> = emptyList(),
    val dueCount: Int = 0,
    val dueTotal: Long = 0,
    val message: String? = null,
    val isError: Boolean = false,
)

class RecurringExpenseViewModel(private val repo: Repo) : ViewModel() {

    private val _extra = MutableStateFlow(RecurringUi())

    val ui: StateFlow<RecurringUi> = _extra.asStateFlow()

    val rows: StateFlow<List<RecurringExpense>> =
        repo.observeRecurringExpenses()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshDue()
    }

    /**
     * «چند قلم این ماه مانده» — از همان پرس‌وجویی که خودِ ثبت استفاده
     * می‌کند، نه شمارشِ جداگانه در کاتلین.
     */
    fun refreshDue() = viewModelScope.launch {
        val due = repo.recurringDue(currentYm())
        _extra.value = _extra.value.copy(
            dueCount = due.size,
            dueTotal = due.sumOf { it.amount },
        )
    }

    fun save(row: RecurringExpense) = viewModelScope.launch {
        repo.upsertRecurringExpense(row)
        refreshDue()
    }

    fun delete(row: RecurringExpense) = viewModelScope.launch {
        repo.deleteRecurringExpense(row)
        refreshDue()
    }

    /**
     * ثبتِ هزینه‌های ثابتِ این ماه.
     *
     * پیامِ نتیجه صریح است و عدد دارد: «۲ از ۳ ثبت شد» بهتر از «انجام
     * شد» است، چون قلمی که صندوقش پول نداشته ثبت نشده و کاربر باید
     * بداند.
     */
    fun postThisMonth() = viewModelScope.launch {
        val ym = currentYm()
        val before = repo.recurringDue(ym).size
        val done = runCatching { repo.postRecurringExpenses(ym) }.getOrElse { e ->
            _extra.value = _extra.value.copy(
                message = e.message ?: "ثبت نشد", isError = true
            )
            return@launch
        }
        refreshDue()
        _extra.value = _extra.value.copy(
            message = when {
                before == 0 -> "این ماه چیزی برای ثبت نبود."
                done == before -> "${done} قلم ثبت شد."
                done == 0 -> "هیچ‌کدام ثبت نشد — موجودیِ صندوق کافی نیست."
                else -> "$done از $before قلم ثبت شد؛ بقیه موجودیِ کافی نداشت."
            },
            isError = done == 0 && before > 0,
        )
    }

    fun clearMessage() {
        _extra.value = _extra.value.copy(message = null, isError = false)
    }
}
