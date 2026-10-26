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
 * «دستیار» — خلاصهٔ هوشمندِ کسب‌وکار به زبانِ ساده، از همان مغزها و
 * جریان‌هایی که صفحه‌های دیگر دارند (سود به مشتری/خیاط، سربه‌سر، روندِ
 * فروش، کارمزدِ باز). کاملاً آفلاین و خصوصی — چیزی از دستگاه بیرون نمی‌رود.
 *
 * این پایهٔ «پرسشِ آزاد با هوش مصنوعی» است؛ آن گام، با اینترنت و اجازهٔ
 * صاحبِ کار، بعداً روی همین می‌نشیند.
 */
class AssistantViewModel(repo: Repo, now: Flow<Long> = clockTicks()) : ViewModel() {

    val insights: StateFlow<List<Insight>> =
        combine(
            dashboardStatsFlow(repo, now),
            breakEvenFlow(repo, now),
            repo.observeAllOrders(),
            repo.observeAllAssignments(),
        ) { stats, be, orders, assignments ->
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

            businessBriefing(stats, be, customers, tailors)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** لحنِ یک نکته — برای رنگ و نشانه. */
enum class InsightTone { GOOD, WATCH, INFO }

/** یک نکتهٔ دستیار: یک پرسش و پاسخش به زبانِ ساده. */
data class Insight(val title: String, val body: String, val tone: InsightTone)

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
