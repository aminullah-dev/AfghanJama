package com.afghanjama.data

/**
 * «نقطهٔ سربه‌سر» — این ماه چقدر باید بفروشم تا کرایه و برق و معاش و بقیهٔ
 * هزینه‌های ثابت درآید؟ و تا امروز چقدرش را درآورده‌ام؟
 *
 * **مسئله‌ای که حل می‌کند.** کارفرما هزینه‌های ثابتش را می‌داند و فروشش را
 * می‌داند، ولی این دو را به هم وصل نمی‌کند: «آیا این ماه از صفر رد شدم یا
 * هنوز دارم فقط کرایه را درمی‌آورم؟» این همان خطی است که زیرِ آن ضرر است و
 * بالایش سود.
 *
 * **منطق.** هر فروش به اندازهٔ **سودِ ناخالصش** (فروش − بهای تمام‌شده) به
 * پوشاندنِ هزینه‌های ثابت کمک می‌کند. وقتی سودِ ناخالصِ انباشتهٔ ماه به
 * هزینه‌های ثابت برسد، سربه‌سر شده‌ای؛ از آن به بعد سودِ خالص است.
 *
 * درآمدِ لازم = هزینهٔ ثابت ÷ حاشیهٔ سودِ ناخالص. اگر حاشیه صفر یا منفی باشد،
 * با فروشِ بیشتر هم سربه‌سر نمی‌شوی (هر فروش ضرر می‌دهد) — و این خودش
 * مهم‌ترین خبر است، پس `null` می‌دهیم نه عددِ قلابی.
 *
 * خالصِ منطق: بی دیتابیس و بی Compose، تا آزمون مستقیم بسنجدش.
 */
object BreakEven {

    data class Inputs(
        /** هزینه‌های ثابتِ ماهانه (کرایه، برق، معاش…) که باید درآید. */
        val fixedCostsMonthly: Long,
        /** حاشیهٔ سودِ ناخالص به درصد (سودِ ناخالص ÷ فروش)، از صورتِ سود و زیان. */
        val grossMarginPercent: Int,
        /** فروشِ خالصِ این ماه تا امروز. */
        val revenueThisMonth: Long,
        /** سودِ ناخالصِ این ماه تا امروز — همین است که هزینهٔ ثابت را می‌پوشاند. */
        val grossProfitThisMonth: Long,
    )

    data class Snapshot(
        val fixedCosts: Long,
        /** درآمدی که برای پوشاندنِ هزینهٔ ثابت لازم است؛ `null` اگر حاشیه ≤ ۰. */
        val breakEvenRevenue: Long?,
        /** چند درصدِ هزینهٔ ثابت تا الان با سودِ ناخالصِ ماه پوشیده شده (می‌تواند از ۱۰۰ بگذرد). */
        val coveredPercent: Int,
        /** درآمدِ بیشتری که تا سربه‌سر مانده؛ ۰ یعنی رسیده. `null` اگر حاشیه ≤ ۰ و هنوز نرسیده. */
        val revenueToGo: Long?,
        /** از سربه‌سر گذشته‌ای؟ (سودِ ناخالصِ ماه ≥ هزینهٔ ثابت.) */
        val pastBreakEven: Boolean,
        /** ماهِ تازه که همه‌اش صفر است، چیزی برای نشان دادن ندارد. */
        val hasData: Boolean,
    )

    fun assess(i: Inputs): Snapshot {
        val fixed = i.fixedCostsMonthly.coerceAtLeast(0)
        val margin = i.grossMarginPercent

        val breakEvenRevenue: Long? = if (margin > 0) fixed * 100 / margin else null

        val coveredPercent: Int =
            if (fixed <= 0) 100
            else (i.grossProfitThisMonth * 100 / fixed).toInt()

        val pastBreakEven = i.grossProfitThisMonth >= fixed

        val remainingProfit = (fixed - i.grossProfitThisMonth).coerceAtLeast(0)
        val revenueToGo: Long? = when {
            remainingProfit == 0L -> 0L
            margin > 0 -> remainingProfit * 100 / margin
            else -> null   // با حاشیهٔ ≤۰ هر فروش ضرر است؛ سربه‌سر با فروشِ بیشتر نمی‌شود
        }

        return Snapshot(
            fixedCosts = fixed,
            breakEvenRevenue = breakEvenRevenue,
            coveredPercent = coveredPercent,
            revenueToGo = revenueToGo,
            pastBreakEven = pastBreakEven,
            hasData = i.fixedCostsMonthly != 0L || i.revenueThisMonth != 0L || i.grossProfitThisMonth != 0L,
        )
    }
}
