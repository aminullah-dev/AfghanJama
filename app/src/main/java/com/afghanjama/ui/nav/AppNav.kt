// app/src/main/java/com/afghanjama/ui/nav/AppNav.kt
package com.afghanjama.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.afghanjama.ui.screens.CuttingScreen
import com.afghanjama.ui.screens.InventoryScreen
import com.afghanjama.ui.screens.LoginScreen
import com.afghanjama.ui.screens.MasterDataScreen
import com.afghanjama.ui.screens.PostLoginQuoteScreen
import com.afghanjama.ui.screens.PurchasePlanScreen
import com.afghanjama.ui.screens.ReviewScreen
import com.afghanjama.ui.screens.SalesScreen
import com.afghanjama.ui.screens.SewingScreen
import com.afghanjama.ui.screens.WalletProfitScreen
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.CuttingViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.InventoryViewModel
import com.afghanjama.ui.vm.MasterDataViewModel
import com.afghanjama.ui.vm.PurchaseViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SalesViewModel
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.UserRole

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
    masterVm: MasterDataViewModel
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

    NavHost(
        navController = navController,
        startDestination = start
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
                onGoWallet = { navController.navigate(Routes.WALLET) },
                onGoMaster = { navController.navigate(Routes.MASTER) },
                onGoCutting = { navController.navigate(Routes.CUTTING) }
            )
        }

        composable(Routes.PURCHASE) {
            PurchasePlanScreen(
                vm = purchaseVm,
                masterVm = masterVm,
                financeVm = financeVm,
                onDone = { navController.popBackStack() }
            )
        }

        composable(Routes.WALLET) {
            WalletProfitScreen(vm = financeVm)
        }

        composable(Routes.MASTER) {
            MasterDataScreen(
                vm = masterVm,
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
