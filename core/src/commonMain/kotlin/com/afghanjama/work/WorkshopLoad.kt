package com.afghanjama.work

import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.SewingAssignment

/**
 * بارِ کارگاه — جوابِ «تا پنج‌شنبه می‌توانم قبولش کنم؟».
 *
 * **چرا این محاسبه لازم شد.** اپ همهٔ داده‌اش را داشت — مهلتِ هر
 * سفارش، چه کسی چه چیزی زیرِ دست دارد، و چند دست در ماهِ گذشته واقعاً
 * تمام شده — ولی هیچ‌جا جمعشان نمی‌کرد. پس اولین سؤالی که هر خیاط سرِ
 * گرفتنِ سفارش دارد، بی‌جواب می‌مانْد و با حدس تصمیم گرفته می‌شد.
 *
 * **ظرفیت سنجیده می‌شود، نه فرض.** عدد از کارِ واقعاً تمام‌شدهٔ
 * [windowDays] روزِ گذشته می‌آید. اگر تاریخچه‌ای نباشد، هیچ عددی
 * ساخته نمی‌شود — [hasCapacityData] می‌گوید نه، و صفحه به‌جای عددِ
 * قلابی، همین را می‌نویسد. عددِ ساختگی بدتر از نبودنِ عدد است، چون
 * کارفرما بر اساسش قول می‌دهد.
 *
 * **تقسیم بر روزِ تقویمی است، نه روزِ کاری.** روزِ کاری را اپ
 * نمی‌داند (جمعه‌ها؟ تعطیلی‌ها؟) و حدس زدنش یعنی عددی که کسی
 * نمی‌تواند وارسی‌اش کند. تقسیمِ تقویمی کمی محتاطانه‌تر جواب می‌دهد و
 * محتاطانه بودنِ یک تخمینِ تحویل، عیب نیست.
 *
 * تابع **خالص** است: ورودی و زمان از بیرون می‌آیند، پس همان‌طور که
 * `Margin` و `Installments` آزموده می‌شوند، این هم آزمودنی است.
 */
object WorkshopLoad {

    /** پنجرهٔ پیش‌فرضِ سنجشِ ظرفیت. */
    const val WINDOW_DAYS = 30

    /** چند هفته جلوتر نشان داده شود. */
    const val WEEKS_AHEAD = 4

    private const val DAY = 86_400_000L
    private const val WEEK = 7 * DAY

    /**
     * مرحله‌هایی که یعنی «کار هنوز تمام نشده».
     *
     * `STORED` و `SENT` نیستند: آن دو یعنی دوخته شده و در انبار است،
     * پس دیگر ظرفیتِ دوخت نمی‌خواهد — حتی اگر مشتری هنوز نبرده باشد.
     */
    private val openStatuses = setOf(
        OrderStatus.IN_STOCK.name,
        OrderStatus.CUTTING.name,
        OrderStatus.CUT_DONE.name,
        OrderStatus.SEWING.name,
        OrderStatus.REVIEW.name,
    )

    data class Bucket(
        /** آغازِ بازه (میلی‌ثانیه). */
        val startMs: Long,
        /** پایانِ بازه — انحصاری. */
        val endMs: Long,
        /** چند دست در این بازه مهلت دارد و هنوز ساخته نشده. */
        val pieces: Int,
        /** با ظرفیتِ سنجیده‌شده، در این بازه چند دست جا می‌شود. */
        val capacity: Int,
    ) {
        val free: Int get() = capacity - pieces
        val over: Boolean get() = pieces > capacity
    }

    data class Result(
        /** میانگینِ دستِ تمام‌شده در روز، از تاریخچهٔ واقعی. */
        val piecesPerDay: Double,
        val windowDays: Int,
        /** چند دست در آن پنجره تمام شد — پایهٔ عدد. */
        val windowPieces: Int,
        val buckets: List<Bucket>,
        /** دستی که مهلتش گذشته و هنوز ساخته نشده. */
        val overduePieces: Int,
        /** دستی که اصلاً مهلت ندارد — قابلِ برنامه‌ریزی نیست. */
        val undatedPieces: Int,
        /** جمعِ کارِ مانده، با مهلت یا بی مهلت. */
        val remainingPieces: Int,
    ) {
        /** آیا اصلاً تاریخچه‌ای برای سنجشِ ظرفیت بود؟ */
        val hasCapacityData: Boolean get() = windowPieces > 0

        /**
         * اولین بازه‌ای که هنوز جا دارد — `null` یعنی هر چهار هفته پر
         * است، یا ظرفیت سنجیده نشده.
         */
        val firstFree: Bucket? get() = if (hasCapacityData) buckets.firstOrNull { it.free > 0 } else null
    }

    /**
     * @param orders همهٔ سفارش‌ها؛ خودش فیلتر می‌کند.
     * @param sewn تحویل‌های دوخت — فقط `DONE`ها با `doneAt` شمرده می‌شوند.
     * @param now زمانِ مرجع، از بیرون تا آزمودنی بماند.
     */
    fun of(
        orders: List<Order>,
        sewn: List<SewingAssignment>,
        now: Long,
        windowDays: Int = WINDOW_DAYS,
        weeksAhead: Int = WEEKS_AHEAD,
    ): Result {
        val since = now - windowDays * DAY
        val windowPieces = sewn
            .filter { it.status == "DONE" && (it.doneAt ?: 0L) >= since && (it.doneAt ?: 0L) <= now }
            .sumOf { it.qty }
        val perDay = if (windowDays > 0) windowPieces.toDouble() / windowDays else 0.0

        val open = orders.filter { it.status in openStatuses }
        // کارِ مانده = آنچه سفارش داده شده منهای آنچه تا حالا در انبار
        // نشسته. `deliveredQty` اینجا به کار نمی‌آید: آن دربارهٔ رفتنِ
        // کالا نزدِ مشتری است، نه ساخته شدنش.
        fun remaining(o: Order) = (o.qty - o.storedQty).coerceAtLeast(0)

        val undated = open.filter { it.dueDate <= 0L }.sumOf { remaining(it) }
        val dated = open.filter { it.dueDate > 0L }
        val overdue = dated.filter { it.dueDate < now }.sumOf { remaining(it) }

        val weekCapacity = (perDay * 7).toInt()
        val buckets = (0 until weeksAhead).map { i ->
            val start = now + i * WEEK
            val end = start + WEEK
            Bucket(
                startMs = start,
                endMs = end,
                pieces = dated.filter { it.dueDate in start until end }.sumOf { remaining(it) },
                capacity = weekCapacity,
            )
        }

        return Result(
            piecesPerDay = perDay,
            windowDays = windowDays,
            windowPieces = windowPieces,
            buckets = buckets,
            overduePieces = overdue,
            undatedPieces = undated,
            remainingPieces = open.sumOf { remaining(it) },
        )
    }
}
