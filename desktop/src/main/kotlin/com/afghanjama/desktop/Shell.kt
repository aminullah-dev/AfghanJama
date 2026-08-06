package com.afghanjama.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afghanjama.data.repo.Repo
import com.afghanjama.desktop.data.DesktopLanHost
import com.afghanjama.ui.screens.AuditScreen
import com.afghanjama.ui.screens.BoardScreen
import com.afghanjama.ui.screens.CustomerDetailScreen
import com.afghanjama.ui.screens.CustomersScreen
import com.afghanjama.ui.screens.DeliveryQueueScreen
import com.afghanjama.ui.screens.FinanceHubScreen
import com.afghanjama.ui.screens.FinishedWarehouseScreen
import com.afghanjama.ui.screens.GuideScreen
import com.afghanjama.ui.screens.InventoryScreen
import com.afghanjama.ui.screens.LedgerScreen
import com.afghanjama.ui.screens.MasterDataScreen
import com.afghanjama.ui.screens.MaterialWarehouseScreen
import com.afghanjama.ui.screens.MoneyMoveScreen
import com.afghanjama.ui.screens.MyWorkScreen
import com.afghanjama.ui.screens.NewSaleScreen
import com.afghanjama.ui.screens.OrderSearchScreen
import com.afghanjama.ui.screens.PayrollScreen
import com.afghanjama.ui.screens.PerformanceScreen
import com.afghanjama.ui.screens.ProcurementScreen
import com.afghanjama.ui.screens.ProductionOrderScreen
import com.afghanjama.ui.screens.PurchasePlanScreen
import com.afghanjama.ui.screens.PurchaseReturnScreen
import com.afghanjama.ui.screens.ReportsScreen
import com.afghanjama.ui.screens.ReviewScreen
import com.afghanjama.ui.screens.SelfTestScreen
import com.afghanjama.ui.nav.Routes
import com.afghanjama.ui.screens.ActionCenterScreen
import com.afghanjama.ui.screens.DailyTradeScreen
import com.afghanjama.ui.vm.ActionCenterViewModel
import com.afghanjama.ui.screens.ShopProfileScreen
import com.afghanjama.ui.screens.SewingScreen
import com.afghanjama.ui.screens.StockLedgerScreen
import com.afghanjama.ui.screens.WorkshopLinkScreen
import com.afghanjama.ui.vm.AuditViewModel
import com.afghanjama.ui.vm.BoardViewModel
import com.afghanjama.ui.vm.CustomerDetailViewModel
import com.afghanjama.ui.vm.CustomersViewModel
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.DeliveryQueueViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.FinishedSaleViewModel
import com.afghanjama.ui.vm.InventoryViewModel
import com.afghanjama.ui.vm.LedgerViewModel
import com.afghanjama.ui.vm.MasterDataViewModel
import com.afghanjama.ui.vm.MoneyMoveViewModel
import com.afghanjama.ui.vm.MyWorkViewModel
import com.afghanjama.ui.vm.NewSaleViewModel
import com.afghanjama.ui.vm.OrderSearchViewModel
import com.afghanjama.ui.vm.PayrollViewModel
import com.afghanjama.ui.vm.PerformanceViewModel
import com.afghanjama.ui.vm.ProcurementViewModel
import com.afghanjama.ui.vm.ProductionViewModel
import com.afghanjama.ui.vm.PurchasePlanViewModel
import com.afghanjama.ui.vm.PurchaseReturnViewModel
import com.afghanjama.ui.vm.ReportsViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SelfTestViewModel
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.Permissions
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.ui.vm.WarehouseViewModel
import com.afghanjama.ui.vm.WorkshopLinkViewModel

/**
 * بخش‌های نوارِ کناری.
 *
 * `Overview` همان چیزی است که تا امروز کلِ پنجره بود: خودآزمایی و
 * وضعیتِ دفتر. حالا یکی از بخش‌هاست، نه همهٔ برنامه.
 *
 * **این فهرست تنها جایی است که بخش‌ها تعریف می‌شوند.** نوارِ کناری از
 * روی همین ساخته می‌شود و `screenSmoke` هم روی همین می‌گردد — پس بخشی
 * نمی‌تواند اضافه شود و از آزمون جا بماند. (پیش‌تر آزمون فهرستِ خودش را
 * داشت و این دو می‌توانستند از هم بیفتند.)
 */
internal enum class Section(val title: String) {
    Overview("وضعیت"),
    ActionCenter("مرکزِ هشدار"),

    // کارگاه — از سفارش تا تحویل
    Board("تختهٔ کار"),
    Inventory("سفارش‌ها"),
    OrderSearch("جست‌وجوی سفارش"),
    ProductionOrder("شروعِ تولید"),
    Sewing("دوخت"),
    Review("نظارت"),
    DeliveryQueue("صفِ تحویل"),
    MyWork("کارِ من"),

    // انبار و خرید
    Warehouse("انبارِ مواد"),
    StockLedger("گردشِ انبار"),
    FinishedWarehouse("انبارِ محصول"),
    Procurement("خریدِ مواد"),
    PurchasePlan("برنامهٔ خرید"),
    PurchaseReturn("برگشتِ خرید"),

    // فروش و پول
    DailyTrade("معاملاتِ روزمره"),
    NewSale("فروشِ نو"),
    Customers("خریداران"),
    Ledger("دفترِ حساب"),
    Finance("مالی"),
    MoneyMove("جابه‌جاییِ پول"),

    // آدم‌ها
    Payroll("پرداختِ حقوق"),
    Performance("کارکرد"),

    // بقیه
    Reports("گزارش‌ها"),
    Audit("رسیدگی"),
    MasterData("اطلاعات پایه"),
    ShopProfile("پروفایل کارگاه"),
    SelfTest("خودآزمایی و سلامتِ داده"),
    WorkshopLink("اشتراکِ کارگاه"),
    Backup("پشتیبان و بازیابی"),
    Guide("راهنما")
}

/**
 * پنجرهٔ اصلیِ ویندوز.
 *
 * **چرا نوارِ کناری و نه همان ناوبریِ اندروید:** `androidx.navigation`
 * فقط اندروید است و به `:core` نمی‌آید. ولی این محدودیت به نفع کار
 * تمام شد: پی‌سی صفحهٔ بزرگ دارد و پشته‌ی «برو و برگرد»ِ گوشی آنجا
 * اضافه است. کارگاه همهٔ بخش‌ها را یک‌جا می‌بیند و با یک کلیک بینشان
 * می‌رود.
 *
 * صفحه‌ها همان‌هایی هستند که روی گوشی اجرا می‌شوند — **کلمه به کلمه
 * همان کد**، از `:core`. هیچ نسخهٔ ویندوزیِ جداگانه‌ای از آن‌ها نیست.
 */
@Composable
fun Shell(repo: Repo?, role: UserRole = UserRole.MANAGER) {
    var section by remember { mutableStateOf(Section.Overview) }
    // جزئیاتِ مشتری بخشِ نوار نیست؛ از دلِ «خریداران» باز می‌شود و با
    // «برگشت» بسته. پس یک حالتِ کوچک کنارِ بخشِ جاری کافی است.
    var openCustomer by remember { mutableStateOf<Long?>(null) }

    // اگر دفتر باز نشد، فقط «وضعیت» می‌ماند — چون همان است که خطا را
    // نشان می‌دهد. بردنِ کاربر به صفحه‌ای که دادهٔ خالی نشان دهد بدتر
    // از نبودنِ آن صفحه است.
    if (repo == null) {
        Overview()
        return
    }

    Row(Modifier.fillMaxSize().background(Bg)) {
        // در چیدمانِ راست‌به‌چپ، اولین عضوِ Row سمتِ راست می‌نشیند.
        Sidebar(section) {
            section = it
            openCustomer = null
        }

        Box(Modifier.fillMaxSize()) {
            val customerId = openCustomer
            if (section == Section.Customers && customerId != null) {
                val vm: CustomerDetailViewModel = viewModel { CustomerDetailViewModel(repo) }
                CustomerDetailScreen(
                    vm,
                    customerId,
                    onBack = { openCustomer = null },
                    // صفحهٔ سفارش هنوز در `:app` است (چاپ و عکس نگهش
                    // داشته). تا آن وقت کلیک بی‌اثر است نه اینکه به جای
                    // اشتباه برود.
                    onOpenOrder = {}
                )
            } else {
                SectionContent(
                    section = section,
                    repo = repo,
                    role = role,
                    go = { section = it },
                    onOpenCustomer = { openCustomer = it }
                )
            }
        }
    }
}

/**
 * محتوای هر بخش.
 *
 * از `Shell` جدا شده تا `screenSmoke` بتواند **همین** را برای هر عضوِ
 * `Section` صدا بزند. اگر این `when` در دلِ `Shell` می‌ماند، آزمون
 * ناچار بود فهرستِ خودش را نگه دارد و آن دو روزی از هم می‌افتادند —
 * یعنی صفحه‌ای اضافه می‌شد و بی‌آزمون به کارگاه می‌رفت.
 */
/**
 * ترجمهٔ مسیرِ اندرویدی به بخشِ نوارِ کناری.
 *
 * هشدارهای «مرکزِ هشدار» در `:core` ساخته می‌شوند و هرکدام یک
 * `Routes.…` همراه دارند — رشته‌ای که روی گوشی به `NavController`
 * می‌رود. ویندوز `NavController` ندارد، پس همان رشته اینجا به بخش
 * تبدیل می‌شود.
 *
 * **`null` یعنی این مقصد روی ویندوز نیست** (حضور و غیاب، و تنظیماتِ
 * اندرویدی). آن‌وقت کلیک هیچ کاری نمی‌کند — که بهتر از بردنِ کاربر به
 * صفحهٔ اشتباه است. وقتی آن صفحه‌ها به `:core` بیایند، فقط یک سطر
 * اینجا اضافه می‌شود.
 */
internal fun sectionForRoute(route: String): Section? = when (route) {
    Routes.PRODUCTION_ORDER -> Section.ProductionOrder
    Routes.PROCUREMENT -> Section.Procurement
    Routes.LEDGER -> Section.Ledger
    Routes.DELIVERY_QUEUE -> Section.DeliveryQueue
    Routes.PAYROLL -> Section.Payroll
    Routes.PURCHASE_PLAN -> Section.PurchasePlan
    Routes.INVENTORY -> Section.Inventory
    Routes.WAREHOUSE -> Section.Warehouse
    Routes.STOCK_LEDGER -> Section.StockLedger
    Routes.FINISHED_SALES -> Section.FinishedWarehouse
    Routes.CUSTOMERS -> Section.Customers
    Routes.PERFORMANCE -> Section.Performance
    Routes.NEW_SALE -> Section.NewSale
    Routes.PURCHASE_RETURN -> Section.PurchaseReturn
    Routes.DAILY_TRADE -> Section.DailyTrade
    Routes.FINANCE -> Section.Finance
    Routes.REPORTS -> Section.Reports
    Routes.SEWING -> Section.Sewing
    Routes.REVIEW -> Section.Review
    Routes.MY_WORK -> Section.MyWork
    Routes.ACTION_CENTER -> Section.ActionCenter
    // روی ویندوز نیستند — حضور و غیاب هنوز در `:app` است، و
    // «تنظیمات»ِ اندروید اینجا به دو بخشِ جدا شکسته شده.
    else -> null
}

@Composable
internal fun SectionContent(
    section: Section,
    repo: Repo,
    role: UserRole,
    go: (Section) -> Unit,
    onOpenCustomer: (Long) -> Unit
) {
    val back = { go(Section.Overview) }

    when (section) {
        Section.Overview -> Overview()

        /*
         * مرکزِ هشدار — همان صفحه‌ای که روی گوشی هست.
         *
         * تنها تفاوتش با اندروید در `onNavigate` است: آنجا رشتهٔ مسیر
         * به `NavController` می‌رود، اینجا به بخشِ نوارِ کناری ترجمه
         * می‌شود. خودِ صفحه و هشدارهایش کلمه‌به‌کلمه یکی‌اند.
         */
        Section.ActionCenter -> {
            val vm: ActionCenterViewModel = viewModel { ActionCenterViewModel(repo) }
            ActionCenterScreen(
                vm = vm,
                onNavigate = { route -> sectionForRoute(route)?.let { go(it) } },
                onBack = back
            )
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
                // نقشِ واقعیِ کاربرِ واردشده — نه فرض. تا دیروز اینجا
                // `UserRole.MANAGER` ثابت بود چون ویندوز ورود نداشت.
                role = role,
                onGoStartProduction = { go(Section.ProductionOrder) },
                onGoWallet = { go(Section.Finance) },
                onGoSearch = { go(Section.OrderSearch) },
                onGoStock = { go(Section.StockLedger) },
                // این دو صفحهٔ مشترک ندارند: «تنظیمات» و «برش» هنوز در
                // `:app`اند. بی‌اثر می‌مانند تا جای اشتباه نبرند.
                onGoSettings = {},
                onGoCutting = {},
                onOpenDetail = {},
                onBack = back
            )
        }

        Section.OrderSearch -> {
            val vm: OrderSearchViewModel = viewModel { OrderSearchViewModel(repo) }
            OrderSearchScreen(vm, onBack = back, onOpenDetail = {})
        }

        Section.ProductionOrder -> {
            val vm: ProductionViewModel = viewModel { ProductionViewModel(repo) }
            ProductionOrderScreen(vm, onBack = back)
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
            MoneyMoveScreen(vm, startAsPayment = false, onBack = back)
        }

        Section.Payroll -> {
            val vm: PayrollViewModel = viewModel { PayrollViewModel(repo) }
            PayrollScreen(vm, onBack = back)
        }

        Section.Performance -> {
            val vm: PerformanceViewModel = viewModel { PerformanceViewModel(repo) }
            PerformanceScreen(vm, onBack = back)
        }

        Section.Reports -> {
            val vm: ReportsViewModel = viewModel { ReportsViewModel(repo) }
            ReportsScreen(vm, onBack = back)
        }

        Section.Audit -> {
            val vm: AuditViewModel = viewModel { AuditViewModel(repo) }
            AuditScreen(vm, onBack = back)
        }

        Section.MasterData -> {
            val vm: MasterDataViewModel = viewModel { MasterDataViewModel(repo) }
            MasterDataScreen(vm, onBack = back)
        }

        // بی این صفحه، هر کاغذی که پی‌سی چاپ می‌کرد نامِ پیش‌فرض داشت:
        // `CompanyPrefs.save` تنها در تنظیماتِ اندروید صدا زده می‌شد و
        // ویندوز هیچ راهی برای نوشتنش نداشت.
        // با آمدنِ مرزِ `Photos` آزاد شد — تنها چیزی که نگهش داشته
        // بود `PhotoStore.delete` بود.
        Section.FinishedWarehouse -> {
            val vm: FinishedSaleViewModel = viewModel { FinishedSaleViewModel(repo) }
            FinishedWarehouseScreen(vm, onBack = back)
        }

        Section.ShopProfile -> ShopProfileScreen(onBack = back)

        // `DELIVERY.md` می‌گوید پیش از هر تحویل این باید اجرا شود و همه
        // سبز باشد. تا امروز روی ویندوز راهی برایش نبود.
        Section.SelfTest -> {
            val vm: SelfTestViewModel = viewModel { SelfTestViewModel(repo) }
            SelfTestScreen(vm, onBack = back)
        }

        // ماشینی که دفترِ حساب است باید بتواند پشتیبان بگیرد.
        // تا دیروز `grep -i backup desktop/src` هیچ نمی‌داد.
        // آخرین صفحهٔ مشترکی که در `:app` گیر افتاده بود — تنها
        // مانعش نبودنِ `LanHost` روی دسکتاپ بود.
        Section.WorkshopLink -> {
            val vm: WorkshopLinkViewModel = viewModel {
                WorkshopLinkViewModel(repo, DesktopLanHost())
            }
            WorkshopLinkScreen(
                vm,
                isManager = role == UserRole.MANAGER,
                onBack = back
            )
        }

        Section.Backup -> BackupScreen(onBack = back)

        Section.Guide -> GuideScreen(onBack = back)
    }
}

@Composable
private fun Sidebar(current: Section, onPick: (Section) -> Unit) {
    Column(
        Modifier
            .width(210.dp)
            .fillMaxHeight()
            .background(CardBg)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "خیاط‌یار",
                fontFamily = Vazirmatn,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Brand
            )
        }

        Section.entries.forEach { s ->
            val selected = s == current
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) BrandSoft else CardBg)
                    .clickable { onPick(s) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    s.title,
                    fontFamily = Vazirmatn,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Brand else Ink
                )
            }
        }

        Text(
            "این صفحه‌ها همان کدی‌اند که روی گوشی اجرا می‌شود.",
            fontFamily = Vazirmatn,
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.padding(top = 18.dp, start = 8.dp, end = 8.dp)
        )
    }
}
