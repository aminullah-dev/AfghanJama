package com.afghanjama.desktop

import com.afghanjama.ui.theme.Radius
import com.afghanjama.ui.theme.PillShape
import com.afghanjama.ui.components.CountPill
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afghanjama.AppInfo
import com.afghanjama.data.repo.Repo
import com.afghanjama.desktop.data.DeskPrefs
import com.afghanjama.desktop.data.DesktopAutoBackup
import com.afghanjama.licence.LicenceGate
import com.afghanjama.licence.Licensing
import com.afghanjama.prefs.BackupPrefs
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.desktop.platform.pickLicenceFile
import com.afghanjama.ui.components.LicenceBanner
import com.afghanjama.ui.components.LicenceRefusalDialog
import com.afghanjama.ui.components.NoAccessNotice
import com.afghanjama.ui.components.WorkStage
import com.afghanjama.ui.nav.DeskGroup
import com.afghanjama.ui.nav.DeskSection
import com.afghanjama.ui.nav.allowedFor
import com.afghanjama.ui.nav.deskSectionForRoute
import com.afghanjama.ui.nav.icon
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
import com.afghanjama.ui.screens.LicenceScreen
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
import com.afghanjama.ui.screens.AnalyticsScreen
import com.afghanjama.ui.screens.ReportsScreen
import com.afghanjama.ui.screens.ReviewScreen
import com.afghanjama.ui.screens.SampleWorkshopScreen
import com.afghanjama.ui.screens.SelfTestScreen
import com.afghanjama.ui.screens.SewingScreen
import com.afghanjama.ui.screens.ShopProfileScreen
import com.afghanjama.ui.screens.StockLedgerScreen
import com.afghanjama.ui.screens.UsersScreen
import com.afghanjama.ui.screens.WorkshopLoadScreen
import com.afghanjama.ui.vm.Access
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
import com.afghanjama.ui.vm.LicenceViewModel
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
import com.afghanjama.ui.vm.AnalyticsViewModel
import com.afghanjama.ui.vm.ReportsViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SampleWorkshopViewModel
import com.afghanjama.ui.vm.SelfTestViewModel
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.ui.vm.UsersViewModel
import com.afghanjama.ui.vm.WarehouseViewModel
import com.afghanjama.ui.vm.WorkshopLinkAccess
import com.afghanjama.ui.vm.WorkshopLoadViewModel
import kotlinx.coroutines.flow.flowOf

/**
 * بخش‌های نوارِ کنار — تعریفش در `:core` است ([DeskSection])، تا
 * آزمونِ واحد ببیندش؛ این نامِ کوتاه فقط برای خوانا ماندنِ همین ماژول است.
 */
internal typealias Section = DeskSection

/**
 * پنجرهٔ اصلیِ کامپیوتر.
 *
 * **چرا نوارِ کناری و نه همان ناوبریِ اندروید:** `androidx.navigation`
 * فقط اندروید است و به `:core` نمی‌آید. ولی این محدودیت به نفع کار
 * تمام شد: کامپیوتر صفحهٔ بزرگ دارد و پشتهٔ «برو و برگرد»ِ گوشی آنجا
 * اضافه است. کارگاه همهٔ بخش‌ها را یک‌جا می‌بیند و با یک کلیک بینشان
 * می‌رود.
 *
 * صفحه‌ها همان‌هایی هستند که روی گوشی اجرا می‌شوند — **کلمه به کلمه
 * همان کد**، از `:core`.
 *
 * از ۱.۱۰: نوار گروه‌بندی و آیکن‌دار است و گروه‌ها جمع می‌شوند، صفحهٔ اول
 * «خانه» است (همان خانهٔ گوشی) نه خودآزمایی، و پایینِ نوار حسابِ کسی
 * است که وارد شده.
 */
@Composable
fun Shell(
    repo: Repo?,
    role: UserRole = UserRole.MANAGER,
    /** تیک‌های این نفر — نوارِ کناری فقط بخش‌هایی را نشان می‌دهد که دارد. */
    access: Access = Access.forRole(role),
    /** حسابِ واردشده؛ `null` فقط در آزمونِ صفحه‌ها. */
    auth: AuthViewModel? = null,
) {
    Shell(repo, role, access, remember { DeskNav() }, auth)
}

@Composable
internal fun Shell(
    repo: Repo?,
    role: UserRole,
    access: Access,
    nav: DeskNav,
    auth: AuthViewModel?,
) {
    // اگر دفتر باز نشد، فقط همین پیدا می‌شود — چون همان است که خطا را
    // نشان می‌دهد. بردنِ کاربر به صفحه‌ای که دادهٔ خالی نشان دهد بدتر
    // از نبودنِ آن صفحه است.
    if (repo == null) {
        LedgerProblem()
        return
    }

    Box(Modifier.fillMaxSize()) {
        DeskBackdrop()

        Row(Modifier.fillMaxSize().padding(12.dp)) {
            // در چیدمانِ راست‌به‌چپ، اولین عضوِ Row سمتِ راست می‌نشیند.
            Sidebar(repo, nav, access, auth)

            Spacer(Modifier.width(12.dp))

            // هر ثبتی که لایسنس رد کند، یک بار و از هر بخشی اینجا گفته می‌شود.
            val licence by LicenceGate.status.collectAsState()
            LicenceRefusalDialog(onOpenLicence = { nav.go(Section.Licence) })

            val contentShape = RoundedCornerShape(24.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .deskGlass(contentShape, elevation = 6.dp)
                    .background(Bg)
            ) {
                // بنرِ لایسنس بالای هر بخش — روزهای مانده و فقط‌خواندنی
                // همیشه بالای کار دیده می‌شود. «خانه» بنرِ خودش را زیرِ
                // سلام دارد (همان خانهٔ گوشی)؛ دو بار گفتنش شلوغی است.
                val ownBanner = nav.section == Section.Licence ||
                    (nav.section == Section.Home && nav.openOrder == null)
                if (!ownBanner && licence?.needsBanner == true) {
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                        LicenceBanner(onOpen = { nav.go(Section.Licence) })
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    ShellContent(repo, role, access, nav, auth)
                    NoticeHost(Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        // جست‌وجوی همه‌جا (⌘/Ctrl+K) روی همه‌چیز.
        if (nav.palette) CommandPalette(repo, access, nav)
    }
}

@Composable
private fun ShellContent(repo: Repo, role: UserRole, access: Access, nav: DeskNav, auth: AuthViewModel?) {
    val orderId = nav.openOrder
    val customerId = nav.openCustomer
    val section = nav.section
    if (orderId != null) {
        val vm: OrderDetailViewModel = viewModel { OrderDetailViewModel(repo) }
        OrderDetailScreen(
            vm = vm,
            orderIdText = orderId,
            // همان قاعدهٔ گوشی (`AppNav`): تیکِ «برگشتِ فروش» و «ویرایش و حذفِ
            // سفارش»، نه «فقط مدیر».
            canReturnSale = Permissions.canReturnSale(role),
            canEdit = Permissions.canEditOrder(role),
            onBack = { nav.openOrder = null }
        )
    } else if ((section == Section.Customers || section == Section.FollowUp) && customerId != null) {
        // پیگیریِ طلب هم پروندهٔ مشتری را باز می‌کند؛ «برگشت» به همان
        // فهرستِ پیگیری برمی‌گردد، نه به «خریداران».
        val vm: CustomerDetailViewModel = viewModel { CustomerDetailViewModel(repo) }
        CustomerDetailScreen(
            vm,
            customerId,
            onBack = { nav.openCustomer = null },
            onOpenOrder = { nav.openOrder = it.toString() }
        )
    } else if (!section.allowedFor(access)) {
        // از هشدار یا پیوندی به بخشی رسیده که تیکش را ندارد.
        NoAccessNotice(onHome = { nav.go(Section.Home) })
    } else {
        SectionContent(
            section = section,
            repo = repo,
            role = role,
            access = access,
            go = { nav.go(it) },
            onOpenCustomer = { nav.openCustomer = it },
            onOpenOrder = { nav.openOrder = it },
            moneyAsPayment = nav.moneyAsPayment,
            onMoney = { nav.goMoney(it) },
            auth = auth,
        )
    }
}

/** ترجمهٔ مسیرِ گوشی به بندِ نوار — تعریفش در `:core` ([deskSectionForRoute]). */
internal fun sectionForRoute(route: String): Section? = deskSectionForRoute(route)

/**
 * محتوای هر بخش.
 *
 * از `Shell` جدا شده تا `screenSmoke` بتواند **همین** را برای هر عضوِ
 * `Section` صدا بزند. اگر این `when` در دلِ `Shell` می‌ماند، آزمون
 * ناچار بود فهرستِ خودش را نگه دارد و آن دو روزی از هم می‌افتادند.
 */
@Composable
internal fun SectionContent(
    section: Section,
    repo: Repo,
    role: UserRole,
    go: (Section) -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onOpenOrder: (String) -> Unit = {},
    /** تیک‌های این نفر — «اشتراکِ کارگاه» دکمه‌هایش را از همین می‌خوانَد. */
    access: Access = Access.forRole(role),
    /** «جابه‌جاییِ پول» به حالتِ پرداخت باز شود؟ — از دکمهٔ «پرداخت»ِ خانه. */
    moneyAsPayment: Boolean = false,
    /** «پرداخت» و «دریافت»ِ خانه — هر دو به «جابه‌جاییِ پول»، با حالتِ خودشان. */
    onMoney: (pay: Boolean) -> Unit = { go(Section.MoneyMove) },
    /** حسابِ واردشده — «تنظیمات» تغییرِ رمز و خروج را از همین دارد. */
    auth: AuthViewModel? = null,
) {

    // «برگشت» به صفحهٔ اول، که از ۱.۱۲ داشبورد است.
    val back = { go(Section.Dashboard) }

    when (section) {
        /*
         * داشبورد — چکیدهٔ کلِ کارگاه، صفحهٔ اولِ کامپیوتر (فازِ C). هر کارت با
         * دسترسیِ صفحهٔ خودش نشان داده می‌شود؛ کلیک روی کارت همان صفحه را باز
         * می‌کند.
         */
        Section.Dashboard -> HubDashboard(repo, access, go = go, onOpenOrder = onOpenOrder)

        /*
         * خانه — همان خانهٔ گوشی، با همان کاشی‌ها و همان قاعدهٔ دسترسی.
         *
         * تا ۱.۹ صفحهٔ اولِ کامپیوتر خودآزمایی بود و `HomeDashboardScreen`
         * اینجا هیچ‌جا صدا زده نمی‌شد. هر کاشی دقیقاً به همان جایی می‌رود که
         * روی گوشی (`AppNav`) می‌رود، فقط به‌جای مسیر، بندِ نوار.
         *
         * کاشیِ «اشتراکِ کارگاه» بالایش می‌ماند: سه پرسشِ کارفرما (اصلی است؟
         * چند گوشی؟ چه منتظر است؟) همین که برنامه باز شود جواب دارند.
         */
        Section.Home -> {
            val homeVm: HomeViewModel = viewModel { HomeViewModel(repo) }
            val actionVm: ActionCenterViewModel = viewModel { ActionCenterViewModel(repo) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // روی صفحهٔ پهن، کاشی‌ها کشیده و توخالی نشوند.
                val columns = when {
                    maxWidth >= 1100.dp -> 4
                    maxWidth >= 760.dp -> 3
                    else -> 2
                }
                HomeDashboardScreen(
                    vm = homeVm,
                    actionVm = actionVm,
                    isManager = access.isManager,
                    onGoActionCenter = { go(Section.ActionCenter) },
                    onGoProcurement = { go(Section.Procurement) },
                    onGoWarehouse = { go(Section.Warehouse) },
                    onGoStockLedger = { go(Section.StockLedger) },
                    onGoProduction = { go(Section.Inventory) },
                    onGoFinishedSales = { go(Section.FinishedWarehouse) },
                    onGoDeliveryQueue = { go(Section.DeliveryQueue) },
                    onGoAttendance = { go(Section.Attendance) },
                    onGoPayroll = { go(Section.Payroll) },
                    onGoPerformance = { go(Section.Performance) },
                    onGoPurchasePlan = { go(Section.PurchasePlan) },
                    onGoMyWork = { go(Section.MyWork) },
                    onGoPay = { onMoney(true) },
                    onGoReceive = { onMoney(false) },
                    onGoDailyTrade = { go(Section.DailyTrade) },
                    onGoDebtFollowUp = { go(Section.FollowUp) },
                    canSeeCustomers = Permissions.canSeeCustomers(role),
                    canBuyMaterial = Permissions.canBuyMaterial(role),
                    onGoCustomers = { go(Section.Customers) },
                    onGoFinance = { go(Section.Finance) },
                    onGoLedger = { go(Section.Ledger) },
                    onGoDocuments = { go(Section.Documents) },
                    onGoReports = { go(Section.Reports) },
                    onGoAudit = { go(Section.Audit) },
                    onGoSearch = { go(Section.OrderSearch) },
                    onGoSettings = { go(Section.Settings) },
                    onGoQuoteCalc = { go(Section.QuoteCalc) },
                    onGoWorkshopLoad = { go(Section.WorkshopLoad) },
                    onGoRecurring = { go(Section.Recurring) },
                    onGoGuide = { go(Section.Guide) },
                    onGoWorkshopLink = { go(Section.WorkshopLink) },
                    onGoBoard = { go(Section.Board) },
                    can = { access.has(it) },
                    onGoLicence = { go(Section.Licence) },
                    columns = columns,
                    // کامپیوتر لرزش ندارد؛ یادآورش در خودِ پنجره و سینی است
                    // (`DesktopReminders`)، و فقط تا وقتی برنامه باز است.
                    shiftReminderNote = "یادآورِ پایانِ شیفت (۸ ساعت) روشن است؛ ۳۰ دقیقه پیش از پایان، " +
                        "تا وقتی برنامه باز است همین‌جا خبر می‌دهد.",
                    extraTop = {
                        WorkshopLinkTile(repo, access, onOpen = { go(Section.WorkshopLink) })
                    },
                )
            }
        }

        /*
         * مرکزِ هشدار — همان صفحه‌ای که روی گوشی هست. تنها تفاوت در
         * `onNavigate` است: آنجا رشتهٔ مسیر به `NavController` می‌رود،
         * اینجا به بندِ نوار ترجمه می‌شود.
         */
        Section.ActionCenter -> {
            val vm: ActionCenterViewModel = viewModel { ActionCenterViewModel(repo) }
            ActionCenterScreen(
                vm = vm,
                onNavigate = { route -> sectionForRoute(route)?.let { go(it) } },
                onBack = back
            )
        }

        Section.Documents -> {
            val vm: DocumentsViewModel = viewModel { DocumentsViewModel(repo) }
            DocumentsScreen(vm = vm, onBack = back)
        }

        Section.DailyTrade -> DailyTradeScreen(
            onGoPurchase = { go(Section.Procurement) },
            onGoSale = { go(Section.NewSale) },
            onGoPurchaseReturn = { go(Section.PurchaseReturn) },
            onGoSaleReturn = { go(Section.FinishedWarehouse) },
            onBack = back
        )

        Section.Board -> {
            val vm: BoardViewModel = viewModel { BoardViewModel(repo) }
            BoardScreen(vm, onBack = back)
        }

        Section.Inventory -> {
            val vm: InventoryViewModel = viewModel { InventoryViewModel(repo) }
            val financeVm: FinanceViewModel = viewModel { FinanceViewModel(repo) }
            InventoryScreen(
                vm = vm,
                financeVm = financeVm,
                // نقشِ واقعیِ کاربرِ واردشده — نه فرض.
                role = role,
                onGoStartProduction = { go(Section.ProductionOrder) },
                onGoWallet = { go(Section.Finance) },
                onGoSearch = { go(Section.OrderSearch) },
                onGoStock = { go(Section.StockLedger) },
                // تا ۱.۹ این دکمه روی کامپیوتر هیچ نمی‌کرد (`{}`) چون
                // «تنظیمات» فقط در `:app` بود. حالا بخشِ خودش را دارد.
                onGoSettings = { go(Section.Settings) },
                onGoCutting = { go(Section.Cutting) },
                onOpenDetail = { onOpenOrder(it.id.toString()) },
                onBack = back
            )
        }

        Section.OrderSearch -> {
            val vm: OrderSearchViewModel = viewModel { OrderSearchViewModel(repo) }
            OrderSearchScreen(vm, onBack = back, onOpenDetail = { onOpenOrder(it.id.toString()) })
        }

        Section.ProductionOrder -> {
            val vm: ProductionViewModel = viewModel { ProductionViewModel(repo) }
            ProductionOrderScreen(
                vm,
                onBack = back,
                onGoProcurement = { go(Section.Procurement) }
            )
        }

        Section.Sewing -> {
            val vm: SewingViewModel = viewModel { SewingViewModel(repo) }
            SewingScreen(vm, onBack = back, onGoReview = { go(Section.Review) })
        }

        Section.Review -> {
            val vm: ReviewViewModel = viewModel { ReviewViewModel(repo) }
            ReviewScreen(vm, onBack = back, onGoSewing = { go(Section.Sewing) })
        }

        Section.DeliveryQueue -> {
            val vm: DeliveryQueueViewModel = viewModel { DeliveryQueueViewModel(repo) }
            DeliveryQueueScreen(vm, onBack = back)
        }

        Section.FollowUp -> {
            val vm: DebtFollowUpViewModel = viewModel { DebtFollowUpViewModel(repo) }
            DebtFollowUpScreen(vm, onBack = back, onOpenCustomer = onOpenCustomer)
        }

        Section.MyWork -> {
            val vm: MyWorkViewModel = viewModel { MyWorkViewModel(repo) }
            // برچسبِ خیاط‌ها از همان جایی می‌آید که روی اندروید
            // (`AppNav`) می‌آمد — خیاط‌ها و ناظرها با هم.
            val master: MasterDataViewModel = viewModel { MasterDataViewModel(repo) }
            val tailors by master.tailors.collectAsState()
            val inspectors by master.inspectors.collectAsState()
            MyWorkScreen(
                vm = vm,
                tailorLabels = (tailors.map { "[${it.code}] ${it.name}" } +
                    inspectors.map { "[${it.code}] ${it.name}" }).distinct(),
                onBack = back
            )
        }

        Section.Warehouse -> {
            val vm: WarehouseViewModel = viewModel { WarehouseViewModel(repo) }
            // همان قاعده‌ای که اندروید به کار می‌برد، نه `true`ِ ثابت.
            MaterialWarehouseScreen(
                vm,
                canAdjust = Permissions.canAdjustMaterial(role),
                onBack = back
            )
        }

        Section.StockLedger -> {
            // همان ViewModelِ انبار؛ این صفحه فقط گردشِ آن را نشان می‌دهد.
            val vm: WarehouseViewModel = viewModel { WarehouseViewModel(repo) }
            StockLedgerScreen(vm, onBack = back)
        }

        Section.Procurement -> {
            val vm: ProcurementViewModel = viewModel { ProcurementViewModel(repo) }
            ProcurementScreen(vm, onBack = back)
        }

        Section.PurchasePlan -> {
            val vm: PurchasePlanViewModel = viewModel { PurchasePlanViewModel(repo) }
            PurchasePlanScreen(
                vm,
                onGoProcurement = { go(Section.Procurement) },
                onBack = back
            )
        }

        Section.PurchaseReturn -> {
            val vm: PurchaseReturnViewModel = viewModel { PurchaseReturnViewModel(repo) }
            PurchaseReturnScreen(vm, onBack = back)
        }

        Section.NewSale -> {
            val vm: NewSaleViewModel = viewModel { NewSaleViewModel(repo) }
            NewSaleScreen(vm, onBack = back)
        }

        Section.Customers -> {
            val vm: CustomersViewModel = viewModel { CustomersViewModel(repo) }
            CustomersScreen(vm, onBack = back, onOpenCustomer = onOpenCustomer)
        }

        Section.Ledger -> {
            val vm: LedgerViewModel = viewModel { LedgerViewModel(repo) }
            LedgerScreen(vm, onBack = back)
        }

        Section.Finance -> {
            val financeVm: FinanceViewModel = viewModel { FinanceViewModel(repo) }
            val dashVm: DashboardViewModel = viewModel { DashboardViewModel(repo) }
            FinanceHubScreen(financeVm, dashVm, onBack = back)
        }

        Section.MoneyMove -> {
            val vm: MoneyMoveViewModel = viewModel { MoneyMoveViewModel(repo) }
            // حالتِ شروع در `remember`ِ خودِ صفحه است؛ `key` آن را با هر
            // «پرداخت»/«دریافت»ِ تازه از نو می‌سازد.
            key(moneyAsPayment) {
                MoneyMoveScreen(vm, startAsPayment = moneyAsPayment, onBack = back)
            }
        }

        Section.Payroll -> {
            val vm: PayrollViewModel = viewModel { PayrollViewModel(repo) }
            PayrollScreen(vm, onBack = back)
        }

        Section.Performance -> {
            val vm: PerformanceViewModel = viewModel { PerformanceViewModel(repo) }
            PerformanceScreen(vm, onBack = back)
        }

        Section.SampleShop -> {
            val vm: SampleWorkshopViewModel = viewModel { SampleWorkshopViewModel(repo) }
            SampleWorkshopScreen(vm, onBack = back)
        }

        Section.Users -> {
            val settings = LocalSettings.current
            val vm: UsersViewModel = viewModel { UsersViewModel(settings, repo) }
            UsersScreen(vm, onBack = back)
        }

        Section.Journal -> {
            val vm: JournalViewModel = viewModel { JournalViewModel(repo) }
            JournalScreen(vm, onBack = back)
        }

        Section.Reports -> {
            val vm: ReportsViewModel = viewModel { ReportsViewModel(repo) }
            ReportsScreen(vm, onBack = back)
        }

        Section.Analytics -> {
            val vm: AnalyticsViewModel = viewModel { AnalyticsViewModel(repo) }
            AnalyticsScreen(vm, onBack = back)
        }

        Section.Audit -> {
            val vm: AuditViewModel = viewModel { AuditViewModel(repo) }
            AuditScreen(vm, onBack = back)
        }

        Section.WorkshopLoad -> {
            val vm: WorkshopLoadViewModel = viewModel { WorkshopLoadViewModel(repo) }
            WorkshopLoadScreen(vm, onBack = back)
        }

        Section.Cutting -> {
            val vm: CuttingViewModel = viewModel { CuttingViewModel(repo) }
            CuttingScreen(
                vm,
                onBack = back,
                onGoSewing = { go(Section.Sewing) },
                onGoStage = { stage ->
                    go(
                        when (stage) {
                            WorkStage.CUT -> Section.Cutting
                            WorkStage.SEW -> Section.Sewing
                            WorkStage.CHECK -> Section.Review
                        }
                    )
                },
            )
        }

        Section.Attendance -> {
            val vm: AttendanceViewModel = viewModel { AttendanceViewModel(repo) }
            val breaks: BreakTimeViewModel = viewModel { BreakTimeViewModel(repo) }
            AttendanceScreen(vm, breaks, onBack = back)
        }

        Section.Recurring -> {
            val vm: RecurringExpenseViewModel = viewModel { RecurringExpenseViewModel(repo) }
            RecurringExpenseScreen(vm, onBack = back)
        }

        Section.MasterData -> {
            val vm: MasterDataViewModel = viewModel { MasterDataViewModel(repo) }
            MasterDataScreen(vm, onBack = back)
        }

        /*
         * «افزودن به فاکتور» و «فاکتور» — همان کاری که گوشی (`AppNav`) می‌کند.
         *
         * تا ۱.۹ هیچ‌کدام داده نمی‌شد و پیش‌فرضِ هر دو `{}` بود: کالا انتخاب
         * می‌شد و هیچ فاکتوری نمی‌دید، و دکمهٔ فاکتور هیچ جا نمی‌برد.
         * `NewSaleViewModel` همان یکی است که «فروشِ نو» نشان می‌دهد (یک صاحب
         * برای کلِ پنجره)، پس کالایی که اینجا افزوده شد همان‌جا پیدا می‌شود.
         */
        Section.FinishedWarehouse -> {
            val vm: FinishedSaleViewModel = viewModel { FinishedSaleViewModel(repo) }
            val newSaleVm: NewSaleViewModel = viewModel { NewSaleViewModel(repo) }
            val invoiceCount by newSaleVm.pickedCount.collectAsState()
            FinishedWarehouseScreen(
                vm,
                onBack = back,
                onOpenInvoice = { go(Section.NewSale) },
                onAddToInvoice = { newSaleVm.addItem(it) },
                invoiceCount = invoiceCount
            )
        }

        // بی این صفحه، هر کاغذی که کامپیوتر چاپ می‌کرد نامِ پیش‌فرض داشت.
        Section.ShopProfile -> {
            val financeVm: FinanceViewModel = viewModel { FinanceViewModel(repo) }
            ShopProfileScreen(financeVm = financeVm, onBack = back)
        }

        // `DELIVERY.md` می‌گوید پیش از هر تحویل این باید اجرا شود و همه
        // سبز باشد. خودآزماییِ حساب — تا ۱.۹ کلِ صفحهٔ اولِ کامپیوتر —
        // حالا کارتِ بالای همین صفحه است.
        Section.SelfTest -> {
            val vm: SelfTestViewModel = viewModel { SelfTestViewModel(repo) }
            SelfTestScreen(vm, onBack = back, header = { SelfCheckCard() })
        }

        // صفحهٔ اشتراک روی کامپیوتر، همان صفحهٔ گوشی.
        // «کارگاهِ وصل» — کامپیوتر به‌عنوانِ مرکز (قراردادِ ۲)، به‌جای صفحهٔ
        // گوشی‌محورِ «اشتراکِ کارگاه».
        Section.WorkshopLink -> HubSection(repo, access, onBack = back)

        // ماشینی که دفترِ حساب است باید بتواند پشتیبان بگیرد.
        Section.Backup -> BackupScreen(onBack = back)

        // «تنظیمات» — تا ۱.۹ روی کامپیوتر نبود؛ هشدارهایی که به آن می‌بردند
        // و چرخ‌دندهٔ «سفارش‌ها» هیچ نمی‌کردند.
        Section.Settings -> SettingsSection(
            repo = repo,
            role = role,
            access = access,
            auth = auth,
            go = go,
            onBack = back
        )

        // همان صفحهٔ گوشی. فایلِ `.lnmlic` با پنجرهٔ بازکردنِ خودِ سیستم.
        Section.Licence -> {
            val settings = LocalSettings.current
            val vm: LicenceViewModel = viewModel { LicenceViewModel(Licensing(settings, repo)) }
            LicenceScreen(
                vm = vm,
                onBack = back,
                onOpenFile = {
                    val text = pickLicenceFile()
                    if (text != null) vm.activate(text)
                }
            )
        }

        Section.QuoteCalc -> QuoteCalculatorScreen(onBack = back)
        Section.Guide -> GuideScreen(onBack = back)
        Section.About -> AboutSection(onBack = back)
    }
}

/** پهنای نوارِ کنار — برای پنجرهٔ ۱۱۰۰ نقطه‌ای هنوز جای کافی برای کار می‌ماند. */
internal val SidebarWidth = 252.dp

/**
 * نوارِ کنار: نام، گروه‌ها با آیکن و نشان، و پایینش حساب.
 *
 * گروهِ جمع‌شده جمعِ نشان‌های بندهایش را کنارِ سرگروه نشان می‌دهد، تا
 * درخواستِ منتظری پشتِ یک گروهِ بسته پنهان نماند. گروهی که بندِ بازِ فعلی
 * در آن است همیشه باز است.
 */
@Composable
private fun Sidebar(repo: Repo, nav: DeskNav, access: Access, auth: AuthViewModel?) {
    val settings = LocalSettings.current

    // شمارِ درخواست‌های گوشیِ کارگران، کنارِ «اشتراکِ کارگاه» — فقط برای
    // کسی که می‌تواند تأییدشان کند ([WorkshopLinkAccess.seesRequests]).
    val pendingRequests by remember(repo, access) {
        if (WorkshopLinkAccess.seesRequests(access)) repo.observePendingRequestCount() else flowOf(0)
    }.collectAsState(0)

    // شمارِ هشدارها کنارِ «مرکزِ هشدار» — فقط برای کسی که آن را می‌بیند.
    // همان ViewModelِ صفحه و خانه (یک صاحب در کلِ پنجره)، پس عدد با
    // خودِ مرکزِ هشدار یکی است.
    var alerts = 0
    var urgentAlerts = false
    if (Section.ActionCenter.allowedFor(access)) {
        val actionVm: ActionCenterViewModel = viewModel { ActionCenterViewModel(repo) }
        // سنِ پشتیبان را صفحه‌ها تزریق می‌کنند؛ نوار هم، تا نشانش از همان
        // اولِ کار درست باشد — و پس از هر پشتیبانِ خودکار دوباره، تا هشدارِ
        // «بکاپ گرفته نشده» همان لحظه برود.
        val autoBackup by DesktopAutoBackup.status.collectAsState()
        LaunchedEffect(actionVm, autoBackup.last) {
            actionVm.setLastBackup(BackupPrefs.lastAuto(settings))
        }
        val ui by actionVm.ui.collectAsState()
        alerts = ui.total
        urgentAlerts = ui.urgent > 0
    }

    fun badgeOf(s: Section): Pair<Int, Boolean> = when (s) {
        Section.ActionCenter -> alerts to urgentAlerts
        Section.WorkshopLink -> pendingRequests to false
        else -> 0 to false
    }

    var folded by remember { mutableStateOf(DeskPrefs.collapsed(settings)) }

    Column(
        Modifier
            .width(SidebarWidth)
            .fillMaxHeight()
            .deskGlass(RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(Radius.md)).background(Brand),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "خ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column {
                Text(
                    AppInfo.NAME,
                    // از مقیاسِ مشترک، نه عددِ خام.
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Brand
                )
                Text(platformName, style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            DeskGroup.entries.forEach { group ->
                val items = Section.entries.filter { it.group == group && it.allowedFor(access) }
                if (items.isEmpty()) return@forEach
                val holdsCurrent = nav.section in items
                val isCollapsed = group in folded && !holdsCurrent
                val hidden = items.map { badgeOf(it) }.filter { it.first > 0 }
                GroupHeader(
                    title = group.title,
                    collapsed = isCollapsed,
                    // دکمهٔ جمع کردن برای گروهِ بندِ باز نیست؛ آن گروه همیشه باز است.
                    canToggle = !holdsCurrent,
                    hiddenCount = if (isCollapsed) hidden.sumOf { it.first } else 0,
                    hiddenUrgent = isCollapsed && hidden.any { it.second },
                    onToggle = {
                        folded = if (group in folded) folded - group else folded + group
                        DeskPrefs.setCollapsed(settings, folded)
                    }
                )
                if (!isCollapsed) {
                    items.forEach { s ->
                        val (count, urgent) = badgeOf(s)
                        SidebarItem(
                            section = s,
                            selected = s == nav.section && nav.openOrder == null,
                            count = count,
                            urgent = urgent,
                            onClick = { nav.go(s) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Hairline)
        )
        AccountFooter(auth, onAbout = { nav.go(Section.About) })
    }
}

@Composable
private fun GroupHeader(
    title: String,
    collapsed: Boolean,
    canToggle: Boolean,
    hiddenCount: Int,
    hiddenUrgent: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp)
            .clip(PillShape)
            .then(if (canToggle) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Muted,
            modifier = Modifier.weight(1f)
        )
        if (hiddenCount > 0) {
            CountPill(hiddenCount, urgent = hiddenUrgent, modifier = Modifier.padding(end = 8.dp))
        }
        if (canToggle) {
            Icon(
                if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = if (collapsed) "باز کردنِ $title" else "جمع کردنِ $title",
                tint = Muted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SidebarItem(
    section: Section,
    selected: Boolean,
    count: Int,
    urgent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .then(
                if (selected) Modifier.background(BrandSoft).border(1.dp, Brand.copy(alpha = 0.12f), PillShape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            section.icon,
            contentDescription = null,
            tint = if (selected) Brand else Muted,
            modifier = Modifier.size(18.dp)
        )
        Text(
            section.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Brand else Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) CountPill(count, urgent = urgent)
    }
}
