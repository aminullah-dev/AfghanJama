package com.afghanjama.data

import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.data.entities.LanDevice
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.work.DeliveryForecast

/**
 * یک عدد روی داشبورد: یا **معلوم** است، یا **نامعلوم با دلیل**.
 *
 * [Unknown.reason] فقط «چرا» است؛ واژهٔ «نامعلوم» را خودِ کارت کنارش می‌گذارد.
 *
 * **چرا نه `0`.** کارگاهِ تازه که هنوز تحویلی ثبت نکرده، «سرعتِ دوخت» ندارد.
 * اگر کارت «۰ دست در روز» بنویسد، کارفرما فکر می‌کند کار خوابیده؛ اگر بنویسد
 * «نامعلوم — هنوز تحویلی ثبت نشده»، می‌داند چه چیزی کم است. `Figure`
 * این تفاوت را در نوع نگه می‌دارد تا هیچ کارتی نتواند بی‌صدا صفر بگذارد.
 */
sealed interface Figure<out T> {
    data class Known<T>(val value: T) : Figure<T>
    data class Unknown(val reason: String) : Figure<Nothing>

    val known: T? get() = (this as? Known<T>)?.value
}

/**
 * از کجا آمده؟ — زیرِ هر کارت، و در توضیحِ «از کجا آمده؟».
 *
 * [label] نامِ کوتاهِ منبع است، [formula] جملهٔ دریِ حساب. یک فهرست، تا دو
 * کارت برای یک منبع دو نام ننویسند.
 */
enum class DigestSource(val label: String, val formula: String) {
    ALERTS("مرکزِ هشدار", "همان هشدارهای «مرکزِ هشدار»، فوری‌ترین اول."),
    REQUESTS("گوشی‌های کارگاه", "درخواست‌هایی که از گوشیِ کارگران رسیده و هنوز تأیید یا رد نشده."),
    ORDERS("دفترِ سفارش‌ها", "سفارش‌های باز با مهلتِ امروز، مهلتِ گذشته، و سفارش‌هایی که امروز ثبت شده."),
    DELIVERY("انبارِ سفارش‌ها", "سفارش‌های ساخته‌شده‌ای که هنوز به مشتری داده نشده."),
    ATTENDANCE("حضور و غیاب", "کسانی که «ورود» زده‌اند و هنوز «خروج» نزده‌اند."),
    PIPELINE("خطِ تولید", "سفارش‌های باز در هر مرحله؛ «معطل» یعنی ۴ روز یا بیشتر در یک مرحله."),
    FORECAST("پیش‌بینیِ تحویل", "کارهای باز به ترتیبِ مهلت، با سرعتِ دوختِ ۳۰ روزِ اخیرِ همین کارگاه."),
    MONEY("دفترِ حساب", "ماندهٔ صندوق، بانک و صندوقِ فایده از دفترِ روزنامه."),
    HEALTH("وضعِ مالی", "نقد تقسیم بر هزینهٔ ۳۰ روزِ اخیر؛ فروشِ ۳۰ روز در برابرِ ۳۰ روزِ پیش از آن."),
    SALES("دفترِ فروش", "فروشِ خالصِ ۳۰ روزِ اخیر، منهای برگشتی‌ها."),
    DEBTS("پیگیریِ طلب", "مشتری‌هایی که امروز نوبتِ پیگیری‌شان است: قولِ شکسته، قسطِ گذشته یا طلبِ کهنه."),
    WAGES("کارمزدِ خیاط‌ها", "کارمزدهای ثبت‌شده‌ای که هنوز تسویه نشده."),
    MATERIALS("انبارِ مواد", "مصرفِ ۳۰ روزِ اخیرِ هر قلم؛ «رو به اتمام» یعنی ۷ روز یا کمتر مانده، یا زیرِ حدِ هشدار."),
    PRODUCTS("انبارِ محصول", "فروشِ ۳۰ روزِ هر کالا در برابرِ موجودی؛ «راکد» یعنی مدتی فروش نرفته."),
    PRICES("قیمت‌دهی", "میانهٔ قیمتِ ۱۰ فروشِ آخرِ هر کالا در ۱۸۰ روز، در برابرِ بهای تمام‌شدهٔ امروز."),
    DEVICES("کارگاهِ وصل", "گوشی‌های جفت‌شده با این کامپیوتر؛ «آنلاین» یعنی در ۴۵ ثانیهٔ اخیر دیده شده."),
}

/**
 * چکیدهٔ کلِ کارگاه برای داشبوردِ کامپیوتر (و برای گوشیِ صاحب‌کار).
 *
 * **تابعِ خالص.** همهٔ ورودی‌ها از جریان‌هایی می‌آیند که همین حالا صفحه‌ها
 * و هشدارها می‌خوانند (`hubDigestFlow`)؛ اینجا فقط چیده و دسته می‌شوند. هیچ
 * عددی ساخته نمی‌شود که منبعی نداشته باشد، و هر جا منبع چیزی ندارد،
 * [Figure.Unknown] با دلیل.
 *
 * زمان از بیرون می‌آید ([build] `now`)؛ آزمون ساعتِ خودش را می‌دهد.
 */
object HubDigest {

    private const val DAY = 86_400_000L

    /** هر ستون حدِ پنج سطر؛ بقیه «همه» در صفحهٔ خودش. */
    const val TOP = 5

    /** چند ساعت در کارگاه، «زیادی» است (کهربایی). همان آستانهٔ خانهٔ گوشی. */
    const val LONG_SHIFT_HOURS = 8

    /** کارمزدِ باز از این چند روز کهنه‌تر یعنی یادآورِ تسویه. */
    const val WAGE_OLD_DAYS = 7L

    /** «آنلاین»: در این چند میلی‌ثانیه دیده شده. */
    const val ONLINE_MS = 45_000L

    private val OPEN = setOf(
        OrderStatus.IN_STOCK.name, OrderStatus.CUTTING.name, OrderStatus.CUT_DONE.name,
        OrderStatus.SEWING.name, OrderStatus.REVIEW.name,
    )

    // ─────────────────────────────── ورودی

    /** یک هشدار، بی آیکن — آیکن مالِ صفحه است. */
    data class AlertRow(
        val id: String,
        val urgent: Boolean,
        val warn: Boolean,
        val title: String,
        val detail: String,
        val route: String?,
    )

    /** یک قلمِ مواد از محاسبهٔ مشترکِ پیشنهادِ خرید (`buildReorderRows`). */
    data class Material(
        val name: String,
        val unit: String,
        val stock: Double,
        val minLevel: Double,
        /** `null` یعنی در ۳۰ روزِ اخیر مصرفی ثبت نشده. */
        val daysLeft: Int?,
        val runsOutSoon: Boolean,
    )

    /** پول، از همان جریانی که خانه می‌خوانَد. */
    data class Money(val wallet: Long, val bank: Long, val profit: Long)

    /**
     * همه‌چیزِ لازم. `null` یعنی آن منبع هنوز چیزی نفرستاده — نه «خالی».
     * داشبورد از همان لحظهٔ اول رسم می‌شود و هر کارت تا رسیدنِ منبعش
     * «در حالِ خواندن» می‌گوید.
     */
    data class Inputs(
        val alerts: List<AlertRow>? = null,
        val requests: List<SyncRequest>? = null,
        val orders: List<Order>? = null,
        val inside: List<AttendanceRecord>? = null,
        val money: Money? = null,
        val health: FinancialHealth.Snapshot? = null,
        val sales30: Long? = null,
        val openWagesTotal: Long? = null,
        val openWagesCount: Int? = null,
        val oldestWageAt: Long? = null,
        val debts: DebtFollowUp.Plan? = null,
        val forecast: DeliveryForecast.Result? = null,
        val materials: List<Material>? = null,
        val products: ProductInsights.Summary? = null,
        val priceBook: PriceAdvisor.Book? = null,
        val finishedStock: List<FinishedStock>? = null,
        val devices: List<LanDevice>? = null,
        /** `null` روی دستگاهی که کارگاهِ وصل ندارد (آیفون). */
        val hubServing: Boolean? = null,
        /** نسخهٔ همین برنامه — گوشیِ عقب‌تر «کهنه» است. */
        val appVersion: String = "",
    )

    // ─────────────────────────────── خروجی

    data class Alerts(val top: List<AlertRow>, val total: Int, val urgent: Int, val warn: Int)

    data class OrderRow(val id: String, val code: String, val title: String, val customer: String, val due: Long)

    data class Today(
        val dueToday: List<OrderRow>,
        val late: List<OrderRow>,
        val createdToday: Int,
    )

    data class Ready(val count: Int, val oldestDays: Int?)

    data class Inside(val name: String, val since: Long, val hours: Int) {
        val long: Boolean get() = hours >= LONG_SHIFT_HOURS
    }

    /** شمارِ سفارش‌های **باز** در هر کدام از پنج قدم ([OrderPipeline.STAGES]). */
    data class Pipeline(val counts: List<Int>, val stuck: Int) {
        val total: Int get() = counts.sum()
    }

    data class Risk(val code: String, val customer: String, val readyAt: Long, val lateDays: Int)

    data class Tailor(val name: String, val inHand: Int, val perDay: Double, val days: Figure<Double>)

    data class Health(
        val level: FinancialHealth.Level,
        val cover: Figure<Int>,
        /** [FinancialHealth.MAX_COVER_DAYS] یعنی «بیش از ۹۹۹ روز»، نه عددِ دقیق. */
        val coverCapped: Boolean,
        val signals: List<FinancialHealth.Signal>,
    )

    data class Sales(val last30: Long, val changePct: Figure<Int>)

    data class Debt(val name: String, val phone: String, val amount: Long, val reason: String, val contactedToday: Boolean)

    data class Wages(val total: Long, val count: Int, val oldestDays: Long?) {
        val old: Boolean get() = (oldestDays ?: 0) >= WAGE_OLD_DAYS
    }

    data class MaterialRow(val name: String, val unit: String, val stock: Double, val days: Figure<Int>)

    data class ProductRow(val name: String, val size: String, val qty: Int, val cover: Figure<Int>, val make: Int)

    data class Products(val restock: List<ProductRow>, val dead: List<ProductRow>, val deadValue: Long)

    data class BelowCost(val name: String, val size: String, val usualPrice: Long, val cost: Long, val samples: Int)

    data class Device(
        val name: String,
        val role: String,
        val person: String,
        val online: Boolean,
        val lastSeen: Long?,
        val version: String,
        val outdated: Boolean,
    )

    data class Devices(val serving: Boolean?, val list: List<Device>) {
        val online: Int get() = list.count { it.online }
    }

    /** کلِ چکیده. هر بخش یا معلوم است یا نامعلوم با دلیل. */
    data class Digest(
        val at: Long,
        val alerts: Figure<Alerts>,
        val requests: Figure<List<SyncRequest>>,
        val today: Figure<Today>,
        val ready: Figure<Ready>,
        val inside: Figure<List<Inside>>,
        val pipeline: Figure<Pipeline>,
        val atRisk: Figure<List<Risk>>,
        val tailors: Figure<List<Tailor>>,
        val money: Figure<Money>,
        val health: Figure<Health>,
        val sales: Figure<Sales>,
        val debts: Figure<List<Debt>>,
        val debtsTotal: Figure<Long>,
        val wages: Figure<Wages>,
        val materials: Figure<List<MaterialRow>>,
        val products: Figure<Products>,
        val belowCost: Figure<List<BelowCost>>,
        val devices: Figure<Devices>,
    )

    /** پیش از رسیدنِ هر عدد — همه «در حالِ خواندن»، هیچ صفری. */
    fun loading(now: Long): Digest = build(Inputs(), now, dayStart = now)

    const val READING = "در حالِ خواندنِ دفتر…"

    /**
     * @param dayStart نیمه‌شبِ امروز به وقتِ محلی — از بیرون، تا آزمون به
     * منطقهٔ زمانی بسته نباشد (همان قاعدهٔ `DebtFollowUp.plan`).
     */
    fun build(i: Inputs, now: Long, dayStart: Long): Digest {
        val dayEnd = dayStart + DAY
        return Digest(
            at = now,
            alerts = i.alerts.fig { a ->
                val sorted = a.sortedWith(compareBy({ !it.urgent }, { !it.warn }))
                Alerts(
                    top = sorted.filter { it.urgent || it.warn }.take(TOP),
                    total = a.size,
                    urgent = a.count { it.urgent },
                    warn = a.count { it.warn && !it.urgent },
                )
            },
            requests = i.requests.fig { r -> r.filter { it.status == "PENDING" }.sortedBy { it.createdAt } },
            today = i.orders.fig { TodayOrders.select(it, dayStart, dayEnd) },
            ready = i.orders.fig { orders ->
                val waiting = orders.filter { it.status == OrderStatus.STORED.name && it.customerName.isNotBlank() }
                Ready(
                    count = waiting.size,
                    oldestDays = waiting.minOfOrNull { since(it) }?.let { ((now - it) / DAY).toInt().coerceAtLeast(0) },
                )
            },
            inside = i.inside.fig { list ->
                list.filter { it.checkOut == null }
                    .sortedBy { it.checkIn }
                    .map { Inside(it.employee, it.checkIn, ((now - it.checkIn) / 3_600_000L).toInt().coerceAtLeast(0)) }
            },
            pipeline = i.orders.fig { orders ->
                Pipeline(
                    counts = OrderPipeline.counts(orders.map { it.status }),
                    stuck = orders.count { it.status in OPEN && (now - since(it)) / DAY >= 4 },
                )
            },
            atRisk = when (val f = i.forecast) {
                null -> Figure.Unknown(READING)
                else -> if (!f.hasData) Figure.Unknown(NO_SPEED)
                else Figure.Known(
                    f.atRisk.take(TOP).map { Risk(it.order.orderCode, it.order.customerName, it.readyAt, it.lateDays) }
                )
            },
            tailors = i.forecast.fig { f ->
                f.tailors.filter { it.inHand > 0 }.sortedByDescending { it.inHand }.take(TOP).map { t ->
                    Tailor(
                        t.tailor, t.inHand, t.perDay,
                        t.daysOfWork?.let { Figure.Known(it) } ?: Figure.Unknown("این خیاط در ۳۰ روزِ اخیر دوختی تحویل نداده"),
                    )
                }
            },
            money = i.money.fig { it },
            health = when (val h = i.health) {
                null -> Figure.Unknown(READING)
                else -> if (!h.hasData) Figure.Unknown("هنوز داده‌ای برای این حساب نیست")
                else Figure.Known(
                    Health(
                        level = h.level,
                        cover = h.coverDays?.let { Figure.Known(it) } ?: Figure.Unknown(NO_EXPENSE),
                        coverCapped = (h.coverDays ?: 0) >= FinancialHealth.MAX_COVER_DAYS,
                        signals = h.signals,
                    )
                )
            },
            sales = if (i.sales30 == null || i.health == null) Figure.Unknown(READING)
            else Figure.Known(
                Sales(
                    last30 = i.sales30,
                    changePct = i.health.salesChangePct?.let { Figure.Known(it) } ?: Figure.Unknown(NO_PREV_SALES),
                )
            ),
            debts = i.debts.fig { p ->
                p.today.take(TOP).map {
                    Debt(it.debtor.name, it.debtor.phone, it.debtor.amount, it.reason.label, it.contactedToday)
                }
            },
            debtsTotal = i.debts.fig { it.todayTotal },
            wages = if (i.openWagesTotal == null || i.openWagesCount == null) Figure.Unknown(READING)
            else Figure.Known(
                Wages(
                    total = i.openWagesTotal,
                    count = i.openWagesCount,
                    oldestDays = i.oldestWageAt?.let { ((now - it) / DAY).coerceAtLeast(0) },
                )
            ),
            materials = i.materials.fig { rows -> materialRows(rows) },
            products = i.products.fig { s ->
                fun row(r: ProductInsights.Row) = ProductRow(
                    r.name, r.size, r.qty,
                    r.coverDays?.let { Figure.Known(it) } ?: Figure.Unknown("در ۳۰ روزِ اخیر فروشی نبوده"),
                    r.suggestedMake,
                )
                Products(s.restock.take(TOP).map(::row), s.dead.take(TOP).map(::row), s.deadValue)
            },
            belowCost = if (i.priceBook == null || i.finishedStock == null) Figure.Unknown(READING)
            else Figure.Known(belowCost(i.priceBook, i.finishedStock).take(TOP)),
            devices = if (i.devices == null) Figure.Unknown(READING)
            else Figure.Known(
                Devices(
                    serving = i.hubServing,
                    list = i.devices.filter { it.active }
                        .sortedByDescending { it.lastSeenAt ?: 0L }
                        .map { d ->
                            Device(
                                name = d.name,
                                role = roleLabel(d.role),
                                person = d.personLabel,
                                online = (d.lastSeenAt ?: 0L) > now - ONLINE_MS,
                                lastSeen = d.lastSeenAt,
                                version = d.appVersion,
                                outdated = d.appVersion.isNotBlank() && i.appVersion.isNotBlank() &&
                                    com.afghanjama.AppVersion.isBehind(d.appVersion, i.appVersion),
                            )
                        }
                )
            ),
        )
    }

    const val NO_SPEED = "در ۳۰ روزِ اخیر تحویلی ثبت نشده تا سرعتِ دوخت معلوم شود"
    const val NO_EXPENSE = "هزینهٔ ۳۰ روزِ اخیر صفر است؛ روزهای پوشش حساب‌شدنی نیست"
    const val NO_PREV_SALES = "۳۰ روزِ پیش از آن فروشی نبوده تا مقایسه شود"
    const val NO_USE = "در ۳۰ روزِ اخیر مصرفی ثبت نشده"

    /**
     * مواد: آنچه با مصرفِ واقعی ۷ روز یا کمتر می‌کشد، **و** آنچه زیرِ حدِ
     * هشدار است ولی مصرفی ثبت نکرده (روزش نامعلوم). تا ۱.۱۱ دومی از این
     * فهرست می‌افتاد چون `runsOutSoon` روزِ معلوم می‌خواهد، در حالی که
     * شمارِ «کمبود»ِ خانه همان را می‌شمرد.
     */
    fun materialRows(rows: List<Material>): List<MaterialRow> {
        val soon = rows.filter { it.runsOutSoon }
        val low = rows.filter { !it.runsOutSoon && it.minLevel > 0.0 && it.stock <= it.minLevel }
        return (soon.sortedBy { it.daysLeft ?: Int.MAX_VALUE } + low).map {
            MaterialRow(it.name, it.unit, it.stock, it.daysLeft?.let { d -> Figure.Known(d) } ?: Figure.Unknown(NO_USE))
        }
    }

    /**
     * کالاهایی که قیمتِ معمولشان به بهای تمام‌شدهٔ امروز نمی‌رسد.
     *
     * فقط با دستِ‌کم [PriceAdvisor.MIN_SAMPLES] فروش در پنجره — زیرِ آن
     * «معمول» یک یا دو فروش است و حدسی است که فهرست نمی‌شود. بهای هر عدد
     * از ارزشِ کلِ ردیفِ انبار تقسیم بر عدد، یا اگر موجودی صفر است از
     * `avgCost`. کالای بی‌بها فهرست نمی‌شود.
     */
    fun belowCost(book: PriceAdvisor.Book, stock: List<FinishedStock>): List<BelowCost> =
        stock.mapNotNull { s ->
            val cost = when {
                s.qty > 0 && s.totalValue > 0 -> s.totalValue / s.qty
                s.avgCost > 0 -> s.avgCost
                else -> return@mapNotNull null
            }
            val adv = book.advise(PriceAdvisor.key(s.name, s.size), cost)
            val usual = adv.usualPrice ?: return@mapNotNull null
            if (adv.samples < PriceAdvisor.MIN_SAMPLES || !adv.usualBelowCost) return@mapNotNull null
            BelowCost(s.name, s.size, usual, cost, adv.samples)
        }.sortedByDescending { it.cost - it.usualPrice }

    private fun roleLabel(wire: String): String = when (wire) {
        "owner" -> "گوشیِ صاحب‌کار"
        "worker" -> "گوشیِ کارگر"
        "wall" -> "تابلوی دیوار"
        else -> "دستگاه"
    }

    private fun since(o: Order): Long = if (o.stageChangedAt > 0) o.stageChangedAt else o.createdAt

    private inline fun <T, R> T?.fig(f: (T) -> R): Figure<R> =
        if (this == null) Figure.Unknown(READING) else Figure.Known(f(this))
}

/**
 * سفارش‌های امروز — سه دسته، با همان قاعدهٔ «باز» که پیش‌بینیِ تحویل دارد.
 *
 * - **سررسیدِ امروز:** مهلتش امروز است و هنوز در کارگاه است.
 * - **عقب‌افتاده:** مهلتش پیش از امروز بوده و هنوز در کارگاه است. سفارشِ
 *   بی‌مهلت (`dueDate = 0`) هرگز عقب نیست، و سفارشِ ساخته‌شده‌ای که منتظرِ
 *   مشتری است (`STORED`) هم نه — کارِ کارگاه تمام است.
 * - **ثبتِ امروز:** هر سفارشی که امروز ساخته شد، در هر مرحله.
 */
object TodayOrders {
    private val OPEN = setOf(
        OrderStatus.IN_STOCK.name, OrderStatus.CUTTING.name, OrderStatus.CUT_DONE.name,
        OrderStatus.SEWING.name, OrderStatus.REVIEW.name,
    )

    fun select(orders: List<Order>, dayStart: Long, dayEnd: Long): HubDigest.Today {
        fun row(o: Order) = HubDigest.OrderRow(o.id.toString(), o.orderCode, o.designTitle, o.customerName, o.dueDate)
        val open = orders.filter { it.status in OPEN && it.dueDate > 0L }
        return HubDigest.Today(
            dueToday = open.filter { it.dueDate in dayStart until dayEnd }.sortedBy { it.dueDate }.map(::row),
            late = open.filter { it.dueDate < dayStart }.sortedBy { it.dueDate }.map(::row),
            createdToday = orders.count { it.createdAt in dayStart until dayEnd },
        )
    }
}
