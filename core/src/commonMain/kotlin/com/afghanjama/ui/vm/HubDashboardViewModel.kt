package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.AppVersion
import com.afghanjama.util.nowMillis
import com.afghanjama.data.entities.LanDevice
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.data.PriceAdvisor
import com.afghanjama.data.ProductInsights
import com.afghanjama.work.DeliveryForecast
import com.afghanjama.data.DebtFollowUp
import com.afghanjama.data.FinancialHealth
import com.afghanjama.data.HubDigest
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.BackupPrefs
import com.afghanjama.prefs.Settings
import com.afghanjama.util.withTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * چکیدهٔ کلِ کارگاه به‌صورتِ جریان — داشبوردِ کامپیوتر از این می‌خوانَد، و
 * گوشیِ صاحب‌کار هم روزی می‌تواند.
 *
 * **چرا تابعِ سطحِ بالا و نه ViewModel.** ViewModel فقط وقتی هست که صفحه‌اش
 * باز شده باشد، و چکیده باید برای هر خواننده‌ای یکی باشد (بازبینی، بندِ ۲۰).
 *
 * **یک منبع برای هر عدد.** هیچ عددی اینجا دوباره حساب نمی‌شود: هشدارها از
 * [ActionAlerts]، پیش‌بینی از [forecastFlow]، پیگیریِ طلب از [debtPlanFlow]،
 * مواد از [reorderRowsFlow]، کالا از [productInsightsFlow]، پول از
 * [homeSummaryFlow]، وضعِ مالی از [healthFlow] — همان‌هایی که صفحه‌ها و
 * هشدارها می‌خوانند. پس کارت و هشدارش هرگز دو عدد نمی‌دهند.
 *
 * **«الان» با [now] جلو می‌رود** (هر دقیقه)، و هر جزء تا عددش نرسیده
 * «در حالِ خواندن» است، نه صفر.
 */
@OptIn(FlowPreview::class)
fun hubDigestFlow(
    repo: Repo,
    settings: Settings,
    access: () -> Access = { CurrentAccess.value },
    /** کارگاهِ وصل روشن است؟ — `null` جایی که کارگاهِ وصل نیست (آیفون). */
    hubServing: Flow<Boolean?> = flowOf(null),
    now: Flow<Long> = clockTicks(),
): Flow<HubDigest.Digest> {
    // همهٔ منبع‌ها سرِ همان مرزِ دقیقه جلو می‌روند ([clockTicks]).
    val ticks = now.distinctUntilChanged()

    val alerts = ActionAlerts(
        repo, access,
        lastBackup = ticks.map { BackupPrefs.lastAuto(settings) }.distinctUntilChanged(),
        now = ticks,
    ).ui.map { ui ->
        ui.alerts.map {
            HubDigest.AlertRow(
                id = it.id,
                urgent = it.severity == AlertSeverity.URGENT,
                warn = it.severity == AlertSeverity.WARN,
                title = it.title,
                detail = it.detail,
                route = it.route,
            )
        }
    }

    val inside = repo.observeAttendance().map { list -> list.filter { it.checkOut == null } }
    val money = homeSummaryFlow(repo).map { HubDigest.Money(it.wallet, it.bank, it.profit) }
    val stats = dashboardStatsFlow(repo, ticks)
    val materials = reorderRowsFlow(repo, ticks).map { rows ->
        rows.map { HubDigest.Material(it.name, it.unit, it.stock, it.minLevel, it.daysOfCover, it.runsOutSoon) }
    }

    // هر جریان با `null` شروع می‌شود: کارتی که منبعش هنوز نرسیده «در حالِ
    // خواندن» است، و بقیهٔ کارت‌ها منتظرِ کندترین منبع نمی‌مانند.
    fun <T> Flow<T>.orLoading(): Flow<T?> = map<T, T?> { it }.onStart { emit(null) }

    val a = combine(
        alerts.orLoading(),
        repo.observePendingRequests().orLoading(),
        repo.observeAllOrders().orLoading(),
        inside.orLoading(),
        money.orLoading(),
    ) { al, rq, or, ins, mo -> HubDigest.Inputs(alerts = al, requests = rq, orders = or, inside = ins, money = mo) }

    val b = combine(
        healthFlow(repo, ticks).orLoading(),
        stats.orLoading(),
        debtPlanFlow(repo, ticks).orLoading(),
        forecastFlow(repo, ticks).orLoading(),
        materials.orLoading(),
    ) { h, st, dp, fc, ma -> listOf(h, st, dp, fc, ma) }

    val c = combine(
        productInsightsFlow(repo, ticks).orLoading(),
        saleBookFlow(repo, ticks).orLoading(),
        repo.observeFinishedStock().orLoading(),
        repo.observeLanDevices().orLoading(),
        hubServing.onStart { emit(null) },
    ) { pr, pb, fs, dv, hs -> listOf(pr, pb, fs, dv, hs) }

    return combine(a, b, c, ticks) { base, bl, cl, n ->
        @Suppress("UNCHECKED_CAST")
        val st = bl[1] as DashboardStats?
        val inputs = base.copy(
            health = bl[0] as FinancialHealth.Snapshot?,
            sales30 = st?.sales30,
            openWagesTotal = st?.openWagesTotal,
            openWagesCount = st?.openWagesCount,
            oldestWageAt = st?.oldestPendingWageAt,
            debts = bl[2] as DebtFollowUp.Plan?,
            forecast = bl[3] as DeliveryForecast.Result?,
            materials = bl[4] as List<HubDigest.Material>?,
            products = cl[0] as ProductInsights.Summary?,
            priceBook = cl[1] as PriceAdvisor.Book?,
            finishedStock = cl[2] as List<FinishedStock>?,
            devices = cl[3] as List<LanDevice>?,
            hubServing = cl[4] as Boolean?,
            appVersion = AppVersion.current,
        )
        HubDigest.build(inputs, n, withTime(n, 0, 0))
    }
        // ده جریان روی فهرستِ کاملِ سفارش‌ها و فروش‌ها: یک ثبت چند جریان را
        // پشتِ‌سرِ‌هم تکان می‌دهد؛ ۲۵۰ میلی‌ثانیه صبر و فقط آخرین حساب.
        .debounce(250)
        .conflate()
        .flowOn(Dispatchers.Default)
}

/**
 * داشبوردِ کامپیوتر: چکیده، و تنها کاری که از خودِ داشبورد می‌شود کرد —
 * تأیید یا ردِ درخواستِ گوشیِ کارگر (از همان درِ [RequestDecisions]).
 */
class HubDashboardViewModel(
    private val repo: Repo,
    settings: Settings,
    private val access: () -> Access = { CurrentAccess.value },
    hubServing: Flow<Boolean?> = flowOf(null),
    now: Flow<Long> = clockTicks(),
    startAt: Long = nowMillis(),
) : ViewModel() {

    val digest: StateFlow<HubDigest.Digest> =
        hubDigestFlow(repo, settings, access, hubServing, now)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HubDigest.loading(startAt))

    private val _message = MutableStateFlow<Pair<String, Boolean>?>(null)
    /** پیامِ پس از تأیید یا رد؛ دومی یعنی خطا. */
    val message: StateFlow<Pair<String, Boolean>?> = _message
    fun clearMessage() { _message.value = null }

    fun canDecide(r: SyncRequest): Boolean = WorkshopLinkAccess.canDecide(access(), r.type)

    fun approve(r: SyncRequest) = viewModelScope.launch {
        val err = RequestDecisions.approve(repo, access(), r)
        _message.value = if (err == null) "تأیید و ثبت شد." to false else err to true
    }

    fun reject(r: SyncRequest) = viewModelScope.launch {
        val err = RequestDecisions.reject(repo, access(), r)
        _message.value = if (err == null) "درخواست رد شد." to false else err to true
    }
}
