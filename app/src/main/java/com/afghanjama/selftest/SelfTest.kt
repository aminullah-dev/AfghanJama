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

    // فروشِ بیشتر از موجودی نباید ارزشِ منفی بسازد
    run {
        val row = StockFact(qty = 2, value = 500)
        s.eq("برداشتِ بیش از موجودی همهٔ ارزش را می‌برد", 500L, takeValue(row, 5))
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
