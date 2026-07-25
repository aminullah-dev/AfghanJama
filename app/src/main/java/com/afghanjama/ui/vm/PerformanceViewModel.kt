package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.QcRecord
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * کارنامهٔ یک خیاط. امتیاز از دو جزء ساخته می‌شود و هر دو جزء کنارِ
 * امتیاز نشان داده می‌شوند تا ستاره «جعبهٔ سیاه» نباشد.
 */
data class TailorScore(
    val name: String,
    val deliveries: Int,        // تحویل‌های تمام‌شده
    val pieces: Int,            // مجموعِ عدد
    val wages: Long,            // کارمزدِ کسب‌شده
    val inProgress: Int,        // کارِ زیرِ دست
    val avgHours: Long,         // میانگینِ زمانِ تحویل (ساعت)
    val good: Int,
    val fair: Int,
    val poor: Int,
    /** سفارش‌هایی که فقط همین خیاط رویشان کار کرده و نظارت شده‌اند. */
    val judgedOrders: Int,
    val rejectedOrders: Int
) {
    val ratedCount: Int get() = good + fair + poor

    /** میانگینِ کیفیتِ ثبت‌شده (۰..۱۰۰) — null اگر کیفیتی ثبت نشده باشد. */
    val qualityScore: Int?
        get() = if (ratedCount == 0) null
        else ((good * 100 + fair * 60 + poor * 20) / ratedCount)

    /** درصدِ برگشت از نظارت — null اگر سفارشِ قابلِ انتساب نباشد. */
    val rejectPercent: Int?
        get() = if (judgedOrders == 0) null else (rejectedOrders * 100) / judgedOrders

    /** امتیازِ کلی: میانگینِ اجزای موجود — null اگر هیچ داده‌ای نیست. */
    val score: Int?
        get() {
            val parts = listOfNotNull(qualityScore, rejectPercent?.let { 100 - it })
            return if (parts.isEmpty()) null else parts.sum() / parts.size
        }

    /** ۱..۵ ستاره — ۰ یعنی هنوز داده‌ای برای قضاوت نیست. */
    val stars: Int
        get() = score?.let { (it / 20 + if (it % 20 >= 10) 1 else 0).coerceIn(1, 5) } ?: 0
}

/**
 * آمارِ یک ناظر. عمداً امتیاز داده نمی‌شود: ناظری که بیشتر برگشت
 * می‌زند لزوماً بدتر نیست — شاید سخت‌گیرتر است. امتیازدادن به نرخِ
 * برگشت، ناظر را به تأییدِ چشم‌بسته تشویق می‌کند.
 */
data class InspectorStat(
    val name: String,
    val total: Int,
    val approved: Int,
    val rejected: Int
) {
    val rejectPercent: Int get() = if (total == 0) 0 else (rejected * 100) / total
}

data class PerformanceUi(
    val tailors: List<TailorScore> = emptyList(),
    val inspectors: List<InspectorStat> = emptyList(),
    /** سفارش‌های چندخیاطه که از محاسبهٔ نرخِ برگشت کنار گذاشته شدند. */
    val sharedOrdersExcluded: Int = 0
) {
    val hasData: Boolean get() = tailors.isNotEmpty() || inspectors.isNotEmpty()
    val topTailor: TailorScore? get() = tailors.firstOrNull { it.stars > 0 }
}

/**
 * کارنامهٔ کارکنانِ تولید: چه کسی چقدر کار تحویل داده، با چه کیفیتی و
 * چقدر برگشت خورده — همه از دادهٔ موجودِ تحویل‌ها و نظارت.
 */
/**
 * محاسبهٔ مشترکِ کارنامهٔ خیاطان — هم «کارنامهٔ کارکنان» (مدیر) و هم
 * «کارِ من» (خودِ خیاط) از همین تابع می‌خوانند تا هرگز دو عددِ متفاوت
 * به کارگر و مدیر نشان داده نشود.
 *
 * برگشت فقط به سفارشی نسبت داده می‌شود که یک خیاط داشته؛ سفارشِ
 * تقسیم‌شده بینِ چند نفر قابلِ انتساب نیست و کنار گذاشته می‌شود.
 */
fun buildTailorScores(
    assignments: List<SewingAssignment>,
    qc: List<QcRecord>
): List<TailorScore> {
    val rejectedOrderIds = qc.filter { it.result == "REJECTED" }.map { it.orderId }.toSet()
    val judgedOrderIds = qc.map { it.orderId }.toSet()

    val tailorsPerOrder = assignments.groupBy { it.orderId }
        .mapValues { (_, list) -> list.map { it.tailorLabel }.distinct() }
    val soleTailorOf = tailorsPerOrder
        .filterValues { it.size == 1 }
        .mapValues { it.value.first() }

    return assignments
        .groupBy { it.tailorLabel }
        .filterKeys { it.isNotBlank() }
        .map { (name, list) ->
            val done = list.filter { it.status == "DONE" }
            val timed = done.filter { it.doneAt != null && it.doneAt!! > it.createdAt }
            val myOrders = soleTailorOf.filterValues { it == name }.keys
            TailorScore(
                name = name,
                deliveries = done.size,
                pieces = done.sumOf { it.qty },
                wages = done.sumOf { it.totalWage },
                inProgress = list.count { it.status != "DONE" },
                avgHours = if (timed.isEmpty()) 0
                else timed.sumOf { ((it.doneAt ?: 0L) - it.createdAt) / 3_600_000L } / timed.size,
                good = list.count { it.quality == "خوب" },
                fair = list.count { it.quality == "متوسط" },
                poor = list.count { it.quality == "ضعیف" },
                judgedOrders = myOrders.count { it in judgedOrderIds },
                rejectedOrders = myOrders.count { it in rejectedOrderIds }
            )
        }
        .sortedWith(
            compareByDescending<TailorScore> { it.score ?: -1 }
                .thenByDescending { it.pieces }
        )
}

/** تعدادِ سفارش‌های چندخیاطه‌ای که نظارت شده‌اند و قابلِ انتساب نیستند. */
fun countSharedJudgedOrders(
    assignments: List<SewingAssignment>,
    qc: List<QcRecord>
): Int {
    val judged = qc.map { it.orderId }.toSet()
    return assignments.groupBy { it.orderId }
        .mapValues { (_, l) -> l.map { it.tailorLabel }.distinct() }
        .filterValues { it.size > 1 }
        .keys.count { it in judged }
}

class PerformanceViewModel(private val repo: Repo) : ViewModel() {

    val ui: StateFlow<PerformanceUi> = combine(
        repo.observeAllAssignments(),
        repo.observeAllQc()
    ) { assignments, qc ->
        val inspectors = qc
            .groupBy { it.inspector }
            .filterKeys { it.isNotBlank() }
            .map { (name, list) ->
                InspectorStat(
                    name = name,
                    total = list.size,
                    approved = list.count { it.result == "APPROVED" },
                    rejected = list.count { it.result == "REJECTED" }
                )
            }
            .sortedByDescending { it.total }

        PerformanceUi(
            tailors = buildTailorScores(assignments, qc),
            inspectors = inspectors,
            sharedOrdersExcluded = countSharedJudgedOrders(assignments, qc)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerformanceUi())
}
