package com.afghanjama.ui.vm

import com.afghanjama.util.nowMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.data.entities.StockMovement
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.ceil

/** پنجرهٔ محاسبهٔ نرخِ مصرف (روز). */
const val BURN_WINDOW_DAYS = 30

/** پوششِ هدف: می‌خواهیم موجودی برای این تعداد روز کفایت کند. */
const val TARGET_COVER_DAYS = 30

/** آستانهٔ «به‌زودی تمام می‌شود» (روز). */
const val RUNOUT_SOON_DAYS = 7

/**
 * یک قلمِ مواد با نرخِ مصرف و پیشنهادِ خرید.
 * همهٔ عددها از گردشِ واقعیِ انبار می‌آید، نه از حدس.
 */
data class ReorderRow(
    val name: String,
    val unit: String,
    val stock: Double,
    val minLevel: Double,
    val avgPrice: Double,
    /** مصرفِ واقعی در پنجرهٔ محاسبه (مثبت). */
    val consumed: Double
) {
    /** میانگینِ مصرفِ روزانه در پنجره. */
    val dailyRate: Double get() = consumed / BURN_WINDOW_DAYS

    /** چند روز دیگر با این نرخ تمام می‌شود — null یعنی مصرفی نداشته. */
    val daysOfCover: Int?
        get() = if (dailyRate <= 0.0) null
        else (stock / dailyRate).toInt()

    /** مقدارِ پیشنهادیِ خرید: تا پوششِ هدف، و دستِ‌کم تا حدِ هشدار. */
    val suggestedQty: Double
        get() {
            val toTarget = dailyRate * TARGET_COVER_DAYS - stock
            val toMin = minLevel - stock
            val need = maxOf(toTarget, toMin, 0.0)
            return if (need <= 0.0) 0.0 else ceil(need * 100) / 100
        }

    val estimatedCost: Long get() = (suggestedQty * avgPrice).toLong()

    /** به‌زودی تمام می‌شود؟ */
    val runsOutSoon: Boolean get() = daysOfCover != null && daysOfCover!! <= RUNOUT_SOON_DAYS

    val needsPurchase: Boolean get() = suggestedQty > 0.0
}

/**
 * محاسبهٔ مشترکِ پیشنهادِ خرید — هم صفحهٔ «پیشنهاد خرید» و هم هشدارِ
 * مرکزِ هشدار از همین یک تابع می‌خوانند تا هرگز دو عددِ متفاوت ندهند.
 *
 * مصرف = گردشِ منفی، بدونِ «اصلاح» دستی (اصلاحِ موجودی مصرفِ واقعی نیست
 * و اگر شمرده شود نرخِ مصرف را الکی بالا می‌برد).
 */
fun buildReorderRows(
    materials: List<MaterialStock>,
    movements: List<StockMovement>
): List<ReorderRow> {
    val consumedBy = movements
        .filter { it.delta < 0 && !it.reason.contains("اصلاح") }
        .groupBy { it.name.trim() to it.unit.trim() }
        .mapValues { (_, list) -> list.sumOf { -it.delta } }

    return materials
        .map { m ->
            ReorderRow(
                name = m.name,
                unit = m.unit,
                stock = m.amount,
                minLevel = m.minLevel,
                avgPrice = m.avgPrice,
                consumed = consumedBy[m.name.trim() to m.unit.trim()] ?: 0.0
            )
        }
        .sortedWith(
            // فوری‌ترین اول: کم‌ترین روزِ باقی‌مانده، بعد بیشترین نیاز
            compareBy<ReorderRow> { it.daysOfCover ?: Int.MAX_VALUE }
                .thenByDescending { it.suggestedQty }
        )
}

data class PurchasePlanUi(
    val rows: List<ReorderRow> = emptyList()
) {
    val needed: List<ReorderRow> get() = rows.filter { it.needsPurchase }
    val runningOut: List<ReorderRow> get() = rows.filter { it.runsOutSoon }
    val totalCost: Long get() = needed.sumOf { it.estimatedCost }
    val hasData: Boolean get() = rows.isNotEmpty()
}

/**
 * پیشنهادِ خرید مواد: «چه چیزی، چقدر، و چرا» — بر اساسِ نرخِ مصرفِ واقعیِ
 * ۳۰ روزِ اخیر. هشدارِ کمبودِ فعلی فقط می‌گفت «کم است»؛ این می‌گوید
 * چند روزِ دیگر تمام می‌شود و چقدر باید خرید.
 */
class PurchasePlanViewModel(private val repo: Repo) : ViewModel() {

    val ui: StateFlow<PurchasePlanUi> = combine(
        repo.observeMaterialStock(),
        repo.observeStockMovementsSince(
            nowMillis() - BURN_WINDOW_DAYS * 86_400_000L
        )
    ) { materials, movements ->
        PurchasePlanUi(buildReorderRows(materials, movements))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchasePlanUi())
}
