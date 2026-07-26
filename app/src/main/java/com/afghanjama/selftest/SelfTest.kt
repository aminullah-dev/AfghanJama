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
