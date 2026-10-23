package com.afghanjama.data

import kotlin.math.ceil

/**
 * «این ماده را چقدر بخرم که تا N روز برسد؟» — خریدِ به‌اندازه، نه حدسی.
 *
 * **مسئله‌ای که حل می‌کند.** اپ می‌گوید «فلان ماده رو به اتمام است»، ولی
 * نمی‌گوید **چقدر** بخری. کم بخری زود تمام می‌شود، زیاد بخری پول می‌خوابد.
 * اینجا از مصرفِ واقعیِ روزانه، مقدارِ خرید حساب می‌شود تا موجودی به
 * پوششِ هدف برسد.
 *
 * **منطق.** موجودیِ هدف = مصرفِ روزانه × روزهای پوشش. خرید = هدف − موجودیِ
 * فعلی (اگر مثبت). اگر مصرفی ثبت نشده، فقط تا حدِ هشدار پیشنهاد می‌شود و
 * روزهای ماندن «نامعلوم» است (نه صفر).
 *
 * خالصِ منطق: ورودی از بیرون، بی دیتابیس و بی Compose، تا آزمون بسنجدش.
 */
object ReorderAdvice {

    data class Inputs(
        /** موجودیِ فعلی (به واحدِ ماده: متر، عدد…). */
        val stock: Double,
        /** مصرفِ روزانه؛ ۰ یعنی در پنجرهٔ اخیر مصرفی ثبت نشده. */
        val dailyUse: Double,
        /** می‌خواهیم موجودی تا این تعداد روز برسد. */
        val coverDays: Int,
        /** حدِ هشدار — موجودیِ زیرِ آن کم است. */
        val minLevel: Double = 0.0,
    )

    data class Result(
        /** موجودیِ فعلی چند روز می‌رسد؛ `null` اگر مصرفی ثبت نشده. */
        val daysLeft: Int?,
        /** مقدارِ پیشنهادیِ خرید (به همان واحد)؛ ۰ یعنی لازم نیست. */
        val suggestedBuy: Double,
        /** زیرِ حدِ هشدار است؟ */
        val belowMin: Boolean,
    ) {
        val hasUse: Boolean get() = daysLeft != null
    }

    fun advise(i: Inputs): Result {
        val belowMin = i.minLevel > 0.0 && i.stock < i.minLevel
        if (i.dailyUse <= 0.0) {
            // بی‌مصرف: فقط تا حدِ هشدار پر کن (اگر تعریف شده)، و روز نامعلوم.
            val toMin = (i.minLevel - i.stock).coerceAtLeast(0.0)
            return Result(daysLeft = null, suggestedBuy = toMin, belowMin = belowMin)
        }
        val daysLeft = (i.stock / i.dailyUse).toInt().coerceAtLeast(0)
        val targetStock = i.coverDays * i.dailyUse
        val suggested = (targetStock - i.stock).coerceAtLeast(0.0)
        // به سقفِ عددِ کامل گرد می‌شود تا کسریِ واحد پیشنهاد نشود، مگر صفر.
        val rounded = if (suggested <= 0.0) 0.0 else ceil(suggested)
        return Result(daysLeft = daysLeft, suggestedBuy = rounded, belowMin = belowMin)
    }
}
