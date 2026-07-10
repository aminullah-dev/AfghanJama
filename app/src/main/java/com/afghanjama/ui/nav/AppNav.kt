// app/src/main/java/com/afghanjama/ui/nav/AppNav.kt
package com.afghanjama.ui.nav

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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.afghanjama.ui.screens.AttendanceScreen
import com.afghanjama.ui.screens.CustomerDetailScreen
import com.afghanjama.ui.screens.CustomersScreen
import com.afghanjama.ui.screens.CuttingScreen
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
import com.afghanjama.ui.screens.OrderSearchScreen
import com.afghanjama.ui.screens.PostLoginQuoteScreen
import com.afghanjama.ui.screens.ProcurementScreen
import com.afghanjama.ui.screens.ProductionOrderScreen
import com.afghanjama.ui.screens.ReviewScreen
import com.afghanjama.ui.screens.SalesScreen
import com.afghanjama.ui.screens.SettingsScreen
import com.afghanjama.ui.screens.SewingScreen
import com.afghanjama.ui.screens.StockLedgerScreen
import com.afghanjama.ui.screens.SupplierScreen
import com.afghanjama.ui.vm.AttendanceViewModel
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.BackupViewModel
import com.afghanjama.ui.vm.CustomerAccountsViewModel
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
import com.afghanjama.ui.vm.OrderDetailViewModel
import com.afghanjama.ui.vm.OrderSearchViewModel
import com.afghanjama.ui.vm.ProcurementViewModel
import com.afghanjama.ui.vm.ProductionViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SalesViewModel
import com.afghanjama.ui.vm.Permissions
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.StockViewModel
import com.afghanjama.ui.vm.SupplierViewModel
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.ui.vm.WagesViewModel
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
        BottomItem(Routes.INVENTORY, "تولید", Icons.Default.Inventory2),
        BottomItem(Routes.CUTTING, "برش", Icons.Default.ContentCut),
        BottomItem(Routes.SEWING, "دوخت", Icons.Default.Checkroom),
        BottomItem(Routes.REVIEW, "نظارت", Icons.Default.VerifiedUser),
        BottomItem(Routes.SALES, "فروش", Icons.Default.Storefront),
        BottomItem(Routes.FINANCE, "مالی", Icons.Default.Payments)
    )

    UserRole.PURCHASE -> listOf(
        BottomItem(Routes.HOME, "خانه", Icons.Default.Home),
        BottomItem(Routes.PROCUREMENT, "خرید مواد", Icons.Default.ShoppingCart),
        BottomItem(Routes.WAREHOUSE, "انبار", Icons.Default.Warehouse),
        BottomItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )

    UserRole.SEWING -> listOf(
        BottomItem(Routes.CUTTING, "برش", Icons.Default.ContentCut),
        BottomItem(Routes.SEWING, "دوخت", Icons.Default.Checkroom),
        BottomItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )

    UserRole.REVIEW -> listOf(
        BottomItem(Routes.REVIEW, "نظارت", Icons.Default.VerifiedUser),
        BottomItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )

    UserRole.SALES -> listOf(
        BottomItem(Routes.SALES, "فروش", Icons.Default.Storefront),
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
    salesVm: SalesViewModel,
    financeVm: FinanceViewModel,
    masterVm: MasterDataViewModel,
    wagesVm: WagesViewModel,
    customersVm: CustomerAccountsViewModel,
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
    finishedSaleVm: FinishedSaleViewModel,
    supplierVm: SupplierViewModel,
    customerDirVm: CustomersViewModel,
    customerDetailVm: CustomerDetailViewModel,
    attendanceVm: AttendanceViewModel
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
            UserRole.SALES -> Routes.SALES
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

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
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
                            UserRole.SALES -> Routes.SALES
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
                    isManager = authUi.role == UserRole.MANAGER,
                    onGoProcurement = { navController.navigate(Routes.PROCUREMENT) },
                    onGoWarehouse = { navController.navigate(Routes.WAREHOUSE) },
                    onGoStockLedger = { navController.navigate(Routes.STOCK_LEDGER) },
                    onGoProduction = { navController.navigate(Routes.INVENTORY) },
                    onGoFinishedSales = { navController.navigate(Routes.FINISHED_SALES) },
                    onGoSuppliers = { navController.navigate(Routes.SUPPLIERS) },
                    onGoAttendance = { navController.navigate(Routes.ATTENDANCE) },
                    onGoCustomers = { navController.navigate(Routes.CUSTOMERS) },
                    // -- customers wired --
                    onGoSales = { navController.navigate(Routes.SALES) },
                    onGoFinance = { navController.navigate(Routes.FINANCE) },
                    onGoLedger = { navController.navigate(Routes.LEDGER) },
                    onGoDocuments = { navController.navigate(Routes.DOCUMENTS) },
                    onGoSearch = { navController.navigate(Routes.SEARCH) },
                    onGoSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }

            composable(Routes.PRODUCTION_ORDER) {
                ProductionOrderScreen(
                    vm = productionVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.FINISHED_SALES) {
                FinishedWarehouseScreen(
                    vm = finishedSaleVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SUPPLIERS) {
                SupplierScreen(
                    vm = supplierVm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ATTENDANCE) {
                AttendanceScreen(
                    vm = attendanceVm,
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

            composable(Routes.FINANCE) {
                FinanceHubScreen(
                    financeVm = financeVm,
                    wagesVm = wagesVm,
                    customersVm = customersVm,
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
                    authVm = authVm,
                    backupVm = backupVm,
                    canManageMaster = Permissions.canManageMaster(authUi.role),
                    canBackup = Permissions.canBackup(authUi.role),
                    onGoMaster = { navController.navigate(Routes.MASTER) },
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
                    onGoSales = { navController.navigate(Routes.SALES) },
                    onGoSewing = { navController.navigate(Routes.SEWING) }
                )
            }

            composable(Routes.SALES) {
                SalesScreen(
                    vm = salesVm,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
