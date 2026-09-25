package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.SampleWorkshop
import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.Settings
import com.afghanjama.ui.format.fa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SampleWorkshopUi(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    /** رمزِ اشتباه — زیرِ همان خانهٔ رمز، نه در پنجره. */
    val pinError: String? = null
)

/**
 * ساختنِ کارگاهِ نمونه — و قفلش.
 *
 * سه چیز را نگه می‌دارد که صفحه بی آن‌ها بد رفتار می‌کند: قفلِ «در حالِ
 * ساخت» تا دو بار زدنِ دکمه دو کارگاهِ نمونه نسازد، اینکه کارگاه اصلاً
 * چیزی دارد یا نه، و اینکه رمز همین حالا زده شده یا نه.
 *
 * **قفل همان لحظهٔ ساخت بسته می‌شود.** بعد از ساختن، باز بودنِ رمز پاک
 * می‌شود؛ ساختنِ بارِ دوم دوباره رمز می‌خواهد.
 */
class SampleWorkshopViewModel(private val repo: Repo) : ViewModel() {

    private val _ui = MutableStateFlow(SampleWorkshopUi())
    val ui: StateFlow<SampleWorkshopUi> = _ui

    /** `null` تا وقتی پرسیده نشده. */
    private val hasData = MutableStateFlow<Boolean?>(null)
    private val unlocked = MutableStateFlow(false)

    val gate: StateFlow<SampleWorkshop.Gate> =
        combine(hasData, unlocked) { d, u -> SampleWorkshop.gate(d, u) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SampleWorkshop.Gate.CHECKING)

    init {
        refresh()
    }

    /**
     * دوباره می‌پرسد کارگاه چیزی دارد یا نه.
     *
     * بعد از بستنِ پیامِ پایانِ ساخت صدا زده می‌شود، نه همان لحظهٔ
     * ساخت: در شروعِ اجباری، این پرسش صفحه را کنار می‌زند، و کاربر باید
     * اول ببیند چه چیزی ساخته شد.
     */
    fun refresh() {
        viewModelScope.launch {
            hasData.value = runCatching { repo.hasAnyWorkshopData() }.getOrDefault(true)
        }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null, isError = false)
        refresh()
    }

    /** رمزِ ورودِ همین دستگاه؛ درست باشد، یک بار ساختن باز می‌شود. */
    fun unlock(settings: Settings, pin: String) {
        if (AuthViewModel.verifyPin(settings, pin)) {
            unlocked.value = true
            _ui.value = _ui.value.copy(pinError = null)
        } else {
            _ui.value = _ui.value.copy(pinError = "رمز اشتباه است.")
        }
    }

    fun create() {
        if (_ui.value.busy) return
        val g = gate.value
        // دفاع در عمق: صفحه دکمه را در این حالت‌ها نشان نمی‌دهد.
        if (g == SampleWorkshop.Gate.CHECKING || g == SampleWorkshop.Gate.LOCKED) return
        _ui.value = SampleWorkshopUi(busy = true)
        viewModelScope.launch {
            val r = runCatching { SampleWorkshop.create(repo) }
            // موفق یا ناتمام، کارگاه حالا چیزی دارد: قفل دوباره بسته.
            unlocked.value = false
            _ui.value = r.fold(
                onSuccess = { res ->
                    SampleWorkshopUi(
                        message = "کارگاهِ نمونه ساخته شد: " +
                            "${res.orders.fa()} سفارش، ${res.people.fa()} خیاط و ناظر، " +
                            "${res.customers.fa()} مشتری و ${res.materials.fa()} قلمِ انبار.\n\n" +
                            "حالا همه را از آنِ خودتان کنید: طرح‌ها، پارچه‌ها، قیمت‌ها، " +
                            "خیاط‌ها، کارکنان و مشتری‌ها قابلِ ویرایش‌اند.\n\n" +
                            "پیش از ثبتِ کارِ واقعی، از تنظیمات «پاک‌کردن کارها و حساب‌ها» " +
                            "را بزنید: سفارش‌ها، پول و انبارِ نمونه می‌روند و آنچه شخصی " +
                            "کرده‌اید می‌ماند.",
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
