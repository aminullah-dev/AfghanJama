package com.afghanjama.data

/**
 * انبارِ محصول: «چه چیزی را دوباره بدوزیم؟» و «پولِ کجا خوابیده؟»
 *
 * **مسئله‌ای که حل می‌کند.** انبارِ مواد از قبل پیش‌بینی داشت
 * ([StockForecast]) ولی انبارِ محصول فقط عدد نشان می‌داد. دو اشتباهِ
 * پرهزینهٔ کارگاه همین‌جاست و هر دو دیده نمی‌شدند:
 *
 * - طرحِ پرفروشی که تمام می‌شود و مشتری دست‌خالی برمی‌گردد — چون کسی
 *   نمی‌دانست هفته‌ای چند تا از آن می‌رود؛
 * - طرحی که دو ماه است یک عدد هم نفروخته و پولِ پارچه و دوختش در انبار
 *   خوابیده — چون از بیرون با بقیه فرقی ندارد.
 *
 * هر دو از دفترِ فروش درمی‌آیند که از اول ثبت می‌شده؛ اینجا فقط خوانده
 * می‌شود. بی دیتابیس و بی Compose، تا آزمون مستقیم بسنجدش.
 */
object ProductInsights {

    /** پنجرهٔ نگاه به فروش. */
    const val WINDOW_DAYS = 30

    /** موجودی‌ای که کمتر از این تعداد روز فروش را بدهد، «دوباره بدوز». */
    const val RESTOCK_DAYS = 14

    /** کالایی که این مدت نه فروش رفته نه تکان خورده، «راکد» است. */
    const val DEAD_DAYS = 60

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** یک ردیفِ انبارِ محصول. [qty] منفی یعنی کسری (مشتری منتظر است). */
    data class Stock(
        val name: String,
        val size: String,
        val qty: Int,
        val value: Long,
        val updatedAt: Long,
    )

    /** یک فروش — [qty] خالص است (منهای برگشتی). */
    data class Sale(val name: String, val size: String, val qty: Int, val at: Long)

    data class Row(
        val name: String,
        val size: String,
        val qty: Int,
        val value: Long,
        /** عددِ فروش‌رفته در [WINDOW_DAYS] روزِ اخیر. */
        val sold30: Int,
        /** موجودی، فروشِ چند روز را می‌دهد؛ `null` وقتی فروشی نبوده. */
        val coverDays: Int?,
        val lastSaleAt: Long?,
        /** چند عدد بدوزیم تا یک ماهِ دیگر فروش پوشش داده شود. */
        val suggestedMake: Int,
        val restock: Boolean,
        val dead: Boolean,
        /** چند روز است نه فروخته نه تکان خورده (فقط برای راکدها معنا دارد). */
        val idleDays: Int,
    )

    data class Summary(
        /** فوری‌ترین اول. */
        val restock: List<Row>,
        /** پرارزش‌ترین اول. */
        val dead: List<Row>,
    ) {
        val deadValue: Long get() = dead.sumOf { it.value }
        val isEmpty: Boolean get() = restock.isEmpty() && dead.isEmpty()
    }

    fun analyze(stock: List<Stock>, sales: List<Sale>, now: Long): Summary {
        val since = now - WINDOW_DAYS * DAY_MS
        val net = sales.filter { it.qty > 0 }
        val soldBy = net.filter { it.at >= since }
            .groupBy { key(it.name, it.size) }
            .mapValues { (_, l) -> l.sumOf { it.qty } }
        val lastBy = net.groupBy { key(it.name, it.size) }
            .mapValues { (_, l) -> l.maxOf { it.at } }

        // کالای پرفروشی که ردیفش دیگر در انبار نیست هم باید دیده شود —
        // تمام شدنِ کامل بدترین حالتِ همین هشدار است.
        val stockKeys = stock.map { key(it.name, it.size) }.toSet()
        val soldOut = soldBy.keys.filter { it !in stockKeys }.map { k ->
            Stock(k.first, k.second, 0, 0, lastBy[k] ?: now)
        }

        val rows = (stock + soldOut).map { s ->
            val k = key(s.name, s.size)
            val sold = soldBy[k] ?: 0
            val last = lastBy[k]
            val onHand = s.qty.coerceAtLeast(0)
            val cover = if (sold > 0) onHand * WINDOW_DAYS / sold else null
            // هدف: یک پنجرهٔ کامل. کسری (qty منفی) هم به آن اضافه می‌شود،
            // چون آن تعداد را مشتری از قبل خریده و منتظر است.
            val make = (sold - s.qty).coerceAtLeast(0)
            val restock = (s.qty < 0) || (sold > 0 && cover != null && cover < RESTOCK_DAYS && make > 0)

            val lastMove = maxOf(s.updatedAt, last ?: Long.MIN_VALUE)
            val idle = ((now - lastMove) / DAY_MS).toInt().coerceAtLeast(0)
            val dead = s.qty > 0 && sold == 0 && idle >= DEAD_DAYS

            Row(
                name = s.name.trim(),
                size = s.size.trim(),
                qty = s.qty,
                value = s.value,
                sold30 = sold,
                coverDays = cover,
                lastSaleAt = last,
                suggestedMake = if (restock) maxOf(make, -s.qty) else 0,
                restock = restock,
                dead = dead,
                idleDays = idle,
            )
        }

        return Summary(
            restock = rows.filter { it.restock }
                .sortedWith(compareBy<Row>({ it.coverDays ?: -1 }, { -it.sold30 })),
            dead = rows.filter { it.dead }.sortedByDescending { it.value },
        )
    }

    private fun key(name: String, size: String) = name.trim() to size.trim()
}
