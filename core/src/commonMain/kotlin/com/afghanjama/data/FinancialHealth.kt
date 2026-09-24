package com.afghanjama.data

/**
 * «وضعِ مالیِ کارگاه چطور است؟» — یک نگاه، از روی همان دفترِ دوطرفه.
 *
 * **مسئله‌ای که حل می‌کند.** بخشِ مالی فروش، فایده و هزینه را جدا جدا
 * نشان می‌داد، ولی سؤالی که کارفرما واقعاً می‌پرسد این است: «اگر فردا
 * همهٔ بدهی‌ها را بخواهند، پول دارم؟ و این نقد تا کِی خرجِ کارگاه را
 * می‌دهد؟» جوابش در ترازنامه بود و کسی ترازنامه نمی‌خواند.
 *
 * **عددها از کجا می‌آیند.** همه از ژورنال (ترازنامه و صورتِ سود و زیانِ
 * ۳۰ روزه) به‌جز فروش، که از دفترِ فروشِ انبارِ محصول می‌آید — همان
 * منبعی که کارتِ «فروش» در همان صفحه از آن می‌خوانَد، تا دو عددِ کنارِ هم
 * از هم نیفتند.
 *
 * اینجا متن ساخته نمی‌شود؛ فقط نشانه و عدد. متن کارِ صفحه است.
 * بی دیتابیس و بی Compose، تا آزمون بتواند مستقیم بسنجدش.
 */
object FinancialHealth {

    /** پنجرهٔ نگاه به گذشته برای هزینه و فروش. */
    const val WINDOW_DAYS = 30

    /** نقدی که کمتر از این تعداد روز هزینه را بدهد، خطر است. */
    const val COVER_RISK_DAYS = 14

    /** زیرِ این، «زیرِ نظر». */
    const val COVER_WATCH_DAYS = 30

    /** افتِ فروش از این درصد به بالا دیده می‌شود. */
    const val SALES_DROP_PCT = 30

    /** رشدِ فروش از این درصد به بالا خبرِ خوب است. */
    const val SALES_RISE_PCT = 20

    /** سقفِ نمایشِ روزهای پوشش — بیشتر از این عددِ بی‌معنایی است. */
    const val MAX_COVER_DAYS = 999

    data class Figures(
        /** صندوق + بانک + صندوقِ فایده. */
        val cash: Long,
        /** طلب از مشتریان + پیش‌پرداختِ کارکنان. */
        val receivable: Long,
        /** مواد + کارِ در جریان + محصولِ آماده. */
        val stock: Long,
        /** هر دارایی دیگری که در سه دستهٔ بالا نیست. */
        val otherAssets: Long = 0,
        /** همهٔ بدهی‌ها: تأمین‌کننده، کارمزد، پیش‌دریافتِ مشتری. */
        val debts: Long,
        /** هزینهٔ عمومیِ [WINDOW_DAYS] روزِ اخیر (بی بهای تمام‌شده). */
        val expense30: Long,
        /** فروشِ خالصِ [WINDOW_DAYS] روزِ اخیر. */
        val sales30: Long,
        /** فروشِ خالصِ [WINDOW_DAYS] روزِ پیش از آن. */
        val salesPrev30: Long,
    )

    enum class Level { GOOD, WATCH, RISK }

    enum class Kind {
        /** بدهی از همهٔ دارایی بیشتر است. */
        NET_NEGATIVE,

        /** حتی با وصولِ همهٔ طلب‌ها، بدهی پرداختنی نیست. */
        DEBT_OVER_CASH_AND_RECEIVABLE,

        /** نقد برای بدهی کافی نیست؛ باید طلب وصول شود. */
        DEBT_OVER_CASH,

        /** نقد هزینهٔ کمتر از [COVER_RISK_DAYS] روز را می‌دهد. */
        COVER_SHORT,

        /** نقد هزینهٔ کمتر از [COVER_WATCH_DAYS] روز را می‌دهد. */
        COVER_LOW,

        /** فروش نسبت به دورهٔ قبل افت کرده. */
        SALES_DROP,

        /** فروش نسبت به دورهٔ قبل بالا رفته. */
        SALES_RISE,
    }

    /** [value] عددِ همان نشانه است: روز، درصد، یا مبلغ. */
    data class Signal(val kind: Kind, val level: Level, val value: Long)

    data class Snapshot(
        val figures: Figures,
        /** دارایی − بدهی. */
        val netWorth: Long,
        /** نقد، هزینهٔ چند روز را می‌دهد؛ `null` وقتی هزینه‌ای ثبت نشده. */
        val coverDays: Int?,
        /** تغییرِ فروش به درصد؛ `null` وقتی دورهٔ قبل فروشی نداشته. */
        val salesChangePct: Int?,
        val level: Level,
        /** بدترین اول. */
        val signals: List<Signal>,
    ) {
        /** چیزی برای نشان دادن هست؟ کارگاهِ تازه همه‌اش صفر است. */
        val hasData: Boolean
            get() = figures.cash != 0L || figures.receivable != 0L || figures.stock != 0L ||
                figures.debts != 0L || figures.expense30 != 0L || figures.sales30 != 0L
    }

    fun assess(f: Figures): Snapshot {
        val assets = f.cash + f.receivable + f.stock + f.otherAssets
        val netWorth = assets - f.debts

        val coverDays: Int? = when {
            f.expense30 <= 0L -> null
            f.cash <= 0L -> 0
            else -> {
                val days = f.cash * WINDOW_DAYS / f.expense30
                if (days > MAX_COVER_DAYS) MAX_COVER_DAYS else days.toInt()
            }
        }

        val salesChangePct: Int? =
            if (f.salesPrev30 <= 0L) null
            else ((f.sales30 - f.salesPrev30) * 100 / f.salesPrev30).toInt()

        val signals = buildList {
            if (netWorth < 0) add(Signal(Kind.NET_NEGATIVE, Level.RISK, -netWorth))

            // «پرداختنی نیست» فقط وقتی معنا دارد که بدهی‌ای باشد.
            if (f.debts > 0) {
                val liquid = f.cash.coerceAtLeast(0)
                when {
                    f.debts > liquid + f.receivable.coerceAtLeast(0) ->
                        add(Signal(Kind.DEBT_OVER_CASH_AND_RECEIVABLE, Level.RISK, f.debts - liquid))
                    f.debts > liquid ->
                        add(Signal(Kind.DEBT_OVER_CASH, Level.WATCH, f.debts - liquid))
                }
            }

            if (coverDays != null) {
                when {
                    coverDays < COVER_RISK_DAYS ->
                        add(Signal(Kind.COVER_SHORT, Level.RISK, coverDays.toLong()))
                    coverDays < COVER_WATCH_DAYS ->
                        add(Signal(Kind.COVER_LOW, Level.WATCH, coverDays.toLong()))
                }
            }

            if (salesChangePct != null) {
                when {
                    salesChangePct <= -SALES_DROP_PCT ->
                        add(Signal(Kind.SALES_DROP, Level.WATCH, (-salesChangePct).toLong()))
                    salesChangePct >= SALES_RISE_PCT ->
                        add(Signal(Kind.SALES_RISE, Level.GOOD, salesChangePct.toLong()))
                }
            }
        }.sortedBy { rank(it.level) }

        val level = signals.map { it.level }.minByOrNull { rank(it) }
            ?.takeIf { it != Level.GOOD } ?: Level.GOOD

        return Snapshot(f, netWorth, coverDays, salesChangePct, level, signals)
    }

    private fun rank(l: Level): Int = when (l) {
        Level.RISK -> 0
        Level.WATCH -> 1
        Level.GOOD -> 2
    }
}
