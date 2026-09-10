// app/src/main/java/com/afghanjama/ui/nav/AppNav.kt
package com.afghanjama.ui.nav

import com.afghanjama.ui.nav.bottomItemsFor
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.afghanjama.ui.screens.ActionCenterScreen
import com.afghanjama.ui.screens.AttendanceScreen
import com.afghanjama.ui.screens.AuditScreen
import com.afghanjama.ui.screens.BoardScreen
import com.afghanjama.ui.screens.CustomerDetailScreen
import com.afghanjama.ui.screens.CustomersScreen
import com.afghanjama.ui.components.WorkStage
import com.afghanjama.ui.screens.CuttingScreen
import com.afghanjama.ui.screens.DailyTradeScreen
import com.afghanjama.ui.screens.DeliveryQueueScreen
import com.afghanjama.ui.components.QrBadge
import com.afghanjama.ui.screens.DocumentsScreen
import com.afghanjama.ui.screens.FinanceHubScreen
import com.afghanjama.ui.screens.FinishedWarehouseScreen
import com.afghanjama.ui.screens.GuideScreen
import com.afghanjama.ui.screens.QuoteCalculatorScreen
import com.afghanjama.ui.screens.HomeDashboardScreen
import com.afghanjama.ui.screens.InventoryScreen
import com.afghanjama.ui.screens.LedgerScreen
import com.afghanjama.ui.screens.LoginScreen
import com.afghanjama.ui.screens.MasterDataScreen
import com.afghanjama.ui.screens.JournalScreen
import com.afghanjama.ui.screens.SampleWorkshopScreen
import com.afghanjama.ui.screens.MaterialWarehouseScreen
import com.afghanjama.ui.screens.MoneyMoveScreen
import com.afghanjama.ui.screens.MyWorkScreen
import com.afghanjama.ui.screens.NewSaleScreen
import com.afghanjama.ui.screens.OrderDetailScreen
import com.afghanjama.ui.screens.OrderSearchScreen
import com.afghanjama.ui.screens.PayrollScreen
import com.afghanjama.ui.screens.PerformanceScreen
import com.afghanjama.ui.screens.PostLoginQuoteScreen
import com.afghanjama.ui.screens.ProcurementScreen
import com.afghanjama.ui.screens.ProductionOrderScreen
import com.afghanjama.ui.screens.PurchasePlanScreen
import com.afghanjama.ui.screens.PurchaseReturnScreen
import com.afghanjama.ui.screens.ReportsScreen
import com.afghanjama.ui.screens.ReviewScreen
import com.afghanjama.ui.screens.SelfTestScreen
import com.afghanjama.ui.screens.SettingsScreen
import com.afghanjama.ui.screens.SewingScreen
import com.afghanjama.ui.screens.StockLedgerScreen
import com.afghanjama.ui.screens.WorkshopLinkScreen
import com.afghanjama.ui.vm.ActionCenterViewModel
import com.afghanjama.ui.vm.AttendanceViewModel
import com.afghanjama.ui.vm.AuditViewModel
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.BackupViewModel
import com.afghanjama.ui.vm.BoardViewModel
import com.afghanjama.ui.vm.BreakTimeViewModel
import com.afghanjama.ui.vm.CustomerDetailViewModel
import com.afghanjama.ui.vm.CustomersViewModel
import com.afghanjama.ui.vm.CuttingViewModel
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.DeliveryQueueViewModel
import com.afghanjama.ui.vm.DocumentsViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.FinishedSaleViewModel
import com.afghanjama.ui.vm.HomeViewModel
import com.afghanjama.ui.vm.InventoryViewModel
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
import com.afghanjama.ui.vm.ReportsViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SelfTestViewModel
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.ui.vm.JournalViewModel
import com.afghanjama.ui.vm.SampleWorkshopViewModel
import com.afghanjama.ui.vm.WarehouseViewModel
import com.afghanjama.ui.vm.WorkshopLinkViewModel

@Composable
fun AppNav(factory: ViewModelProvider.Factory) {
    /*
     * صاحبِ ViewModelها **خودِ اکتیویتی** است، نه مقصدِ ناوبری.
     *
     * اگر پیش‌فرض را می‌گذاشتیم، هر ViewModel به `NavBackStackEntry`
     * بسته می‌شد و با بیرون رفتن از صفحه پاک می‌شد — یعنی فرمِ
     * نیمه‌پرشده سرِ برگشت خالی. آن تغییرِ رفتار است، نه بهینه‌سازی.
     * اینجا فقط **زمانِ ساخت** عوض می‌شود، نه طولِ عمر.
     */
    val vmOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "ViewModelStoreOwner نیست — AppNav باید زیرِ setContent اکتیویتی باشد."
    }

    // تنها ViewModelی که همین‌جا لازم است: مسیرِ شروع و نوارِ پایین به
    // نقشِ کاربر وابسته‌اند. بقیه داخلِ مقصدِ خودشان ساخته می‌شوند.
    val authVm = viewModel<AuthViewModel>(vmOwner, factory = factory)
    val navController = rememberNavController()
    val authUi by authVm.ui.collectAsState()

    val start = when {
        !authUi.isLoggedIn -> Routes.LOGIN
        else -> when (authUi.role) {
            UserRole.MANAGER -> Routes.POST_LOGIN
            UserRole.PURCHASE -> Routes.HOME
            UserRole.SEWING -> Routes.SEWING
            UserRole.REVIEW -> Routes.REVIEW
            UserRole.SALES -> Routes.HOME
        }
    }

    val bottomItems = bottomItemsFor(authUi.role)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // نوار پایین فقط وقتی کاربر وارد شده و در صفحات اصلی است دیده می‌شود
    val showBottomBar = authUi.isLoggedIn &&
        bottomItems.size >= 2 &&
        currentRoute != null &&
        currentRoute != Routes.LOGIN &&
        currentRoute != Routes.POST_LOGIN

    /*
     * دکمهٔ برگشتِ گوشی: از هر صفحه‌ای یک‌راست به داشبورد.
     *
     * پیش از این، برگشت راهِ رفته را یکی‌یکی عقب می‌آمد؛ کاربری که چند
     * صفحه جلو رفته بود باید ده بار می‌زد تا به خانه برسد — حسِ اینکه ده
     * اپ باز است.
     *
     * برگشت غیرفعال **نشد**. روی گوشی‌های امروزی برگشت با اشارهٔ انگشت
     * انجام می‌شود و اصلی‌ترین راهِ حرکت است؛ اگر کار نکند کاربر فکر
     * می‌کند اپ هنگ کرده. به‌جایش رفتارش ساده و پیش‌بینی‌پذیر شد:
     *
     *   دکمهٔ گوشی  → داشبورد
     *   فلشِ داخلِ صفحه → یک قدم عقب (مثل قبل)
     *
     * روی خودِ داشبورد می‌پرسد «خروج از برنامه؟» تا با یک لمسِ اتفاقی
     * وسطِ کار از اپ بیرون نیفتد.
     */
    var askExit by remember { mutableStateOf(false) }
    // نقشِ دوخت و نظارت از صفحهٔ خودشان شروع می‌کنند، نه از خانه؛ برای
    // آنها هم همان صفحه «داشبورد» است و برگشت باید خروج را بپرسد.
    val onDashboard = currentRoute == Routes.HOME ||
        currentRoute == Routes.POST_LOGIN ||
        currentRoute == start

    if (authUi.isLoggedIn && currentRoute != null && currentRoute != Routes.LOGIN) {
        BackHandler {
            if (onDashboard) {
                askExit = true
            } else if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                // اگر داشبورد در پشته نبود (مثلاً نقشی که از جای دیگری
                // شروع می‌کند) خودش باز می‌شود و پشته پاک می‌ماند.
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    if (askExit) {
        val activity = LocalContext.current as? Activity
        AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text("خروج از برنامه؟") },
            text = { Text("کارِ ثبت‌نشده‌ای اگر دارید، اول ذخیره‌اش کنید.") },
            confirmButton = {
                TextButton(onClick = {
                    askExit = false
                    activity?.finish()
                }) { Text("خروج") }
            },
            dismissButton = {
                TextButton(onClick = { askExit = false }) { Text("ماندن") }
            }
        )
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                // یک خطِ مویی بالای نوار: نوار و محتوا هر دو روشن‌اند و
                // بدونِ این خط، لبهٔ نوار در تمِ روشن گم می‌شد و فهرست
                // انگار زیرِ آن ادامه داشت.
                Column {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        bottomItems.forEach { item ->
                            NavigationBarItem(
                                selected = item.owns(currentRoute),
                                onClick = {
                                    if (!item.owns(currentRoute)) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(pad)
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    vm = authVm,
                    onLoggedIn = {
                        val next = when (authVm.ui.value.role) {
                            UserRole.MANAGER -> Routes.POST_LOGIN
                            UserRole.PURCHASE -> Routes.HOME
                            UserRole.SEWING -> Routes.SEWING
                            UserRole.REVIEW -> Routes.REVIEW
                            UserRole.SALES -> Routes.HOME
                        }
                        navController.navigate(next) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.POST_LOGIN) {
                PostLoginQuoteScreen(
                    onContinue = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.POST_LOGIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                val homeVm = viewModel<HomeViewModel>(vmOwner, factory = factory)
                val actionVm = viewModel<ActionCenterViewModel>(vmOwner, factory = factory)
                HomeDashboardScreen(
                    vm = homeVm,
                    actionVm = actionVm,
                    isManager = authUi.role == UserRole.MANAGER,
                    onGoActionCenter = { navController.navigate(Routes.ACTION_CENTER) },
                    onGoProcurement = { navController.navigate(Routes.PROCUREMENT) },
                    onGoWarehouse = { navController.navigate(Routes.WAREHOUSE) },
                    onGoStockLedger = { navController.navigate(Routes.STOCK_LEDGER) },
                    onGoProduction = { navController.navigate(Routes.INVENTORY) },
                    onGoFinishedSales = { navController.navigate(Routes.FINISHED_SALES) },
                    onGoDeliveryQueue = { navController.navigate(Routes.DELIVERY_QUEUE) },
                    onGoAttendance = { navController.navigate(Routes.ATTENDANCE) },
                    onGoPayroll = { navController.navigate(Routes.PAYROLL) },
                    onGoPerformance = { navController.navigate(Routes.PERFORMANCE) },
                    onGoPurchasePlan = { navController.navigate(Routes.PURCHASE_PLAN) },
                    onGoMyWork = { navController.navigate(Routes.MY_WORK) },
                    onGoPay = { navController.navigate(Routes.PAY) },
                    onGoReceive = { navController.navigate(Routes.RECEIVE) },
                    onGoDailyTrade = { navController.navigate(Routes.DAILY_TRADE) },
                    canSeeCustomers = Permissions.canSeeCustomers(authUi.role),
                    canBuyMaterial = Permissions.canBuyMaterial(authUi.role),
                    onGoCustomers = { navController.navigate(Routes.CUSTOMERS) },
                    // -- customers wired --
                    onGoFinance = { navController.navigate(Routes.FINANCE) },
                    onGoLedger = { navController.navigate(Routes.LEDGER) },
                    onGoDocuments = { navController.navigate(Routes.DOCUMENTS) },
                    onGoReports = { navController.navigate(Routes.REPORTS) },
                    onGoAudit = { navController.navigate(Routes.AUDIT) },
                    onGoSearch = { navController.navigate(Routes.SEARCH) },
                    onGoSettings = { navController.navigate(Routes.SETTINGS) },
                    onGoQuoteCalc = { navController.navigate(Routes.QUOTE_CALC) },
                    onGoGuide = { navController.navigate(Routes.GUIDE) },
                    onGoWorkshopLink = { navController.navigate(Routes.WORKSHOP_LINK) },
                    onGoBoard = { navController.navigate(Routes.BOARD) }
                )
            }

            composable(Routes.PRODUCTION_ORDER) {
                val productionVm = viewModel<ProductionViewModel>(vmOwner, factory = factory)
                ProductionOrderScreen(
                    vm = productionVm,
                    onBack = { navController.popBackStack() },
                    onGoProcurement = { navController.navigate(Routes.PROCUREMENT) }
                )
            }

            composable(Routes.FINISHED_SALES) {
                val finishedSaleVm = viewModel<FinishedSaleViewModel>(vmOwner, factory = factory)
                val newSaleVm = viewModel<NewSaleViewModel>(vmOwner, factory = factory)
                // شمارندهٔ فاکتورِ در دست از همان ViewModelِ فاکتور می‌آید، پس
                // کالاهایی که اینجا انتخاب می‌شوند همان‌جا پیدا می‌شوند.
                val invoiceCount by newSaleVm.pickedCount.collectAsState()
                FinishedWarehouseScreen(
                    vm = finishedSaleVm,
                    onBack = { navController.popBackStack() },
                    onOpenInvoice = { navController.navigate(Routes.NEW_SALE) },
                    onAddToInvoice = { newSaleVm.addItem(it) },
                    invoiceCount = invoiceCount
                )
            }

            composable(Routes.ATTENDANCE) {
                val attendanceVm = viewModel<AttendanceViewModel>(vmOwner, factory = factory)
                val breakVm = viewModel<BreakTimeViewModel>(vmOwner, factory = factory)
                AttendanceScreen(
                    vm = attendanceVm,
                    breakVm = breakVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PAYROLL) {
                val payrollVm = viewModel<PayrollViewModel>(vmOwner, factory = factory)
                PayrollScreen(
                    vm = payrollVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PERFORMANCE) {
                val performanceVm = viewModel<PerformanceViewModel>(vmOwner, factory = factory)
                PerformanceScreen(
                    vm = performanceVm,
                    onBack = { navController.popBackStack() }
                )
            }

            // ورودی‌اش در تنظیمات است — «خودآزمایی و سلامتِ داده»
            composable(Routes.SELF_TEST) {
                val selfTestVm = viewModel<SelfTestViewModel>(vmOwner, factory = factory)
                SelfTestScreen(
                    vm = selfTestVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.NEW_SALE) {
                val newSaleVm = viewModel<NewSaleViewModel>(vmOwner, factory = factory)
                NewSaleScreen(
                    vm = newSaleVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.BOARD) {
                val boardVm = viewModel<BoardViewModel>(vmOwner, factory = factory)
                BoardScreen(
                    vm = boardVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.WORKSHOP_LINK) {
                val linkVm = viewModel<WorkshopLinkViewModel>(vmOwner, factory = factory)
                WorkshopLinkScreen(
                    vm = linkVm,
                    isManager = authUi.role == UserRole.MANAGER,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.QUOTE_CALC) {
                QuoteCalculatorScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.GUIDE) {
                GuideScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.DELIVERY_QUEUE) {
                val deliveryQueueVm = viewModel<DeliveryQueueViewModel>(vmOwner, factory = factory)
                DeliveryQueueScreen(
                    vm = deliveryQueueVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.DAILY_TRADE) {
                DailyTradeScreen(
                    onGoPurchase = { navController.navigate(Routes.PROCUREMENT) },
                    onGoSale = { navController.navigate(Routes.NEW_SALE) },
                    onGoPurchaseReturn = { navController.navigate(Routes.PURCHASE_RETURN) },
                    // برگشتِ فروش از روی خودِ فروشِ ثبت‌شده در انبار محصول
                    onGoSaleReturn = { navController.navigate(Routes.FINISHED_SALES) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PURCHASE_RETURN) {
                val purchaseReturnVm = viewModel<PurchaseReturnViewModel>(vmOwner, factory = factory)
                PurchaseReturnScreen(
                    vm = purchaseReturnVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PAY) {
                val moneyVm = viewModel<MoneyMoveViewModel>(vmOwner, factory = factory)
                MoneyMoveScreen(
                    vm = moneyVm,
                    startAsPayment = true,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.RECEIVE) {
                val moneyVm = viewModel<MoneyMoveViewModel>(vmOwner, factory = factory)
                MoneyMoveScreen(
                    vm = moneyVm,
                    startAsPayment = false,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.MY_WORK) {
                val masterVm = viewModel<MasterDataViewModel>(vmOwner, factory = factory)
                val myWorkVm = viewModel<MyWorkViewModel>(vmOwner, factory = factory)
                val tailors by masterVm.tailors.collectAsState()
                val inspectors by masterVm.inspectors.collectAsState()
                MyWorkScreen(
                    vm = myWorkVm,
                    tailorLabels = (tailors.map { "[${it.code}] ${it.name}" } +
                        inspectors.map { "[${it.code}] ${it.name}" }).distinct(),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PURCHASE_PLAN) {
                val purchasePlanVm = viewModel<PurchasePlanViewModel>(vmOwner, factory = factory)
                PurchasePlanScreen(
                    vm = purchasePlanVm,
                    onGoProcurement = { navController.navigate(Routes.PROCUREMENT) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CUSTOMERS) {
                val customerDirVm = viewModel<CustomersViewModel>(vmOwner, factory = factory)
                CustomersScreen(
                    vm = customerDirVm,
                    onBack = { navController.popBackStack() },
                    onOpenCustomer = { id -> navController.navigate("${Routes.CUSTOMER_DETAIL}/$id") }
                )
            }

            composable(
                route = "${Routes.CUSTOMER_DETAIL}/{customerId}",
                arguments = listOf(navArgument("customerId") { type = NavType.LongType })
            ) { entry ->
                val customerDetailVm =
                    viewModel<CustomerDetailViewModel>(vmOwner, factory = factory)
                CustomerDetailScreen(
                    vm = customerDetailVm,
                    customerId = entry.arguments?.getLong("customerId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenOrder = { oid -> navController.navigate("${Routes.ORDER_DETAIL}/$oid") }
                )
            }

            composable(Routes.PROCUREMENT) {
                val procurementVm = viewModel<ProcurementViewModel>(vmOwner, factory = factory)
                ProcurementScreen(
                    vm = procurementVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.WAREHOUSE) {
                val warehouseVm = viewModel<WarehouseViewModel>(vmOwner, factory = factory)
                MaterialWarehouseScreen(
                    vm = warehouseVm,
                    canAdjust = Permissions.canAdjustMaterial(authUi.role),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.STOCK_LEDGER) {
                val warehouseVm = viewModel<WarehouseViewModel>(vmOwner, factory = factory)
                StockLedgerScreen(
                    vm = warehouseVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.INVENTORY) {
                val financeVm = viewModel<FinanceViewModel>(vmOwner, factory = factory)
                val inventoryVm = viewModel<InventoryViewModel>(vmOwner, factory = factory)
                InventoryScreen(
                    onBack = { navController.popBackStack() },
                    vm = inventoryVm,
                    financeVm = financeVm,
                    role = authUi.role,
                    onGoStartProduction = { navController.navigate(Routes.PRODUCTION_ORDER) },
                    onGoWallet = { navController.navigate(Routes.FINANCE) },
                    onGoSettings = { navController.navigate(Routes.SETTINGS) },
                    onGoSearch = { navController.navigate(Routes.SEARCH) },
                    onGoStock = { navController.navigate(Routes.WAREHOUSE) },
                    onGoCutting = { navController.navigate(Routes.CUTTING) },
                    onOpenDetail = { o -> navController.navigate("${Routes.ORDER_DETAIL}/${o.id}") }
                )
            }

            composable(Routes.LEDGER) {
                val ledgerVm = viewModel<LedgerViewModel>(vmOwner, factory = factory)
                LedgerScreen(
                    vm = ledgerVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.DOCUMENTS) {
                val documentsVm = viewModel<DocumentsViewModel>(vmOwner, factory = factory)
                DocumentsScreen(
                    vm = documentsVm,
                    onBack = { navController.popBackStack() },
                    // نشانِ QR فقط روی گوشی معنا دارد و فقط گوشی
                    // می‌تواند بسازدش.
                    qr = { text -> QrBadge(text) }
                )
            }

            composable(Routes.REPORTS) {
                val reportsVm = viewModel<ReportsViewModel>(vmOwner, factory = factory)
                ReportsScreen(
                    vm = reportsVm,
                    onBack = { navController.popBackStack() },
                    onGoJournal = { navController.navigate(Routes.JOURNAL) }
                )
            }

            composable(Routes.SAMPLE_WORKSHOP) {
                val sampleVm = viewModel<SampleWorkshopViewModel>(vmOwner, factory = factory)
                SampleWorkshopScreen(
                    vm = sampleVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.JOURNAL) {
                val journalVm = viewModel<JournalViewModel>(vmOwner, factory = factory)
                JournalScreen(
                    vm = journalVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.AUDIT) {
                val auditVm = viewModel<AuditViewModel>(vmOwner, factory = factory)
                AuditScreen(
                    vm = auditVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ACTION_CENTER) {
                val actionVm = viewModel<ActionCenterViewModel>(vmOwner, factory = factory)
                ActionCenterScreen(
                    vm = actionVm,
                    onNavigate = { route -> navController.navigate(route) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.FINANCE) {
                val financeVm = viewModel<FinanceViewModel>(vmOwner, factory = factory)
                val dashboardVm = viewModel<DashboardViewModel>(vmOwner, factory = factory)
                FinanceHubScreen(
                    onBack = { navController.popBackStack() },
                    financeVm = financeVm,
                    dashboardVm = dashboardVm
                )
            }

            composable(Routes.MASTER) {
                val masterVm = viewModel<MasterDataViewModel>(vmOwner, factory = factory)
                MasterDataScreen(
                    vm = masterVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                val financeVm = viewModel<FinanceViewModel>(vmOwner, factory = factory)
                val backupVm = viewModel<BackupViewModel>(vmOwner, factory = factory)
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    authVm = authVm,
                    backupVm = backupVm,
                    financeVm = financeVm,
                    canManageMaster = Permissions.canManageMaster(authUi.role),
                    canBackup = Permissions.canBackup(authUi.role),
                    canResetData = Permissions.canResetData(authUi.role),
                    onGoMaster = { navController.navigate(Routes.MASTER) },
                    onGoSelfTest = { navController.navigate(Routes.SELF_TEST) },
                    onGoSampleWorkshop = { navController.navigate(Routes.SAMPLE_WORKSHOP) },
                    onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.SEARCH) {
                val searchVm = viewModel<OrderSearchViewModel>(vmOwner, factory = factory)
                OrderSearchScreen(
                    vm = searchVm,
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { o -> navController.navigate("${Routes.ORDER_DETAIL}/${o.id}") }
                )
            }

            composable(
                route = "${Routes.ORDER_DETAIL}/{orderId}",
                arguments = listOf(navArgument("orderId") { type = NavType.StringType })
            ) { entry ->
                val orderDetailVm =
                    viewModel<OrderDetailViewModel>(vmOwner, factory = factory)
                OrderDetailScreen(
                    vm = orderDetailVm,
                    orderIdText = entry.arguments?.getString("orderId"),
                    canReturnSale = Permissions.canReturnSale(authUi.role),
                    canEdit = Permissions.canEditOrder(authUi.role),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CUTTING) {
                val cuttingVm = viewModel<CuttingViewModel>(vmOwner, factory = factory)
                CuttingScreen(
                    vm = cuttingVm,
                    onBack = { navController.popBackStack() },
                    onGoSewing = { navController.navigate(Routes.SEWING) },
                    onGoStage = { stage ->
                        navController.navigate(
                            when (stage) {
                                WorkStage.CUT -> Routes.CUTTING
                                WorkStage.SEW -> Routes.SEWING
                                WorkStage.CHECK -> Routes.REVIEW
                            }
                        ) {
                            // مرحله‌ها هم‌سطح‌اند، نه تودرتو: رفتن از
                            // دوخت به نظارت نباید پشته را بلندتر کند،
                            // وگرنه دکمهٔ برگشتِ گوشی کاربر را در
                            // زنجیره‌ای از مرحله‌ها عقب می‌بَرد.
                            popUpTo(Routes.CUTTING) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.SEWING) {
                val sewingVm = viewModel<SewingViewModel>(vmOwner, factory = factory)
                SewingScreen(
                    vm = sewingVm,
                    onBack = { navController.popBackStack() },
                    // دوخت → نظارت
                    onGoReview = { navController.navigate(Routes.REVIEW) },
                    onGoStage = { stage ->
                        navController.navigate(
                            when (stage) {
                                WorkStage.CUT -> Routes.CUTTING
                                WorkStage.SEW -> Routes.SEWING
                                WorkStage.CHECK -> Routes.REVIEW
                            }
                        ) {
                            // مرحله‌ها هم‌سطح‌اند، نه تودرتو: رفتن از
                            // دوخت به نظارت نباید پشته را بلندتر کند،
                            // وگرنه دکمهٔ برگشتِ گوشی کاربر را در
                            // زنجیره‌ای از مرحله‌ها عقب می‌بَرد.
                            popUpTo(Routes.CUTTING) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.REVIEW) {
                val reviewVm = viewModel<ReviewViewModel>(vmOwner, factory = factory)
                ReviewScreen(
                    vm = reviewVm,
                    onBack = { navController.popBackStack() },
                    onGoSewing = { navController.navigate(Routes.SEWING) },
                    onGoStage = { stage ->
                        navController.navigate(
                            when (stage) {
                                WorkStage.CUT -> Routes.CUTTING
                                WorkStage.SEW -> Routes.SEWING
                                WorkStage.CHECK -> Routes.REVIEW
                            }
                        ) {
                            // مرحله‌ها هم‌سطح‌اند، نه تودرتو: رفتن از
                            // دوخت به نظارت نباید پشته را بلندتر کند،
                            // وگرنه دکمهٔ برگشتِ گوشی کاربر را در
                            // زنجیره‌ای از مرحله‌ها عقب می‌بَرد.
                            popUpTo(Routes.CUTTING) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

        }
    }
}
