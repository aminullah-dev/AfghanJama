// app/src/main/java/com/afghanjama/ui/nav/AppNav.kt
package com.afghanjama.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.afghanjama.ui.screens.CuttingScreen
import com.afghanjama.ui.screens.FabricStockScreen
import com.afghanjama.ui.screens.FinanceHubScreen
import com.afghanjama.ui.screens.InventoryScreen
import com.afghanjama.ui.screens.LoginScreen
import com.afghanjama.ui.screens.MasterDataScreen
import com.afghanjama.ui.screens.OrderSearchScreen
import com.afghanjama.ui.screens.PostLoginQuoteScreen
import com.afghanjama.ui.screens.PurchasePlanScreen
import com.afghanjama.ui.screens.ReviewScreen
import com.afghanjama.ui.screens.SalesScreen
import com.afghanjama.ui.screens.SettingsScreen
import com.afghanjama.ui.screens.SewingScreen
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.CustomerAccountsViewModel
import com.afghanjama.ui.vm.CuttingViewModel
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.InventoryViewModel
import com.afghanjama.ui.vm.MasterDataViewModel
import com.afghanjama.ui.vm.OrderSearchViewModel
import com.afghanjama.ui.vm.PurchaseViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SalesViewModel
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.StockViewModel
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.ui.vm.WagesViewModel

/** آیتم نوار پایین. */
private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/** آیتم‌های نوار پایین بر اساس نقش کاربر. */
private fun bottomItemsFor(role: UserRole): List<BottomItem> = when (role) {
    UserRole.MANAGER -> listOf(
        BottomItem(Routes.INVENTORY, "انبار", Icons.Default.Inventory2),
        BottomItem(Routes.CUTTING, "برش", Icons.Default.ContentCut),
        BottomItem(Routes.SEWING, "دوخت", Icons.Default.Checkroom),
        BottomItem(Routes.REVIEW, "نظارت", Icons.Default.VerifiedUser),
        BottomItem(Routes.SALES, "فروش", Icons.Default.Storefront),
        BottomItem(Routes.FINANCE, "مالی", Icons.Default.Payments)
    )

    UserRole.PURCHASE -> listOf(
        BottomItem(Routes.INVENTORY, "انبار", Icons.Default.Inventory2),
        BottomItem(Routes.PURCHASE, "خرید", Icons.Default.ShoppingCart),
        BottomItem(Routes.STOCK, "پارچه", Icons.Default.Layers),
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
    purchaseVm: PurchaseViewModel,
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
    stockVm: StockViewModel
) {
    val navController = rememberNavController()
    val authUi by authVm.ui.collectAsState()

    val start = when {
        !authUi.isLoggedIn -> Routes.LOGIN
        else -> when (authUi.role) {
            UserRole.MANAGER -> Routes.POST_LOGIN
            UserRole.PURCHASE -> Routes.PURCHASE
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
                            UserRole.PURCHASE -> Routes.PURCHASE
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
                        navController.navigate(Routes.INVENTORY) {
                            popUpTo(Routes.POST_LOGIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.INVENTORY) {
                InventoryScreen(
                    vm = inventoryVm,
                    financeVm = financeVm,
                    role = authUi.role,
                    onGoPurchase = { navController.navigate(Routes.PURCHASE) },
                    onGoWallet = { navController.navigate(Routes.FINANCE) },
                    onGoSettings = { navController.navigate(Routes.SETTINGS) },
                    onGoSearch = { navController.navigate(Routes.SEARCH) },
                    onGoStock = { navController.navigate(Routes.STOCK) },
                    onGoCutting = { navController.navigate(Routes.CUTTING) }
                )
            }

            composable(Routes.PURCHASE) {
                PurchasePlanScreen(
                    vm = purchaseVm,
                    masterVm = masterVm,
                    financeVm = financeVm,
                    stockVm = stockVm,
                    onDone = { navController.popBackStack() }
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
                    canManageMaster = authUi.role == UserRole.MANAGER,
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
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.STOCK) {
                FabricStockScreen(
                    vm = stockVm,
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
