package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** کارِ خودِ کارگر: زیرِ دست، تحویل‌شده، طلب، و کارنامه. */
data class MyWorkUi(
    val label: String = "",
    val inProgress: List<SewingAssignment> = emptyList(),
    val doneThisWeek: List<SewingAssignment> = emptyList(),
    /** کارمزدِ تسویه‌نشدهٔ من (طلبم از کارگاه). */
    val unpaidWage: Long = 0,
    val score: TailorScore? = null
) {
    val identified: Boolean get() = label.isNotBlank()
    val inProgressPieces: Int get() = inProgress.sumOf { it.qty }
    val weekPieces: Int get() = doneThisWeek.sumOf { it.qty }
    val weekWage: Long get() = doneThisWeek.sumOf { it.totalWage }
}

/**
 * صفحهٔ «کارِ من» برای خیاط و ناظر: به‌جای داشبوردِ مدیریتی، فقط کارِ
 * خودش را می‌بیند — چه چیزی زیرِ دست دارد، این هفته چه تحویل داده،
 * چقدر طلبکار است و کارنامه‌اش چطور است.
 */
class MyWorkViewModel(private val repo: Repo) : ViewModel() {

    private val _label = MutableStateFlow("")

    /** برچسبِ کارگرِ این دستگاه را از تنظیمات می‌گیرد. */
    fun setLabel(label: String) { _label.value = label.trim() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<MyWorkUi> = _label
        .flatMapLatest { label ->
            combine(
                repo.observeAllAssignments(),
                repo.observePendingWages(),
                repo.observeAllQc()
            ) { assignments, wages, qc ->
                if (label.isBlank()) return@combine MyWorkUi()

                val mine = assignments.filter { it.tailorLabel == label }
                val weekAgo = System.currentTimeMillis() - 7L * 86_400_000L

                MyWorkUi(
                    label = label,
                    inProgress = mine.filter { it.status != "DONE" }
                        .sortedBy { it.createdAt },
                    doneThisWeek = mine.filter {
                        it.status == "DONE" && (it.doneAt ?: 0L) >= weekAgo
                    }.sortedByDescending { it.doneAt ?: 0L },
                    unpaidWage = wages.filter { it.tailorLabel == label }.sumOf { it.amount },
                    // همان محاسبهٔ «کارنامهٔ کارکنان» تا کارگر و مدیر یک عدد ببینند
                    score = buildTailorScores(assignments, qc)
                        .firstOrNull { it.name == label.trim() }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyWorkUi())
}
