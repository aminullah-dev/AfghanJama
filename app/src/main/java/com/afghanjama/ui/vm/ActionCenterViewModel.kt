package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.STAGE_WARN_DAYS
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.stageDays
import com.afghanjama.ui.nav.Routes
import kotlinx.coroutines.flow.Flow
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

    val ui: StateFlow<ActionCenterUi> = combine(coreAlerts, payrollAlerts) { core, payroll ->
        ActionCenterUi((core + payroll).sortedBy { it.severity.ordinal })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActionCenterUi())

    private companion object {
        const val SHIFT_MS = 8L * 60 * 60 * 1000
    }
}
