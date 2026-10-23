package com.afghanjama.data

/**
 * «پولِ نقدِ کارگاه در روزهای پیشِ رو چه می‌شود؟» — پیش‌بینیِ ساده و محتاط،
 * از روی همان عددهایی که اپ از قبل دارد.
 *
 * **مسئله‌ای که حل می‌کند.** `FinancialHealth` می‌گوید «نقد الان چقدر است و
 * چند روز هزینه را می‌دهد». ولی سؤالِ بعدیِ کارفرما این است: «با طلب‌هایی که
 * می‌آید، بدهی‌هایی که باید بدهم، کرایه و کارمزدی که در راه است — آیا وسطِ
 * ماه پولم کم می‌شود؟» این یک پیش‌بینی است، نه عکسِ لحظه‌ای.
 *
 * **محتاط، نه خوش‌بین.** پول‌هایی که باید بدهیم زودتر فرض می‌شوند و طلبی که
 * باید بگیریم دیرتر — تا هشدار «کم می‌آید» زود برسد، نه بعد از آنکه کم آمد.
 * همهٔ فرض‌ها اینجا ثابت و پیداست (پایین)، نه پنهان در کد.
 *
 * **فرض‌ها (آهنگِ ورود و خروجِ پول):**
 *  - فروش و هزینهٔ روزانه یکنواخت‌اند (میانگینِ ۳۰ روزِ اخیر، تقسیم بر ۳۰).
 *  - کارمزدِ باز در [WAGE_DAYS] روزِ اول پرداخت می‌شود (تسویهٔ هفتگی).
 *  - بدهیِ ما در [PAY_DAYS] روز پرداخت می‌شود، خطی.
 *  - طلبِ ما در [COLLECT_DAYS] روز وصول می‌شود، خطی (دیرتر از پرداخت‌ها — محتاط).
 *
 * خالصِ منطق: بی دیتابیس و بی Compose، تا آزمون مستقیم بسنجدش. متن کارِ صفحه است.
 */
object CashForecast {

    /** افقِ پیش‌بینی — یک ماهِ پیشِ رو. */
    const val HORIZON_DAYS = 30

    /** کارمزدِ باز تا این روز پرداخت می‌شود (تسویهٔ هفتگی). */
    const val WAGE_DAYS = 7

    /** بدهیِ ما تا این روز پرداخت می‌شود. */
    const val PAY_DAYS = 21

    /** طلبِ ما تا این روز وصول می‌شود (دیرتر از پرداخت‌ها — محتاط). */
    const val COLLECT_DAYS = 30

    /** پولِ نقدِ کمتر از این، «کم آمدن» است. از بیرون قابلِ تغییر (حاشیهٔ امن). */
    const val SAFETY_BUFFER = 0L

    data class Inputs(
        /** پولِ نقدِ امروز (صندوق + بانک + صندوقِ فایده). */
        val cash: Long,
        /** فروشِ خالصِ ۳۰ روزِ اخیر — ورودیِ روزانه از آن حساب می‌شود. */
        val sales30: Long,
        /** هزینهٔ عمومیِ ۳۰ روزِ اخیر — خروجیِ روزانه از آن حساب می‌شود. */
        val expense30: Long,
        /** طلبِ قابلِ وصول از مشتریان (ورودی در افق). */
        val receivable: Long,
        /** بدهیِ ما به تأمین‌کننده‌ها و… (خروجی در افق). */
        val payable: Long,
        /** کارمزدِ تسویه‌نشده (خروجیِ زود). */
        val openWages: Long,
        val horizonDays: Int = HORIZON_DAYS,
        val safetyBuffer: Long = SAFETY_BUFFER,
    )

    enum class Level { GOOD, WATCH, RISK }

    enum class Kind {
        /** حتی با وصولِ همهٔ طلب هم، پول وسطِ افق زیرِ حاشیهٔ امن می‌رود. */
        SHORT_EVEN_WITH_COLLECTING,

        /** اگر طلب را به‌موقع وصول نکنی، پول کم می‌آید؛ با وصول، می‌رسد. */
        SHORT_UNLESS_COLLECTING,

        /** پول در افق می‌ماند ولی روند نزولی است (تهِ افق کمتر از امروز). */
        DECLINING,

        /** همه‌چیز روبه‌راه است. */
        HEALTHY,
    }

    /** [value] عددِ همان نشانه است: روز یا مبلغ. */
    data class Signal(val kind: Kind, val level: Level, val value: Long)

    data class Snapshot(
        /** پولِ امروز. */
        val start: Long,
        /** پولِ پیش‌بینی‌شده در تهِ افق. */
        val end: Long,
        /** پایین‌ترین پولِ پیش‌بینی‌شده در افق، و روزش. */
        val lowest: Long,
        val lowestDay: Int,
        /** اولین روزی که پول زیرِ حاشیهٔ امن می‌رود؛ `null` یعنی هرگز. */
        val shortDay: Int?,
        /** تهِ افق اگر طلب **وصول نشود** — تا تأثیرِ وصولِ طلب دیده شود. */
        val endWithoutCollecting: Long,
        val level: Level,
        /** بدترین اول. */
        val signals: List<Signal>,
    ) {
        /** کارگاهِ تازه که هیچ عددی ندارد، پیش‌بینی هم ندارد. */
        val hasData: Boolean
            get() = start != 0L || end != 0L || lowest != 0L
    }

    /**
     * پولِ پیش‌بینی‌شده در پایانِ روزِ [day] (۰ = امروز).
     * [withCollecting] = false یعنی طلب وصول نمی‌شود (برای مقایسه).
     */
    private fun cashAt(i: Inputs, day: Int, withCollecting: Boolean): Long {
        val d = day.coerceIn(0, i.horizonDays)
        val win = WINDOW().toLong()
        // عملیاتِ روزانه: (فروش − هزینه) × روز، هر دو از پنجرهٔ ۳۰ روزه.
        val operating = (i.sales30 - i.expense30) * d / win
        val wagesOut = ramp(i.openWages, d, WAGE_DAYS)
        val payOut = ramp(i.payable, d, PAY_DAYS)
        val collectIn = if (withCollecting) ramp(i.receivable, d, COLLECT_DAYS) else 0L
        return i.cash + operating + collectIn - wagesOut - payOut
    }

    /** مبلغی که تا روزِ [day] به‌شکلِ خطی تا [overDays] پخش شده، وارد/خارج شده. */
    private fun ramp(total: Long, day: Int, overDays: Int): Long {
        if (overDays <= 0) return total
        return total * day.coerceAtMost(overDays) / overDays
    }

    private fun WINDOW(): Int = FinancialHealth.WINDOW_DAYS

    fun project(i: Inputs): Snapshot {
        val horizon = i.horizonDays.coerceAtLeast(1)
        val start = cashAt(i, 0, withCollecting = true)

        var lowest = start
        var lowestDay = 0
        var shortDay: Int? = null
        for (day in 0..horizon) {
            val c = cashAt(i, day, withCollecting = true)
            if (c < lowest) {
                lowest = c
                lowestDay = day
            }
            if (shortDay == null && c < i.safetyBuffer) shortDay = day
        }

        val end = cashAt(i, horizon, withCollecting = true)
        val endWithoutCollecting = cashAt(i, horizon, withCollecting = false)

        // آیا بدونِ وصولِ طلب، جایی از افق زیرِ حاشیه می‌رود؟
        val shortWithoutCollecting = (0..horizon).any { cashAt(i, it, withCollecting = false) < i.safetyBuffer }

        val signals = buildList {
            when {
                shortDay != null ->
                    add(Signal(Kind.SHORT_EVEN_WITH_COLLECTING, Level.RISK, shortDay.toLong()))
                shortWithoutCollecting ->
                    // با وصولِ طلب نجات پیدا می‌کند — ولی اگر وصول نشود، کم می‌آید.
                    add(Signal(Kind.SHORT_UNLESS_COLLECTING, Level.WATCH, i.receivable.coerceAtLeast(0)))
                end < start ->
                    add(Signal(Kind.DECLINING, Level.WATCH, start - end))
                else ->
                    add(Signal(Kind.HEALTHY, Level.GOOD, end))
            }
        }

        val level = signals.minByOrNull { rank(it.level) }?.level ?: Level.GOOD

        return Snapshot(
            start = start,
            end = end,
            lowest = lowest,
            lowestDay = lowestDay,
            shortDay = shortDay,
            endWithoutCollecting = endWithoutCollecting,
            level = level,
            signals = signals.sortedBy { rank(it.level) },
        )
    }

    private fun rank(l: Level): Int = when (l) {
        Level.RISK -> 0
        Level.WATCH -> 1
        Level.GOOD -> 2
    }
}
