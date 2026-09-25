package com.afghanjama.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afghanjama.data.repo.Repo
import com.afghanjama.ios.data.openDatabase
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.components.WorkStage
import com.afghanjama.ui.nav.Routes
import com.afghanjama.ui.nav.bottomItemsFor
import com.afghanjama.ui.screens.ActionCenterScreen
import com.afghanjama.ui.screens.AttendanceScreen
import com.afghanjama.ui.screens.AuditScreen
import com.afghanjama.ui.screens.BoardScreen
import com.afghanjama.ui.screens.CustomerDetailScreen
import com.afghanjama.ui.screens.CustomersScreen
import com.afghanjama.ui.screens.CuttingScreen
import com.afghanjama.ui.screens.DailyTradeScreen
import com.afghanjama.ui.screens.DebtFollowUpScreen
import com.afghanjama.ui.screens.DeliveryQueueScreen
import com.afghanjama.ui.screens.DocumentsScreen
import com.afghanjama.ui.screens.FinanceHubScreen
import com.afghanjama.ui.screens.FinishedWarehouseScreen
import com.afghanjama.ui.screens.GuideScreen
import com.afghanjama.ui.screens.HomeDashboardScreen
import com.afghanjama.ui.screens.InventoryScreen
import com.afghanjama.ui.screens.JournalScreen
import com.afghanjama.ui.screens.LedgerScreen
import com.afghanjama.ui.screens.LoginScreen
import com.afghanjama.ui.screens.MasterDataScreen
import com.afghanjama.ui.screens.MaterialWarehouseScreen
import com.afghanjama.ui.screens.MoneyMoveScreen
import com.afghanjama.ui.screens.MyWorkScreen
import com.afghanjama.ui.screens.NewSaleScreen
import com.afghanjama.ui.screens.OrderDetailScreen
import com.afghanjama.ui.screens.OrderSearchScreen
import com.afghanjama.ui.screens.PayrollScreen
import com.afghanjama.ui.screens.PerformanceScreen
import com.afghanjama.ui.screens.ProcurementScreen
import com.afghanjama.ui.screens.ProductionOrderScreen
import com.afghanjama.ui.screens.PurchasePlanScreen
import com.afghanjama.ui.screens.PurchaseReturnScreen
import com.afghanjama.ui.screens.QuoteCalculatorScreen
import com.afghanjama.ui.screens.RecurringExpenseScreen
import com.afghanjama.ui.screens.ReportsScreen
import com.afghanjama.ui.screens.ReviewScreen
import com.afghanjama.ui.screens.SampleWorkshopScreen
import com.afghanjama.ui.screens.SelfTestScreen
import com.afghanjama.ui.screens.SewingScreen
import com.afghanjama.ui.screens.ShopProfileScreen
import com.afghanjama.ui.screens.StockLedgerScreen
import com.afghanjama.ui.screens.WorkshopLoadScreen
import com.afghanjama.ui.vm.ActionCenterViewModel
import com.afghanjama.ui.vm.AttendanceViewModel
import com.afghanjama.ui.vm.AuditViewModel
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.BoardViewModel
import com.afghanjama.ui.vm.BreakTimeViewModel
import com.afghanjama.ui.vm.CustomerDetailViewModel
import com.afghanjama.ui.vm.CustomersViewModel
import com.afghanjama.ui.vm.CuttingViewModel
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.DebtFollowUpViewModel
import com.afghanjama.ui.vm.DeliveryQueueViewModel
import com.afghanjama.ui.vm.DocumentsViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.FinishedSaleViewModel
import com.afghanjama.ui.vm.HomeViewModel
import com.afghanjama.ui.vm.InventoryViewModel
import com.afghanjama.ui.vm.JournalViewModel
import com.afghanjama.ui.vm.LedgerViewModel
import com.afghanjama.ui.vm.MasterDataViewModel
import com.afghanjama.ui.vm.MoneyMoveViewModel
import com.afghanjama.ui.vm.MyWorkViewModel
import com.afghanjama.ui.vm.NewSaleViewModel
import com.afghanjama.ui.vm.OrderDetailViewModel
import com.afghanjama.ui.vm.OrderSearchViewModel
import com.afghanjama.ui.vm.PayrollViewModel
import com.afghanjama.ui.vm.PerformanceViewModel
import com.afghanjama.ui.vm.Permissions
import com.afghanjama.ui.vm.ProcurementViewModel
import com.afghanjama.ui.vm.ProductionViewModel
import com.afghanjama.ui.vm.PurchasePlanViewModel
import com.afghanjama.ui.vm.PurchaseReturnViewModel
import com.afghanjama.ui.vm.RecurringExpenseViewModel
import com.afghanjama.ui.vm.ReportsViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SampleWorkshopViewModel
import com.afghanjama.ui.vm.SelfTestViewModel
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.ui.vm.UsersViewModel
import com.afghanjama.ui.vm.RouteAccess
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.screens.UsersScreen
import com.afghanjama.ui.components.NoAccessNotice
import com.afghanjama.prefs.Settings
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.vm.WarehouseViewModel
import com.afghanjama.ui.vm.WorkshopLoadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * پوستهٔ نسخهٔ آیفون: ورود، دفتر، و پیمایش بینِ صفحه‌ها.
 *
 * نوارِ پایین همان `bottomItemsFor`ِ `:core` است که اندروید هم از آن
 * می‌خوانَد — پس اگر روزی خانه‌ای اضافه یا کم شود، هر دو سکو با هم
 * عوض می‌شوند.
 *
 * **مقصدهایی که هنوز نیامده‌اند صریح می‌گویند نیامده‌اند.** جای دکمهٔ
 * بی‌کار یا صفحهٔ خالی، یک جملهٔ روشن: کاربر باید بداند که چیزی نیست،
 * نه اینکه فکر کند خراب است. و «هنوز نیامده» اینجا معنیِ دقیقی دارد:
 * صفحه‌اش در `:app` (یا `jvmAndroidMain`) است، نه در `commonMain` —
 * یعنی برای آیفون اصلاً کامپایل نمی‌شود. هر صفحه‌ای که در `commonMain`
 * باشد اینجا سیم‌کشی شده است.
 */

/** وضعیتِ باز شدنِ دفتر — تا پیش از آن نباید هیچ صفحه‌ای رسم شود. */
private sealed interface Ledger {
    data object Opening : Ledger
    data class Ready(val repo: Repo) : Ledger
    data class Failed(val message: String) : Ledger
}

/**
 * پروفایلِ کارگاه — `Routes` ثابتی برایش ندارد.
 *
 * روی اندروید این فرم داخلِ خودِ صفحهٔ تنظیمات است و مقصدِ جدا نمی‌خواهد؛
 * روی ویندوز یک `Section` است، نه یک `Route`. پس افزودنِ یک ثابت به
 * `Routes`ِ مشترک فقط برای آیفون، به دو سکوی دیگر مسیری می‌داد که
 * هیچ‌کدام رسیدگی‌اش نمی‌کنند. محلی می‌مانَد تا روزی که هر سه لازمش
 * داشته باشند.
 */
private const val SHOP_PROFILE = "ios_shop_profile"

@Composable
internal fun IosShell(auth: AuthViewModel) {
    val ui by auth.ui.collectAsState()
    var ledger by remember { mutableStateOf<Ledger>(Ledger.Opening) }

    /*
     * دفتر **بعد از ورود** باز می‌شود، نه سرِ راه‌اندازی.
     *
     * باز کردنش فایل می‌سازد و اسکیما را می‌چیند؛ کاربری که هنوز رمز
     * نگذاشته دلیلی ندارد این هزینه را بدهد. همان ترتیبی که ویندوز
     * دارد.
     */
    LaunchedEffect(ui.isLoggedIn) {
        if (!ui.isLoggedIn || ledger !is Ledger.Opening) return@LaunchedEffect
        ledger = runCatching { withContext(Dispatchers.Default) { Repo(openDatabase()) } }
            .fold(
                onSuccess = { Ledger.Ready(it) },
                // پیام خام نشان داده می‌شود و بلعیده نمی‌شود: اگر دفتر
                // باز نشد، کارگاه باید بداند چرا، نه اینکه صفحهٔ خالی
                // ببیند.
                onFailure = { Ledger.Failed(it.message ?: it.toString()) },
            )
    }

    if (!ui.isLoggedIn) {
        LoginScreen(vm = auth, onLoggedIn = {})
        return
    }

    when (val l = ledger) {
        is Ledger.Opening -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }

        is Ledger.Failed -> Message("دفترِ کارگاه باز نشد:\n${l.message}")

        is Ledger.Ready -> LoggedIn(l.repo, ui.role, auth, ui.access)
    }
}

@Composable
private fun Message(text: String) = Box(Modifier.fillMaxSize(), Alignment.Center) {
    Text(
        text,
        Modifier.padding(32.dp),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

/**
 * ViewModelهای این نشست — به **دفتر** بسته، نه به صفحه.
 *
 * همان تصمیمی که `AppNav` گرفت و توضیحش را نوشت: اگر هر مقصد
 * ViewModelِ خودش را می‌ساخت، فرمِ نیمه‌پرشده سرِ برگشت خالی می‌شد.
 *
 * **`by lazy` و نه ساختِ یک‌جا.** تا دیروز شش ViewModel بود و همه را
 * می‌شد سرِ ورود ساخت؛ حالا سی‌وچند تاست و ساختِ همه یعنی هزینهٔ سی
 * صفحه برای کاربری که شاید سه‌تا را باز کند. `by lazy` هر کدام را سرِ
 * اولین استفاده می‌سازد و بعد نگهش می‌دارد — یعنی همان طولِ عمر، بی
 * آن هزینه.
 *
 * روی اندروید و ویندوز این کار را `viewModel { }` می‌کند؛ نسخهٔ
 * چندسکویی‌اش (`lifecycle-viewmodel-compose`) برای iOS منتشر نشده، پس
 * اینجا دست‌ساز است.
 */
private class Vms(repo: Repo, settings: Settings) {
    val action by lazy { ActionCenterViewModel(repo) }
    val audit by lazy { AuditViewModel(repo) }
    val board by lazy { BoardViewModel(repo) }
    val customerDetail by lazy { CustomerDetailViewModel(repo) }
    val customers by lazy { CustomersViewModel(repo) }
    val dashboard by lazy { DashboardViewModel(repo) }
    val deliveryQueue by lazy { DeliveryQueueViewModel(repo) }
    val debtFollowUp by lazy { DebtFollowUpViewModel(repo) }
    val documents by lazy { DocumentsViewModel(repo) }
    val finance by lazy { FinanceViewModel(repo) }
    val finishedSale by lazy { FinishedSaleViewModel(repo) }
    val home by lazy { HomeViewModel(repo) }
    val inventory by lazy { InventoryViewModel(repo) }
    val journal by lazy { JournalViewModel(repo) }
    val ledger by lazy { LedgerViewModel(repo) }
    val master by lazy { MasterDataViewModel(repo) }
    val moneyMove by lazy { MoneyMoveViewModel(repo) }
    val myWork by lazy { MyWorkViewModel(repo) }
    val newSale by lazy { NewSaleViewModel(repo) }
    val orderDetail by lazy { OrderDetailViewModel(repo) }
    val payroll by lazy { PayrollViewModel(repo) }
    val performance by lazy { PerformanceViewModel(repo) }
    val procurement by lazy { ProcurementViewModel(repo) }
    val production by lazy { ProductionViewModel(repo) }
    val purchasePlan by lazy { PurchasePlanViewModel(repo) }
    val purchaseReturn by lazy { PurchaseReturnViewModel(repo) }
    val reports by lazy { ReportsViewModel(repo) }
    val review by lazy { ReviewViewModel(repo) }
    val sampleWorkshop by lazy { SampleWorkshopViewModel(repo) }
    val users by lazy { UsersViewModel(settings, repo) }
    val search by lazy { OrderSearchViewModel(repo) }
    val selfTest by lazy { SelfTestViewModel(repo) }
    val sewing by lazy { SewingViewModel(repo) }
    val warehouse by lazy { WarehouseViewModel(repo) }
    val workshopLoad by lazy { WorkshopLoadViewModel(repo) }
    val recurring by lazy { RecurringExpenseViewModel(repo) }
    val cutting by lazy { CuttingViewModel(repo) }
    val attendance by lazy { AttendanceViewModel(repo) }
    val breakTime by lazy { BreakTimeViewModel(repo) }
}

@Composable
private fun LoggedIn(repo: Repo, role: UserRole, auth: AuthViewModel, access: Access) {
    val nav = rememberNavStack()
    val items = remember(role, access) { bottomItemsFor(role, access) }
    val settings = LocalSettings.current
    val vm = remember(repo) { Vms(repo, settings) }

    val back = { nav.back() }
    val go = { route: String -> nav.go(route) }

    /*
     * چیپ‌های مرحله (برش / دوخت / نظارت) جایگزین می‌شوند، نه انباشته —
     * دلیلش در `IosNavStack.replace` نوشته شده.
     */
    val goStage = { stage: WorkStage ->
        nav.replace(
            when (stage) {
                WorkStage.CUT -> Routes.CUTTING
                WorkStage.SEW -> Routes.SEWING
                WorkStage.CHECK -> Routes.REVIEW
            }
        )
    }

    /*
     * مقصدهای آرگومان‌دار — «سفارش شمارهٔ فلان» و «مشتریِ فلان».
     *
     * روی اندروید `NavType` این کار را می‌کند؛ اینجا مقصد یک رشته است
     * و آرگومان بعد از `/` می‌آید، همان قالبی که `AppNav` هم برای
     * `navigate` می‌سازد. پس رشتهٔ مقصد بینِ دو سکو یکی می‌مانَد.
     */
    val base = nav.current.substringBefore('/')
    val arg = nav.current.substringAfter('/', "")

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        // `base` و نه مسیرِ کامل: صفحهٔ «سفارش شمارهٔ
                        // فلان» هم باید خانهٔ «سفارش‌ها» را روشن نگه
                        // دارد.
                        selected = item.owns(base),
                        onClick = { nav.switchTab(item.route) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            // پردهٔ «دسترسی ندارید» — همان قاعدهٔ اندروید (`RouteAccess`).
            if (!RouteAccess.canOpen(access, base)) {
                NoAccessNotice(onHome = { nav.switchTab(Routes.HOME) })
            } else when (base) {
                Routes.HOME -> HomeDashboardScreen(
                    vm = vm.home,
                    actionVm = vm.action,
                    isManager = role == UserRole.MANAGER,
                    // همان قاعده‌ای که اندروید به کار می‌برد، نه
                    // «مدیر یا هیچ‌کس»: فروشنده هم مشتری می‌بیند و
                    // مسئولِ خرید هم مواد می‌خرد.
                    canSeeCustomers = Permissions.canSeeCustomers(role),
                    canBuyMaterial = Permissions.canBuyMaterial(role),
                    onGoActionCenter = { go(Routes.ACTION_CENTER) },
                    onGoProcurement = { go(Routes.PROCUREMENT) },
                    onGoWarehouse = { go(Routes.WAREHOUSE) },
                    onGoStockLedger = { go(Routes.STOCK_LEDGER) },
                    onGoProduction = { go(Routes.PRODUCTION_ORDER) },
                    onGoFinishedSales = { go(Routes.FINISHED_SALES) },
                    onGoDeliveryQueue = { go(Routes.DELIVERY_QUEUE) },
                    onGoDebtFollowUp = { go(Routes.DEBT_FOLLOW_UP) },
                    onGoAttendance = { go(Routes.ATTENDANCE) },
                    onGoPayroll = { go(Routes.PAYROLL) },
                    onGoPerformance = { go(Routes.PERFORMANCE) },
                    onGoPurchasePlan = { go(Routes.PURCHASE_PLAN) },
                    onGoMyWork = { go(Routes.MY_WORK) },
                    onGoPay = { go(Routes.PAY) },
                    onGoReceive = { go(Routes.RECEIVE) },
                    onGoDailyTrade = { go(Routes.DAILY_TRADE) },
                    onGoCustomers = { go(Routes.CUSTOMERS) },
                    onGoFinance = { go(Routes.FINANCE) },
                    onGoLedger = { go(Routes.LEDGER) },
                    onGoDocuments = { go(Routes.DOCUMENTS) },
                    onGoReports = { go(Routes.REPORTS) },
                    onGoAudit = { go(Routes.AUDIT) },
                    onGoSearch = { go(Routes.SEARCH) },
                    onGoSettings = { go(Routes.SETTINGS) },
                    onGoQuoteCalc = { go(Routes.QUOTE_CALC) },
                    onGoWorkshopLoad = { go(Routes.WORKSHOP_LOAD) },
                    onGoRecurring = { go(Routes.RECURRING) },
                    onGoGuide = { go(Routes.GUIDE) },
                    onGoWorkshopLink = { go(Routes.WORKSHOP_LINK) },
                    onGoBoard = { go(Routes.BOARD) },
                    can = { access.has(it) },
                )

                Routes.ACTION_CENTER -> ActionCenterScreen(
                    vm = vm.action,
                    // مرکزِ هشدار خودش مقصد می‌دهد؛ همان رشته‌های
                    // `Routes` است، پس مستقیم به پشته می‌رود.
                    onNavigate = { go(it) },
                    onBack = back,
                )

                Routes.INVENTORY -> InventoryScreen(
                    vm = vm.inventory,
                    financeVm = vm.finance,
                    role = role,
                    onGoStartProduction = { go(Routes.PRODUCTION_ORDER) },
                    onGoWallet = { nav.switchTab(Routes.FINANCE) },
                    onGoSettings = { go(Routes.SETTINGS) },
                    onGoSearch = { go(Routes.SEARCH) },
                    onGoStock = { go(Routes.WAREHOUSE) },
                    onGoCutting = { go(Routes.CUTTING) },
                    onOpenDetail = { o -> go("${Routes.ORDER_DETAIL}/${o.id}") },
                    onBack = back,
                )

                Routes.PRODUCTION_ORDER -> ProductionOrderScreen(
                    vm = vm.production,
                    onBack = back,
                    onGoProcurement = { go(Routes.PROCUREMENT) },
                )

                Routes.ORDER_DETAIL -> OrderDetailScreen(
                    vm = vm.orderDetail,
                    orderIdText = arg.ifEmpty { null },
                    canReturnSale = Permissions.canReturnSale(role),
                    canEdit = Permissions.canEditOrder(role),
                    onBack = back,
                )

                Routes.SEARCH -> OrderSearchScreen(
                    vm = vm.search,
                    onBack = back,
                    onOpenDetail = { o -> go("${Routes.ORDER_DETAIL}/${o.id}") },
                )

                Routes.DELIVERY_QUEUE -> DeliveryQueueScreen(vm = vm.deliveryQueue, onBack = back)
                Routes.DEBT_FOLLOW_UP -> DebtFollowUpScreen(
                    vm = vm.debtFollowUp,
                    onBack = back,
                    onOpenCustomer = { id -> go("${Routes.CUSTOMER_DETAIL}/$id") }
                )

                /*
                 * «کارگاه» — سه مرحلهٔ برش و دوخت و نظارت.
                 *
                 * دوخت و نظارت در `commonMain`اند و همین‌جا باز
                 * می‌شوند. **برش نه** — `CuttingScreen` تنها صفحه‌ای از
                 * این سه است که هنوز در `:app` مانده، و ویندوز هم
                 * ندارَدش.
                 */
                Routes.SEWING -> SewingScreen(
                    vm = vm.sewing,
                    onBack = back,
                    onGoReview = { go(Routes.REVIEW) },
                    onGoStage = goStage,
                )

                Routes.REVIEW -> ReviewScreen(
                    vm = vm.review,
                    onBack = back,
                    onGoSewing = { go(Routes.SEWING) },
                    onGoStage = goStage,
                )

                /*
                 * برش حالا در `commonMain` است (از همان کاری که آن را به
                 * ویندوز رساند)، پس روی آیفون هم باز می‌شود.
                 *
                 * **اسکنرِ QR اینجا نیست و این حفره نیست:** آیفون
                 * `CodeScanner` ندارد، پس دکمهٔ اسکن نشان داده نمی‌شود و
                 * ورودِ دستیِ کد — که از قبل بود — تنها راه است. همان
                 * رفتاری که ویندوز دارد.
                 */
                Routes.CUTTING -> CuttingScreen(
                    vm = vm.cutting,
                    onBack = back,
                    onGoSewing = { go(Routes.SEWING) },
                    onGoStage = goStage,
                )

                Routes.MY_WORK -> {
                    // برچسبِ خیاط‌ها از همان جایی می‌آید که روی اندروید
                    // و ویندوز می‌آمد — خیاط‌ها و ناظرها با هم.
                    val tailors by vm.master.tailors.collectAsState()
                    val inspectors by vm.master.inspectors.collectAsState()
                    MyWorkScreen(
                        vm = vm.myWork,
                        tailorLabels = (tailors.map { "[${it.code}] ${it.name}" } +
                            inspectors.map { "[${it.code}] ${it.name}" }).distinct(),
                        onBack = back,
                    )
                }

                Routes.FINISHED_SALES -> FinishedWarehouseScreen(
                    vm = vm.finishedSale,
                    onBack = back,
                    onOpenInvoice = { go(Routes.NEW_SALE) },
                )

                Routes.NEW_SALE -> NewSaleScreen(vm = vm.newSale, onBack = back)

                Routes.CUSTOMERS -> CustomersScreen(
                    vm = vm.customers,
                    onBack = back,
                    onOpenCustomer = { id -> go("${Routes.CUSTOMER_DETAIL}/$id") },
                )

                Routes.CUSTOMER_DETAIL -> CustomerDetailScreen(
                    vm = vm.customerDetail,
                    customerId = arg.toLongOrNull() ?: 0L,
                    onBack = back,
                    onOpenOrder = { oid -> go("${Routes.ORDER_DETAIL}/$oid") },
                )

                Routes.PROCUREMENT -> ProcurementScreen(vm = vm.procurement, onBack = back)

                Routes.PURCHASE_PLAN -> PurchasePlanScreen(
                    vm = vm.purchasePlan,
                    onGoProcurement = { go(Routes.PROCUREMENT) },
                    onBack = back,
                )

                Routes.PURCHASE_RETURN -> PurchaseReturnScreen(vm = vm.purchaseReturn, onBack = back)

                Routes.WAREHOUSE -> MaterialWarehouseScreen(
                    vm = vm.warehouse,
                    // همان قاعدهٔ نقش‌ها، نه `true`ِ ثابت.
                    canAdjust = Permissions.canAdjustMaterial(role),
                    onBack = back,
                )

                // همان ViewModelِ انبار؛ این صفحه فقط گردشِ آن را نشان می‌دهد.
                Routes.STOCK_LEDGER -> StockLedgerScreen(vm = vm.warehouse, onBack = back)

                Routes.DAILY_TRADE -> DailyTradeScreen(
                    onGoPurchase = { go(Routes.PROCUREMENT) },
                    onGoSale = { go(Routes.NEW_SALE) },
                    onGoPurchaseReturn = { go(Routes.PURCHASE_RETURN) },
                    onGoSaleReturn = { go(Routes.FINISHED_SALES) },
                    onBack = back,
                )

                Routes.FINANCE -> FinanceHubScreen(
                    financeVm = vm.finance,
                    dashboardVm = vm.dashboard,
                    onBack = back,
                )

                Routes.PAY -> MoneyMoveScreen(
                    vm = vm.moneyMove,
                    startAsPayment = true,
                    onBack = back,
                )

                Routes.RECEIVE -> MoneyMoveScreen(
                    vm = vm.moneyMove,
                    startAsPayment = false,
                    onBack = back,
                )

                Routes.LEDGER -> LedgerScreen(vm = vm.ledger, onBack = back)

                Routes.JOURNAL -> JournalScreen(vm = vm.journal, onBack = back)

                Routes.REPORTS -> ReportsScreen(
                    vm = vm.reports,
                    onBack = back,
                    onGoJournal = { go(Routes.JOURNAL) },
                )

                Routes.AUDIT -> AuditScreen(vm = vm.audit, onBack = back)

                /*
                 * `qr` داده نمی‌شود و پیش‌فرضش `null` است.
                 *
                 * ساختِ ماتریس پرتابل است ولی کشیدنش نه — اندروید
                 * `Bitmap` دارد و ویندوز `BufferedImage`. سهمِ iOS
                 * (`UIImage`) هنوز نوشته نشده، و خودِ صفحه بی آن کارش
                 * را می‌کند.
                 */
                Routes.DOCUMENTS -> DocumentsScreen(vm = vm.documents, onBack = back)

                Routes.PAYROLL -> PayrollScreen(vm = vm.payroll, onBack = back)

                Routes.PERFORMANCE -> PerformanceScreen(vm = vm.performance, onBack = back)

                Routes.BOARD -> BoardScreen(vm = vm.board, onBack = back)

                Routes.WORKSHOP_LOAD -> WorkshopLoadScreen(vm = vm.workshopLoad, onBack = back)

                Routes.RECURRING -> RecurringExpenseScreen(vm = vm.recurring, onBack = back)

                Routes.QUOTE_CALC -> QuoteCalculatorScreen(onBack = back)

                Routes.GUIDE -> GuideScreen(onBack = back)

                Routes.MASTER -> MasterDataScreen(vm = vm.master, onBack = back)

                Routes.SELF_TEST -> SelfTestScreen(vm = vm.selfTest, onBack = back)

                Routes.SAMPLE_WORKSHOP -> SampleWorkshopScreen(vm = vm.sampleWorkshop, onBack = back)

                Routes.USERS -> UsersScreen(vm = vm.users, onBack = back)

                SHOP_PROFILE -> ShopProfileScreen(financeVm = vm.finance, onBack = back)

                Routes.SETTINGS -> IosSettings(
                    role = role,
                    onGo = go,
                    onLogout = { auth.logout() },
                    onBack = back,
                )

                /*
                 * «حضور و غیاب» — `AttendanceScreen` در `:app` است.
                 * ViewModelش در `:core` هست، پس آوردنش کارِ کوچکی است،
                 * ولی کارِ این کامیت نیست.
                 */
                /*
                 * حضور و غیاب هم مشترک شد. `BiometricGate` و `Reminders`
                 * روی آیفون `null`اند، پس ثبت بی سنجشِ اثرِ انگشت انجام
                 * می‌شود و یادآورِ زمان‌بندی‌شده نمی‌آید — همان رفتارِ
                 * ویندوز، و صفحه خودش می‌گوید.
                 */
                Routes.ATTENDANCE -> AttendanceScreen(
                    vm = vm.attendance,
                    breakVm = vm.breakTime,
                    onBack = back,
                )

                /*
                 * «اشتراکِ کارگاه» — صفحه‌اش در `jvmAndroidMain` است و
                 * خودِ `LanApi`ِ iOS هم هنوز جواب نمی‌دهد. یعنی این
                 * یکی دو تکه کم دارد، نه یکی.
                 */
                Routes.WORKSHOP_LINK -> Message(
                    "اشتراکِ کارگاه روی وای‌فای در نسخهٔ آیفون هنوز نیست.\n" +
                        "فعلاً از گوشیِ اندرویدی یا کامپیوتر استفاده کنید."
                )

                else -> Message("این صفحه هنوز در نسخهٔ آیفون نیست: ${nav.current}")
            }
        }
    }
}

/**
 * تنظیمات — نسخهٔ آیفون.
 *
 * **چرا اینجا و نه در `:core`.** صفحهٔ تنظیماتِ اندروید
 * (`SettingsScreen`) پشتیبان‌گیری، بازیابی، خروجیِ CSV و قفلِ اپ را در
 * خود دارد و هر چهار به فایل‌سیستم و رمزنگاریِ سکو بسته‌اند؛ آوردنش به
 * `commonMain` یعنی چهار `expect/actual`ِ تازه. آن کارِ درستی است، ولی
 * کارِ این کامیت نیست.
 *
 * بی این صفحه اما، چهار مقصدِ `commonMain` — اطلاعات پایه، پروفایلِ
 * کارگاه، خودآزمایی و کارگاهِ نمونه — روی آیفون هیچ راهی نداشتند، چون
 * تنها درشان از تنظیمات باز می‌شود. و برای چهار نقش از پنج نقش،
 * «تنظیمات» یکی از خانه‌های نوارِ پایین است؛ یعنی کاربر روی خانه‌ای
 * می‌زد که می‌گفت «این صفحه هنوز نیست».
 *
 * پس این فهرست عمداً کوچک است: فقط درِ همان چهار صفحه، به‌اضافهٔ خروج،
 * و یک جملهٔ صریح دربارهٔ آنچه نیست.
 */
@Composable
private fun IosSettings(
    role: UserRole,
    onGo: (String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) = AppScreen(title = "تنظیمات", onBack = onBack) { pad ->
    Column(
        Modifier
            .padding(pad)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val canManage = Permissions.canManageMaster(role)
        // پروفایل، خودآزمایی، کارگاهِ نمونه و کاربران فقط مالِ مدیرند —
        // کاربرِ شخصی حتی با تیکِ «اطلاعات پایه» راهی به آن‌ها ندارد.
        val isManager = role == UserRole.MANAGER

        if (canManage) {
            SettingsRow("اطلاعات پایه", "خیاط، ناظر، پارچه، رنگ، سایز") {
                onGo(Routes.MASTER)
            }
        }
        if (isManager) {
            SettingsRow("پروفایل کارگاه", "نام و تلفن و آدرس روی رسیدها") {
                onGo(SHOP_PROFILE)
            }
            SettingsRow("کاربران و دسترسی‌ها", "رمز و تیکِ بخش‌ها برای هر نفر") {
                onGo(Routes.USERS)
            }
            SettingsRow("خودآزمایی و سلامتِ داده", "می‌گوید دفتر سالم است یا نه") {
                onGo(Routes.SELF_TEST)
            }
            SettingsRow("کارگاهِ نمونه", "داده‌های آزمایشی برای یاد گرفتنِ اپ") {
                onGo(Routes.SAMPLE_WORKSHOP)
            }
        }

        SettingsRow("راهنما", "کوتاه است، چون خودِ اپ باید واضح باشد") {
            onGo(Routes.GUIDE)
        }

        HorizontalDivider()

        /*
         * صریح، چون سکوت اینجا خطرناک است: کاربری که فکر کند بکاپ
         * گرفته می‌شود روزی دفترش را از دست می‌دهد و تازه آن‌وقت
         * می‌فهمد.
         */
        AppCard {
            Text(
                "در نسخهٔ آیفون هنوز نیست",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "پشتیبان‌گیری و بازیابی، خروجیِ CSV، تغییرِ رمز، و پاک‌کردنِ " +
                    "کارها و حساب‌ها.\n\nتا آن روز، پشتیبانِ کارگاه را از " +
                    "گوشیِ اندرویدی یا کامپیوتر بگیرید.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("خروج از حساب")
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) =
    AppCard(onClick = onClick) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
