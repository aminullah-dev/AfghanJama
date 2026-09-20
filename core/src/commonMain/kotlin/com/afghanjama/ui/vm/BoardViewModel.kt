package com.afghanjama.ui.vm

import com.afghanjama.util.nowMillis
import com.afghanjama.prefs.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.lanClient
import com.afghanjama.lan.LanResult
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/*
 * `BoardRow` به `:core` رفت (همان بستهٔ `com.afghanjama.ui.vm`).
 * `LanClient` با نامِ کاملاً مقید صدایش می‌زند و آن حالا در `:core` است.
 */


data class BoardUi(
    val rows: List<BoardRow> = emptyList(),
    val updatedAt: Long = 0L,
    val remote: Boolean = false,
    val error: String? = null
) {
    val pieces: Int get() = rows.sumOf { it.qty }
    val tailors: Int get() = rows.map { it.tailor }.distinct().size
    val lateCount: Int get() = rows.count { it.late }
}

/**
 * تابلوی «در حال دوخت» — مثلِ تابلوی پروازِ فرودگاه.
 *
 * دو منبع دارد و خودش تشخیص می‌دهد کدام:
 *  - روی گوشیِ اصلی/تنها، مستقیم از دیتابیس و کاملاً زنده (Flow).
 *  - روی گوشیِ دیوار که «کارگر» است، هر چند ثانیه از گوشیِ اصلی می‌پرسد.
 *
 * `tick` فقط برای این است که «چند روز» و ساعتِ بالای تابلو بدونِ تغییرِ
 * داده هم جلو بروند.
 */
class BoardViewModel(private val repo: Repo) : ViewModel() {

    /**
     * کدِ طرحِ هر سفارش (DIP-12) — کلید: کدِ سفارش.
     * تعریفش یک‌جا در `Repo` است تا صفحه‌ها از هم جدا نیفتند.
     */
    val designCodeByOrder: StateFlow<Map<String, String>> =
        repo.observeDesignCodeByOrder()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val tick = MutableStateFlow(nowMillis())

    private val _remote = MutableStateFlow(BoardUi(remote = true))

    /** حالتِ محلی: از خودِ دیتابیس، بدونِ هیچ تأخیری. */
    private val local: StateFlow<BoardUi> =
        combine(
            repo.observeAssignmentsInProgress(),
            repo.observeAllOrders(),
            tick
        ) { assignments, orders, now ->
            val byCode = orders.associateBy { it.orderCode }
            val rows = assignments.map { a ->
                val o = byCode[a.orderCode]
                BoardRow(
                    tailor = a.tailorLabel.trim(),
                    orderCode = a.orderCode,
                    design = o?.designTitle.orEmpty(),
                    qty = a.qty,
                    days = ((now - a.createdAt) / 86_400_000L).toInt().coerceAtLeast(0),
                    dueIn = o?.dueDate?.takeIf { it > 0 }?.let {
                        ((it - now) / 86_400_000L).toInt()
                    }
                )
            }.sortedWith(
                // دیرشده‌ها بالا، بعد قدیمی‌ترها — همان ترتیبی که تابلوی
                // فرودگاه دارد: چیزی که مشکل دارد اول دیده شود.
                compareByDescending<BoardRow> { it.late }
                    .thenByDescending { it.warn }
                    .thenByDescending { it.days }
            )
            BoardUi(rows = rows, updatedAt = now, remote = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUi())

    private val _mode = MutableStateFlow(DeviceMode.STANDALONE)

    val ui: StateFlow<BoardUi> =
        combine(_mode, local, _remote) { mode, l, r ->
            if (mode == DeviceMode.WORKER) r else l
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUi())

    /** ساعت و «چند روز» را زنده نگه می‌دارد. */
    fun start(settings: Settings) {
        _mode.value = LanPrefs.mode(settings)
        viewModelScope.launch {
            while (true) {
                tick.value = nowMillis()
                if (_mode.value == DeviceMode.WORKER) pullRemote(settings)
                delay(REFRESH_MS)
            }
        }
    }

    private suspend fun pullRemote(settings: Settings) {
        val host = LanPrefs.host(settings)
        if (host.isBlank()) {
            _remote.value = _remote.value.copy(error = "نشانیِ دستگاهِ اصلی تنظیم نشده.")
            return
        }
        when (val r = lanClient(host, LanPrefs.code(settings)).board()) {
            is LanResult.Err -> _remote.value = _remote.value.copy(error = r.message)
            is LanResult.Ok -> _remote.value = BoardUi(
                rows = r.value,
                updatedAt = nowMillis(),
                remote = true,
                error = null
            )
        }
    }

    companion object {
        /** هر ۱۵ ثانیه — برای تابلوی دیوار کافی است و باتری را نمی‌سوزاند. */
        const val REFRESH_MS = 15_000L
    }
}
