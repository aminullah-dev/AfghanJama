package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.BreakEven
import com.afghanjama.data.PartyProfit
import com.afghanjama.data.entities.Order
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs

/**
 * «دستیار» — خلاصهٔ هوشمندِ کسب‌وکار و پاسخ به پرسش‌ها، به زبانِ ساده، از
 * همان مغزها و جریان‌های همین اپ. **کاملاً آفلاین و خصوصی** — هیچ‌چیز از
 * دستگاه بیرون نمی‌رود، هیچ تماسِ اینترنتی نیست. هوشش روی خودِ دفتر است.
 */
class AssistantViewModel(repo: Repo, now: Flow<Long> = clockTicks()) : ViewModel() {

    // عددهای بیشتر برای پاسخ‌ها: نقد، در خطرِ دیرکرد، موادِ رو به اتمام —
    // از همان جریان‌های داشبورد و انبار (combine جدا چون پنج‌تایی می‌شود).
    private val extra: Flow<ExtraFigures> =
        combine(healthFlow(repo, now), forecastFlow(repo, now), reorderRowsFlow(repo, now)) { health, forecast, materials ->
            ExtraFigures(
                cash = health.figures.cash,
                atRiskCount = forecast.atRisk.size,
                lowMaterials = materials.count { it.runsOutSoon },
            )
        }

    val data: StateFlow<AssistantData> =
        combine(
            dashboardStatsFlow(repo, now),
            breakEvenFlow(repo, now),
            repo.observeAllOrders(),
            repo.observeAllAssignments(),
            extra,
        ) { stats, be, orders, assignments, ex ->
            fun costOf(o: Order): Long = o.fabricPrice + o.workCost + o.sewingCost
            val priced = orders.filter { it.agreedPrice > 0 }

            val customers = PartyProfit.summarize(
                priced.map { PartyProfit.Entry(it.customerName, it.qty, it.agreedPrice, costOf(it)) },
                unknown = "بی‌نام",
            )
            val byOrder = assignments.groupBy { it.orderId }
            val sewn = priced.map { o ->
                val sewers = byOrder[o.id.toString()]?.map { it.tailorLabel to it.qty }?.takeIf { it.isNotEmpty() }
                    ?: o.assignedTailor?.takeIf { it.isNotBlank() }?.let { listOf(it to o.qty) }
                    ?: emptyList()
                PartyProfit.SewnOrder(o.agreedPrice, costOf(o), sewers)
            }
            val tailors = PartyProfit.summarize(PartyProfit.tailorEntries(sewn), unknown = "بی‌خیاط")

            AssistantData(
                insights = businessBriefing(stats, be, customers, tailors),
                customers = customers,
                tailors = tailors,
                be = be,
                sales30 = stats.sales30,
                salesMonths = stats.salesMonths,
                openWagesTotal = stats.openWagesTotal,
                oldestPendingWageDays = stats.oldestPendingWageDays,
                cash = ex.cash,
                atRiskCount = ex.atRiskCount,
                lowMaterials = ex.lowMaterials,
                readyPieces = stats.readyPieces,
                salesCount = stats.salesCount,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantData())
}

/** عددهای کمکی که از جریان‌های دیگر می‌آیند. */
private data class ExtraFigures(val cash: Long, val atRiskCount: Int, val lowMaterials: Int)

/** همهٔ چیزی که دستیار برای خلاصه و پاسخ لازم دارد — از دفترِ محلی. */
data class AssistantData(
    val insights: List<Insight> = emptyList(),
    val customers: List<PartyProfit.Row> = emptyList(),
    val tailors: List<PartyProfit.Row> = emptyList(),
    val be: BreakEven.Snapshot? = null,
    val sales30: Long = 0,
    val salesMonths: List<MonthPoint> = emptyList(),
    val openWagesTotal: Long = 0,
    val oldestPendingWageDays: Long = 0,
    val cash: Long = 0,
    val atRiskCount: Int = 0,
    val lowMaterials: Int = 0,
    val readyPieces: Int = 0,
    val salesCount: Int = 0,
)

/** لحنِ یک نکته — برای رنگ و نشانه. */
enum class InsightTone { GOOD, WATCH, INFO }

/** یک نکتهٔ دستیار: یک پرسش و پاسخش به زبانِ ساده. */
data class Insight(val title: String, val body: String, val tone: InsightTone)

/** پرسش‌هایی که دستیار آفلاین بلد است — برای پیشنهاد و راهنمایی. */
val assistantSuggestions: List<String> = listOf(
    "پرسودترین مشتری کیست؟",
    "بهترین خیاط کدام است؟",
    "پولِ نقد چقدر دارم؟",
    "فروشِ این ماه چطور است؟",
    "سربه‌سر شده‌ام؟",
    "کدام سفارش‌ها دیر می‌شود؟",
    "کدام مواد رو به اتمام است؟",
    "کارمزدِ باز چقدر است؟",
)

/**
 * پاسخِ **آفلاین** به یک پرسش — تطبیقِ کلیدواژه روی دفترِ محلی، بی اینترنت.
 * `null` یعنی نفهمید (صفحه پیشنهادها را نشان می‌دهد).
 */
fun answerOffline(query: String, d: AssistantData): String? {
    val q = query.normalizeFa()
    fun has(vararg words: String) = words.any { q.contains(it.normalizeFa()) }

    return when {
        has("مشتری", "خریدار") -> d.customers.firstOrNull()?.takeIf { it.profit > 0 }?.let {
            "ارزشمندترین مشتری‌ات ${it.name} است — ${it.profit.afn()} سود از ${it.count.fa()} سفارش."
        } ?: "هنوز سودِ سفارشی به تفکیکِ مشتری نیست."

        has("خیاط") -> d.tailors.firstOrNull()?.takeIf { it.profit > 0 }?.let {
            "سودآورترین خیاط‌ات ${it.name} است — ${it.profit.afn()} سود."
        } ?: "هنوز سودی به تفکیکِ خیاط نیست."

        has("سربه‌سر", "سر به سر", "سربسر") -> d.be?.takeIf { it.hasData && it.fixedCosts > 0 }?.let { be ->
            if (be.pastBreakEven) "بله — این ماه از سربه‌سر گذشته‌ای؛ از این‌جا سودِ خالص است."
            else be.revenueToGo?.let { "هنوز نه — تا سربه‌سر حدودِ ${it.afn()} فروشِ دیگر مانده (${be.coveredPercent.fa()}٪ پوشیده)." }
                ?: "با حاشیهٔ فعلی سربه‌سر نمی‌شوی؛ قیمت‌ها را ببین."
        } ?: "برای سربه‌سر، اول هزینه‌های ثابتِ ماهانه را در «هزینه‌های ثابت» وارد کن."

        has("کارمزد", "حقوق خیاط") -> if (d.openWagesTotal > 0)
            "کارمزدِ تسویه‌نشده ${d.openWagesTotal.afn()} است؛ قدیمی‌ترینش ${d.oldestPendingWageDays.fa()} روز پیش."
        else "کارمزدِ تسویه‌نشده‌ای نیست."

        has("آماده", "انبار محصول", "موجودی محصول") ->
            "${d.readyPieces.fa()} عدد آمادهٔ فروش در انبارِ محصول داری."

        has("دیرکرد", "دیر می", "عقب", "خطر") -> if (d.atRiskCount > 0)
            "${d.atRiskCount.fa()} سفارش در خطرِ دیرکرد است — در «بارِ کارگاه» ببین کدام‌ها."
        else "با سرعتِ فعلی هیچ سفارشی از مهلتش دیر نمی‌شود."

        has("مواد", "خرید") -> if (d.lowMaterials > 0)
            "${d.lowMaterials.fa()} ماده رو به اتمام است — در «برنامهٔ خرید» پیشنهادِ خرید را ببین."
        else "هیچ ماده‌ای رو به اتمام نیست."

        has("نقد", "صندوق", "پول", "بانک") ->
            "پولِ نقدت (صندوق + بانک + صندوقِ فایده) ${d.cash.afn()} است."

        has("سود", "فایده", "منفعت") -> {
            val profit = d.customers.sumOf { it.profit }
            val revenue = d.customers.sumOf { it.revenue }
            val margin = if (revenue > 0) ((profit * 100) / revenue).toInt() else null
            if (revenue > 0) "سودِ سفارش‌ها ${profit.afn()}" + (margin?.let { " (حاشیه ${it.fa()}٪)." } ?: ".")
            else "هنوز سفارشِ قیمت‌خورده‌ای نیست."
        }

        has("فروش") -> {
            val months = d.salesMonths
            if (months.size >= 2) {
                val cur = months.last(); val prev = months[months.size - 2]
                val pct = if (prev.revenue > 0) ((cur.revenue - prev.revenue) * 100 / prev.revenue).toInt() else null
                "فروشِ این ماه ${cur.revenue.afn()}" +
                    (pct?.let { " — ${if (cur.revenue >= prev.revenue) "بیشتر" else "کمتر"} از ماهِ قبل (${abs(it).fa()}٪)." } ?: ".")
            } else "فروشِ ۳۰ روزِ اخیر ${d.sales30.afn()}."
        }

        else -> null
    }
}

/** یکسان‌سازیِ ی/ک عربی و فاصله، تا تطبیقِ کلیدواژه سخت‌گیر نباشد. */
private fun String.normalizeFa(): String =
    trim().replace('ي', 'ی').replace('ك', 'ک').replace('‌', ' ')

/**
 * خلاصهٔ کسب‌وکار از داده‌های آماده — خالص و قابل‌تست، بی ViewModel.
 *
 * فقط چیزی می‌گوید که داده‌اش هست؛ نکتهٔ بی‌پایه نمی‌سازد. ترتیب از مهم به
 * کم‌اهمیت: پول (سربه‌سر)، روندِ فروش، بهترین مشتری و خیاط، کارمزدِ باز.
 */
fun businessBriefing(
    stats: DashboardStats,
    be: BreakEven.Snapshot?,
    customers: List<PartyProfit.Row>,
    tailors: List<PartyProfit.Row>,
): List<Insight> = buildList {

    // سربه‌سرِ این ماه — فقط وقتی هزینهٔ ثابتی ثبت شده.
    if (be != null && be.hasData && be.fixedCosts > 0) {
        if (be.pastBreakEven) {
            add(Insight(
                "سربه‌سرِ این ماه",
                "از سربه‌سر گذشته‌ای — هزینه‌های ثابتِ این ماه (${be.fixedCosts.afn()}) درآمده و از این‌جا به بعد سودِ خالص است.",
                InsightTone.GOOD,
            ))
        } else {
            val toGo = be.revenueToGo
            add(Insight(
                "سربه‌سرِ این ماه",
                if (toGo != null)
                    "تا سربه‌سر حدودِ ${toGo.afn()} فروشِ دیگر مانده (${be.coveredPercent.fa()}٪ از هزینهٔ ثابت پوشیده شده)."
                else
                    "با حاشیهٔ سودِ فعلی، با فروشِ بیشتر هم سربه‌سر نمی‌شوی — هر فروش ضرر می‌دهد. قیمت‌ها را ببین.",
                InsightTone.WATCH,
            ))
        }
    }

    // روندِ فروش — ماهِ جاری نسبت به ماهِ قبل.
    val months = stats.salesMonths
    if (months.size >= 2) {
        val cur = months[months.size - 1]
        val prev = months[months.size - 2]
        if (prev.revenue > 0 || cur.revenue > 0) {
            val pct = if (prev.revenue > 0) ((cur.revenue - prev.revenue) * 100 / prev.revenue).toInt() else null
            val up = cur.revenue >= prev.revenue
            add(Insight(
                "روندِ فروش",
                "فروشِ این ماه ${cur.revenue.afn()} است" +
                    (pct?.let { " — ${if (up) "بیشتر" else "کمتر"} از ماهِ قبل (${abs(it).fa()}٪)." } ?: " (ماهِ قبل فروشی نبود)."),
                if (up) InsightTone.GOOD else InsightTone.WATCH,
            ))
        }
    }

    // ارزشمندترین مشتری.
    customers.firstOrNull()?.takeIf { it.profit > 0 }?.let { c ->
        add(Insight(
            "ارزشمندترین مشتری",
            "${c.name} بیشترین سود را ساخته — ${c.profit.afn()} از ${c.count.fa()} سفارش. رابطه‌ات را با او نگه دار.",
            InsightTone.INFO,
        ))
    }

    // سودآورترین خیاط.
    tailors.firstOrNull()?.takeIf { it.profit > 0 }?.let { t ->
        add(Insight(
            "سودآورترین خیاط",
            "${t.name} بیشترین سود را برایت ساخته — ${t.profit.afn()}. کارِ خوب را به او بسپار.",
            InsightTone.INFO,
        ))
    }

    // کارمزدِ تسویه‌نشده که کهنه شده.
    if (stats.wageReminderDue && stats.openWagesTotal > 0) {
        add(Insight(
            "کارمزدِ خیاط",
            "کارمزدِ تسویه‌نشده ${stats.openWagesTotal.afn()} است و قدیمی‌ترینش ${stats.oldestPendingWageDays.fa()} روز پیش ثبت شده — وقتِ تسویه است.",
            InsightTone.WATCH,
        ))
    }

    if (isEmpty()) {
        add(Insight(
            "همه‌چیز آرام است",
            "فعلاً نکتهٔ مهمی برای گفتن نیست. وقتی سفارش‌ها و فروش بیشتر شوند، این‌جا خلاصهٔ کسب‌وکارت را می‌گویم.",
            InsightTone.INFO,
        ))
    }
}
