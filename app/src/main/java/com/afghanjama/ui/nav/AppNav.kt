// app/src/main/java/com/afghanjama/ui/nav/AppNav.kt
package com.afghanjama.ui.nav

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.afghanjama.ui.screens.ActionCenterScreen
import com.afghanjama.ui.screens.AttendanceScreen
import com.afghanjama.ui.screens.AuditScreen
import com.afghanjama.ui.screens.CustomerDetailScreen
import com.afghanjama.ui.screens.CustomersScreen
import com.afghanjama.ui.screens.CuttingScreen
import com.afghanjama.ui.screens.DailyTradeScreen
import com.afghanjama.ui.screens.DeliveryQueueScreen
import com.afghanjama.ui.screens.BoardScreen
import com.afghanjama.ui.screens.GuideScreen
import com.afghanjama.ui.screens.WorkshopLinkScreen
import com.afghanjama.ui.screens.NewSaleScreen
import com.afghanjama.ui.screens.SelfTestScreen
import com.afghanjama.ui.screens.FinishedWarehouseScreen
import com.afghanjama.ui.screens.HomeDashboardScreen
import com.afghanjama.ui.screens.OrderDetailScreen
import com.afghanjama.ui.screens.FinanceHubScreen
import com.afghanjama.ui.screens.InventoryScreen
import com.afghanjama.ui.screens.LoginScreen
import com.afghanjama.ui.screens.MasterDataScreen
import com.afghanjama.ui.screens.MaterialWarehouseScreen
import com.afghanjama.ui.screens.DocumentsScreen
import com.afghanjama.ui.screens.LedgerScreen
import com.afghanjama.ui.screens.MoneyMoveScreen
import com.afghanjama.ui.screens.MyWorkScreen
import com.afghanjama.ui.screens.OrderSearchScreen
import com.afghanjama.ui.screens.PayrollScreen
import com.afghanjama.ui.screens.PerformanceScreen
import com.afghanjama.ui.screens.PurchasePlanScreen
import com.afghanjama.ui.screens.PurchaseReturnScreen
import com.afghanjama.ui.screens.PostLoginQuoteScreen
import com.afghanjama.ui.screens.ProcurementScreen
import com.afghanjama.ui.screens.ProductionOrderScreen
import com.afghanjama.ui.screens.ReportsScreen
import com.afghanjama.ui.screens.ReviewScreen
import com.afghanjama.ui.screens.SettingsScreen
import com.afghanjama.ui.screens.SewingScreen
import com.afghanjama.ui.screens.StockLedgerScreen
import com.afghanjama.ui.vm.ActionCenterViewModel
import com.afghanjama.ui.vm.AttendanceViewModel
import com.afghanjama.ui.vm.AuditViewModel
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.BackupViewModel
import com.afghanjama.ui.vm.CustomerDetailViewModel
import com.afghanjama.ui.vm.CustomersViewModel
import com.afghanjama.ui.vm.CuttingViewModel
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.DocumentsViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.FinishedSaleViewModel
import com.afghanjama.ui.vm.HomeViewModel
import com.afghanjama.ui.vm.InventoryViewModel
import com.afghanjama.ui.vm.LedgerViewModel
import com.afghanjama.ui.vm.MasterDataViewModel
import com.afghanjama.ui.vm.MoneyMoveViewModel
import com.afghanjama.ui.vm.MyWorkViewModel
import com.afghanjama.ui.vm.OrderDetailViewModel
import com.afghanjama.ui.vm.OrderSearchViewModel
import com.afghanjama.ui.vm.PayrollViewModel
import com.afghanjama.ui.vm.DeliveryQueueViewModel
import com.afghanjama.ui.vm.BoardViewModel
import com.afghanjama.ui.vm.BreakTimeViewModel
import com.afghanjama.ui.vm.WorkshopLinkViewModel
import com.afghanjama.ui.vm.NewSaleViewModel
import com.afghanjama.ui.vm.SelfTestViewModel
import com.afghanjama.ui.vm.PerformanceViewModel
import com.afghanjama.ui.vm.PurchasePlanViewModel
import com.afghanjama.ui.vm.PurchaseReturnViewModel
import com.afghanjama.ui.vm.ProcurementViewModel
import com.afghanjama.ui.vm.ProductionViewModel
import com.afghanjama.ui.vm.ReportsViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.Permissions
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.StockViewModel
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.ui.vm.WarehouseViewModel

/** آیتم نوار پایین. */
private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/** آیتم‌های نوار پایین بر اساس نقش کاربر. */
private fun bottomItemsFor(role: UserRole): List<BottomItem> = when (role) {
    UserRole.MANAGER -> listOf(
        BottomItem(Routes.HOME, "خانه", Icons.Default.Home),
        BottomItem(Routes.INVENTORY, "تولید", Icons.Default.Inventory2),
        BottomItem(Routes.CUTTING, "برش", Icons.Default.ContentCut),
        BottomItem(Routes.SEWING, "دوخت", Icons.Default.Checkroom),
        BottomItem(Routes.REVIEW, "نظارت", Icons.Default.VerifiedUser),
        BottomItem(Routes.FINISHED_SALES, "فروش", Icons.Default.Storefront),
        BottomItem(Routes.FINANCE, "مالی", Icons.Default.Payments)
    )

    UserRole.PURCHASE -> listOf(
        BottomItem(Routes.HOME, "خانه", Icons.Default.Home),
        BottomItem(Routes.PROCUREMENT, "خرید مواد", Icons.Default.ShoppingCart),
        BottomItem(Routes.WAREHOUSE, "انبار", Icons.Default.Warehouse),
        BottomItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )

    UserRole.SEWING -> listOf(
        BottomItem(Routes.HOME, "خانه", Icons.Default.Home),
        BottomItem(Routes.CUTTING, "برش", Icons.Default.ContentCut),
        BottomItem(Routes.SEWING, "دوخت", Icons.Default.Checkroom),
        BottomItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )

    UserRole.REVIEW -> listOf(
        BottomItem(Routes.HOME, "خانه", Icons.Default.Home),
        BottomItem(Routes.REVIEW, "نظارت", Icons.Default.VerifiedUser),
        BottomItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )

    UserRole.SALES -> listOf(
        BottomItem(Routes.HOME, "خانه", Icons.Default.Home),
        BottomItem(Routes.FINISHED_SALES, "فروش", Icons.Default.Storefront),
        BottomItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )
}

@Composable
fun AppNav(
    authVm: AuthViewModel,
    inventoryVm: InventoryViewModel,
    cuttingVm: CuttingViewModel,
    sewingVm: SewingViewModel,
    reviewVm: ReviewViewModel,
    financeVm: FinanceViewModel,
    masterVm: MasterDataViewModel,
    dashboardVm: DashboardViewModel,
    searchVm: OrderSearchViewModel,
    stockVm: StockViewModel,
    backupVm: BackupViewModel,
    orderDetailVm: OrderDetailViewModel,
    procurementVm: ProcurementViewModel,
    warehouseVm: WarehouseViewModel,
    homeVm: HomeViewModel,
    productionVm: ProductionViewModel,
    ledgerVm: LedgerViewModel,
    documentsVm: DocumentsViewModel,
    reportsVm: ReportsViewModel,
    finishedSaleVm: FinishedSaleViewModel,
    customerDirVm: CustomersViewModel,
    customerDetailVm: CustomerDetailViewModel,
    attendanceVm: AttendanceViewModel,
    auditVm: AuditViewModel,
    actionVm: ActionCenterViewModel,
    payrollVm: PayrollViewModel,
    performanceVm: PerformanceViewModel,
    deliveryQueueVm: DeliveryQueueViewModel,
    newSaleVm: NewSaleViewModel,
    breakVm: BreakTimeViewModel,
    linkVm: WorkshopLinkViewModel,
    boardVm: BoardViewModel,
    selfTestVm: SelfTestViewModel,
    purchasePlanVm: PurchasePlanViewModel,
    myWorkVm: MyWorkViewModel,
    moneyVm: MoneyMoveViewModel,
    purchaseReturnVm: PurchaseReturnViewModel
) {
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
                                selected = currentRoute == item.route,
                                onClick = {
                                    if (currentRoute != item.route) {
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
                    onGoGuide = { navController.navigate(Routes.GUIDE) },
                    onGoWorkshopLink = { navController.navigate(Routes.WORKSHOP_LINK) },
                    onGoBoard = { navController.navigate(Routes.BOARD) }
                )
            }

            composable(Routes.PRODUCTION_ORDER) {
                ProductionOrderScreen(
                    vm = productionVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.FINISHED_SALES) {
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
                AttendanceScreen(
                    vm = attendanceVm,
                    breakVm = breakVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PAYROLL) {
                PayrollScreen(
                    vm = payrollVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PERFORMANCE) {
                PerformanceScreen(
                    vm = performanceVm,
                    onBack = { navController.popBackStack() }
                )
            }

            // ورودی‌اش در تنظیمات است — «خودآزمایی و سلامتِ داده»
            composable(Routes.SELF_TEST) {
                SelfTestScreen(
                    vm = selfTestVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.NEW_SALE) {
                NewSaleScreen(
                    vm = newSaleVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.BOARD) {
                BoardScreen(
                    vm = boardVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.WORKSHOP_LINK) {
                WorkshopLinkScreen(
                    vm = linkVm,
                    isManager = authUi.role == UserRole.MANAGER,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.GUIDE) {
                GuideScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.DELIVERY_QUEUE) {
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
                PurchaseReturnScreen(
                    vm = purchaseReturnVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PAY) {
                MoneyMoveScreen(
                    vm = moneyVm,
                    startAsPayment = true,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.RECEIVE) {
                MoneyMoveScreen(
                    vm = moneyVm,
                    startAsPayment = false,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.MY_WORK) {
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
                PurchasePlanScreen(
                    vm = purchasePlanVm,
                    onGoProcurement = { navController.navigate(Routes.PROCUREMENT) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CUSTOMERS) {
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
                CustomerDetailScreen(
                    vm = customerDetailVm,
                    customerId = entry.arguments?.getLong("customerId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenOrder = { oid -> navController.navigate("${Routes.ORDER_DETAIL}/$oid") }
                )
            }

            composable(Routes.PROCUREMENT) {
                ProcurementScreen(
                    vm = procurementVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.WAREHOUSE) {
                MaterialWarehouseScreen(
                    vm = warehouseVm,
                    canAdjust = Permissions.canAdjustMaterial(authUi.role),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.STOCK_LEDGER) {
                StockLedgerScreen(
                    vm = warehouseVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.INVENTORY) {
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
                LedgerScreen(
                    vm = ledgerVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.DOCUMENTS) {
                DocumentsScreen(
                    vm = documentsVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.REPORTS) {
                ReportsScreen(
                    vm = reportsVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.AUDIT) {
                AuditScreen(
                    vm = auditVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ACTION_CENTER) {
                ActionCenterScreen(
                    vm = actionVm,
                    onNavigate = { route -> navController.navigate(route) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.FINANCE) {
                FinanceHubScreen(
                    onBack = { navController.popBackStack() },
                    financeVm = financeVm,
                    dashboardVm = dashboardVm
                )
            }

            composable(Routes.MASTER) {
                MasterDataScreen(
                    vm = masterVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
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
                    onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.SEARCH) {
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
                OrderDetailScreen(
                    vm = orderDetailVm,
                    orderIdText = entry.arguments?.getString("orderId"),
                    canReturnSale = Permissions.canReturnSale(authUi.role),
                    canEdit = Permissions.canEditOrder(authUi.role),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CUTTING) {
                CuttingScreen(
                    vm = cuttingVm,
                    onBack = { navController.popBackStack() },
                    onGoSewing = { navController.navigate(Routes.SEWING) }
                )
            }

            composable(Routes.SEWING) {
                SewingScreen(
                    vm = sewingVm,
                    onBack = { navController.popBackStack() },
                    onGoReview = { navController.navigate(Routes.REVIEW) } // ✅ دوخت → نظارت
                )
            }

            composable(Routes.REVIEW) {
                ReviewScreen(
                    vm = reviewVm,
                    onBack = { navController.popBackStack() },
                    onGoSewing = { navController.navigate(Routes.SEWING) }
                )
            }

        }
    }
}
