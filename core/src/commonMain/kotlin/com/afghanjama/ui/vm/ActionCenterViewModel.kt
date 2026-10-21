package com.afghanjama.ui.vm

import com.afghanjama.ui.components.signalText
import com.afghanjama.ui.components.restockLine
import com.afghanjama.data.ProductInsights
import com.afghanjama.data.FinancialHealth
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.EventRepeat
import com.afghanjama.util.nowMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.ui.graphics.vector.ImageVector
import com.afghanjama.data.DebtFollowUp
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
    val icon: ImageVector,
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

class ActionCenterViewModel(
    repo: Repo,
    /**
     * دسترسیِ کسی که همین حالا وارد شده — هشدارِ درخواست‌های گوشیِ کارگران
     * فقط برای کسی است که می‌تواند تأییدشان کند ([pendingRequestsAlert]).
     */
    access: () -> Access = { CurrentAccess.value },
    now: Flow<Long> = clockTicks(),
) : ViewModel() {

    /**
     * زمانِ آخرین پشتیبانِ خودکار — از تنظیماتِ دستگاه خوانده و از صفحه
     * تزریق می‌شود (ViewModel به Context دسترسی ندارد). ۰ = هرگز.
     */
    private val lastBackup = MutableStateFlow(0L)
    fun setLastBackup(millis: Long) { lastBackup.value = millis }

    val ui: StateFlow<ActionCenterUi> =
        ActionAlerts(repo, access, lastBackup, now).ui
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActionCenterUi())
}

/**
 * همهٔ قاعده‌های مرکزِ هشدار، بیرون از ViewModel — تا داشبوردِ کامپیوتر همان
 * هشدارها را از **همین** حساب بخوانَد، نه نسخهٔ دوم (بازبینی، بندِ ۲۰).
 *
 * **«الان» از [now] می‌آید و جلو می‌رود.** تا ۱.۱۱ هر قاعده «الان» را فقط
 * وقتی حساب می‌کرد که دفتر عوض می‌شد، و پنجرهٔ مصرفِ مواد یک بار سرِ ساختن؛
 * روی کامپیوتری که روزها باز است «۴ روز معطل» فردا هم ۴ روز می‌ماند (بندِ ۱۸).
 */
class ActionAlerts(
    private val repo: Repo,
    private val access: () -> Access,
    lastBackup: Flow<Long>,
    private val now: Flow<Long>,
) {

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
        repo.observeAllOrders().at(now),
        repo.observePendingWages(),
        repo.observeLedgerBalances(),
        repo.observeMaterialStock(),
        repo.observeAttendance()
    ) { ordersAt, wages, balances, materials, attendance ->
        val (orders, now) = ordersAt
        val list = buildList {

            // ۱) سفارش‌های معطل در تولید
            val stuck = orders.filter {
                it.status in activeProduction &&
                    stageDays(it.stageChangedAt, it.createdAt, now) >= STAGE_WARN_DAYS
            }
            if (stuck.isNotEmpty()) {
                val worst = stuck.maxByOrNull { stageDays(it.stageChangedAt, it.createdAt, now) }!!
                val worstDays = stageDays(worst.stageChangedAt, worst.createdAt, now)
                add(
                    Alert(
                        id = "stuck_orders",
                        severity = if (worstDays >= STAGE_WARN_DAYS * 2) AlertSeverity.URGENT
                        else AlertSeverity.WARN,
                        icon = Icons.Default.HourglassBottom,
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
                        icon = Icons.Default.EventBusy,
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
                        icon = Icons.Default.Schedule,
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
                        icon = Icons.Default.Inventory2,
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
                        icon = Icons.Default.ContentCut,
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
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
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
                        icon = Icons.Default.WatchLater,
                        title = "${overtime.size.fa()} کارمند هنوز خروج نزده",
                        detail = "$names (بیش از ۸ ساعت)",
                        route = Routes.ATTENDANCE
                    )
                )
            }

            // ۶) کارِ آماده‌ای که مشتری نبرده → `deliveryAlerts`

            // ۷) طلب از مشتریان → `followUpAlerts`. «طلب از N مشتری»ِ
            // همیشه‌روشن اینجا بود و برداشته شد: مبلغ را می‌گفت ولی نه
            // اینکه امروز سراغِ کی برو، و ماندهٔ خامِ دفتر را می‌خواند که
            // سفارشِ هنوز‌دوخته‌نشده را هم «طلب» می‌دانست.
        }
        list
    }

    /**
     * پیگیریِ طلبِ امروز — قسطِ گذشته، قولِ شکسته و بدهیِ مانده، یک هشدار.
     *
     * تا دیروز سه هشدار بود: «طلب از N مشتری»، «N قسط گذشته» و «طلبِ N
     * نفر کهنه». هر سه دربارهٔ یک کار بودند — زنگ زدن — و هیچ‌کدام
     * نمی‌گفت به کی. حالا یکی است و به همان فهرستی می‌رود که شماره و
     * دکمهٔ «قول داد» دارد. حساب از `debtPlanFlow` می‌آید، همانی که صفحه
     * می‌خوانَد، تا هشدار «۵ نفر» نگوید و صفحه چهار نفر نشان دهد.
     *
     * وقتی امروز به همه سر زده شد، هشدار خاموش می‌شود: کارِ امروز تمام
     * است، حتی اگر پول هنوز نرسیده.
     */
    private val followUpAlerts: Flow<List<Alert>> =
        debtPlanFlow(repo, now).map { plan ->
            val pending = plan.today.filterNot { it.contactedToday }
            buildList {
                if (pending.isNotEmpty()) {
                    val first = pending.first()
                    val hot = pending.any {
                        it.reason == DebtFollowUp.Reason.BROKEN_PROMISE ||
                            it.reason == DebtFollowUp.Reason.LATE_INSTALLMENT
                    }
                    add(
                        Alert(
                            id = "debt_follow_up",
                            severity = if (hot) AlertSeverity.URGENT else AlertSeverity.WARN,
                            icon = Icons.Default.AccountBalanceWallet,
                            title = "امروز ${pending.size.fa()} نفر را برای طلب پیگیری کنید",
                            detail = "جمعاً ${pending.sumOf { it.debtor.amount }.afn()} — اول: " +
                                "${first.debtor.name} (${first.reason.label})",
                            route = Routes.DEBT_FOLLOW_UP
                        )
                    )
                }
            }
        }

    /**
     * هشدارِ حقوقِ پرداخت‌نشدهٔ ماهِ جاری. جدا از [coreAlerts] است چون
     * combine بیش از پنج جریانِ نوع‌دار نمی‌پذیرد.
     */
    private val payrollAlerts: Flow<List<Alert>> = combine(
        repo.observeStaff().at(now),
        repo.observeSalaryPayments()
    ) { staffAt, payments ->
        val (staff, now) = staffAt
        val key = PersianDate.monthKey(now)
        val label = PersianDate.monthLabel(now)
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
                        icon = Icons.Default.Payments,
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
    private val stockAlerts: Flow<List<Alert>> = reorderRowsFlow(repo, now).map { rows ->
        val runningOut = rows.filter { it.runsOutSoon }
        buildList {
            if (runningOut.isNotEmpty()) {
                val soonest = runningOut.minByOrNull { it.daysOfCover ?: Int.MAX_VALUE }!!
                add(
                    Alert(
                        id = "runout_soon",
                        severity = AlertSeverity.WARN,
                        icon = Icons.Default.AddShoppingCart,
                        title = "${runningOut.size.fa()} قلم مواد به‌زودی تمام می‌شود",
                        detail = "زودترین: ${soonest.name} — حدود ${(soonest.daysOfCover ?: 0).fa()} روز باقی",
                        route = Routes.PURCHASE_PLAN
                    )
                )
            }
        }
    }

    /**
     * حسابی که مانده دارد و ماه‌هاست هیچ اتفاقی در آن نیفتاده.
     *
     * هشدارِ «طلب از N مشتری» از قبل بود ولی فقط **مبلغ** را می‌گفت،
     * و مبلغ به‌تنهایی کاری با کسی نمی‌کند: کارگاهی که هر روز نسیه
     * می‌دهد همیشه طلبکار است و آن هشدار همیشه روشن. هشداری که همیشه
     * روشن باشد دیده نمی‌شود.
     *
     * چیزی که واقعاً تغییرِ رفتار می‌دهد **زمان** است: طلبی که سه ماه
     * تکان نخورده همانی است که باید سراغش رفت. این داده از روزِ اول در
     * دفتر بود و هیچ‌جا خوانده نمی‌شد.
     *
     * فقط طلبِ **ما از دیگران** شمرده می‌شود، نه بدهیِ خودمان: بدهیِ
     * کهنه‌ای که طرف پیگیرش نیست، کارِ امروزِ کارفرما نیست.
     */
    private val staleAlerts: Flow<List<Alert>> = combine(
        repo.observeLedgerBalances().at(now),
        repo.observeAllLedgerEntries()
    ) { balancesAt, entries ->
        val (balances, now) = balancesAt
        val lastAt = entries.groupBy { it.partyType + "|" + it.partyName }
            .mapValues { (_, rows) -> rows.maxOf { it.at } }

        // مشتری‌ها در `followUpAlerts`اند، با سنِ درستِ بدهی؛ اینجا فقط
        // بقیه — کارمند یا تأمین‌کننده‌ای که به ما بدهکار مانده.
        val stale = balances
            .filter { it.net > 0 && it.type != "CUSTOMER" }
            .mapNotNull { p ->
                val at = lastAt[p.type + "|" + p.name] ?: return@mapNotNull null
                val days = ((now - at) / 86_400_000L).toInt()
                if (days >= STALE_DEBT_DAYS) p to days else null
            }
            .sortedByDescending { it.second }

        buildList {
            if (stale.isNotEmpty()) {
                val (worst, worstDays) = stale.first()
                add(
                    Alert(
                        id = "stale_receivable",
                        severity = AlertSeverity.WARN,
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "طلبِ ${stale.size.fa()} نفر بیش از ${STALE_DEBT_DAYS.fa()} روز بی‌حرکت است",
                        detail = "کهنه‌ترین: ${worst.name} — ${worst.net.afn()} از ${worstDays.fa()} روز پیش",
                        route = Routes.LEDGER
                    )
                )
            }
        }
    }


    /**
     * بکاپِ خودکار روزانه زمان‌بندی شده، اما می‌تواند بی‌صدا متوقف شود —
     * بهینه‌سازیِ باتری، نبودِ فضا، یا خاموش‌ماندنِ طولانیِ گوشی. تا حالا
     * هیچ‌چیز این را نشان نمی‌داد؛ یعنی خرابی دقیقاً وقتی کشف می‌شد که
     * دیگر دیر بود.
     *
     * **متن همان جایی را می‌گوید که لمس می‌برد: «تنظیمات».** تا ۱.۱۲.۰ متن
     * «از «پشتیبان و بازیابی»…» بود ولی مسیر `Routes.SETTINGS`؛ آن نام فقط
     * روی کامپیوتر هست (کارتی در همان تنظیمات). روی اندروید بخشِ پشتیبان
     * کارتِ «پشتیبان‌گیری و خروجی داده‌ها» در تنظیمات است، و روی آیفون
     * تنظیمات صریح می‌گوید پشتیبان هنوز آنجا نیست و از کجا بگیرید.
     */
    private val backupAlerts: Flow<List<Alert>> = lastBackup.at(now).map { (last, now) ->
        val days = if (last <= 0L) -1L else (now - last) / 86_400_000L
        buildList {
            when {
                last <= 0L -> add(
                    Alert(
                        id = "backup_never",
                        severity = AlertSeverity.WARN,
                        icon = Icons.Default.CloudOff,
                        title = "هنوز از دفتر پشتیبان گرفته نشده",
                        detail = "پشتیبانِ خودکار روزانه است؛ اگر تا فردا چیزی ثبت نشد، " +
                            "در «تنظیمات»، بخشِ پشتیبان، یک پشتیبانِ دستی بگیرید.",
                        route = Routes.SETTINGS
                    )
                )
                days >= 3 -> add(
                    Alert(
                        id = "backup_stale",
                        severity = if (days >= 7) AlertSeverity.URGENT else AlertSeverity.WARN,
                        icon = Icons.Default.CloudOff,
                        title = "${days.fa()} روز است پشتیبان گرفته نشده",
                        detail = "همهٔ اطلاعاتِ کارگاه روی همین دستگاه است. " +
                            "در «تنظیمات»، بخشِ پشتیبان، یک پشتیبانِ دستی بگیرید.",
                        route = Routes.SETTINGS
                    )
                )
            }
        }
    }

    /**
     * کارِ آماده‌ای که مشتری نبرده — **دو هشدار، نه یکی.**
     *
     * تا دیروز یک هشدار بود و هر روز همان را می‌گفت، چه کارفرما به
     * مشتری خبر داده باشد چه نه. یعنی تنها جایی که «خبر دادم یا نه»
     * نوشته می‌شد، حافظهٔ کارفرما بود.
     *
     * حالا جدا شده‌اند و ترتیبِ شدتشان هم عمدی است: **خبر نداده‌ایم**
     * کاری است که هنوز نکرده‌ایم، پس `WARN`. **خبر داده‌ایم و نیامده**
     * کاری است که کرده‌ایم و جواب نداده، پس `INFO` — یادآوری، نه
     * سرزنش.
     *
     * جدا از [coreAlerts] است چون `combine` بیش از پنج جریانِ نوع‌دار
     * نمی‌پذیرد و آن یکی پنج‌تا دارد.
     */
    private val deliveryAlerts: Flow<List<Alert>> = combine(
        repo.observeAllOrders().at(now),
        repo.observeCustomerNotified()
    ) { ordersAt, notified ->
        val (orders, now) = ordersAt
        val waiting = orders.filter {
            it.status == OrderStatus.STORED.name && it.customerName.isNotBlank() &&
                stageDays(it.stageChangedAt, it.createdAt, now) >= DELIVERY_WAIT_DAYS
        }
        val (told, untold) = waiting.partition { notified.containsKey(it.id.toString()) }
        buildList {
            if (untold.isNotEmpty()) {
                val worst = untold.maxByOrNull { stageDays(it.stageChangedAt, it.createdAt, now) }!!
                add(
                    Alert(
                        id = "awaiting_delivery",
                        severity = AlertSeverity.WARN,
                        icon = Icons.Default.LocalShipping,
                        title = "${untold.size.fa()} کارِ آماده و مشتری خبر ندارد",
                        detail = "قدیمی‌ترین: ${worst.customerName} — " +
                            "${stageDays(worst.stageChangedAt, worst.createdAt, now).fa()} روز در انبار",
                        route = Routes.DELIVERY_QUEUE
                    )
                )
            }
            if (told.isNotEmpty()) {
                val worst = told.maxByOrNull { stageDays(it.stageChangedAt, it.createdAt, now) }!!
                add(
                    Alert(
                        id = "awaiting_pickup",
                        severity = AlertSeverity.INFO,
                        icon = Icons.Default.LocalShipping,
                        title = "${told.size.fa()} مشتری خبر دارد ولی نیامده",
                        detail = "قدیمی‌ترین: ${worst.customerName} — " +
                            "${stageDays(worst.stageChangedAt, worst.createdAt, now).fa()} روز در انبار",
                        route = Routes.DELIVERY_QUEUE
                    )
                )
            }
        }
    }

    /**
     * هزینه‌های ثابتی که این ماه ثبت نشده‌اند.
     *
     * **چرا هشدار لازم است و خودِ صفحه کافی نیست.** کسی که فراموش کرده
     * کرایه را ثبت کند، به صفحهٔ «هزینه‌های ثابت» هم سر نمی‌زند — همان
     * فراموشی هر دو را می‌گیرد. تنها جایی که هر روز دیده می‌شود مرکزِ
     * اقدام است.
     *
     * و چرا `WARN` نه `INFO`: تا وقتی ثبت نشود، **سودِ این ماه بیشتر
     * از واقعیت نشان داده می‌شود**. این عددِ غلط در دفتر است، نه یک
     * یادآوریِ ساده.
     */
    private val recurringAlerts: Flow<List<Alert>> =
        repo.observeRecurringExpenses().reemitOn(now).map { rows ->
            val ym = currentYm()
            val due = rows.filter { it.enabled && it.lastPostedYm < ym }
            buildList {
                if (due.isNotEmpty()) {
                    add(
                        Alert(
                            id = "recurring_due",
                            severity = AlertSeverity.WARN,
                            icon = Icons.Default.EventRepeat,
                            title = "${due.size.fa()} هزینهٔ ثابتِ این ماه ثبت نشده",
                            detail = "جمعاً ${due.sumOf { it.amount }.afn()} — " +
                                "تا ثبت نشود سودِ این ماه بیشتر از واقعیت است",
                            route = Routes.RECURRING
                        )
                    )
                }
            }
        }

    /**
     * وضعِ مالی — همان جریانی که کارتِ «وضعیت مالی» از آن می‌خوانَد.
     *
     * **فقط آنچه کاری می‌خواهد هشدار می‌شود.** نقدی که از بدهی کمتر است
     * ولی طلب‌ها پوششش می‌دهند در بیشترِ کارگاه‌ها حالتِ عادیِ هر ماه
     * است؛ اگر هر روز «هشدار» می‌داد، کارفرما یاد می‌گرفت مرکزِ هشدار را
     * نخواند. پس آن و «کمتر از یک ماه پوشش» فقط `INFO`اند؛ `URGENT`
     * مالِ جایی است که واقعاً پول نمی‌رسد.
     */
    private val financeAlerts: Flow<List<Alert>> =
        healthFlow(repo, now).map { h ->
            h.signals.mapNotNull { sig ->
                val (severity, title) = when (sig.kind) {
                    FinancialHealth.Kind.NET_NEGATIVE ->
                        AlertSeverity.URGENT to "بدهی از کلِ دارایی بیشتر است"
                    FinancialHealth.Kind.DEBT_OVER_CASH_AND_RECEIVABLE ->
                        AlertSeverity.URGENT to "بدهی از نقد و طلب بیشتر است"
                    FinancialHealth.Kind.COVER_SHORT ->
                        AlertSeverity.URGENT to "نقد فقط ${sig.value.fa()} روز هزینه را می‌دهد"
                    FinancialHealth.Kind.SALES_DROP ->
                        AlertSeverity.WARN to "فروش ${sig.value.fa()}٪ افت کرده"
                    FinancialHealth.Kind.DEBT_OVER_CASH ->
                        AlertSeverity.INFO to "نقد از بدهی‌ها کمتر است"
                    FinancialHealth.Kind.COVER_LOW ->
                        AlertSeverity.INFO to "نقد کمتر از یک ماه هزینه را می‌دهد"
                    FinancialHealth.Kind.SALES_RISE -> return@mapNotNull null
                }
                Alert(
                    id = "finance_" + sig.kind.name.lowercase(),
                    severity = severity,
                    icon = if (sig.kind == FinancialHealth.Kind.SALES_DROP) Icons.AutoMirrored.Filled.TrendingDown
                    else Icons.Default.AccountBalance,
                    title = title,
                    detail = signalText(sig),
                    route = Routes.FINANCE
                )
            }
        }

    /**
     * انبارِ محصول — پرفروشِ رو به اتمام و کالای راکد.
     *
     * پرفروشِ رو به اتمام `WARN` است چون کاری است که باید امروز شروع شود:
     * دوخت چند روز طول می‌کشد و تا آن موقع قفسه خالی است. راکد `INFO`
     * است؛ پولِ خوابیده ضرر است ولی فوری نیست.
     */
    private val productAlerts: Flow<List<Alert>> =
        productInsightsFlow(repo, now).map { s ->
            buildList {
                s.restock.firstOrNull()?.let { worst ->
                    add(
                        Alert(
                            id = "restock_bestsellers",
                            severity = AlertSeverity.WARN,
                            icon = Icons.Default.ContentCut,
                            title = "${s.restock.size.fa()} طرحِ پرفروش رو به اتمام است",
                            detail = restockLine(worst),
                            route = Routes.FINISHED_SALES
                        )
                    )
                }
                if (s.dead.isNotEmpty()) {
                    add(
                        Alert(
                            id = "dead_stock",
                            severity = AlertSeverity.INFO,
                            icon = Icons.Default.Inventory2,
                            title = "${s.deadValue.afn()} در کالای راکد خوابیده",
                            detail = "${s.dead.size.fa()} کالا بیش از " +
                                "${ProductInsights.DEAD_DAYS.fa()} روز فروش نرفته — " +
                                "پرارزش‌ترین: ${s.dead.first().name}",
                            route = Routes.FINISHED_SALES
                        )
                    )
                }
            }
        }

    /**
     * سفارش‌هایی که هنوز مهلت دارند ولی با سرعتِ فعلی دیر آماده می‌شوند.
     *
     * هشدارِ «از مهلت گذشته» وقتی روشن می‌شد که دیگر کاری نمی‌شد کرد.
     * این یکی چند روز زودتر می‌گوید — وقتی هنوز می‌شود کار را جابه‌جا
     * کرد، خیاطِ دیگری را آورد، یا به مشتری زودتر خبر داد. `WARN` است نه
     * `URGENT`: پیش‌بینی است، نه اتفاقِ افتاده.
     */
    private val lateForecastAlerts: Flow<List<Alert>> =
        forecastFlow(repo, now).map { f ->
            val risk = f.atRisk
            buildList {
                if (risk.isNotEmpty()) {
                    val worst = risk.first()
                    add(
                        Alert(
                            id = "late_forecast",
                            severity = AlertSeverity.WARN,
                            icon = Icons.Default.Schedule,
                            title = "${risk.size.fa()} سفارش با سرعتِ فعلی دیر آماده می‌شود",
                            detail = "بدترین: ${worst.order.orderCode}" +
                                (if (worst.order.customerName.isNotBlank()) " (${worst.order.customerName})" else "") +
                                " — حدودِ ${worst.lateDays.fa()} روز بعد از مهلت",
                            route = Routes.WORKSHOP_LOAD
                        )
                    )
                }
            }
        }

    /**
     * درخواست‌های گوشیِ کارگران که منتظرِ تأییدند — فقط روی دستگاهِ اصلی پر
     * می‌شود؛ روی گوشیِ کارگر و دستگاهِ تنها همیشه صفر است.
     */
    private val requestAlerts: Flow<List<Alert>> =
        repo.observePendingRequestCount().map { listOfNotNull(pendingRequestsAlert(it, access())) }

    val ui: Flow<ActionCenterUi> =
        combine(
            coreAlerts,
            payrollAlerts,
            stockAlerts,
            backupAlerts,
            // `combine` بیش از پنج جریانِ نوع‌دار نمی‌پذیرد، پس این
            // شش‌تا پیش از رسیدن به آنجا یکی می‌شوند.
            combine(
                staleAlerts, followUpAlerts, deliveryAlerts, recurringAlerts,
                combine(financeAlerts, productAlerts, lateForecastAlerts, requestAlerts) { f, p, l, r -> f + p + l + r }
            ) { stale, inst, deliv, rec, smart ->
                stale + inst + deliv + rec + smart
            }
        ) { core, payroll, stock, backup, rest ->
            ActionCenterUi(
                (core + payroll + stock + backup + rest).sortedBy { it.severity.ordinal }
            )
        }

    private companion object {
        const val SHIFT_MS = 8L * 60 * 60 * 1000

        /**
         * از چند روز بی‌حرکتی، طلب «کهنه» شمرده می‌شود.
         *
         * ۳۰ — همان عددی که صفحهٔ دفتر کل هم به کار می‌برد. یک دورهٔ
         * کاریِ کارگاه: کمتر از آن هنوز جریانِ عادیِ سفارش و تحویل
         * است، بالای آن یعنی چیزی متوقف شده.
         */
        const val STALE_DEBT_DAYS = 30
    }
}

/**
 * «۳ درخواست از گوشیِ کارگران منتظرِ تأیید است» — یا `null`.
 *
 * **چرا هشدار لازم شد.** تا ۱.۹.۱ درخواستی که از گوشیِ کارگر می‌رسید فقط در
 * صفحهٔ «اشتراکِ کارگاه» دیده می‌شد، و آن صفحه روی کامپیوتر سی‌ونهمین بندِ
 * نوار بود. ورود و «تمام شد»ِ کارگر تا تأیید نشود ثبت نمی‌شود؛ پس درخواستی
 * که کسی نمی‌بیند یعنی حضور و کارمزدی که گم می‌شود.
 *
 * فقط برای کسی که دست‌کم یک نوعِ درخواست را می‌تواند تأیید کند
 * ([WorkshopLinkAccess.seesRequests]) — همان قاعدهٔ نشانِ نوارِ کنار.
 * بقیه کاری با آن ندارند و هشدارِ بی‌کار فقط یاد می‌دهد مرکزِ هشدار را نخوانند.
 */
fun pendingRequestsAlert(count: Int, access: Access): Alert? {
    if (count <= 0 || !WorkshopLinkAccess.seesRequests(access)) return null
    return Alert(
        id = "pending_requests",
        severity = AlertSeverity.WARN,
        icon = Icons.Default.Smartphone,
        title = "${count.fa()} درخواست از گوشیِ کارگران منتظرِ تأیید است",
        detail = "ورود، خروج و «تمام شد»ِ کارگر تا تأیید نشود ثبت نمی‌شود — در «کارگاهِ وصل» تأیید یا رد کنید.",
        route = Routes.WORKSHOP_LINK
    )
}

/**
 * پرفروشِ رو به اتمام و کالای راکد، زنده — هشدارِ مرکزِ هشدار و کارتِ
 * داشبورد هر دو از همین (یک منبع برای یک عدد).
 */
fun productInsightsFlow(repo: Repo, now: Flow<Long> = clockTicks()): Flow<ProductInsights.Summary> =
    combine(repo.observeFinishedStock(), repo.observeFinishedSales(), now) { stock, sales, n ->
        ProductInsights.analyze(
            stock.map { ProductInsights.Stock(it.name, it.size, it.qty, it.totalValue, it.updatedAt) },
            sales.map { ProductInsights.Sale(it.productName, it.size, it.qty - it.returnedQty, it.createdAt) },
            n,
        )
    }
