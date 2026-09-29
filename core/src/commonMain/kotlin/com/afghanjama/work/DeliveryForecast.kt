package com.afghanjama.work

import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.SewingAssignment
import kotlin.math.ceil

/**
 * پیش‌بینیِ تحویل — «این سفارش کِی واقعاً آماده می‌شود؟».
 *
 * **چرا [WorkshopLoad] کافی نبود.** آن صفحه می‌گوید هر هفته چند دست
 * مهلت دارد و چند دست جا می‌شود — دربارهٔ **هفته** حرف می‌زند، نه
 * دربارهٔ **سفارش**. کارفرما سفارشی را که دیر می‌شود وقتی می‌فهمید که
 * مهلتش گذشته بود و مرکزِ هشدار قرمز شده بود؛ یعنی وقتی دیگر کاری
 * نمی‌شد کرد جز عذرخواهی.
 *
 * اینجا کارهای باز **به ترتیبِ مهلت** در صف می‌نشینند — همان ترتیبی که
 * هر خیاطِ باتجربه کار را برمی‌دارد — و با سرعتِ **سنجیده‌شدهٔ** همین
 * کارگاه جلو می‌روند. هر سفارشی که با این حساب بعد از مهلتش تمام شود،
 * **پیش از** گذشتنِ مهلت دیده می‌شود.
 *
 * **سرعت از همان پنجره‌ای می‌آید که [WorkshopLoad] می‌سنجد.** دو صفحه
 * نباید برای «چند دست در روز» دو عدد بدهند. بی تاریخچه، عددی ساخته
 * نمی‌شود ([Result.hasData]).
 *
 * **کارِ مانده = آنچه هنوز دوخته نشده.** دستی که دوختش تمام شده و در
 * نظارت است، دیگر ظرفیتِ دوخت نمی‌خواهد؛ اگر شمرده می‌شد، سفارشی که
 * فقط منتظرِ نظارت است «دیر» پیش‌بینی می‌شد.
 *
 * تابعِ خالص؛ زمان از بیرون می‌آید.
 */
object DeliveryForecast {

    private const val DAY = 86_400_000L

    private val openStatuses = setOf(
        OrderStatus.IN_STOCK.name,
        OrderStatus.CUTTING.name,
        OrderStatus.CUT_DONE.name,
        OrderStatus.SEWING.name,
        OrderStatus.REVIEW.name,
    )

    /** یک سفارش در صف. [lateDays] مثبت یعنی بعد از مهلتش آماده می‌شود. */
    data class Line(
        val order: Order,
        /** دستی که هنوز دوخته نشده. */
        val remaining: Int,
        /** زمانِ پیش‌بینی‌شدهٔ تمام شدنِ دوختش. */
        val readyAt: Long,
        val lateDays: Int,
    ) {
        val hasDue: Boolean get() = order.dueDate > 0L
    }

    /** کارِ زیرِ دستِ یک خیاط، با سرعتِ خودش. */
    data class TailorLoad(
        val tailor: String,
        /** دستی که گرفته و هنوز تحویل نداده. */
        val inHand: Int,
        /** دستِ تمام‌شده در روز، از پنجرهٔ سنجش. */
        val perDay: Double,
    ) {
        /** چند روز کار زیرِ دست دارد؛ `null` یعنی سرعتش هنوز معلوم نیست. */
        val daysOfWork: Double? get() = if (perDay > 0.0) inHand / perDay else null
    }

    /** اثرِ پذیرفتنِ یک سفارشِ تازه. */
    data class Impact(
        /** سفارشِ تازه کِی آماده می‌شود. */
        val readyAt: Long,
        /** چند روز بعد از مهلتی که برایش گذاشته شده؛ ۰ یعنی به‌موقع. */
        val lateDays: Int,
        /** سفارش‌هایی که تا حالا به‌موقع بودند و با این یکی دیر می‌شوند. */
        val pushedLate: List<Line>,
    )

    data class Result(
        val perDay: Double,
        val lines: List<Line>,
        val tailors: List<TailorLoad>,
        val now: Long,
    ) {
        val hasData: Boolean get() = perDay > 0.0

        /**
         * سفارش‌هایی که هنوز مهلتشان نگذشته ولی با این سرعت دیر می‌شوند.
         *
         * آن‌هایی که مهلتشان **گذشته** اینجا نیستند: هشدارِ خودشان را
         * دارند و «دیر می‌شود» درباره‌شان خبرِ تازه‌ای نیست.
         */
        val atRisk: List<Line>
            get() = if (!hasData) emptyList()
            else lines.filter { it.hasDue && it.order.dueDate >= now && it.lateDays > 0 }
                .sortedByDescending { it.lateDays }

        /** جمعِ دستِ باز در صف. */
        val queuePieces: Int get() = lines.sumOf { it.remaining }

        /**
         * اگر سفارشی با [qty] دست و مهلتِ [dueDate] (۰ = بی مهلت) پذیرفته
         * شود، کِی آماده می‌شود و کدام سفارش‌ها را عقب می‌اندازد.
         *
         * سفارشِ تازه هم به ترتیبِ مهلت در صف می‌نشیند — همان قاعدهٔ
         * بقیه. بی مهلت یعنی پشتِ همه. `null` یعنی سرعت معلوم نیست.
         */
        fun impactOf(qty: Int, dueDate: Long): Impact? {
            if (!hasData || qty <= 0) return null
            val probe = Entry(key = PROBE, remaining = qty, dueDate = dueDate, createdAt = Long.MAX_VALUE)
            val before = lines.associate { it.order.id.toString() to it.lateDays }
            val entries = lines.map { Entry(it.order.id.toString(), it.remaining, it.order.dueDate, it.order.createdAt) }
            val timed = schedule(entries + probe, perDay, now)
            val mine = timed.first { it.first.key == PROBE }
            val byKey = lines.associateBy { it.order.id.toString() }
            val pushed = timed
                .filter { (e, _) -> e.key != PROBE && e.dueDate > 0L && e.dueDate >= now }
                .mapNotNull { (e, readyAt) ->
                    val late = lateDays(readyAt, e.dueDate)
                    val line = byKey[e.key] ?: return@mapNotNull null
                    if (late > 0 && (before[e.key] ?: 0) <= 0) line.copy(readyAt = readyAt, lateDays = late)
                    else null
                }
            return Impact(
                readyAt = mine.second,
                lateDays = if (dueDate > 0L) lateDays(mine.second, dueDate) else 0,
                pushedLate = pushed,
            )
        }

        /**
         * زودترین مهلتی (به روز از امروز) که می‌شود برای سفارشی با [qty] دست
         * قول داد — بی آنکه خودش دیر شود یا سفارشِ دیگری را دیر کند.
         *
         * **چرا جست‌وجو و نه یک تقسیم.** مهلتِ دورتر سفارش را در صف عقب‌تر
         * می‌برد، پشتِ کارهایی که مهلتِ زودتری دارند؛ پس «روزِ آماده شدن»
         * خودش به مهلت بسته است. اگر فقط `کارِ جلویی ÷ سرعت` گفته می‌شد و
         * کارفرما همان را می‌نوشت، سفارش پشتِ کارهای دیگر می‌رفت و باز دیر
         * می‌شد. روز به روز جلو می‌رود تا اولین روزی که واقعاً امن است.
         *
         * `null` یعنی سرعت معلوم نیست، یا تا [maxDays] روز جایی نیست.
         */
        fun earliestSafeDays(qty: Int, maxDays: Int = 180): Int? {
            if (!hasData || qty <= 0) return null
            for (d in 1..maxDays) {
                val hit = impactOf(qty, now + d * DAY) ?: return null
                if (hit.lateDays == 0 && hit.pushedLate.isEmpty()) return d
            }
            return null
        }
    }

    private const val PROBE = "\u0000new"

    private data class Entry(val key: String, val remaining: Int, val dueDate: Long, val createdAt: Long)

    /**
     * صف به ترتیبِ مهلت؛ بی‌مهلت‌ها پشتِ همه، به ترتیبِ ثبت. هر کدام وقتی
     * تمام می‌شود که همهٔ جلویی‌ها و خودش دوخته شده باشند.
     */
    private fun schedule(entries: List<Entry>, perDay: Double, now: Long): List<Pair<Entry, Long>> {
        val ordered = entries.sortedWith(
            compareBy<Entry> { if (it.dueDate > 0L) 0 else 1 }
                .thenBy { if (it.dueDate > 0L) it.dueDate else it.createdAt }
                .thenBy { it.createdAt }
                .thenBy { it.key }
        )
        var cum = 0
        return ordered.map { e ->
            cum += e.remaining
            e to (now + (cum / perDay * DAY).toLong())
        }
    }

    private fun lateDays(readyAt: Long, dueDate: Long): Int =
        if (dueDate <= 0L || readyAt <= dueDate) 0
        else ceil((readyAt - dueDate).toDouble() / DAY).toInt()

    /**
     * @param orders همهٔ سفارش‌ها؛ خودش بازها را جدا می‌کند.
     * @param sewn همهٔ تحویل‌های دوخت.
     */
    fun of(
        orders: List<Order>,
        sewn: List<SewingAssignment>,
        now: Long,
        windowDays: Int = WorkshopLoad.WINDOW_DAYS,
    ): Result {
        val since = now - windowDays * DAY
        val recent = sewn.filter {
            it.status == "DONE" && (it.doneAt ?: 0L) in since..now
        }
        val perDay = if (windowDays > 0) recent.sumOf { it.qty }.toDouble() / windowDays else 0.0

        val sewnByOrder = sewn.filter { it.status == "DONE" }
            .groupBy { it.orderId }
            .mapValues { (_, rows) -> rows.sumOf { it.qty } }

        val open = orders
            .filter { it.status in openStatuses }
            .map { o ->
                val done = maxOf(o.storedQty, sewnByOrder[o.id.toString()] ?: 0)
                o to (o.qty - done).coerceAtLeast(0)
            }
            .filter { (_, rem) -> rem > 0 }

        val lines = if (perDay <= 0.0) {
            open.map { (o, rem) -> Line(o, rem, 0L, 0) }
        } else {
            val byKey = open.associate { (o, _) -> o.id.toString() to o }
            schedule(
                open.map { (o, rem) -> Entry(o.id.toString(), rem, o.dueDate, o.createdAt) },
                perDay, now
            ).map { (e, readyAt) ->
                Line(byKey.getValue(e.key), e.remaining, readyAt, lateDays(readyAt, e.dueDate))
            }
        }

        val tailors = (recent.map { it.tailorLabel } +
            sewn.filter { it.status != "DONE" }.map { it.tailorLabel })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { t ->
                TailorLoad(
                    tailor = t,
                    inHand = sewn.filter { it.status != "DONE" && it.tailorLabel.trim() == t }.sumOf { it.qty },
                    perDay = if (windowDays > 0)
                        recent.filter { it.tailorLabel.trim() == t }.sumOf { it.qty }.toDouble() / windowDays
                    else 0.0,
                )
            }
            // کم‌کارترین بالا — «کارِ تازه را به کی بدهم؟».
            .sortedWith(compareBy<TailorLoad> { it.daysOfWork ?: Double.MAX_VALUE }.thenBy { it.tailor })

        return Result(perDay = perDay, lines = lines, tailors = tailors, now = now)
    }
}
