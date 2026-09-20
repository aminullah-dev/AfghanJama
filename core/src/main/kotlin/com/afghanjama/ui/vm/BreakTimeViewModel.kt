package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.BreakTime
import com.afghanjama.data.repo.Repo
import com.afghanjama.platform.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * وقت‌های ثابتِ کارگاه (چای صبح، نان چاشت، …) و یادآورشان.
 *
 * ViewModel خودش چیزی از سکو نگه نمی‌دارد؛ هر کاری که زمان‌بندی لازم
 * دارد [Reminders] را از صفحه می‌گیرد، چون یادآور به دستگاه وابسته است
 * نه به داده.
 *
 * تا دیروز همین پارامتر `Context` بود و همین یک کلمه کلِ این فایل — و
 * صفحهٔ حضور و غیاب — را در `:app` نگه می‌داشت، یعنی ویندوز اصلاً حضور
 * و غیاب نداشت. `null` بودنِ [Reminders] یعنی «این سکو یادآور ندارد» و
 * بقیهٔ کار سرِ جایش انجام می‌شود.
 */
class BreakTimeViewModel(private val repo: Repo) : ViewModel() {

    val times: StateFlow<List<BreakTime>> =
        repo.observeBreakTimes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _inside = MutableStateFlow(0)
    /** چند نفر همین حالا داخل‌اند — همان عددی که در یادآور هم می‌آید. */
    val inside: StateFlow<Int> = _inside.asStateFlow()

    fun refreshInside() = viewModelScope.launch { _inside.value = repo.insideCount() }

    fun save(reminders: Reminders?, existing: BreakTime?, title: String, hour: Int, minute: Int) =
        viewModelScope.launch {
            val name = title.trim().ifBlank { "وقت استراحت" }
            val h = hour.coerceIn(0, 23)
            val m = minute.coerceIn(0, 59)
            val row = existing?.copy(title = name, hour = h, minute = m)
                ?: BreakTime(title = name, hour = h, minute = m)
            val id = repo.upsertBreakTime(row)
            // upsert روی سطرِ موجود همان id را برمی‌گرداند؛ روی سطرِ تازه id جدید
            val realId = if (row.id != 0L) row.id else id
            if (row.enabled) reminders?.scheduleBreak(realId, h, m)
            else reminders?.cancelBreak(realId)
        }

    fun setEnabled(reminders: Reminders?, row: BreakTime, enabled: Boolean) = viewModelScope.launch {
        repo.upsertBreakTime(row.copy(enabled = enabled))
        if (enabled) reminders?.scheduleBreak(row.id, row.hour, row.minute)
        else reminders?.cancelBreak(row.id)
    }

    fun delete(reminders: Reminders?, row: BreakTime) = viewModelScope.launch {
        reminders?.cancelBreak(row.id)
        repo.deleteBreakTime(row)
    }

    /**
     * وقت‌های معمولِ یک کارگاه، برای شروع. فقط وقتی چیزی ثبت نشده باشد
     * کار می‌کند تا هرگز روی تنظیماتِ خودِ کارگاه ننشیند.
     */
    fun addDefaults(reminders: Reminders?) = viewModelScope.launch {
        if (repo.breakTimeCount() > 0) return@launch
        listOf(
            Triple("چای صبح", 9, 0),
            Triple("نان چاشت", 12, 0),
            Triple("چای عصر", 15, 30)
        ).forEach { (title, h, m) ->
            val id = repo.upsertBreakTime(BreakTime(title = title, hour = h, minute = m))
            reminders?.scheduleBreak(id, h, m)
        }
    }
}
