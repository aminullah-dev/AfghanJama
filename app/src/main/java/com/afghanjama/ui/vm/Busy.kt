package com.afghanjama.ui.vm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * نگهبانِ «یک بار در یک زمان» برای کارهایی که پول می‌نویسند.
 *
 * دو ضربهٔ سریع روی «ثبت پرداخت» می‌توانست دو پرداخت ثبت کند: تابع در
 * پس‌زمینه اجرا می‌شد و دکمه تا برگشتنِ نتیجه فعال می‌ماند. بستنِ دیالوگ
 * هم دفاعِ واقعی نیست — فقط زمان‌بندیِ رابط است.
 *
 * [once] روی نخِ اصلی صدا زده می‌شود (viewModelScope پیش‌فرض Main است)،
 * پس خواندن و ست‌کردنِ پرچم بینشان وقفه‌ای ندارد و ضربهٔ دوم قطعاً رد
 * می‌شود. [state] برای غیرفعال‌کردن و چرخاندنِ دکمه است.
 */
class Busy {
    private val _state = MutableStateFlow(false)
    val state: StateFlow<Boolean> = _state.asStateFlow()

    /** اگر کاری در جریان است، این یکی اصلاً اجرا نمی‌شود. */
    suspend fun once(block: suspend () -> Unit) {
        if (_state.value) return
        _state.value = true
        try {
            block()
        } finally {
            _state.value = false
        }
    }
}
