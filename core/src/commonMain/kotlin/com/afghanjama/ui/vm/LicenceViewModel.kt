package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.licence.LicenceGate
import com.afghanjama.licence.LicenceStatus
import com.afghanjama.licence.Licensing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LicenceUi(
    val status: LicenceStatus? = null,
    /** کدِ این دستگاه، `XXXX-XXXX-XXXX-XXXX`. */
    val machineCode: String = "",
    /** کد از شناسهٔ جایگزین آمده، نه از شناسهٔ سکو — صفحه می‌گوید. */
    val machineFallback: Boolean = false,
    val keyText: String = "",
    val message: String? = null,
    val isError: Boolean = false,
    val working: Boolean = false,
)

/**
 * صفحهٔ لایسنس: وضعیت، کدِ دستگاه، و واردکردنِ کلید.
 *
 * تصمیمی اینجا گرفته نمی‌شود؛ همه با [Licensing] و [LicenceGate] است و
 * این فقط نشانشان می‌دهد. وضعیت از همان `LicenceGate.status` می‌آید که
 * بنرِ خانه می‌خوانَد، پس دو جا هرگز دو چیز نمی‌گویند.
 */
class LicenceViewModel(private val licensing: Licensing) : ViewModel() {

    private val _ui = MutableStateFlow(LicenceUi())

    val ui: StateFlow<LicenceUi> = combine(_ui, LicenceGate.status) { u, s ->
        u.copy(status = s ?: u.status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LicenceUi())

    init {
        viewModelScope.launch {
            val m = licensing.machine()
            _ui.value = _ui.value.copy(machineCode = m.code, machineFallback = m.fallback)
            licensing.refresh()
        }
    }

    fun setKey(text: String) {
        _ui.value = _ui.value.copy(keyText = text, message = null, isError = false)
    }

    /** کلیدِ چسبانده‌شده در خانهٔ متن. */
    fun activate() = activate(_ui.value.keyText)

    /** متنِ فایلِ `.lnmlic` یا هر متنی که کلید در آن است. */
    fun activate(text: String) {
        if (_ui.value.working) return
        _ui.value = _ui.value.copy(working = true, message = null, isError = false)
        viewModelScope.launch {
            val r = licensing.activate(text)
            _ui.value = when (r) {
                is Licensing.Activation.Done -> _ui.value.copy(
                    working = false, keyText = "",
                    message = r.warning ?: "لایسنس فعال شد. همهٔ محدودیت‌ها برداشته شد؛ داده‌ها همان است که بود.",
                    isError = r.warning != null,
                )
                is Licensing.Activation.Rejected -> _ui.value.copy(
                    working = false, message = r.message, isError = true,
                )
            }
        }
    }

    /** فایل خوانده نشد. */
    fun fileFailed() {
        _ui.value = _ui.value.copy(message = "فایلِ لایسنس خوانده نشد.", isError = true)
    }
}
