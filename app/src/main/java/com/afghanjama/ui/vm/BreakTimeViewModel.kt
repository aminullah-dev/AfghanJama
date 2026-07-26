package com.afghanjama.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.BreakTime
import com.afghanjama.data.repo.Repo
import com.afghanjama.work.BreakReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * وقت‌های ثابتِ کارگاه (چای صبح، نان چاشت، …) و یادآورشان.
 *
 * ViewModel خودش Context نگه نمی‌دارد؛ هر کاری که زمان‌بندی لازم دارد
 * Context را از صفحه می‌گیرد، چون یادآور به دستگاه وابسته است نه به داده.
 */
class BreakTimeViewModel(private val repo: Repo) : ViewModel() {

    val times: StateFlow<List<BreakTime>> =
        repo.observeBreakTimes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _inside = MutableStateFlow(0)
    /** چند نفر همین حالا داخل‌اند — همان عددی که در یادآور هم می‌آید. */
    val inside: StateFlow<Int> = _inside.asStateFlow()

    fun refreshInside() = viewModelScope.launch { _inside.value = repo.insideCount() }

    fun save(context: Context, existing: BreakTime?, title: String, hour: Int, minute: Int) =
        viewModelScope.launch {
            val name = title.trim().ifBlank { "وقت استراحت" }
            val h = hour.coerceIn(0, 23)
            val m = minute.coerceIn(0, 59)
            val row = existing?.copy(title = name, hour = h, minute = m)
                ?: BreakTime(title = name, hour = h, minute = m)
            val id = repo.upsertBreakTime(row)
            // upsert روی سطرِ موجود همان id را برمی‌گرداند؛ روی سطرِ تازه id جدید
            val realId = if (row.id != 0L) row.id else id
            if (row.enabled) BreakReminderWorker.schedule(context, realId, h, m)
            else BreakReminderWorker.cancel(context, realId)
        }

    fun setEnabled(context: Context, row: BreakTime, enabled: Boolean) = viewModelScope.launch {
        repo.upsertBreakTime(row.copy(enabled = enabled))
        if (enabled) BreakReminderWorker.schedule(context, row.id, row.hour, row.minute)
        else BreakReminderWorker.cancel(context, row.id)
    }

    fun delete(context: Context, row: BreakTime) = viewModelScope.launch {
        BreakReminderWorker.cancel(context, row.id)
        repo.deleteBreakTime(row)
    }

    /**
     * وقت‌های معمولِ یک کارگاه، برای شروع. فقط وقتی چیزی ثبت نشده باشد
     * کار می‌کند تا هرگز روی تنظیماتِ خودِ کارگاه ننشیند.
     */
    fun addDefaults(context: Context) = viewModelScope.launch {
        if (repo.breakTimeCount() > 0) return@launch
        listOf(
            Triple("چای صبح", 9, 0),
            Triple("نان چاشت", 12, 0),
            Triple("چای عصر", 15, 30)
        ).forEach { (title, h, m) ->
            val id = repo.upsertBreakTime(BreakTime(title = title, hour = h, minute = m))
            BreakReminderWorker.schedule(context, id, h, m)
        }
    }
}
