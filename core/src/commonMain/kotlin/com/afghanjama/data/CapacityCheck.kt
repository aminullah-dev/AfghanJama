package com.afghanjama.data

import kotlin.math.ceil

/**
 * «این سفارشِ تازه را تا فلان تاریخ می‌رسانم یا نه؟» — پیش از قبولِ کار.
 *
 * **مسئله‌ای که حل می‌کند.** کارفرما سرِ تحویل قول می‌دهد، بی‌آنکه بداند
 * کارگاه چقدر کارِ زیرِ دست دارد. پیش‌بینیِ تحویلِ اپ کارهای **موجود** را
 * می‌سنجد؛ این یک قدم جلوتر است: «اگر این سفارش را هم قبول کنم، با سرعتِ
 * فعلی کِی آماده می‌شود و از مهلت دیر می‌شود یا نه؟»
 *
 * **منطق.** سفارشِ تازه تهِ صفِ کار می‌نشیند: ابتدا کارهای باز تمام می‌شوند،
 * بعد این. روزهای لازم = (کارِ باز + این سفارش) ÷ سرعتِ دوختِ روزانه.
 *
 * سرعتِ صفر یعنی «هنوز معلوم نیست» (کارگاهِ تازه بی‌تحویل)، نه «هرگز».
 *
 * خالصِ منطق: ورودی از بیرون، بی دیتابیس و بی Compose، تا آزمون بسنجدش.
 */
object CapacityCheck {

    data class Inputs(
        /** تعدادِ عددِ کارِ بازِ فعلیِ کارگاه (پیش از این سفارش). */
        val openPieces: Int,
        /** سرعتِ دوختِ روزانهٔ کارگاه (عدد در روز) — از پیش‌بینیِ تحویل. */
        val piecesPerDay: Double,
        /** تعدادِ عددِ سفارشِ تازه. */
        val newOrderPieces: Int,
        /** از امروز تا مهلتِ سفارشِ تازه، به روز. */
        val daysUntilDue: Int,
    )

    data class Result(
        /** چند روز تا این سفارش آماده می‌شود (صف + خودش)؛ `null` اگر سرعت معلوم نیست. */
        val readyInDays: Int?,
        /** چند روز از مهلت دیر می‌شود؛ ۰ یعنی به‌موقع. `null` اگر سرعت معلوم نیست. */
        val lateByDays: Int?,
        /** به‌موقع می‌رسد؟ `null` اگر سرعت معلوم نیست. */
        val canMakeIt: Boolean?,
    ) {
        val hasRate: Boolean get() = readyInDays != null
    }

    fun check(i: Inputs): Result {
        if (i.piecesPerDay <= 0.0) return Result(readyInDays = null, lateByDays = null, canMakeIt = null)
        val queue = (i.openPieces + i.newOrderPieces).coerceAtLeast(0)
        val ready = ceil(queue / i.piecesPerDay).toInt()
        val late = (ready - i.daysUntilDue).coerceAtLeast(0)
        return Result(readyInDays = ready, lateByDays = late, canMakeIt = ready <= i.daysUntilDue)
    }
}
