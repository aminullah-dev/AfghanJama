package com.afghanjama.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.afghanjama.ui.nav.Routes
import com.afghanjama.ui.nav.bottomItemsFor
import com.afghanjama.ui.screens.ActionCenterScreen
import com.afghanjama.ui.screens.FinanceHubScreen
import com.afghanjama.ui.screens.FinishedWarehouseScreen
import com.afghanjama.ui.screens.HomeDashboardScreen
import com.afghanjama.ui.screens.InventoryScreen
import com.afghanjama.ui.screens.LoginScreen
import com.afghanjama.ui.vm.ActionCenterViewModel
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.FinishedSaleViewModel
import com.afghanjama.ui.vm.HomeViewModel
import com.afghanjama.ui.vm.InventoryViewModel
import com.afghanjama.ui.vm.UserRole
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
 * نه اینکه فکر کند خراب است.
 */

/** وضعیتِ باز شدنِ دفتر — تا پیش از آن نباید هیچ صفحه‌ای رسم شود. */
private sealed interface Ledger {
    data object Opening : Ledger
    data class Ready(val repo: Repo) : Ledger
    data class Failed(val message: String) : Ledger
}

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

        is Ledger.Ready -> LoggedIn(l.repo, ui.role)
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

@Composable
private fun LoggedIn(repo: Repo, role: UserRole) {
    val nav = rememberNavStack()
    val items = remember(role) { bottomItemsFor(role) }

    /*
     * ViewModelها به **دفتر** بسته‌اند، نه به صفحه.
     *
     * همان تصمیمی که `AppNav` گرفت و توضیحش را نوشت: اگر هر مقصد
     * ViewModelِ خودش را می‌ساخت، فرمِ نیمه‌پرشده سرِ برگشت خالی
     * می‌شد. اینجا با `remember(repo)` عمرشان به عمرِ دفتر بسته است.
     */
    val home = remember(repo) { HomeViewModel(repo) }
    val action = remember(repo) { ActionCenterViewModel(repo) }
    val inventory = remember(repo) { InventoryViewModel(repo) }
    val finance = remember(repo) { FinanceViewModel(repo) }
    val dashboard = remember(repo) { DashboardViewModel(repo) }
    val finishedSale = remember(repo) { FinishedSaleViewModel(repo) }

    val isManager = role == UserRole.MANAGER
    val back = { nav.back() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = item.owns(nav.current),
                        onClick = { nav.switchTab(item.route) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (nav.current) {
                Routes.HOME -> HomeDashboardScreen(
                    vm = home,
                    actionVm = action,
                    isManager = isManager,
                    canSeeCustomers = isManager,
                    canBuyMaterial = isManager,
                    onGoActionCenter = { nav.go(Routes.ACTION_CENTER) },
                    onGoWarehouse = { nav.go(Routes.FINISHED_SALES) },
                    onGoFinishedSales = { nav.go(Routes.FINISHED_SALES) },
                    onGoFinance = { nav.go(Routes.FINANCE) },
                    // ── مقصدهایی که صفحه‌شان هنوز به :core نیامده ──
                    onGoProcurement = {}, onGoStockLedger = {},
                    onGoProduction = {}, onGoDeliveryQueue = {},
                    onGoAttendance = {}, onGoPayroll = {}, onGoPerformance = {},
                    onGoPurchasePlan = {}, onGoMyWork = {}, onGoPay = {},
                    onGoReceive = {}, onGoDailyTrade = {}, onGoCustomers = {},
                    onGoLedger = {}, onGoDocuments = {}, onGoReports = {},
                    onGoAudit = {}, onGoSearch = {}, onGoSettings = {},
                    onGoQuoteCalc = {}, onGoGuide = {}, onGoWorkshopLink = {},
                    onGoBoard = {},
                )

                Routes.ACTION_CENTER -> ActionCenterScreen(
                    vm = action,
                    // مرکزِ هشدار خودش مقصد می‌دهد؛ همان رشته‌های
                    // `Routes` است، پس مستقیم به پشته می‌رود.
                    onNavigate = { nav.go(it) },
                    onBack = back,
                )

                Routes.INVENTORY -> InventoryScreen(
                    vm = inventory,
                    financeVm = finance,
                    role = role,
                    onGoWallet = { nav.switchTab(Routes.FINANCE) },
                    onGoStartProduction = {}, onGoSettings = {},
                    onGoSearch = {}, onGoStock = {}, onGoCutting = {},
                    onOpenDetail = {},
                    onBack = back,
                )

                Routes.FINISHED_SALES -> FinishedWarehouseScreen(
                    vm = finishedSale,
                    onBack = back,
                )

                Routes.FINANCE -> FinanceHubScreen(
                    financeVm = finance,
                    dashboardVm = dashboard,
                    onBack = back,
                )

                /*
                 * «کارگاه» — سه مرحلهٔ برش و دوخت و نظارت.
                 *
                 * صفحه‌هایشان هنوز در `:app` هستند و نه `:core`، پس روی
                 * آیفون در دسترس نیستند. آوردنشان کارِ جداگانه‌ای است و
                 * تا آن روز این پیام صادق‌تر از یک صفحهٔ خالی است.
                 */
                Routes.CUTTING, Routes.SEWING, Routes.REVIEW ->
                    Message(
                        "صفحه‌های کارگاه (برش، دوخت، نظارت) هنوز به نسخهٔ " +
                            "آیفون نیامده‌اند.\nفعلاً از گوشیِ اندرویدی یا " +
                            "کامپیوتر استفاده کنید."
                    )

                else -> Message("این صفحه هنوز در نسخهٔ آیفون نیست: ${nav.current}")
            }
        }
    }
}
