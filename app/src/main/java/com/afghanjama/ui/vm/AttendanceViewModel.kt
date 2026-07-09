package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** وضعیت حضورِ یک کارمند. */
data class EmployeeAttendance(
    val name: String,
    val isIn: Boolean,
    val since: Long?          // زمان ورودِ بازهٔ باز (اگر داخل باشد)
)

/** حضور و غیاب کارمند با تأیید اثر انگشت. */
class AttendanceViewModel(private val repo: Repo) : ViewModel() {

    val records: StateFlow<List<AttendanceRecord>> =
        repo.observeAttendance()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** فهرست کارمندان (خیاط‌ها + ناظرها) با وضعیت حضور. */
    val employees: StateFlow<List<EmployeeAttendance>> =
        combine(
            repo.observeTailors(),
            repo.observeInspectors(),
            repo.observeAttendance()
        ) { tailors, inspectors, recs ->
            val names = (tailors.map { it.name } + inspectors.map { it.name })
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
            names.map { name ->
                val open = recs.firstOrNull { it.employee == name && it.checkOut == null }
                EmployeeAttendance(name = name, isIn = open != null, since = open?.checkIn)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun checkIn(name: String) = viewModelScope.launch { repo.checkIn(name) }
    fun checkOut(name: String) = viewModelScope.launch { repo.checkOut(name) }
}
