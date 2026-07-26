package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.STAGE_WARN_DAYS
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.dueDaysLate
import com.afghanjama.ui.format.dueDaysLeft
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.isDueSoon
import com.afghanjama.ui.format.isOverdue
import com.afghanjama.ui.format.stageDays
import com.afghanjama.ui.nav.Routes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** شدتِ هشدار: بحرانی، هشدار، اطلاع. ترتیبِ enum برای مرتب‌سازی استفاده می‌شود. */
enum class AlertSeverity { URGENT, WARN, INFO }

/**
 * یک کارِ ناتمام که نیاز به توجه دارد. از داده‌های موجود محاسبه می‌شود
 * (بدونِ جدولِ جدید) و با یک ضربه کاربر را به محلِ رفعش می‌برد.
 */
data class Alert(
    val id: String,
    val severity: AlertSeverity,
    val icon: String,
    val title: String,
    val detail: String,
    val route: String?
)

/** وضعیتِ مرکزِ هوشمندِ هشدار. */
data class ActionCenterUi(
    val alerts: List<Alert> = emptyList()
) {
    val urgent: Int get() = alerts.count { it.severity == AlertSeverity.URGENT }
    val warn: Int get() = alerts.count { it.severity == AlertSeverity.WARN }
    val total: Int get() = alerts.size
    val allClear: Boolean get() = alerts.isEmpty()
}

/**
 * مرکزِ هوشمندِ هشدار: همهٔ مشکلاتِ کارگاه را یک‌جا و اولویت‌بندی‌شده
 * جمع می‌کند — سفارش‌های معطلِ تولید، کمبودِ مواد، کارمزدِ تسویه‌نشده،
 * طلب از مشتری، بدهی به تأمین‌کننده و کارمندانِ بی‌خروج. کاملاً از روی
 * جریان‌های موجودِ Repo محاسبه می‌شود؛ هیچ مهاجرتِ دیتابیسی لازم نیست.
 */
/** بیشتر از این چند روز ماندنِ کارِ آماده در انبار، یعنی باید زنگ زد. */
private const val DELIVERY_WAIT_DAYS = 3

class ActionCenterViewModel(private val repo: Repo) : ViewModel() {

    private val activeProduction = setOf(
        OrderStatus.IN_STOCK.name, OrderStatus.CUTTING.name, OrderStatus.CUT_DONE.name,
        OrderStatus.SEWING.name, OrderStatus.REVIEW.name
    )

    private fun stageLabel(s: String): String = when (s) {
        OrderStatus.IN_STOCK.name -> "انبار"
        OrderStatus.CUTTING.name -> "برش"
        OrderStatus.CUT_DONE.name -> "برش تمام"
        OrderStatus.SEWING.name -> "دوخت"
        OrderStatus.REVIEW.name -> "بازرسی"
        else -> s
    }

    /** هشدارهای تولید، انبار، کارمزد، بدهی، حضور و طلب. */
    private val coreAlerts: Flow<List<Alert>> = combine(
        repo.observeAllOrders(),
        repo.observePendingWages(),
        repo.observeLedgerBalances(),
        repo.observeMaterialStock(),
        repo.observeAttendance()
    ) { orders, wages, balances, materials, attendance ->
        val now = System.currentTimeMillis()
        val list = buildList {

            // ۱) سفارش‌های معطل در تولید
            val stuck = orders.filter {
                it.status in activeProduction &&
                    stageDays(it.stageChangedAt, it.createdAt) >= STAGE_WARN_DAYS
            }
            if (stuck.isNotEmpty()) {
                val worst = stuck.maxByOrNull { stageDays(it.stageChangedAt, it.createdAt) }!!
                val worstDays = stageDays(worst.stageChangedAt, worst.createdAt)
                add(
                    Alert(
                        id = "stuck_orders",
                        severity = if (worstDays >= STAGE_WARN_DAYS * 2) AlertSeverity.URGENT
                        else AlertSeverity.WARN,
                        icon = "⏳",
                        title = "${stuck.size.fa()} سفارش در تولید معطل مانده",
                        detail = "قدیمی‌ترین: ${worst.orderCode} — ${worstDays.fa()} روز در ${stageLabel(worst.status)}",
                        route = Routes.PRODUCTION_ORDER
                    )
                )
            }

            // ۱.۵) مهلتِ تحویل: گذشته و نزدیک
            val active = orders.filter { it.status in activeProduction }
            val overdue = active.filter { isOverdue(it.dueDate, now) }
            if (overdue.isNotEmpty()) {
                val worst = overdue.maxByOrNull { dueDaysLate(it.dueDate, now) }!!
                add(
                    Alert(
                        id = "overdue_orders",
                        severity = AlertSeverity.URGENT,
                        icon = "📅",
                        title = "${overdue.size.fa()} سفارش از مهلتِ تحویل گذشته",
                        detail = "بدترین: ${worst.orderCode} — ${dueDaysLate(worst.dueDate, now).fa()} روز تأخیر" +
                            (if (worst.customerName.isNotBlank()) " (${worst.customerName})" else ""),
                        route = Routes.PRODUCTION_ORDER
                    )
                )
            }
            val dueSoon = active.filter { isDueSoon(it.dueDate, now) }
            if (dueSoon.isNotEmpty()) {
                val next = dueSoon.minByOrNull { it.dueDate }!!
                add(
                    Alert(
                        id = "due_soon_orders",
                        severity = AlertSeverity.WARN,
                        icon = "⏱",
                        title = "${dueSoon.size.fa()} سفارش نزدیکِ مهلتِ تحویل",
                        detail = "نزدیک‌ترین: ${next.orderCode} — ${dueDaysLeft(next.dueDate, now).fa()} روز مانده",
                        route = Routes.PRODUCTION_ORDER
                    )
                )
            }

            // ۲) کمبودِ موادِ خام
            val low = materials.filter { it.minLevel > 0.0 && it.amount <= it.minLevel }
            if (low.isNotEmpty()) {
                val empty = low.count { it.amount <= 0.0 }
                val names = low.take(3).joinToString("، ") { it.name }
                add(
                    Alert(
                        id = "low_stock",
                        severity = if (empty > 0) AlertSeverity.URGENT else AlertSeverity.WARN,
                        icon = "📦",
                        title = "${low.size.fa()} قلم مواد رو به اتمام",
                        detail = names + if (low.size > 3) " و ${(low.size - 3).fa()} قلم دیگر" else "",
                        route = Routes.PROCUREMENT
                    )
                )
            }

            // ۳) کارمزدِ تسویه‌نشدهٔ خیاطان
            if (wages.isNotEmpty()) {
                val total = wages.sumOf { it.amount }
                val tailors = wages.map { it.tailorLabel }.distinct().size
                add(
                    Alert(
                        id = "wages",
                        severity = AlertSeverity.WARN,
                        icon = "🧵",
                        title = "کارمزدِ تسویه‌نشدهٔ ${tailors.fa()} خیاط",
                        detail = "جمعاً ${total.afn()}",
                        route = Routes.LEDGER
                    )
                )
            }

            // ۴) بدهی به تأمین‌کنندگان (ماندهٔ منفی = ما بدهکاریم)
            val payables = balances.filter { it.type == "SUPPLIER" && it.net < 0 }
            if (payables.isNotEmpty()) {
                val total = payables.sumOf { -it.net }
                add(
                    Alert(
                        id = "payable",
                        severity = AlertSeverity.WARN,
                        icon = "🧾",
                        title = "بدهی به ${payables.size.fa()} تأمین‌کننده",
                        detail = "جمعاً ${total.afn()}",
                        route = Routes.LEDGER
                    )
                )
            }

            // ۵) کارمندانِ هنوز داخل (بیش از یک شیفتِ ۸ ساعته)
            val overtime = attendance.filter {
                it.checkOut == null && (now - it.checkIn) >= SHIFT_MS
            }
            if (overtime.isNotEmpty()) {
                val names = overtime.take(3).joinToString("، ") { it.employee }
                add(
                    Alert(
                        id = "overtime",
                        severity = AlertSeverity.WARN,
                        icon = "🕗",
                        title = "${overtime.size.fa()} کارمند هنوز خروج نزده",
                        detail = "$names (بیش از ۸ ساعت)",
                        route = Routes.ATTENDANCE
                    )
                )
            }

            // ۶) کارِ تمام‌شده‌ای که مشتری هنوز نبرده
            //
            // این حالت پول و جا هر دو را قفل می‌کند: لباس دوخته شده،
            // باقی‌ماندهٔ پولش وصول نشده و انبار هم اشغال است.
            val waiting = orders.filter {
                it.status == OrderStatus.STORED.name && it.customerName.isNotBlank() &&
                    stageDays(it.stageChangedAt, it.createdAt) >= DELIVERY_WAIT_DAYS
            }
            if (waiting.isNotEmpty()) {
                val worst = waiting.maxByOrNull { stageDays(it.stageChangedAt, it.createdAt) }!!
                add(
                    Alert(
                        id = "awaiting_delivery",
                        severity = AlertSeverity.WARN,
                        icon = "📦",
                        title = "${waiting.size.fa()} کارِ آماده را مشتری نبرده",
                        detail = "قدیمی‌ترین: ${worst.customerName} — " +
                            "${stageDays(worst.stageChangedAt, worst.createdAt).fa()} روز در انبار",
                        route = Routes.DELIVERY_QUEUE
                    )
                )
            }

            // ۶) طلب از مشتریان (ماندهٔ مثبت = آن‌ها بدهکارند)
            val receivables = balances.filter { it.type == "CUSTOMER" && it.net > 0 }
            if (receivables.isNotEmpty()) {
                val total = receivables.sumOf { it.net }
                add(
                    Alert(
                        id = "receivable",
                        severity = AlertSeverity.INFO,
                        icon = "💰",
                        title = "طلب از ${receivables.size.fa()} مشتری",
                        detail = "جمعاً ${total.afn()}",
                        route = Routes.LEDGER
                    )
                )
            }
        }
        list
    }

    /**
     * هشدارِ حقوقِ پرداخت‌نشدهٔ ماهِ جاری. جدا از [coreAlerts] است چون
     * combine بیش از پنج جریانِ نوع‌دار نمی‌پذیرد.
     */
    private val payrollAlerts: Flow<List<Alert>> = combine(
        repo.observeStaff(),
        repo.observeSalaryPayments()
    ) { staff, payments ->
        val key = PersianDate.monthKey(System.currentTimeMillis())
        val label = PersianDate.monthLabel(System.currentTimeMillis())
        val salaried = staff.filter { it.monthlySalary > 0 }
        val unpaid = salaried.filter { s ->
            val paid = payments
                .filter { it.employee == s.name && it.periodKey == key }
                .sumOf { it.amount }
            paid < s.monthlySalary
        }
        buildList {
            if (unpaid.isNotEmpty()) {
                val total = unpaid.sumOf { s ->
                    val paid = payments
                        .filter { it.employee == s.name && it.periodKey == key }
                        .sumOf { it.amount }
                    (s.monthlySalary - paid).coerceAtLeast(0)
                }
                add(
                    Alert(
                        id = "salary_unpaid",
                        severity = AlertSeverity.WARN,
                        icon = "👛",
                        title = "حقوقِ $label برای ${unpaid.size.fa()} کارمند پرداخت نشده",
                        detail = "باقی‌مانده ${total.afn()} — ${unpaid.take(3).joinToString("، ") { it.name }}",
                        route = Routes.PAYROLL
                    )
                )
            }
        }
    }

    /**
     * هشدارِ «به‌زودی تمام می‌شود» — بر پایهٔ نرخِ مصرف، نه سطحِ موجودی.
     * مکملِ هشدارِ کمبود است: قلمی می‌تواند بالای حدِ هشدار باشد ولی
     * چون تند مصرف می‌شود زودتر تمام شود. از همان محاسبهٔ صفحهٔ
     * «پیشنهاد خرید» استفاده می‌کند تا دو عددِ متفاوت ندهند.
     */
    private val stockAlerts: Flow<List<Alert>> = combine(
        repo.observeMaterialStock(),
        repo.observeStockMovementsSince(
            System.currentTimeMillis() - BURN_WINDOW_DAYS * 86_400_000L
        )
    ) { materials, movements ->
        val runningOut = buildReorderRows(materials, movements).filter { it.runsOutSoon }
        buildList {
            if (runningOut.isNotEmpty()) {
                val soonest = runningOut.minByOrNull { it.daysOfCover ?: Int.MAX_VALUE }!!
                add(
                    Alert(
                        id = "runout_soon",
                        severity = AlertSeverity.WARN,
                        icon = "🛒",
                        title = "${runningOut.size.fa()} قلم مواد به‌زودی تمام می‌شود",
                        detail = "زودترین: ${soonest.name} — حدود ${(soonest.daysOfCover ?: 0).fa()} روز باقی",
                        route = Routes.PURCHASE_PLAN
                    )
                )
            }
        }
    }

    /**
     * زمانِ آخرین بکاپِ خودکار — از تنظیماتِ دستگاه خوانده و از صفحه
     * تزریق می‌شود (ViewModel به Context دسترسی ندارد). ۰ = هرگز.
     */
    private val _lastBackup = MutableStateFlow(0L)
    fun setLastBackup(millis: Long) { _lastBackup.value = millis }

    /**
     * بکاپِ خودکار روزانه زمان‌بندی شده، اما می‌تواند بی‌صدا متوقف شود —
     * بهینه‌سازیِ باتری، نبودِ فضا، یا خاموش‌ماندنِ طولانیِ گوشی. تا حالا
     * هیچ‌چیز این را نشان نمی‌داد؛ یعنی خرابی دقیقاً وقتی کشف می‌شد که
     * دیگر دیر بود.
     */
    private val backupAlerts: Flow<List<Alert>> = _lastBackup.map { last ->
        val now = System.currentTimeMillis()
        val days = if (last <= 0L) -1L else (now - last) / 86_400_000L
        buildList {
            when {
                last <= 0L -> add(
                    Alert(
                        id = "backup_never",
                        severity = AlertSeverity.WARN,
                        icon = "💾",
                        title = "هنوز هیچ بکاپی گرفته نشده",
                        detail = "بکاپِ خودکار روزانه است؛ اگر تا فردا چیزی ثبت نشد، " +
                            "از تنظیمات یک بکاپِ دستی بگیرید.",
                        route = Routes.SETTINGS
                    )
                )
                days >= 3 -> add(
                    Alert(
                        id = "backup_stale",
                        severity = if (days >= 7) AlertSeverity.URGENT else AlertSeverity.WARN,
                        icon = "💾",
                        title = "بکاپ ${days.fa()} روز است گرفته نشده",
                        detail = "همهٔ اطلاعاتِ کارگاه روی همین گوشی است. " +
                            "از تنظیمات یک بکاپِ دستی بگیرید.",
                        route = Routes.SETTINGS
                    )
                )
            }
        }
    }

    val ui: StateFlow<ActionCenterUi> =
        combine(coreAlerts, payrollAlerts, stockAlerts, backupAlerts) { core, payroll, stock, backup ->
            ActionCenterUi((core + payroll + stock + backup).sortedBy { it.severity.ordinal })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActionCenterUi())

    private companion object {
        const val SHIFT_MS = 8L * 60 * 60 * 1000
    }
}
