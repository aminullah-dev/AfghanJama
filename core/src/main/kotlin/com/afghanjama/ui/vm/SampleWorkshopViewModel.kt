package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.SampleWorkshop
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.fa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SampleWorkshopUi(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

/**
 * ساختنِ کارگاهِ نمونه.
 *
 * تنها کاری که می‌کند صدا زدنِ [SampleWorkshop.create] است، ولی دو
 * چیز را نگه می‌دارد که صفحه بی آن‌ها بد رفتار می‌کند: قفلِ «در حالِ
 * ساخت» تا دو بار زدنِ دکمه دو کارگاهِ نمونه نسازد، و اینکه اصلاً
 * دیتابیس از قبل خالی هست یا نه.
 */
class SampleWorkshopViewModel(private val repo: Repo) : ViewModel() {

    private val _ui = MutableStateFlow(SampleWorkshopUi())
    val ui: StateFlow<SampleWorkshopUi> = _ui

    /**
     * آیا کارگاه از قبل داده دارد؟
     *
     * صفحه با این تصمیم می‌گیرد چه هشداری بدهد. ساختنِ نمونه روی
     * دادهٔ واقعی چیزی را خراب نمی‌کند — فقط اضافه می‌کند — ولی
     * کارفرمایی که شش ماه است با این اپ کار می‌کند نباید ناغافل شش
     * سفارشِ ساختگی در دفترش پیدا کند.
     */
    val hasData: StateFlow<Boolean> =
        repo.observeAllOrders()
            .map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun clearMessage() = _ui.value = _ui.value.copy(message = null, isError = false)

    fun create() {
        if (_ui.value.busy) return
        _ui.value = SampleWorkshopUi(busy = true)
        viewModelScope.launch {
            val r = runCatching { SampleWorkshop.create(repo) }
            _ui.value = r.fold(
                onSuccess = { res ->
                    SampleWorkshopUi(
                        message = "کارگاهِ نمونه ساخته شد: " +
                            "${res.orders.fa()} سفارش، ${res.people.fa()} خیاط و ناظر، " +
                            "${res.customers.fa()} مشتری و ${res.materials.fa()} قلمِ انبار.",
                        isError = false
                    )
                },
                onFailure = { e ->
                    // پیامِ خامِ استثنا نشان داده می‌شود و این عمدی است:
                    // اگر جریانِ سفارش روزی بشکند، این صفحه اولین جایی
                    // است که خبر می‌دهد — و «خطایی رخ داد» هیچ کمکی
                    // به فهمیدنش نمی‌کند.
                    SampleWorkshopUi(
                        message = "ساختِ کارگاهِ نمونه ناتمام ماند: ${e.message ?: e::class.simpleName}",
                        isError = true
                    )
                }
            )
        }
    }
}
