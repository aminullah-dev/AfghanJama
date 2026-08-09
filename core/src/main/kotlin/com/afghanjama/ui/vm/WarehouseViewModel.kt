package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.data.entities.StockMovement
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.toPersianDigits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WarehouseUi(
    val message: String? = null,
    val isError: Boolean = false
)

/** رقمِ مقدار برای پیام‌ها: بدونِ اعشارِ بی‌مصرف. */
private fun qty(v: Double): String =
    (if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()).toPersianDigits()

/**
 * انبار عمومی مواد خام.
 *
 * **هر کنش می‌گوید واقعاً چه شد.** تا امروز هر تابع بعد از فراخوانِ
 * مخزن یک پیامِ خوش‌بینانه می‌داد — «ثبت شد» — بی آنکه بپرسد چیزی
 * ثبت شد یا نه. اگر قلم موجودی نداشت یا کمتر از خواسته داشت، مخزن
 * بی‌صدا رد می‌شد و کاربر پیامِ سبز می‌دید. حالا [Repo.adjustMaterialStock]
 * مقدارِ واقعاً جابه‌جاشده را برمی‌گرداند و پیام از روی همان ساخته
 * می‌شود، نه از روی آنچه کاربر خواسته بود.
 */
class WarehouseViewModel(private val repo: Repo) : ViewModel() {

    val materials: StateFlow<List<MaterialStock>> =
        repo.observeMaterialStock()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** کاردکس/گردش انبار (رد حسابرسی هر تغییر موجودی). */
    val movements: StateFlow<List<StockMovement>> =
        repo.observeStockMovements()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(WarehouseUi())
    val ui: StateFlow<WarehouseUi> = _ui

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    private fun say(text: String, error: Boolean = false) =
        _ui.update { it.copy(message = text, isError = error) }

    /** کاردکسِ یک قلم — ورود و خروجش به ترتیبِ زمان. */
    fun movementsOf(item: MaterialStock) =
        repo.observeItemMovements(item.name, item.unit)

    /**
     * ورودِ تازه بدونِ خرید: برگشت از تولید، یا اضافه‌ای که در
     * انبارگردانی پیدا شده. خریدِ واقعی از صفحهٔ «خرید مواد» می‌آید
     * چون آنجا پول هم جابه‌جا می‌شود.
     */
    fun receive(item: MaterialStock, amount: Double, reason: String, note: String = "") =
        viewModelScope.launch {
            if (amount <= 0.0) {
                say("مقدارِ ورود باید بیشتر از صفر باشد.", error = true)
                return@launch
            }
            val moved = repo.adjustMaterialStock(item.name, item.unit, amount, reason, note)
            if (moved > 0.0) {
                say("${qty(moved)} ${item.unit} به «${item.name}» اضافه شد.")
            } else {
                say("ورودِ «${item.name}» ثبت نشد.", error = true)
            }
        }

    /**
     * خروجِ تازه: مصرف، ضایعات، یا کسریِ انبارگردانی.
     *
     * اگر موجودی کمتر از خواسته باشد، هرچه هست خارج می‌شود و پیام
     * **همان مقدار** را می‌گوید — نه عددی که کاربر تایپ کرده بود.
     */
    fun issue(item: MaterialStock, amount: Double, reason: String, note: String = "") =
        viewModelScope.launch {
            if (amount <= 0.0) {
                say("مقدارِ خروج باید بیشتر از صفر باشد.", error = true)
                return@launch
            }
            val moved = -repo.adjustMaterialStock(item.name, item.unit, -amount, reason, note)
            when {
                moved <= 0.0 ->
                    say("«${item.name}» موجودی نداشت؛ چیزی خارج نشد.", error = true)
                moved < amount ->
                    say(
                        "فقط ${qty(moved)} ${item.unit} موجود بود و همان خارج شد " +
                            "(درخواست: ${qty(amount)}).",
                        error = true
                    )
                else ->
                    say("${qty(moved)} ${item.unit} از «${item.name}» خارج شد.")
            }
        }

    /**
     * انبارگردانی: موجودی روی رقمِ **شمرده‌شده** تنظیم می‌شود.
     *
     * کاربر عددی را که در انبار شمرده وارد می‌کند، نه اختلاف را —
     * حساب کردنِ اختلاف کارِ اپ است. تفاوت با دلیلِ «انبارگردانی» در
     * کاردکس می‌نشیند تا شش ماه بعد معلوم باشد چرا موجودی پرید.
     */
    fun countTo(item: MaterialStock, counted: Double, note: String = "") =
        viewModelScope.launch {
            if (counted < 0.0) {
                say("مقدارِ شمرده‌شده منفی نمی‌شود.", error = true)
                return@launch
            }
            val delta = counted - item.amount
            if (delta == 0.0) {
                say("شمارش با موجودیِ ثبت‌شده یکی است؛ چیزی تغییر نکرد.")
                return@launch
            }
            val moved = repo.adjustMaterialStock(
                item.name, item.unit, delta, reason = "انبارگردانی", note = note
            )
            if (moved == 0.0) {
                say("انبارگردانیِ «${item.name}» ثبت نشد.", error = true)
                return@launch
            }
            say(
                "«${item.name}»: از ${qty(item.amount)} به ${qty(item.amount + moved)} " +
                    "${item.unit} اصلاح شد (${if (moved > 0) "اضافه" else "کسری"} ${qty(kotlin.math.abs(moved))})."
            )
        }

    /**
     * قلمِ تازه با موجودیِ اولیه — مهاجرت از اپ یا دفترِ قبلی.
     *
     * جدا از «خرید» است چون پولی جابه‌جا نمی‌شود؛ توضیحش در
     * [Repo.setOpeningStock].
     */
    fun addOpening(
        name: String,
        unit: String,
        amount: Double,
        unitPrice: Long,
        note: String = ""
    ) = viewModelScope.launch {
        val ok = repo.setOpeningStock(name, unit, amount, unitPrice, note)
        if (ok) {
            say("«${name.trim()}» با ${qty(amount)} ${unit.trim()} به انبار افزوده شد.")
        } else {
            say("نام، واحد و مقدار لازم است؛ مقدار هم باید بیشتر از صفر باشد.", error = true)
        }
    }

    /**
     * حذفِ ردیفِ خالی — برای ردیف‌های شبحی که با ۰ متر ساخته شده بودند.
     * ردیفی که موجودی دارد حذف نمی‌شود؛ اول باید ضایعاتش ثبت شود تا
     * سندِ حسابداری‌اش هم بخورد.
     */
    fun deleteRow(item: MaterialStock) = viewModelScope.launch {
        val ok = repo.deleteMaterialStockIfEmpty(item)
        if (ok) {
            say("ردیف «${item.name}» حذف شد.")
        } else {
            say(
                "«${item.name}» هنوز ${qty(item.amount)} ${item.unit} موجودی دارد. " +
                    "اول با «خروج» یا «انبارگردانی» صفرش کنید تا سندش هم ثبت شود.",
                error = true
            )
        }
    }

    fun setMinLevel(item: MaterialStock, minLevel: Double) = viewModelScope.launch {
        repo.setMaterialMinLevel(item.name, item.unit, minLevel)
        say(
            if (minLevel > 0.0)
                "هشدارِ «${item.name}» زیر ${qty(minLevel)} ${item.unit} روشن می‌شود."
            else "هشدارِ کمبودِ «${item.name}» خاموش شد."
        )
    }
}
