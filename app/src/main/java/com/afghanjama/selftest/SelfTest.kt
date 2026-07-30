package com.afghanjama.selftest

/**
 * موتورِ خودآزمایی.
 *
 * عمداً هیچ وابستگیِ اندرویدی ندارد و هیچ‌جا چیزی نمی‌نویسد: یا ریاضیِ
 * خالص را می‌سنجد، یا دادهٔ خوانده‌شده را وارسی می‌کند. برای همین هم
 * بی‌خطر است که مالک روی گوشیِ خودش و روی دادهٔ واقعیِ کارگاه اجرایش کند،
 * و هم هر وقت خواستید همین فایل مستقیم به تستِ JUnit تبدیل می‌شود.
 */

enum class CheckStatus { PASS, FAIL, SKIP }

data class CheckResult(
    val group: String,
    val name: String,
    val status: CheckStatus,
    /** وقتی رد شد: چه انتظار داشتیم و چه دیدیم. وقتی قبول شد: خلاصهٔ چیزی که سنجیده شد. */
    val detail: String
)

/** جمع‌کنندهٔ نتیجه‌ها — تا هر بررسی یک‌خطی نوشته شود. */
class CheckSink(private val group: String) {
    val results = mutableListOf<CheckResult>()

    fun pass(name: String, detail: String = "") {
        results += CheckResult(group, name, CheckStatus.PASS, detail)
    }

    fun fail(name: String, detail: String) {
        results += CheckResult(group, name, CheckStatus.FAIL, detail)
    }

    fun skip(name: String, detail: String) {
        results += CheckResult(group, name, CheckStatus.SKIP, detail)
    }

    /** ادعای برابری با پیامِ خودکار. */
    fun eq(name: String, expected: Any?, actual: Any?, note: String = "") {
        if (expected == actual) pass(name, if (note.isBlank()) "= $actual" else note)
        else fail(name, "انتظار: $expected — دیده شد: $actual" + if (note.isBlank()) "" else " ($note)")
    }

    fun isTrue(name: String, condition: Boolean, failDetail: String, passDetail: String = "") {
        if (condition) pass(name, passDetail) else fail(name, failDetail)
    }
}

// =====================================================================
// ۱) ریاضیِ پول: تفکیکِ بیعانه / نقد / نسیه در فروش
// =====================================================================

/**
 * بازتابِ دقیقِ تفکیکِ پول در `Repo.sellFinished`. اگر آن تابع عوض شود و
 * این‌جا به‌روز نشود، تست‌ها با هم اختلاف پیدا می‌کنند — که خودش هشدار است.
 */
data class MoneySplit(val prepay: Long, val cash: Long, val credit: Long)

fun splitSaleMoney(revenue: Long, receivedNow: Long, applyPrepay: Long): MoneySplit {
    val prepay = applyPrepay.coerceIn(0L, revenue)
    val cash = (if (receivedNow < 0) revenue - prepay else receivedNow)
        .coerceIn(0L, revenue - prepay)
    return MoneySplit(prepay, cash, revenue - prepay - cash)
}

fun checkMoneySplit(): List<CheckResult> {
    val s = CheckSink("تفکیک پولِ فروش")

    // هر سه بخش همیشه باید دقیقاً کلِ فروش را بسازند — این ادعای اصلی است.
    var worstGap = 0L
    var cases = 0
    for (revenue in listOf(1L, 7L, 100L, 9_999L, 10_000L, 123_457L)) {
        for (prepay in listOf(-5L, 0L, 1L, revenue / 3, revenue, revenue * 2)) {
            for (received in listOf(-1L, 0L, 1L, revenue / 2, revenue, revenue * 2)) {
                val r = splitSaleMoney(revenue, received, prepay)
                cases++
                val gap = revenue - (r.prepay + r.cash + r.credit)
                if (kotlin.math.abs(gap) > kotlin.math.abs(worstGap)) worstGap = gap
                if (r.prepay < 0 || r.cash < 0 || r.credit < 0) {
                    s.fail(
                        "هیچ بخشی منفی نمی‌شود",
                        "فروش=$revenue بیعانه=$prepay نقد=$received → $r"
                    )
                    return s.results
                }
            }
        }
    }
    s.eq("جمعِ سه بخش = کلِ فروش", 0L, worstGap, "$cases حالت آزموده شد")
    s.pass("هیچ بخشی منفی نمی‌شود", "$cases حالت")

    // رفتارِ سازگار با نسخهٔ قدیمی: receivedNow = -1 یعنی «همه نقد»
    s.eq(
        "نبودِ مبلغِ نقد یعنی همه نقد",
        MoneySplit(0, 10_000, 0),
        splitSaleMoney(10_000, -1, 0)
    )
    s.eq(
        "بیعانه از نقد جلو می‌افتد",
        MoneySplit(3_000, 7_000, 0),
        splitSaleMoney(10_000, -1, 3_000)
    )
    s.eq(
        "بیعانهٔ بزرگ‌تر از فروش سرریز نمی‌کند",
        MoneySplit(10_000, 0, 0),
        splitSaleMoney(10_000, -1, 25_000)
    )
    s.eq(
        "نقدِ بیش از باقی‌مانده پذیرفته نمی‌شود",
        MoneySplit(3_000, 7_000, 0),
        splitSaleMoney(10_000, 50_000, 3_000)
    )
    s.eq(
        "نسیهٔ کامل",
        MoneySplit(0, 0, 10_000),
        splitSaleMoney(10_000, 0, 0)
    )
    return s.results
}

// =====================================================================
// ۲) دفترِ مشتری در چرخهٔ کاملِ یک سفارش
// =====================================================================

/**
 * بازپخشِ دفترِ یک مشتری از ثبتِ سفارش تا تحویل، با همان سطرهایی که
 * Repo می‌نویسد. ماندهٔ نهایی باید دقیقاً «آنچه فروختیم منهای آنچه گرفتیم»
 * باشد. همین بررسی بود که اشکالِ دوباره‌حساب‌شدنِ بیعانه را پیدا کرد.
 */
fun replayCustomerLedger(
    agreedPrice: Long,
    qty: Int,
    deposit: Long,
    unitPriceAtDelivery: Long,
    cashAtDelivery: Long
): Long {
    var balance = 0L
    fun post(debit: Long = 0, credit: Long = 0) { balance += debit - credit }

    post(debit = agreedPrice)                       // SALE_BILLING هنگام ثبتِ سفارش
    if (deposit > 0) post(credit = deposit)         // CUSTOMER_ADVANCE
    post(credit = agreedPrice)                      // SALE_TO_STOCK هنگام تأییدِ نظارت

    val revenue = unitPriceAtDelivery * qty
    val split = splitSaleMoney(revenue, cashAtDelivery, deposit)
    // PREPAY_APPLIED عمداً خنثی است (بدهکار = بستانکار)
    if (split.prepay > 0) post(debit = split.prepay, credit = split.prepay)
    post(debit = revenue)                           // SALE_BILLING هنگام فروش
    if (split.cash > 0) post(credit = split.cash)   // CUSTOMER_SALE
    return balance
}

fun checkCustomerLedger(): List<CheckResult> {
    val s = CheckSink("دفترِ مشتری")

    data class Case(
        val label: String, val agreed: Long, val qty: Int,
        val deposit: Long, val unit: Long, val cash: Long
    )

    val cases = listOf(
        Case("بیعانه + بقیه نقد → تسویه", 10_000, 1, 3_000, 10_000, 7_000),
        Case("بیعانه + بقیه نسیه", 10_000, 1, 3_000, 10_000, 0),
        Case("بدون بیعانه، همه نقد", 10_000, 1, 0, 10_000, 10_000),
        Case("بدون بیعانه، همه نسیه", 10_000, 1, 0, 10_000, 0),
        Case("بیعانهٔ بیشتر از فروش", 10_000, 1, 12_000, 10_000, 0),
        Case("سه عدد، بیعانه + نقد", 30_000, 3, 5_000, 10_000, 25_000),
        Case("بیعانه + نقدِ جزئی", 10_000, 1, 3_000, 10_000, 4_000),
        Case("قیمت موقع تحویل بالا رفت", 10_000, 1, 3_000, 12_000, 8_000)
    )

    cases.forEach { c ->
        val balance = replayCustomerLedger(c.agreed, c.qty, c.deposit, c.unit, c.cash)
        val revenue = c.unit * c.qty
        val split = splitSaleMoney(revenue, c.cash, c.deposit)
        // حقیقت: آنچه فروختیم منهای آنچه واقعاً از او گرفتیم.
        // منفی یعنی بیش از سفارش پرداخته و ما به او بدهکاریم.
        val expected = revenue - split.cash - c.deposit
        s.eq(c.label, expected, balance, "مانده ${balance} ؋")
    }
    return s.results
}

// =====================================================================
// ۳) انتسابِ برگشتِ نظارت به خیاط
// =====================================================================

data class QcFact(val rejected: Boolean, val blamedTailor: String)

/** بازتابِ `verdictOf` در PerformanceViewModel. null = قابلِ انتساب نیست. */
fun verdictFor(tailors: List<String>, records: List<QcFact>): Pair<Set<String>, Set<String>>? {
    if (tailors.isEmpty() || records.isEmpty()) return null
    val rejects = records.filter { it.rejected }
    if (rejects.isEmpty()) return tailors.toSet() to emptySet()
    if (tailors.size == 1) return tailors.toSet() to tailors.toSet()
    if (rejects.any { it.blamedTailor.isBlank() }) return null
    val blamed = rejects.map { it.blamedTailor.trim() }.toSet()
    return (tailors.toSet() + blamed) to blamed
}

fun checkTailorAttribution(): List<CheckResult> {
    val s = CheckSink("انتسابِ برگشت به خیاط")
    val ok = QcFact(rejected = false, blamedTailor = "")
    fun bad(who: String = "") = QcFact(rejected = true, blamedTailor = who)

    s.eq("تک‌خیاطه، تأیید", setOf("احمد") to emptySet<String>(),
        verdictFor(listOf("احمد"), listOf(ok)))
    s.eq("تک‌خیاطه، برگشت", setOf("احمد") to setOf("احمد"),
        verdictFor(listOf("احمد"), listOf(bad())))
    s.eq("چندخیاطه و تأییدشده → برای همه یک کارِ سالم",
        setOf("احمد", "بلال") to emptySet<String>(),
        verdictFor(listOf("احمد", "بلال"), listOf(ok)))
    s.eq("چندخیاطه، برگشت با نام → فقط به نامِ او",
        setOf("احمد", "بلال") to setOf("بلال"),
        verdictFor(listOf("احمد", "بلال"), listOf(bad("بلال"))))
    s.isTrue("چندخیاطه، برگشت بی‌نام → کنار گذاشته می‌شود",
        verdictFor(listOf("احمد", "بلال"), listOf(bad())) == null,
        "انتظار داشتیم قابلِ انتساب نباشد")
    s.isTrue("اگر یکی از برگشت‌ها بی‌نام باشد، کلِ سفارش کنار می‌رود",
        verdictFor(listOf("احمد", "بلال"), listOf(bad("بلال"), bad())) == null,
        "انتظار داشتیم قابلِ انتساب نباشد")

    // بی‌گناه نباید برگشت بگیرد — ادعای اخلاقیِ اصلیِ این بخش
    val v = verdictFor(listOf("احمد", "بلال", "کریم"), listOf(bad("بلال")))
    s.isTrue("خیاطِ نام‌برده‌نشده برگشت نمی‌گیرد",
        v != null && "احمد" !in v.second && "کریم" !in v.second,
        "به کسی که نامش برده نشده برگشت خورد: ${v?.second}")
    return s.results
}

// =====================================================================
// ۴) تقویمِ جلالی
// =====================================================================

fun checkJalali(
    toJalali: (Long) -> Triple<Int, Int, Int>,
    atMillis: (Int, Int, Int) -> Long
): List<CheckResult> {
    val s = CheckSink("تقویم جلالی")
    var checked = 0
    var firstBad: String? = null

    // ۱۳۹۵ تا ۱۴۰۷ — شاملِ چند سالِ کبیسه
    for (year in 1395..1406) {
        for (month in 1..12) {
            val days = if (month <= 6) 31 else if (month <= 11) 30 else 29
            for (day in 1..days) {
                val millis = atMillis(year, month, day)
                val back = toJalali(millis)
                checked++
                if (back != Triple(year, month, day) && firstBad == null) {
                    firstBad = "$year/$month/$day → برگشت $back"
                }
            }
        }
    }
    if (firstBad == null) s.pass("تبدیل رفت‌وبرگشت دقیق است", "$checked روز آزموده شد")
    else s.fail("تبدیل رفت‌وبرگشت دقیق است", firstBad!!)
    return s.results
}

// =====================================================================
// ۵) فاکتور فروشِ چندردیفی
// =====================================================================

/** یک ردیفِ فاکتور برای آزمایش: شناسهٔ کالا، موجودی، تعداد و فی. */
data class LineFact(val itemId: Long, val stock: Int, val qty: Int, val unitPrice: Long)

/** بازتابِ سنجشِ موجودی در `Repo.sellInvoice`. */
fun invoiceHasEnoughStock(lines: List<LineFact>): Boolean {
    val valid = lines.filter { it.qty > 0 && it.unitPrice > 0 }
    if (valid.isEmpty()) return false
    val needed = valid.groupBy { it.itemId }.mapValues { (_, r) -> r.sumOf { it.qty } }
    return valid.distinctBy { it.itemId }.all { (needed[it.itemId] ?: 0) <= it.stock }
}

fun invoiceTotal(lines: List<LineFact>): Long =
    lines.filter { it.qty > 0 && it.unitPrice > 0 }.sumOf { it.qty * it.unitPrice }

fun checkMultiLineInvoice(): List<CheckResult> {
    val s = CheckSink("فاکتور چندردیفی")

    val threeDesigns = listOf(
        LineFact(1, stock = 10, qty = 2, unitPrice = 1_500),
        LineFact(2, stock = 5, qty = 1, unitPrice = 4_000),
        LineFact(3, stock = 8, qty = 3, unitPrice = 900)
    )
    s.eq("جمعِ فاکتورِ سه‌طرحه", 2 * 1_500L + 4_000L + 3 * 900L, invoiceTotal(threeDesigns))
    s.isTrue("سه طرحِ مختلف در یک فاکتور", invoiceHasEnoughStock(threeDesigns),
        "با وجودِ موجودیِ کافی رد شد")

    // ردیفِ ناقص نباید کلِ فاکتور را از کار بیندازد، فقط خودش کنار می‌رود
    val withBlank = threeDesigns + LineFact(4, stock = 3, qty = 0, unitPrice = 0)
    s.eq("ردیفِ ناقص در جمع نمی‌آید", invoiceTotal(threeDesigns), invoiceTotal(withBlank))
    s.isTrue("ردیفِ ناقص فاکتور را باطل نمی‌کند", invoiceHasEnoughStock(withBlank),
        "فاکتورِ درست به‌خاطرِ یک ردیفِ خالی رد شد")

    // مهم‌ترین حالت: دو ردیف از یک کالا با هم نباید از موجودی بیشتر شوند
    val sameItemTwice = listOf(
        LineFact(7, stock = 5, qty = 3, unitPrice = 1_000),
        LineFact(7, stock = 5, qty = 3, unitPrice = 1_000)
    )
    s.isTrue(
        "دو ردیف از یک کالا با هم سنجیده می‌شوند",
        !invoiceHasEnoughStock(sameItemTwice),
        "۶ عدد از موجودیِ ۵ عددی فروخته شد — موجودی منفی می‌شد"
    )
    val sameItemOk = listOf(
        LineFact(7, stock = 5, qty = 3, unitPrice = 1_000),
        LineFact(7, stock = 5, qty = 2, unitPrice = 1_200)
    )
    s.isTrue("دو ردیف از یک کالا تا سقفِ موجودی مجاز است",
        invoiceHasEnoughStock(sameItemOk), "۵ عدد از موجودیِ ۵ عددی رد شد")

    s.isTrue("فاکتورِ خالی ثبت نمی‌شود", !invoiceHasEnoughStock(emptyList()),
        "فاکتورِ بی‌ردیف پذیرفته شد")

    // پولِ فاکتورِ چندردیفی هم باید مثلِ تک‌ردیفی تفکیک شود
    val total = invoiceTotal(threeDesigns)
    val split = splitSaleMoney(total, receivedNow = 3_000, applyPrepay = 2_000)
    s.eq("تفکیکِ پول روی جمعِ کلِ فاکتور", total, split.prepay + split.cash + split.credit)
    return s.results
}

// =====================================================================
// ۶) ارزشِ انبارِ محصول — نباید ته‌مانده جا بگذارد
// =====================================================================

/** یک ردیفِ انبار در حالِ آزمایش: تعداد و ارزشِ کل. */
data class StockFact(var qty: Int, var value: Long)

/** بازتابِ `takeFromStock`: آخرین عددها هرچه مانده را با خود می‌برند. */
fun takeValue(row: StockFact, qty: Int): Long =
    if (qty >= row.qty) row.value else row.value * qty / row.qty

fun checkStockValuation(): List<CheckResult> {
    val s = CheckSink("ارزشِ انبار محصول")

    // ورود و خروجِ کاملِ یک دسته: حساب باید به صفر برگردد
    run {
        val row = StockFact(qty = 3, value = 10_000)   // ۱۰٬۰۰۰ برای ۳ عدد
        val c1 = takeValue(row, 1); row.qty -= 1; row.value -= c1
        val c2 = takeValue(row, 1); row.qty -= 1; row.value -= c2
        val c3 = takeValue(row, 1); row.qty -= 1; row.value -= c3
        s.eq("سه فروشِ تکی، جمعِ بها = کلِ ورودی", 10_000L, c1 + c2 + c3)
        s.eq("ارزشِ باقی‌مانده پس از خالی‌شدن", 0L, row.value)
    }

    // مبلغی که بر تعداد بخش‌پذیر نیست — همان حالتی که قبلاً افغانی گم می‌کرد
    run {
        val row = StockFact(qty = 7, value = 10_000)
        var taken = 0L
        repeat(7) { taken += takeValue(row, 1).also { c -> row.qty -= 1; row.value -= c } }
        s.eq("۱۰٬۰۰۰ روی ۷ عدد بدونِ گم‌شدن پخش می‌شود", 10_000L, taken)
        s.eq("ته‌مانده در انبار نمی‌ماند", 0L, row.value)
    }

    // چند دستهٔ متفاوت روی یک ردیف، بعد خالی‌کردنِ کامل
    run {
        val row = StockFact(qty = 0, value = 0)
        listOf(3 to 10_000L, 5 to 7_777L, 2 to 999L).forEach { (q, v) ->
            row.qty += q; row.value += v
        }
        val deposited = 10_000L + 7_777L + 999L
        var taken = 0L
        while (row.qty > 0) {
            val q = if (row.qty >= 3) 3 else row.qty
            val c = takeValue(row, q)
            taken += c; row.qty -= q; row.value -= c
        }
        s.eq("سه دستهٔ متفاوت، جمعِ بها = جمعِ ورودی‌ها", deposited, taken)
        s.eq("ارزشِ ردیف پس از خالی‌شدن", 0L, row.value)
    }

    // برداشتِ بیش از موجودی، هرچه در ردیف هست را می‌برد. از اینجا به بعد
    // ردیف وارد «کسری» می‌شود و ارزشش می‌تواند منفی شود — که درست است و
    // به‌طور کامل در بررسیِ «کسری انبار» سنجیده می‌شود، نه اینجا.
    run {
        val row = StockFact(qty = 2, value = 500)
        s.eq("برداشتِ بیش از موجودی همهٔ ارزشِ موجود را می‌برد", 500L, takeValue(row, 5))
    }
    return s.results
}

// =====================================================================
// ۷) زمان‌بندیِ یادآورِ نان و چای
// =====================================================================

/**
 * سنجشِ `BreakReminderWorker.delayUntilNext`. تابع خودش Calendar لازم
 * دارد پس از بیرون تزریق می‌شود، و اینجا فقط ادعاها بررسی می‌شوند.
 */
fun checkBreakSchedule(delayUntilNext: (Int, Int, Long) -> Long): List<CheckResult> {
    val s = CheckSink("یادآور نان و چای")
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour

    // یک زمانِ مبنا با ثانیهٔ غیرِ صفر، چون همان‌جاست که خطا پنهان می‌شود
    val now = System.currentTimeMillis() / minute * minute + 42_000L

    var allPositive = true
    var maxDelay = 0L
    for (h in 0..23) {
        for (m in listOf(0, 15, 30, 45, 59)) {
            val d = delayUntilNext(h, m, now)
            if (d <= 0L) allPositive = false
            if (d > maxDelay) maxDelay = d
        }
    }
    s.isTrue(
        "هیچ یادآوری فوراً یا در گذشته شلیک نمی‌کند",
        allPositive,
        "دستِ‌کم یکی از ۱۲۰ حالت فاصلهٔ صفر یا منفی داد"
    )
    s.isTrue(
        "هیچ فاصله‌ای بیشتر از یک شبانه‌روز نیست",
        maxDelay <= day,
        "بیشترین فاصله ${maxDelay / hour} ساعت شد"
    )

    // چیدنِ پشت‌سرهم نباید هر روز چند دقیقه جلو/عقب برود
    var t = now
    var sameEveryDay = true
    repeat(5) {
        val d = delayUntilNext(12, 0, t)
        t += d
        // هر شلیک باید دقیقاً روی دقیقهٔ ۰ از ساعت ۱۲ بنشیند
        if ((t % hour) != 0L) sameEveryDay = false
    }
    s.isTrue(
        "پنج روز پشت‌سرهم دقیقاً سرِ ساعت می‌ماند",
        sameEveryDay,
        "زمانِ شلیک از سرِ ساعت جدا افتاد"
    )
    return s.results
}

// =====================================================================
// ۸) هندسهٔ برگهٔ چاپی
// =====================================================================

/**
 * برگه‌ای که از کاغذ بیرون بزند هیچ خطایی نمی‌دهد — فقط بد چاپ می‌شود و
 * تا وقتی کسی برگه را دستش نگیرد کسی نمی‌فهمد. این بررسی همان را می‌گیرد:
 * جمعِ ستون‌ها باید دقیقاً عرضِ محتوا باشد و هر سرستون در خانهٔ خودش جا شود.
 *
 * پارامترها تزریق می‌شوند تا این فایل به `com.afghanjama.pdf` وابسته نماند
 * و همان‌طور بی‌وابستگی بماند که بوده.
 */
data class PaperSpec(
    val label: String,
    val contentW: Int,
    val cellTextSize: Float,
    val columnTitles: List<String>,
    val columnWidths: List<Float>
)

fun checkPaperGeometry(papers: List<PaperSpec>): List<CheckResult> {
    val s = CheckSink("هندسهٔ برگهٔ چاپی")

    if (papers.isEmpty()) {
        s.skip("کاغذی برای بررسی نبود", "فهرستِ کاغذها خالی بود")
        return s.results
    }

    papers.forEach { p ->
        val sum = p.columnWidths.sum()
        s.isTrue(
            "${p.label}: ستون‌ها دقیقاً عرضِ محتوا را پر می‌کنند",
            kotlin.math.abs(sum - p.contentW) < 0.5f,
            "جمعِ ستون‌ها ${"%.1f".format(sum)} شد ولی عرضِ محتوا ${p.contentW} است",
            "${p.columnWidths.size} ستون روی ${p.contentW} نقطه"
        )

        // تقریبِ محافظه‌کارانهٔ پهنای متنِ فارسی: هر حرف ≈ ۰٫۵۸ اندازهٔ قلم.
        // ۴ نقطه هم فاصلهٔ خانه. اگر سرستون جا نشود، وسطش می‌شکند و
        // ردیف‌ها روی هم می‌افتند.
        val perChar = p.cellTextSize * 0.58f
        var tightest = Float.MAX_VALUE
        var tightestName = ""
        p.columnTitles.forEachIndexed { i, title ->
            val need = title.length * perChar + 4f
            val slack = (p.columnWidths.getOrElse(i) { 0f }) - need
            if (slack < tightest) {
                tightest = slack
                tightestName = title
            }
        }
        s.isTrue(
            "${p.label}: سرستون‌ها در یک خط جا می‌شوند",
            tightest >= 0f,
            "«$tightestName» ${"%.1f".format(-tightest)} نقطه جا کم دارد",
            "تنگ‌ترین ستون «$tightestName» با ${"%.1f".format(tightest)} نقطه فضای اضافه"
        )
    }
    return s.results
}

// =====================================================================
// ۹) اعدادِ پایینِ فاکتور
// =====================================================================

/**
 * فاکتور سه عددِ پایین دارد که مشتری دقیقاً همان‌ها را می‌خواند:
 * «بدهی قبلی»، «پرداخت» و «مبلغ قابل پرداخت». هر سه باید از دفتر کل
 * بیرون بیایند، نه از حسابِ جداگانه — وگرنه روزی برگه با دفتر اختلاف
 * پیدا می‌کند و کارگاه نمی‌فهمد کدام درست است.
 *
 * رابطه‌ای که همیشه باید برقرار باشد:
 *   بدهی قبلی + جمعِ فاکتور − پرداخت = مبلغ قابل پرداخت = ماندهٔ دفتر
 */
data class LedgerRow(val at: Long, val refId: String, val debit: Long, val credit: Long)

fun balanceBefore(rows: List<LedgerRow>, excludeRef: String, atMs: Long): Long =
    rows.filter { it.refId != excludeRef && it.at <= atMs }.sumOf { it.debit - it.credit }

fun balanceUpTo(rows: List<LedgerRow>, atMs: Long): Long =
    rows.filter { it.at <= atMs }.sumOf { it.debit - it.credit }

fun checkInvoiceTotals(): List<CheckResult> {
    val s = CheckSink("اعدادِ پایینِ فاکتور")

    /** همان ترتیبی که `Repo.sellInvoice` در دفتر ثبت می‌کند. */
    fun sale(at: Long, code: String, revenue: Long, cash: Long, prepay: Long): List<LedgerRow> =
        buildList {
            if (prepay > 0) add(LedgerRow(at, code, prepay, prepay))   // خنثی
            add(LedgerRow(at, code, revenue, 0))                       // صورت‌حساب
            if (cash > 0) add(LedgerRow(at, code, 0, cash))            // پرداخت
        }

    fun figures(rows: List<LedgerRow>, code: String, at: Long, subtotal: Long): Triple<Long, Long, Long> {
        val prev = balanceBefore(rows, code, at)
        val cur = balanceUpTo(rows, at)
        val paid = prev + subtotal - cur
        return Triple(prev, paid, subtotal + prev - paid)
    }

    // ---- مشتریِ تازه که کاملاً نقد می‌خرد ----
    run {
        val rows = sale(10, "FR-1", 59_394, 59_394, 0)
        val (prev, paid, payable) = figures(rows, "FR-1", 10, 59_394)
        s.eq("مشتریِ تازهٔ نقدی: بدهی قبلی صفر", 0L, prev)
        s.eq("مشتریِ تازهٔ نقدی: پرداخت برابرِ کلِ فاکتور", 59_394L, paid)
        s.eq("مشتریِ تازهٔ نقدی: چیزی برای پرداخت نمی‌ماند", 0L, payable)
    }

    // ---- مشتریِ بدهکارِ قبلی، پرداختِ جزئی ----
    run {
        val rows = sale(1, "FR-OLD", 239_930, 0, 0) + sale(10, "FR-2", 21_060, 13_860, 0)
        val (prev, paid, payable) = figures(rows, "FR-2", 10, 21_060)
        s.eq("بدهیِ قبلی از فاکتورهای گذشته می‌آید", 239_930L, prev)
        s.eq("پرداختِ همین فاکتور جدا شمرده می‌شود", 13_860L, paid)
        s.eq("مبلغ قابل پرداخت = ۲۳۹٬۹۳۰ + ۲۱٬۰۶۰ − ۱۳٬۸۶۰", 247_130L, payable)
        s.eq("و همان ماندهٔ دفتر است", balanceUpTo(rows, 10), payable)
    }

    // ---- بیعانه نباید در «پرداخت» دوباره شمرده شود ----
    run {
        val rows = sale(10, "FR-3", 10_000, 3_000, 4_000)
        val (prev, paid, payable) = figures(rows, "FR-3", 10, 10_000)
        s.eq("سطرِ خنثای بیعانه ماندهٔ قبلی را تکان نمی‌دهد", 0L, prev)
        s.eq("پرداخت فقط نقدِ واقعی است، نه نقد + بیعانه", 3_000L, paid)
        s.eq("باقی‌ماندهٔ طلب درست می‌ماند", 7_000L, payable)
    }

    // ---- پرداختِ دستیِ بی‌مرجع نباید به این فاکتور بچسبد ----
    run {
        val rows = listOf(LedgerRow(1, "", 0, 5_000)) + sale(10, "FR-4", 8_000, 8_000, 0)
        val (prev, paid, _) = figures(rows, "FR-4", 10, 8_000)
        s.eq("پرداختِ بی‌مرجعِ قبلی در «بدهی قبلی» می‌نشیند", -5_000L, prev)
        s.eq("و در «پرداختِ» این فاکتور نمی‌آید", 8_000L, paid)
    }

    // ---- مرجعِ مشترک: اشکالی که واقعاً وجود داشت ----
    run {
        // اگر پرداخت به‌جای کدِ فاکتور برچسبِ ثابت بگیرد، از «سطرهای این
        // فاکتور» بیرون می‌ماند و در بدهیِ قبلی می‌نشیند.
        val wrong = listOf(
            LedgerRow(10, "FR-5", 59_394, 0),
            LedgerRow(10, "FINISHED", 0, 56_737)
        )
        val prevWrong = balanceBefore(wrong, "FR-5", 10)
        s.isTrue(
            "پرداخت باید کدِ فاکتورِ خودش را داشته باشد",
            prevWrong == -56_737L,
            "انتظارِ بازتولیدِ اشکالِ قدیمی داشتیم ولی $prevWrong آمد",
            "با برچسبِ ثابت، «بدهی قبلی» ${prevWrong} می‌شد — برای همین کدِ فاکتور جایگزینش شد"
        )
    }
    return s.results
}

// =====================================================================
// ۱۰) کسری: فروشِ بیشتر از موجودی
// =====================================================================

/**
 * کسری خطرناک‌ترین جای حساب است، چون کالایی فروخته می‌شود که بهایش هنوز
 * معلوم نیست. سه قاعده باید همیشه برقرار بماند وگرنه حسابِ «موجودی محصول»
 * بی‌سروصدا از واقعیت جدا می‌افتد:
 *
 *   الف) ماندهٔ حسابِ موجودی == جمعِ ارزشِ ردیف‌ها
 *   ب )  تعدادِ صفر یعنی ارزشِ صفر
 *   ج )  تعدادِ منفی یعنی ارزش == تعداد × برآوردِ بهای هر عدد
 *
 * این بازتابِ دقیقِ `Repo.takeFromStock` و `Repo.addFinishedStock` است.
 */
data class StockRow(var qty: Int, var value: Long, var avg: Long)

/** بهای خروجِ [n] عدد و ارزشِ باقی‌مانده — آینهٔ `takeFromStock`. */
fun takeCost(row: StockRow, n: Int): Pair<Long, Long> {
    if (n <= 0) return 0L to row.value
    val available = row.qty.coerceAtLeast(0)
    if (n <= available) {
        if (n >= row.qty) return row.value to 0L
        val cost = row.value * n / row.qty
        return cost to (row.value - cost)
    }
    val fromStock = if (available > 0) row.value else 0L
    val cost = fromStock + (n - available) * row.avg
    return cost to (row.value - cost)
}

/** فروش: تعداد و ارزش را جلو می‌برد و بهای تمام‌شده را برمی‌گرداند. */
fun sellFrom(row: StockRow, n: Int): Long {
    val (cost, left) = takeCost(row, n)
    row.qty -= n
    row.value = left
    if (row.qty > 0) row.avg = left / row.qty
    return cost
}

/** ورودِ جنس؛ مقدارِ برگشتی، اثرِ خالص روی حسابِ موجودی است. */
fun addTo(row: StockRow, n: Int, batchValue: Long): Long {
    if (n <= 0) return 0L
    val variance =
        if (row.qty < 0) {
            val covered = minOf(n, -row.qty)
            covered * batchValue / n - covered * row.avg
        } else 0L
    row.qty += n
    row.value = row.value + batchValue - variance
    if (row.qty > 0) row.avg = row.value / row.qty
    return batchValue - variance
}

fun checkShortage(): List<CheckResult> {
    val s = CheckSink("کسری انبار")

    // ---- سناریوی اصلی: ۳ تا داریم، ۵ تا می‌فروشیم، ۲ تا وارد می‌کنیم ----
    run {
        val row = StockRow(qty = 0, value = 0, avg = 0)
        var finished = addTo(row, 3, 300)                 // ۳ عدد، هرکدام ۱۰۰
        s.eq("سه عدد وارد شد", 3, row.qty)
        s.eq("ارزشِ انبار ۳۰۰", 300L, row.value)

        finished -= sellFrom(row, 5)                      // دو تا بیشتر از موجودی
        s.eq("تعداد به کسریِ ۲ رسید", -2, row.qty)
        s.eq("ارزش منفی شد به اندازهٔ برآورد", -200L, row.value)
        s.eq("قاعدهٔ (ج): ارزش == تعداد × برآورد", row.qty * row.avg, row.value)
        s.eq("حسابِ موجودی با جمعِ ارزش‌ها یکی است", row.value, finished)

        finished += addTo(row, 2, 200)                    // با همان بها می‌رسد
        s.eq("کسری تسویه شد", 0, row.qty)
        s.eq("قاعدهٔ (ب): تعدادِ صفر یعنی ارزشِ صفر", 0L, row.value)
        s.eq("حسابِ موجودی دقیقاً به صفر برگشت", 0L, finished)
    }

    // ---- وقتی جنسِ تازه گران‌تر از برآورد درمی‌آید ----
    run {
        val row = StockRow(qty = 0, value = 0, avg = 0)
        var finished = addTo(row, 3, 300)
        finished -= sellFrom(row, 5)
        finished += addTo(row, 2, 300)                    // هرکدام ۱۵۰ نه ۱۰۰
        s.eq("با بهای گران‌تر هم تعداد صفر می‌شود", 0, row.qty)
        s.eq("و ارزش دقیقاً صفر می‌ماند", 0L, row.value)
        s.eq("اختلافِ برآورد به حساب نشست، نه در انبار", 0L, finished)
    }

    // ---- کالایی که هرگز واردش نکرده‌ایم و مستقیم فروخته می‌شود ----
    run {
        val row = StockRow(qty = 0, value = 0, avg = 0)
        var finished = 0L
        finished -= sellFrom(row, 4)
        s.eq("کسریِ ۴ ثبت شد", -4, row.qty)
        s.eq("بهای نامعلوم صفر برآورد می‌شود، نه عددِ ساختگی", 0L, row.value)
        finished += addTo(row, 4, 800)                    // بعداً معلوم شد ۲۰۰ تایی
        s.eq("بعد از ورود، تعداد صفر", 0, row.qty)
        s.eq("و ارزش صفر", 0L, row.value)
        s.eq("کلِ ۸۰۰ به بهای تمام‌شده رفت، نه به دارایی", 0L, finished)
    }

    // ---- هزار سناریوی تصادفی ----
    run {
        var seed = 20250729L
        fun rnd(bound: Int): Int {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return (((seed ushr 33).toInt() % bound) + bound) % bound
        }
        var broken = 0
        var negatives = 0
        repeat(1000) {
            val row = StockRow(0, 0, 0)
            var finished = 0L
            repeat(10) {
                if (rnd(2) == 0) {
                    val n = rnd(6) + 1
                    finished += addTo(row, n, (n * (rnd(300) + 50)).toLong())
                } else {
                    val n = rnd(8) + 1
                    finished -= sellFrom(row, n)
                    if (row.qty < 0) negatives++
                }
                if (row.value != finished) broken++
                if (row.qty == 0 && row.value != 0L) broken++
                if (row.qty < 0 && row.value != row.qty * row.avg) broken++
            }
            // همه‌چیز را به صفر برگردان
            if (row.qty < 0) finished += addTo(row, -row.qty, (-row.qty) * row.avg)
            if (row.qty > 0) finished -= sellFrom(row, row.qty)
            if (finished != 0L) broken++
        }
        s.isTrue(
            "هزار سناریوی تصادفی، هر سه قاعده برقرار و حساب به صفر برمی‌گردد",
            broken == 0,
            "$broken بار یکی از قاعده‌ها شکست",
            "$negatives بار موجودی منفی شد و هیچ‌کدام حساب را خراب نکرد"
        )
    }
    return s.results
}

// =====================================================================
// ۱۱) تخفیفِ ردیفِ فاکتور و برگشت از فروش
// =====================================================================

/**
 * تخفیف ساده به نظر می‌رسد ولی دو جا را خراب می‌کند اگر «تعداد × فی» جای
 * «جمعِ ردیف» به کار برود:
 *  - برگشتِ کاملِ یک ردیفِ تخفیف‌دار بیشتر از چیزی که مشتری داده پس می‌دهد.
 *  - تقسیمِ صحیح در برگشتِ تکه‌تکه هر بار چند افغانی جا می‌گذارد.
 *
 * بازتابِ `FinishedSale.share` است.
 */
fun shareOf(amount: Long, qty: Int, returned: Int, n: Int): Long {
    if (qty <= 0 || n <= 0) return 0L
    val doneSoFar = if (returned >= qty) amount else amount * returned / qty
    val remaining = qty - returned
    return if (n >= remaining) amount - doneSoFar
    else amount * (returned + n) / qty - doneSoFar
}

fun checkDiscountMath(): List<CheckResult> {
    val s = CheckSink("تخفیف و برگشت از فروش")

    // ---- جمعِ ردیف با تخفیف ----
    run {
        val qty = 12
        val unit = 600L
        val discount = 1_200L
        val total = qty * unit - discount
        s.eq("جمعِ ردیف = تعداد × فی − تخفیف", 6_000L, total)
        s.eq("تخفیفِ بیشتر از ردیف پذیرفته نمی‌شود", 7_200L, 9_999L.coerceIn(0L, qty * unit))
    }

    // ---- برگشتِ کامل باید دقیقاً همان مبلغِ فروش باشد ----
    run {
        val qty = 12
        val total = 6_000L                       // با تخفیف، نه ۷٬۲۰۰
        val old = qty * 600L                     // روشِ قدیمی: تعداد × فی
        s.isTrue(
            "روشِ قدیمی بیشتر از مبلغِ فروش پس می‌داد",
            old > total,
            "انتظار داشتیم روشِ قدیمی زیادی پس بدهد ولی $old ≤ $total",
            "${old - total} ؋ اضافه روی همین یک ردیف"
        )
        s.eq("برگشتِ کاملِ یک‌جا دقیقاً مبلغِ فروش است", total, shareOf(total, qty, 0, qty))
    }

    // ---- برگشتِ تکه‌تکه نباید افغانی گم کند ----
    run {
        val qty = 7
        val total = 10_000L                      // بر ۷ بخش‌پذیر نیست
        var returned = 0
        var got = 0L
        while (returned < qty) {
            val n = if (qty - returned >= 2) 2 else qty - returned
            got += shareOf(total, qty, returned, n)
            returned += n
        }
        s.eq("جمعِ برگشت‌های تکه‌تکه = مبلغِ فروش", total, got)
    }

    // ---- بهای تمام‌شده هم با همان قاعده برمی‌گردد ----
    run {
        val qty = 9
        val cost = 4_444L
        var returned = 0
        var back = 0L
        while (returned < qty) {
            val n = if (qty - returned >= 4) 4 else qty - returned
            back += shareOf(cost, qty, returned, n)
            returned += n
        }
        s.eq("جمعِ بهای برگشتی = بهای تمام‌شدهٔ ردیف", cost, back)
    }

    // ---- هزار ردیفِ تصادفی ----
    run {
        var seed = 4242L
        fun rnd(bound: Int): Int {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return (((seed ushr 33).toInt() % bound) + bound) % bound
        }
        var broken = 0
        repeat(1000) {
            val qty = rnd(30) + 1
            val unit = (rnd(4_950) + 50).toLong()
            val discount = if (rnd(3) == 0) (rnd((qty * unit / 2).toInt() + 1)).toLong() else 0L
            val total = qty * unit - discount
            var returned = 0
            var got = 0L
            while (returned < qty) {
                val n = minOf(rnd(5) + 1, qty - returned)
                got += shareOf(total, qty, returned, n)
                returned += n
            }
            if (got != total) broken++
        }
        s.isTrue(
            "هزار ردیفِ تصادفی: جمعِ برگشت‌ها دقیقاً برابرِ مبلغِ فروش",
            broken == 0,
            "$broken ردیف اختلاف داشت"
        )
    }
    return s.results
}

// =====================================================================
// 12) فایلِ پشتیبان
// =====================================================================

/**
 * دو چیز در پشتیبان می‌تواند بی‌سروصدا خراب کند:
 *
 *  - **نامِ ورودیِ zip.** فایلِ پشتیبان ممکن است از هر جایی آمده باشد.
 *    نامی با ../ می‌تواند بیرون از پوشهٔ اپ بنویسد. باید رد شود.
 *  - **تشخیصِ قالب.** پشتیبانِ قدیمی فایلِ خامِ SQLite بود و باید تا همیشه
 *    باز شود؛ فایلِ ناشناس هم باید خطای روشن بدهد نه کرش.
 *
 * توابع تزریق می‌شوند تا این فایل بدونِ وابستگی بماند.
 */
fun checkBackupArchive(
    safePhotoName: (String) -> String?,
    detect: (ByteArray) -> String
): List<CheckResult> {
    val s = CheckSink("فایل پشتیبان")

    // ---- نام‌هایی که باید پذیرفته شوند ----
    listOf("p_1712345678_4242.jpg", "photo.png", "aks-1.jpg").forEach { n ->
        s.eq("نامِ سالم پذیرفته می‌شود: $n", n, safePhotoName("photos/$n"))
    }

    // ---- نام‌هایی که باید رد شوند ----
    val dangerous = listOf(
        "photos/../../../databases/afghanjama.db" to "بالا رفتن از پوشه",
        "photos/../evil.jpg" to "یک پله بالا",
        "photos/sub/dir.jpg" to "زیرپوشه",
        "photos/" to "نامِ خالی",
        "photos/.." to "خودِ پوشهٔ بالا",
        "database" to "خارج از پوشهٔ عکس",
        "meta.txt" to "خارج از پوشهٔ عکس",
        "evil.jpg" to "بدونِ پیشوند"
    )
    var blocked = 0
    dangerous.forEach { (name, why) ->
        val got = safePhotoName(name)
        if (got == null) blocked++
        else s.fail("«$why» باید رد شود", "ولی «$got» برگشت (ورودی: $name)")
    }
    s.eq("همهٔ نام‌های خطرناک رد شدند", dangerous.size, blocked)

    // ---- تشخیصِ قالب ----
    val zipHead = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0)
    val sqliteHead = "SQLite format 3 ".toByteArray(Charsets.US_ASCII)
    s.eq("فایلِ zip شناخته می‌شود", "ZIP", detect(zipHead))
    s.eq("پشتیبانِ قدیمیِ SQLite شناخته می‌شود", "RAW_DB", detect(sqliteHead))
    s.eq("فایلِ آشغال ناشناس می‌ماند", "UNKNOWN", detect("hello world!!!!!".toByteArray()))
    s.eq("فایلِ خالی ناشناس می‌ماند", "UNKNOWN", detect(ByteArray(0)))
    s.eq("چند بایتِ ناقص کرش نمی‌کند", "UNKNOWN", detect(byteArrayOf(0x50, 0x4B)))
    return s.results
}

// =====================================================================
// 13) فهرستِ ریست
// =====================================================================

/**
 * ریست فقط وقتی درست است که فهرستِ جدول‌ها با واقعیتِ دیتابیس بخواند.
 *
 * خطرناک‌ترین حالت سکوت است: جدولی که در هیچ فهرستی نیست، بی‌سروصدا از
 * ریست جان به در می‌برد و کارگاه فکر می‌کند همه‌چیز پاک شده. این اتفاق
 * هر بار که جدولِ تازه‌ای اضافه شود ممکن است — مثلِ `order_photos` که
 * همین اواخر اضافه شد.
 *
 * [actualTables] از دیتابیسِ زنده می‌آید؛ اگر خالی باشد (اجرای بیرون از
 * گوشی) فقط خودِ فهرست‌ها سنجیده می‌شوند.
 */
fun checkResetPlan(
    clear: List<String>,
    keep: List<String>,
    actualTables: List<String> = emptyList()
): List<CheckResult> {
    val s = CheckSink("فهرست ریست")

    s.isTrue("فهرستِ پاک‌شدنی خالی نیست", clear.isNotEmpty(), "هیچ جدولی برای پاک‌کردن نیست")
    s.isTrue("فهرستِ ماندنی خالی نیست", keep.isNotEmpty(), "هیچ جدولی نگه داشته نمی‌شود")

    val both = clear.toSet() intersect keep.toSet()
    s.isTrue(
        "هیچ جدولی هم‌زمان پاک و نگه داشته نمی‌شود",
        both.isEmpty(),
        "در هر دو فهرست: " + both.sorted().joinToString("، ")
    )

    listOf("پاک‌شدنی" to clear, "ماندنی" to keep).forEach { (label, list) ->
        val dupes = list.groupBy { it }.filter { it.value.size > 1 }.keys
        s.isTrue(
            "نامِ تکراری در فهرستِ $label نیست",
            dupes.isEmpty(),
            "تکراری: " + dupes.sorted().joinToString("، ")
        )
    }

    if (actualTables.isEmpty()) {
        s.skip("هر جدولِ دیتابیس دسته‌بندی شده", "بیرون از گوشی اجرا شد")
        return s.results
    }

    val listed = clear.toSet() + keep.toSet()
    val uncategorised = (actualTables.toSet() - listed).sorted()
    s.isTrue(
        "هر جدولِ دیتابیس دسته‌بندی شده",
        uncategorised.isEmpty(),
        "دسته‌بندی‌نشده — با ریست پاک نمی‌شود: " + uncategorised.joinToString("، "),
        "${actualTables.size} جدول"
    )

    val ghosts = (listed - actualTables.toSet()).sorted()
    s.isTrue(
        "هر نامِ فهرست واقعاً در دیتابیس هست",
        ghosts.isEmpty(),
        "نامِ جدولی که وجود ندارد: " + ghosts.joinToString("، ")
    )
    return s.results
}

// =====================================================================
// 14) چرخهٔ کاملِ یک سفارش — «کار در جریان» باید به صفر برگردد
// =====================================================================

/**
 * سفارش از برش تا ورودِ محصول به انبار، با همان سطرهایی که `Repo`
 * می‌نویسد. حسابِ «کار در جریان» یک حسابِ گذراست: هرچه داخلش می‌رود باید
 * با ورودِ محصول به انبار کامل بیرون بیاید. اگر نه، یعنی «موجودی محصول»
 * بیش‌ازواقع است و دارایی‌های اپ باد کرده.
 *
 * سه راهِ خراب‌شدنش که این بررسی می‌گیرد:
 *  - خرج‌کار از «کار در جریان» بستانکار شود بی‌آنکه هرگز بدهکارش شده باشد
 *  - ارزشِ موادِ برداشته‌شده با برآوردِ ثبتِ سفارش فرق کند و تفاوت جایی نرود
 *  - حذفِ سفارشِ برش‌خورده جنس را برگرداند ولی سندش را خنثی نکند
 */
data class CycleInput(
    /** برآوردِ موادِ سفارش، حساب‌شده هنگامِ ثبت. */
    val fabricPrice: Long,
    /** ارزشِ واقعیِ موادی که هنگامِ برش از انبار رفت. */
    val takenValue: Long,
    val workCost: Long,
    val wage: Long
)

/** ماندهٔ حساب‌ها پس از یک چرخهٔ کامل. کلید: نامِ حساب. */
fun runOrderCycle(input: CycleInput, deletedAfterCutting: Boolean = false,
                  returnedValue: Long = 0L,
                  sewnBeforeDelete: Boolean = false): Map<String, Long> {
    val acc = mutableMapOf<String, Long>()
    fun post(lines: List<Triple<String, Long, Long>>) {
        val d = lines.sumOf { it.second }
        val c = lines.sumOf { it.third }
        if (d != c) {
            acc["__UNBALANCED__"] = (acc["__UNBALANCED__"] ?: 0L) + (d - c)
            return
        }
        lines.forEach { (a, dr, cr) -> acc[a] = (acc[a] ?: 0L) + dr - cr }
    }

    // ثبتِ سفارش: خرج‌کار وارد «کار در جریان»
    if (input.workCost > 0) {
        post(listOf(Triple("WIP", input.workCost, 0L), Triple("PAYABLE", 0L, input.workCost)))
    }
    // برش: ارزشِ واقعیِ مواد، بعد تسویهٔ تفاوتِ برآورد
    if (input.takenValue > 0) {
        post(listOf(Triple("WIP", input.takenValue, 0L), Triple("MATERIALS", 0L, input.takenValue)))
    }
    val gap = input.fabricPrice - input.takenValue
    if (gap > 0) post(listOf(Triple("WIP", gap, 0L), Triple("EXPENSES", 0L, gap)))
    if (gap < 0) post(listOf(Triple("EXPENSES", -gap, 0L), Triple("WIP", 0L, -gap)))

    if (deletedAfterCutting) {
        // اگر پیش از حذف دوخته شده بود، دستمزدش هم داخلِ «کار در جریان»
        // رفته و باید از آنجا بیرون بیاید
        if (sewnBeforeDelete && input.wage > 0) {
            post(listOf(Triple("WIP", input.wage, 0L), Triple("WAGES_PAYABLE", 0L, input.wage)))
        }
        val back = input.fabricPrice
        val g = back - returnedValue
        post(
            buildList {
                if (returnedValue > 0) add(Triple("MATERIALS", returnedValue, 0L))
                if (g > 0) add(Triple("EXPENSES", g, 0L))
                if (g < 0) add(Triple("EXPENSES", 0L, -g))
                if (back > 0) add(Triple("WIP", 0L, back))
            }
        )
        // خرج‌کار همان لحظهٔ ثبتِ سفارش وارد سند شد، پس با حذفِ سفارش هم
        // برمی‌گردد — بی‌شرط، چون به برش کاری ندارد. تا وقتی خرج‌کار از
        // رابط قابلِ انتخاب نبود این مبلغ همیشه صفر بود و کسی نمی‌دید که
        // در «کار در جریان» جا می‌ماند.
        if (input.workCost > 0) {
            post(
                listOf(
                    Triple("PAYABLE", input.workCost, 0L),
                    Triple("WIP", 0L, input.workCost)
                )
            )
        }
        // دستمزدِ دوخت هم از «کار در جریان» بیرون می‌آید — ولی بدهیِ خیاط
        // برنمی‌گردد، چون او واقعاً کار کرده. پس زیانِ سفارشِ لغوشده است.
        if (sewnBeforeDelete && input.wage > 0) {
            post(
                listOf(
                    Triple("EXPENSES", input.wage, 0L),
                    Triple("WIP", 0L, input.wage)
                )
            )
        }
        return acc
    }

    // کارمزد دوخت
    if (input.wage > 0) {
        post(listOf(Triple("WIP", input.wage, 0L), Triple("WAGES_PAYABLE", 0L, input.wage)))
    }
    // ورود به انبار محصول
    val totalCost = input.fabricPrice + input.workCost + input.wage
    if (totalCost > 0) {
        post(listOf(Triple("FINISHED", totalCost, 0L), Triple("WIP", 0L, totalCost)))
    }
    return acc
}

fun checkOrderCycle(): List<CheckResult> {
    val s = CheckSink("چرخهٔ سفارش")

    // ---- سناریوی روشن: برآورد با واقعیت فرق دارد و خرج‌کار هم هست ----
    run {
        val r = runOrderCycle(CycleInput(fabricPrice = 500, takenValue = 1_000,
                                        workCost = 300, wage = 2_000))
        s.eq("«کار در جریان» پس از تکمیل صفر می‌شود", 0L, r["WIP"] ?: 0L)
        s.eq("هیچ سندِ ناترازی ثبت نشد", null, r["__UNBALANCED__"])
        s.eq("موجودی مواد به اندازهٔ ارزشِ واقعی کم شد", -1_000L, r["MATERIALS"] ?: 0L)
        s.eq(
            "موجودی محصول = پارچه + خرج‌کار + دستمزد",
            2_800L, r["FINISHED"] ?: 0L
        )
        // تفاوتِ برآورد (۵۰۰ − ۱۰۰۰ = −۵۰۰) به هزینه‌ها رفت
        s.eq("تفاوتِ برآورد در هزینه‌ها نشست", 500L, r["EXPENSES"] ?: 0L)
    }

    // ---- خرج‌کارِ تنها: همان اشکالی که بمبِ ساعتی بود ----
    run {
        val r = runOrderCycle(CycleInput(0, 0, workCost = 750, wage = 0))
        s.eq("سفارشی با فقط خرج‌کار هم «کار در جریان» را صفر می‌کند", 0L, r["WIP"] ?: 0L)
        s.eq("خرج‌کار بدهیِ پرداختنی ساخت", -750L, r["PAYABLE"] ?: 0L)
    }

    // ---- حذفِ سفارشِ برش‌خورده ----
    run {
        val r = runOrderCycle(
            CycleInput(fabricPrice = 1_000, takenValue = 1_000, workCost = 0, wage = 0),
            deletedAfterCutting = true, returnedValue = 1_400
        )
        s.eq("حذف پس از برش «کار در جریان» را صفر می‌کند", 0L, r["WIP"] ?: 0L)
        s.eq("هیچ سندِ ناترازی در حذف نبود", null, r["__UNBALANCED__"])
        s.eq("جنس با ارزشِ امروزش به انبار برگشت", 400L, r["MATERIALS"] ?: 0L)
    }

    // ---- حذفِ سفارشی که خرج‌کار داشت ----
    // تا وقتی خرج‌کار از رابط قابلِ انتخاب نبود، این حالت هرگز پیش نمی‌آمد
    // و مبلغش بی‌سروصدا در «کار در جریان» و «پرداختنی» جا می‌ماند.
    run {
        val r = runOrderCycle(
            CycleInput(fabricPrice = 2_000, takenValue = 2_000, workCost = 900, wage = 0),
            deletedAfterCutting = true, returnedValue = 2_000
        )
        s.eq("حذفِ سفارشِ خرج‌کاردار «کار در جریان» را صفر می‌کند", 0L, r["WIP"] ?: 0L)
        s.eq("بدهیِ خرج‌کار با حذفِ سفارش برگشت", 0L, r["PAYABLE"] ?: 0L)
        s.eq("هیچ سندِ ناترازی در این حذف نبود", null, r["__UNBALANCED__"])
    }

    // ---- حذفِ سفارشی که دوخته شده بود ----
    // امروز از رابط نمی‌شود چنین سفارشی را حذف کرد، ولی آن محافظت یک شرطِ
    // صفحه بود نه یک قاعده؛ حالا هم قاعده هست و هم سندِ برگشتش.
    run {
        val r = runOrderCycle(
            CycleInput(fabricPrice = 4_000, takenValue = 4_000, workCost = 600, wage = 3_500),
            deletedAfterCutting = true, returnedValue = 4_000, sewnBeforeDelete = true
        )
        s.eq("حذفِ سفارشِ دوخته‌شده «کار در جریان» را صفر می‌کند", 0L, r["WIP"] ?: 0L)
        s.eq(
            "بدهیِ خیاط با حذفِ سفارش برنمی‌گردد — کار انجام شده",
            -3_500L, r["WAGES_PAYABLE"] ?: 0L
        )
        s.eq("دستمزدِ سفارشِ لغوشده زیان است", 3_500L, r["EXPENSES"] ?: 0L)
        s.eq("هیچ سندِ ناترازی در این حذف نبود", null, r["__UNBALANCED__"])
    }

    // ---- صدها حالتِ تصادفی ----
    run {
        var seed = 314159L
        fun rnd(bound: Int): Int {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return (((seed ushr 33).toInt() % bound) + bound) % bound
        }
        var brokenWip = 0
        var unbalanced = 0
        repeat(600) {
            val input = CycleInput(
                fabricPrice = rnd(20_000).toLong(),
                takenValue = rnd(20_000).toLong(),
                workCost = if (rnd(3) == 0) rnd(3_000).toLong() else 0L,
                wage = if (rnd(4) == 0) 0L else rnd(5_000).toLong()
            )
            val deleted = rnd(4) == 0
            val sewn = deleted && rnd(2) == 0
            val r = runOrderCycle(
                input, deleted,
                if (deleted) rnd(20_000).toLong() else 0L,
                sewnBeforeDelete = sewn
            )
            // هر دو راه — تکمیل یا حذف — باید «کار در جریان» را به صفر
            // برگردانند. هیچ حالتی نمانده که مبلغی داخلش جا بماند.
            if ((r["WIP"] ?: 0L) != 0L) brokenWip++
            if (r["__UNBALANCED__"] != null) unbalanced++
        }
        s.eq("۶۰۰ چرخهٔ تصادفی: هیچ سندِ ناترازی", 0, unbalanced)
        s.eq("۶۰۰ چرخهٔ تصادفی: «کار در جریان» همیشه به صفر برمی‌گردد", 0, brokenWip)
    }
    return s.results
}

// =====================================================================
// 15) قاعدهٔ خروجِ پول از صندوق — همه‌جا یکی
// =====================================================================

/** یک مسیرِ خروجِ پول، همان‌طور که `CashPolicy.OUTFLOWS` توصیفش می‌کند. */
data class CashPath(
    val name: String,
    /** نامِ نگهبانی که **پیش از هر نوشتنی** موجودی را می‌سنجد. خالی = بی‌نگهبان. */
    val guard: String,
    /** وقتی رد شد، به کاربر می‌گوید؟ سکوت بدترین حالت است. */
    val reportsRefusal: Boolean
)

/**
 * قاعدهٔ خروجِ پول را می‌سنجد، نه یک مسیرِ خاص را.
 *
 * پیش‌تر هر مسیر قاعدهٔ خودش را داشت — چهار مسیر موجودی را می‌سنجیدند و
 * چهار مسیر نه — و کارگاه می‌توانست صندوق را منفی کند. حالا یک قاعده هست
 * و اینجا سنجیده می‌شود: مرزهایش، و اینکه هیچ مسیری بی‌نگهبان و بی‌صدا
 * نمانده باشد.
 */
fun checkCashOutflowPolicy(
    canSpend: (Long, Long) -> Boolean,
    isCashSource: (String) -> Boolean,
    paths: List<CashPath>
): List<CheckResult> {
    val s = CheckSink("قاعدهٔ خروج پول")

    // ---- مرزها: صندوق تا صفر خالی می‌شود، ولی نه یک افغانی زیرِ آن ----
    s.isTrue("مبلغِ برابرِ موجودی قبول می‌شود", canSpend(10_000L, 10_000L),
        "صندوقِ ۱۰٬۰۰۰ نتوانست ۱۰٬۰۰۰ بدهد — کارگاه نمی‌تواند صندوق را خالی کند")
    s.isTrue("یک افغانی بیشتر از موجودی رد می‌شود", !canSpend(10_000L, 10_001L),
        "صندوقِ ۱۰٬۰۰۰ مبلغِ ۱۰٬۰۰۱ را قبول کرد — صندوق منفی می‌شود")
    s.isTrue("صندوقِ خالی هیچ پرداختی نمی‌کند", !canSpend(0L, 1L),
        "صندوقِ خالی مبلغِ ۱ را قبول کرد")
    s.isTrue("صندوقِ خالی مبلغِ صفر را قبول می‌کند", canSpend(0L, 0L),
        "مبلغِ صفر خروجِ پولی ندارد ولی رد شد")
    s.isTrue("مبلغِ منفی به این قاعده گیر نمی‌کند", canSpend(0L, -500L),
        "ردکردنِ مبلغِ منفی کارِ کنترلِ ورودی است، نه این قاعده")
    s.isTrue("از صندوقِ منفی هم پرداخت نمی‌شود", !canSpend(-100L, 1L),
        "صندوقی که از قبل منفی است بازهم پرداخت کرد")

    // ---- منبعِ پرداخت: نسیه و حسابِ مشتری به صندوق کار ندارند ----
    listOf("WALLET", "BANK", "PROFIT").forEach { src ->
        s.isTrue("$src از صندوق پول کم می‌کند", isCashSource(src),
            "$src نقدی شمرده نشد، پس موجودی‌اش کنترل نمی‌شود")
    }
    listOf("CREDIT", "CUSTOMER").forEach { src ->
        s.isTrue("$src به موجودیِ صندوق گیر نمی‌کند", !isCashSource(src),
            "$src نقدی شمرده شد، پس خریدِ نسیه با صندوقِ خالی رد می‌شود")
    }

    // ---- هیچ‌گاه اجازهٔ منفی‌شدن ----
    run {
        var seed = 271828L
        fun rnd(bound: Int): Long {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((((seed ushr 33).toInt() % bound) + bound) % bound).toLong()
        }
        var wouldGoNegative = 0
        var wronglyRefused = 0
        repeat(2_000) {
            val balance = rnd(500_000)
            val amount = rnd(500_000) + 1
            if (canSpend(balance, amount)) {
                if (balance - amount < 0L) wouldGoNegative++
            } else {
                if (balance - amount >= 0L) wronglyRefused++
            }
        }
        s.eq("۲۰۰۰ حالتِ تصادفی: هیچ پرداختی صندوق را منفی نمی‌کند", 0, wouldGoNegative)
        s.eq("۲۰۰۰ حالتِ تصادفی: هیچ پرداختِ ممکنی رد نمی‌شود", 0, wronglyRefused)
    }

    // ---- فهرستِ مسیرها ----
    s.isTrue("فهرستِ مسیرهای خروجِ پول خالی نیست", paths.isNotEmpty(),
        "هیچ مسیری ثبت نشده — این بررسی بی‌معنا می‌شود")

    val unguarded = paths.filter { it.guard.isBlank() }
    s.isTrue(
        "هر مسیرِ خروجِ پول نگهبانِ موجودی دارد",
        unguarded.isEmpty(),
        "بی‌نگهبان: " + unguarded.joinToString("، ") { it.name },
        "${paths.size} مسیر"
    )

    val silent = paths.filter { !it.reportsRefusal }
    s.isTrue(
        "هر مسیر ردشدنش را به کاربر می‌گوید",
        silent.isEmpty(),
        "بی‌صدا رد می‌شوند — کاربر فکر می‌کند ثبت شد: " +
            silent.joinToString("، ") { it.name },
        "${paths.size} مسیر"
    )

    val dupes = paths.groupBy { it.name }.filter { it.value.size > 1 }.keys
    s.isTrue(
        "نامِ مسیرِ تکراری در فهرست نیست",
        dupes.isEmpty(),
        "تکراری: " + dupes.joinToString("، ")
    )

    return s.results
}

// =====================================================================
// 16) حقوق و پیش‌پرداختِ کارمند — تهاتر باید کامل باشد
// =====================================================================

/** نتیجهٔ یک پرداختِ حقوق: چه از صندوق رفت و حساب‌ها کجا نشستند. */
data class SalaryResult(
    val cashOut: Long,
    val deducted: Long,
    /** ماندهٔ کارمند در دفتر کل پس از پرداخت — باید صفر شود. */
    val employeeBalance: Long,
    /** ماندهٔ «پیش‌پرداخت کارکنان» پس از پرداخت. */
    val advanceLeft: Long,
    /** هزینهٔ حقوقِ ثبت‌شده — باید حقوقِ کامل باشد، نه فقط نقد. */
    val expense: Long,
    val unbalanced: Boolean
)

/**
 * بازتابِ دقیقِ `Repo.paySalary` به‌همراهِ پیش‌پرداختی که از قبل داده شده.
 *
 * چرا این‌جا؟ چون تا پیش از این، پیش‌پرداخت هیچ‌وقت تهاتر نمی‌شد: کارمند
 * تا ابد در دفتر بدهکار می‌ماند، حسابِ «پیش‌پرداخت کارکنان» بادکرده، و
 * هزینهٔ حقوق به اندازهٔ همان پیش‌پرداخت کمتر از واقع. سه اشکال از یک ریشه.
 */
fun runSalaryCycle(salary: Long, advance: Long, askedDeduct: Long): SalaryResult {
    var employee = 0L          // بدهکار مثبت = کارمند به ما بدهکار است
    var staffAdvance = 0L
    var expense = 0L
    var unbalanced = false

    fun post(lines: List<Pair<Long, Long>>) {
        if (lines.sumOf { it.first } != lines.sumOf { it.second }) unbalanced = true
    }

    // پیش‌پرداختِ قبلی: کارمند بدهکار، حسابِ پیش‌پرداخت پر
    if (advance > 0) {
        employee += advance
        staffAdvance += advance
        post(listOf(advance to advance))
    }

    // کسر نه از حقوق بیشتر می‌شود و نه از پیش‌پرداختِ واقعی
    val deduct = askedDeduct.coerceIn(0L, minOf(salary, employee.coerceAtLeast(0L)))
    val cashOut = salary - deduct

    // دفتر کل: تعهدِ حقوقِ کامل بستانکار، نقدِ پرداختی بدهکار
    employee -= salary
    if (cashOut > 0) employee += cashOut

    // ژورنال: هزینهٔ کاملِ حقوق در برابرِ تهاترِ پیش‌پرداخت و نقد
    expense += salary
    staffAdvance -= deduct
    post(listOf(salary to (deduct + cashOut)))

    return SalaryResult(
        cashOut = cashOut,
        deducted = deduct,
        employeeBalance = employee,
        advanceLeft = staffAdvance,
        expense = expense,
        unbalanced = unbalanced
    )
}

fun checkSalaryAdvance(): List<CheckResult> {
    val s = CheckSink("حقوق و پیش‌پرداخت")

    // ---- تهاترِ کامل: هر سه حساب باید صفر شوند ----
    run {
        val r = runSalaryCycle(salary = 10_000, advance = 2_000, askedDeduct = 2_000)
        s.eq("نقدِ پرداختی = حقوق منهای پیش‌پرداخت", 8_000L, r.cashOut)
        s.eq("ماندهٔ کارمند پس از پرداخت صفر می‌شود", 0L, r.employeeBalance)
        s.eq("حسابِ پیش‌پرداخت کارکنان خالی می‌شود", 0L, r.advanceLeft)
        s.eq("هزینهٔ حقوق کاملِ حقوق است، نه فقط نقد", 10_000L, r.expense)
        s.isTrue("سندِ حقوق تراز است", !r.unbalanced, "بدهکار و بستانکار نخواندند")
    }

    // ---- بی‌کسر: رفتارِ قبلی مو‌به‌مو ----
    run {
        val r = runSalaryCycle(salary = 10_000, advance = 2_000, askedDeduct = 0)
        s.eq("بی‌کسر، همهٔ حقوق نقد داده می‌شود", 10_000L, r.cashOut)
        s.eq("بی‌کسر، پیش‌پرداخت سرِ جایش می‌ماند", 2_000L, r.advanceLeft)
        s.eq("بی‌کسر، کارمند همان‌قدر بدهکار می‌ماند", 2_000L, r.employeeBalance)
        s.eq("بی‌کسر هم هزینه کاملِ حقوق است", 10_000L, r.expense)
    }

    // ---- حقوق تماماً از پیش‌پرداخت: نقدی خارج نمی‌شود ----
    run {
        val r = runSalaryCycle(salary = 3_000, advance = 3_000, askedDeduct = 3_000)
        s.eq("وقتی پیش‌پرداخت همهٔ حقوق است، نقدی خارج نمی‌شود", 0L, r.cashOut)
        s.eq("ماندهٔ کارمند بازهم صفر می‌شود", 0L, r.employeeBalance)
        s.eq("هزینهٔ حقوق بازهم کامل ثبت می‌شود", 3_000L, r.expense)
        s.isTrue("سندِ بی‌نقد هم تراز است", !r.unbalanced, "سندِ ناتراز")
    }

    // ---- کسرِ بیش از حد باید بریده شود ----
    run {
        val r = runSalaryCycle(salary = 3_000, advance = 5_000, askedDeduct = 5_000)
        s.eq("کسر از حقوقِ این ماه بیشتر نمی‌شود", 3_000L, r.deducted)
        s.eq("پیش‌پرداختِ باقی‌مانده برای ماهِ بعد می‌ماند", 2_000L, r.advanceLeft)
        s.eq("نقدی خارج نمی‌شود", 0L, r.cashOut)
    }
    run {
        val r = runSalaryCycle(salary = 10_000, advance = 2_000, askedDeduct = 9_999)
        s.eq("کسر از پیش‌پرداختِ واقعی بیشتر نمی‌شود", 2_000L, r.deducted)
        s.eq("بقیه نقد داده می‌شود", 8_000L, r.cashOut)
    }
    run {
        val r = runSalaryCycle(salary = 10_000, advance = 0, askedDeduct = 8_000)
        s.eq("بی پیش‌پرداخت، کسری انجام نمی‌شود", 0L, r.deducted)
        s.eq("همهٔ حقوق نقد داده می‌شود", 10_000L, r.cashOut)
    }

    // ---- صدها حالتِ تصادفی ----
    run {
        var seed = 161803L
        fun rnd(bound: Int): Long {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((((seed ushr 33).toInt() % bound) + bound) % bound).toLong()
        }
        var brokenBalance = 0
        var brokenExpense = 0
        var negativeCash = 0
        var unbalanced = 0
        var leakedAdvance = 0
        repeat(1_000) {
            val salary = rnd(60_000) + 1
            val advance = if (rnd(3) == 0L) 0L else rnd(30_000)
            val asked = advance                     // کارگاه همهٔ پیش‌پرداخت را کسر می‌کند
            val r = runSalaryCycle(salary, advance, asked)
            val expectedDeduct = minOf(salary, advance)
            if (r.cashOut < 0) negativeCash++
            if (r.expense != salary) brokenExpense++
            if (r.unbalanced) unbalanced++
            // ماندهٔ کارمند باید همان پیش‌پرداختِ کسرنشده باشد
            if (r.employeeBalance != advance - expectedDeduct) brokenBalance++
            if (r.advanceLeft != advance - expectedDeduct) leakedAdvance++
        }
        s.eq("۱۰۰۰ حالتِ تصادفی: نقدِ منفی نداریم", 0, negativeCash)
        s.eq("۱۰۰۰ حالتِ تصادفی: هزینهٔ حقوق همیشه کاملِ حقوق است", 0, brokenExpense)
        s.eq("۱۰۰۰ حالتِ تصادفی: هیچ سندِ ناترازی", 0, unbalanced)
        s.eq("۱۰۰۰ حالتِ تصادفی: ماندهٔ کارمند همان‌قدر که باید", 0, brokenBalance)
        s.eq("۱۰۰۰ حالتِ تصادفی: پیش‌پرداخت جا نمی‌ماند", 0, leakedAdvance)
    }

    return s.results
}

// =====================================================================
// 17) جریانِ نقد — جابه‌جاییِ داخلی نه درآمد است نه هزینه
// =====================================================================

/** یک سطرِ صندوق، همان‌قدر که گزارشِ نقد از آن می‌داند. */
data class CashRow(
    /** IN یا OUT */
    val type: String,
    val amount: Long,
    val category: String
)

/** خروجیِ کارتِ «جریان نقد دوره». */
data class CashFlow(
    val income: Long,
    val expense: Long,
    val internalMoves: Long,
    val byCategory: List<Pair<String, Long>>
) {
    val net: Long get() = income - expense
}

/**
 * بازتابِ دقیقِ `ReportsViewModel.build` برای کارتِ جریانِ نقد.
 *
 * سه مسیر پول را بینِ صندوق‌های خودِ کارگاه جابه‌جا می‌کنند و هر سه یک جفت
 * سطرِ IN/OUT می‌نویسند. تا پیش از این، گزارش این جفت‌ها را پولِ واقعی
 * می‌شمرد: سودِ **هر فروش** هم ورودیِ نقد می‌شد و هم خروجیِ نقد، و مبلغش
 * زیرِ دستهٔ «سایر» به‌عنوان هزینه دیده می‌شد.
 */
fun runCashFlow(rows: List<CashRow>, isInternal: (String) -> Boolean): CashFlow {
    val real = rows.filterNot { isInternal(it.category) }
    val moves = rows.filter { isInternal(it.category) }
    val out = real.filter { it.type == "OUT" }
    return CashFlow(
        income = real.filter { it.type == "IN" }.sumOf { it.amount },
        expense = out.sumOf { it.amount },
        // فقط یک سمتِ هر جفت، وگرنه مبلغ دو برابر دیده می‌شود
        internalMoves = moves.filter { it.type == "OUT" }.sumOf { it.amount },
        byCategory = out
            .groupBy { it.category.ifBlank { "سایر" } }
            .map { (cat, list) -> cat to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
    )
}

fun checkCashFlow(internalMoveCategory: String, isInternal: (String) -> Boolean): List<CheckResult> {
    val s = CheckSink("جریان نقد")

    s.isTrue("دستهٔ انتقالِ داخلی خالی نیست", internalMoveCategory.isNotBlank(),
        "بی نام، هیچ سطری قابلِ تشخیص نیست")
    s.isTrue("دستهٔ انتقالِ داخلی شناخته می‌شود", isInternal(internalMoveCategory),
        "خودِ ثابت با تابعِ تشخیص نمی‌خوانَد")
    s.isTrue("دستهٔ خالی داخلی شمرده نمی‌شود", !isInternal(""),
        "سطرِ بی‌دسته داخلی شمرده شد — هزینه‌های دسته‌بندی‌نشده ناپدید می‌شوند")
    s.isTrue("دستهٔ هزینهٔ عادی داخلی شمرده نمی‌شود", !isInternal("کرایه"),
        "«کرایه» داخلی شمرده شد")

    // ---- یک فروشِ سوددار: همان حالتی که در هر فروش رخ می‌داد ----
    run {
        val rows = listOf(
            CashRow("IN", 10_000, ""),                          // نقدِ فروش
            CashRow("OUT", 4_000, internalMoveCategory),        // سود به فایده
            CashRow("IN", 4_000, internalMoveCategory)
        )
        val f = runCashFlow(rows, isInternal)
        s.eq("ورودیِ نقد فقط پولِ واقعیِ فروش است", 10_000L, f.income)
        s.eq("فروشِ سوددار هیچ خروجِ نقدی نمی‌سازد", 0L, f.expense)
        s.eq("انتقالِ داخلی جدا گزارش می‌شود", 4_000L, f.internalMoves)
        s.isTrue(
            "سودِ فروش در فهرستِ هزینه‌ها ظاهر نمی‌شود",
            f.byCategory.none { it.first == "سایر" },
            "زیر «سایر» نشست: " + f.byCategory.joinToString("، ") { "${it.first}=${it.second}" }
        )
    }

    // ---- هزینهٔ واقعیِ بی‌دسته باید بمانَد ----
    run {
        val rows = listOf(
            CashRow("OUT", 1_500, ""),                          // خرجِ دسته‌بندی‌نشده
            CashRow("OUT", 4_000, internalMoveCategory),
            CashRow("IN", 4_000, internalMoveCategory)
        )
        val f = runCashFlow(rows, isInternal)
        s.eq("خرجِ دسته‌بندی‌نشدهٔ واقعی حذف نمی‌شود", 1_500L, f.expense)
        s.eq("«سایر» فقط خرجِ واقعی را دارد", 1_500L,
            f.byCategory.firstOrNull { it.first == "سایر" }?.second ?: 0L)
    }

    // ---- جمعِ دسته‌ها همیشه باید با خروجیِ نقد بخوانَد ----
    run {
        val rows = listOf(
            CashRow("OUT", 3_000, "کرایه"),
            CashRow("OUT", 2_000, "حقوق کارکنان"),
            CashRow("OUT", 500, ""),
            CashRow("OUT", 9_000, internalMoveCategory),
            CashRow("IN", 9_000, internalMoveCategory),
            CashRow("IN", 50_000, "")
        )
        val f = runCashFlow(rows, isInternal)
        s.eq("جمعِ دسته‌ها با خروجیِ نقد می‌خوانَد", f.expense,
            f.byCategory.sumOf { it.second })
        s.eq("ورودیِ نقد", 50_000L, f.income)
        s.eq("خروجیِ نقد", 5_500L, f.expense)
    }

    // ---- جمعِ خالص نباید عوض شده باشد ----
    // انتقالِ داخلی جفت است، پس در تفاضل خودش را خنثی می‌کرد؛ همان عددی که
    // از قبل درست بود نباید تکان بخورد.
    run {
        var seed = 2718281L
        fun rnd(bound: Int): Long {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((((seed ushr 33).toInt() % bound) + bound) % bound).toLong()
        }
        var netChanged = 0
        var moveLeaked = 0
        var categorySumBroken = 0
        repeat(500) {
            val rows = ArrayList<CashRow>()
            var naiveIn = 0L
            var naiveOut = 0L
            repeat((rnd(20) + 1).toInt()) {
                when (rnd(4)) {
                    0L -> {
                        val a = rnd(60_000) + 1
                        rows += CashRow("IN", a, "")
                        naiveIn += a
                    }
                    1L -> {
                        val a = rnd(20_000) + 1
                        rows += CashRow("OUT", a, "کرایه")
                        naiveOut += a
                    }
                    else -> {
                        // جفتِ انتقالِ داخلی
                        val a = rnd(40_000) + 1
                        rows += CashRow("OUT", a, internalMoveCategory)
                        rows += CashRow("IN", a, internalMoveCategory)
                        naiveIn += a
                        naiveOut += a
                    }
                }
            }
            val f = runCashFlow(rows, isInternal)
            if (f.net != naiveIn - naiveOut) netChanged++
            if (f.byCategory.any { isInternal(it.first) }) moveLeaked++
            if (f.byCategory.sumOf { it.second } != f.expense) categorySumBroken++
        }
        s.eq("۵۰۰ حالتِ تصادفی: جمعِ خالصِ نقد عوض نشد", 0, netChanged)
        s.eq("۵۰۰ حالتِ تصادفی: انتقالِ داخلی به دسته‌ها نفوذ نکرد", 0, moveLeaked)
        s.eq("۵۰۰ حالتِ تصادفی: جمعِ دسته‌ها با خروجیِ نقد می‌خوانَد", 0, categorySumBroken)
    }

    return s.results
}

// =====================================================================
// 18) نامِ کارگر و کارکردِ ماهانه
// =====================================================================

/** یک بازهٔ حضور، همان‌قدر که محاسبهٔ کارکرد از آن می‌داند. */
data class Shift(val checkIn: Long, val checkOut: Long?)

/**
 * کارکردِ یک کارمند از بازه‌هایش — بازتابِ
 * `AttendanceViewModel.monthlyWork`.
 *
 * [dayKey] روزِ تقویمیِ **محلی** را برمی‌گرداند. تقسیمِ سادهٔ زمان بر
 * ۸۶۴۰۰۰۰۰ مرزش UTC است، یعنی ۰۴:۳۰ به وقتِ کابل، و هر ورودِ سرِ صبح روزِ
 * قبل شمرده می‌شد.
 */
fun runWorkSummary(shifts: List<Shift>, dayKey: (Long) -> Any): Pair<Long, Int> {
    val closed = shifts.filter { it.checkOut != null }
    // صفرکَف روی هر بازه، نه روی جمع — وگرنه یک سطرِ خرابِ منفی کارکردِ
    // بازه‌های سالم را کم می‌کند
    val minutes = closed.sumOf {
        (((it.checkOut ?: 0L) - it.checkIn) / 60_000L).coerceAtLeast(0)
    }
    val days = closed.map { dayKey(it.checkIn) }.distinct().size
    return minutes to days
}

fun checkWorkerName(bare: (String) -> String): List<CheckResult> {
    val s = CheckSink("نام کارگر")

    s.eq("برچسبِ معمولی پاک می‌شود", "احمد", bare("[T10] احمد"))
    s.eq("برچسبِ بازرس هم پاک می‌شود", "نجیب", bare("[I2] نجیب"))
    s.eq("نامِ بی‌برچسب دست نمی‌خورد", "احمد", bare("احمد"))
    s.eq("فاصلهٔ اضافی برداشته می‌شود", "احمد", bare("  [T10]   احمد  "))
    s.eq("نامِ چندبخشی کامل می‌مانَد", "احمد شاه کریمی", bare("[T7] احمد شاه کریمی"))
    s.eq("نامِ خالی خالی می‌مانَد", "", bare(""))
    s.eq("فقط فاصله خالی می‌شود", "", bare("   "))
    // براکتِ ناقص یعنی داده‌ی عجیب؛ بریدنش می‌تواند نام را نابود کند
    s.eq("براکتِ بازِ بی‌بسته دست‌نخورده می‌مانَد", "[T10 احمد", bare("[T10 احمد"))
    // برچسبِ تنها بی نام: اگر کورکورانه ببُریم، نام خالی می‌شود و بازهٔ
    // حضور به هیچ‌کس نمی‌چسبد
    s.eq("برچسبِ تنها بی‌نام حذف نمی‌شود", "[T10]", bare("[T10]"))
    s.eq("نامی که خودش براکت دارد سالم می‌مانَد", "احمد [کوچک]", bare("[T3] احمد [کوچک]"))
    // بی‌اثر بودنِ دوباره‌زدن: نامِ پاک‌شده دیگر عوض نمی‌شود
    s.eq("دوباره‌زدن نتیجه را عوض نمی‌کند", bare("[T10] احمد"), bare(bare("[T10] احمد")))

    return s.results
}

fun checkWorkSummary(): List<CheckResult> {
    val s = CheckSink("کارکرد ماهانه")

    val hour = 3_600_000L
    // روزِ تقویمیِ ساختگی با مرزِ محلی: کابل UTC+4:30
    val kabulOffset = 4 * hour + 1_800_000L
    val localDay: (Long) -> Any = { (it + kabulOffset) / 86_400_000L }
    val utcDay: (Long) -> Any = { it / 86_400_000L }

    // ---- بازهٔ ساده ----
    run {
        val (minutes, days) = runWorkSummary(
            listOf(Shift(0L, 8 * hour)), localDay
        )
        s.eq("هشت ساعت = ۴۸۰ دقیقه", 480L, minutes)
        s.eq("یک بازه یعنی یک روز", 1, days)
    }

    // ---- بازهٔ باز شمرده نمی‌شود ----
    run {
        val (minutes, days) = runWorkSummary(
            listOf(Shift(0L, null), Shift(86_400_000L, 86_400_000L + 4 * hour)), localDay
        )
        s.eq("بازهٔ بازِ بی‌خروج در کارکرد نمی‌آید", 240L, minutes)
        s.eq("فقط روزِ بازهٔ بسته شمرده می‌شود", 1, days)
    }

    // ---- سطرِ خرابِ منفی نباید کارکردِ سالم را بخورد ----
    run {
        val shifts = listOf(
            Shift(10 * hour, 18 * hour),        // ۸ ساعتِ سالم
            Shift(20 * hour, 12 * hour)         // خروجِ پیش از ورود
        )
        val (minutes, _) = runWorkSummary(shifts, localDay)
        s.eq("بازهٔ منفی صفر شمرده می‌شود، نه کسرکننده", 480L, minutes)
        s.isTrue("کارکرد هرگز منفی نمی‌شود", minutes >= 0, "کارکردِ منفی: $minutes")
    }

    // ---- مرزِ روز: همان حالتی که با UTC غلط می‌شد ----
    run {
        // ۰۲:۰۰ به وقتِ کابل روزِ دوم = ۲۱:۳۰ UTC روزِ اول
        val kabulTwoAm = 86_400_000L + 2 * hour - kabulOffset
        val shifts = listOf(
            Shift(6 * hour, 14 * hour),         // روزِ اول، صبح
            Shift(kabulTwoAm, kabulTwoAm + 3 * hour)
        )
        val (_, localDays) = runWorkSummary(shifts, localDay)
        val (_, utcDays) = runWorkSummary(shifts, utcDay)
        s.eq("دو روزِ تقویمیِ محلی، دو روز شمرده می‌شود", 2, localDays)
        s.isTrue(
            "مرزِ UTC همین حالت را اشتباه می‌شمرد",
            utcDays != localDays,
            "اگر این ادعا رد شد یعنی حالتِ آزمون دیگر تفاوت را نشان نمی‌دهد " +
                "و باید بازنویسی شود (محلی=$localDays، UTC=$utcDays)"
        )
    }

    // ---- دو بازه در یک روز = یک روز ----
    run {
        val (minutes, days) = runWorkSummary(
            listOf(Shift(6 * hour, 10 * hour), Shift(12 * hour, 17 * hour)), localDay
        )
        s.eq("دو بازه در یک روز جمع می‌شوند", 540L, minutes)
        s.eq("ولی یک روز شمرده می‌شود", 1, days)
    }

    // ---- بی بازه ----
    run {
        val (minutes, days) = runWorkSummary(emptyList(), localDay)
        s.eq("بی بازه، کارکرد صفر است", 0L, minutes)
        s.eq("بی بازه، روزی شمرده نمی‌شود", 0, days)
    }

    return s.results
}

// =====================================================================
// 19) بازیابیِ پشتیبان — نباید همان داده‌ای را که نجات می‌دهد نابود کند
// =====================================================================

/**
 * سنجشِ قاعدهٔ پذیرشِ فایلِ پشتیبان.
 *
 * چرا این مهم‌ترین بررسیِ این بخش است: `fallbackToDestructiveMigration`
 * روشن است. اگر فایلِ خراب یا ساخته‌شده با نسخهٔ جلوترِ اپ جایگزینِ
 * دیتابیسِ زنده شود، Room در اجرای بعدی نمی‌تواند بازش کند و **کلِ دادهٔ
 * کارگاه را بی هیچ پیامی پاک می‌کند**. یعنی خودِ بازیابی — ویژگی‌ای که
 * برای نجاتِ داده ساخته شده — می‌تواند نابودش کند.
 *
 * [verdict] همان تابعِ `BackupArchive.verdict` است و [labelOf] نامِ
 * نتیجه، تا این بررسی به هیچ کلاسِ اندرویدی وابسته نشود.
 */
fun checkRestoreVerdict(
    verdict: (Boolean, Boolean, Int, Int) -> Any,
    labelOf: (Any) -> String,
    appVersion: Int
): List<CheckResult> {
    val s = CheckSink("پذیرشِ پشتیبان")

    fun v(openable: Boolean, integrity: Boolean, fileVersion: Int) =
        labelOf(verdict(openable, integrity, fileVersion, appVersion))

    // ---- پشتیبانِ سالمِ همین نسخه ----
    s.eq("پشتیبانِ سالمِ همین نسخه پذیرفته می‌شود", "OK", v(true, true, appVersion))

    // ---- نسخهٔ عقب‌تر بی‌خطر است: مهاجرت‌ها بالا می‌آورندش ----
    s.eq("پشتیبانِ نسخهٔ قبل پذیرفته می‌شود", "OK", v(true, true, appVersion - 1))
    s.eq("پشتیبانِ خیلی قدیمی هم پذیرفته می‌شود", "OK", v(true, true, 19))

    // ---- نسخهٔ جلوتر باید رد شود، وگرنه Room همه‌چیز را پاک می‌کند ----
    s.eq("پشتیبانِ نسخهٔ جلوتر رد می‌شود", "TOO_NEW", v(true, true, appVersion + 1))
    s.eq("پشتیبانِ خیلی جلوتر هم رد می‌شود", "TOO_NEW", v(true, true, appVersion + 40))

    // ---- خراب ----
    s.eq("فایلی که باز نمی‌شود رد می‌شود", "CORRUPT", v(false, false, appVersion))
    s.eq("فایلی که باز می‌شود ولی سالم نیست رد می‌شود", "CORRUPT", v(true, false, appVersion))
    // نسخهٔ صفر یعنی Room هیچ‌وقت رویش ننشسته — دیتابیسِ ما نیست
    s.eq("دیتابیسِ بی‌نسخه رد می‌شود", "CORRUPT", v(true, true, 0))
    s.eq("نسخهٔ منفی رد می‌شود", "CORRUPT", v(true, true, -3))

    // ---- خرابی بر نسخه مقدم است ----
    // فایلی که هم خراب است و هم جلوتر، باید «خراب» گزارش شود؛ پیامِ
    // «اپ را به‌روز کنید» برای فایلِ خراب گمراه‌کننده است
    s.eq(
        "فایلِ خرابِ جلوتر «خراب» شمرده می‌شود، نه «جلوتر»",
        "CORRUPT",
        v(false, false, appVersion + 5)
    )

    // ---- هیچ نسخهٔ قابلِ قبولی نباید رد شود و برعکس ----
    run {
        var wrongAccept = 0
        var wrongReject = 0
        for (fileVersion in -5..(appVersion + 20)) {
            val got = v(true, true, fileVersion)
            val shouldAccept = fileVersion in 1..appVersion
            if (shouldAccept && got != "OK") wrongReject++
            if (!shouldAccept && got == "OK") wrongAccept++
        }
        s.eq("هیچ پشتیبانِ قابلِ بازیابی رد نمی‌شود", 0, wrongReject)
        s.eq("هیچ پشتیبانِ خطرناکی پذیرفته نمی‌شود", 0, wrongAccept)
    }

    // ---- فایلِ خراب در هیچ نسخه‌ای پذیرفته نمی‌شود ----
    run {
        var accepted = 0
        for (fileVersion in 0..(appVersion + 5)) {
            if (v(false, true, fileVersion) == "OK") accepted++
            if (v(true, false, fileVersion) == "OK") accepted++
        }
        s.eq("فایلِ خراب در هیچ نسخه‌ای پذیرفته نمی‌شود", 0, accepted)
    }

    return s.results
}
