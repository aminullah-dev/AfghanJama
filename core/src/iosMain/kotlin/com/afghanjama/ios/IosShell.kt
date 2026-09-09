package com.afghanjama.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.afghanjama.data.repo.Repo
import com.afghanjama.ios.data.openDatabase
import com.afghanjama.ui.screens.HomeDashboardScreen
import com.afghanjama.ui.screens.LoginScreen
import com.afghanjama.ui.vm.ActionCenterViewModel
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.HomeViewModel
import com.afghanjama.ui.vm.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * پوستهٔ نسخهٔ آیفون: ورود، و بعد داشبورد روی دفترِ واقعی.
 *
 * **پیمایشِ کامل هنوز نیست.** `AppNav` روی اندروید ۹۷ مقصد دارد و
 * `Shell` روی ویندوز همان‌قدر؛ آوردنِ هر دو به iOS کارِ جداگانه‌ای است.
 * آنچه اینجا هست، کمترین چیزی است که ثابت می‌کند **دفتر روی آیفون باز
 * می‌شود و صفحه‌های واقعی رویش می‌نشینند** — نه یک صفحهٔ نمایشی.
 *
 * دکمه‌های ناوبریِ داشبورد فعلاً کاری نمی‌کنند و این عمداً صریح است: به
 * جای اینکه صفحه‌ای نصفه‌کاره باز شود، هیچ اتفاقی نمی‌افتد. مقصدها با
 * همان `Routes`ِ مشترک اضافه خواهند شد.
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

        is Ledger.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                "دفترِ کارگاه باز نشد:\n${l.message}",
                Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        is Ledger.Ready -> {
            val home = remember(l.repo) { HomeViewModel(l.repo) }
            val action = remember(l.repo) { ActionCenterViewModel(l.repo) }
            HomeDashboardScreen(
                vm = home,
                actionVm = action,
                isManager = ui.role == UserRole.MANAGER,
                canSeeCustomers = ui.role == UserRole.MANAGER,
                canBuyMaterial = ui.role == UserRole.MANAGER,
                // ── مقصدها هنوز نیامده‌اند ──
                onGoActionCenter = {}, onGoProcurement = {}, onGoWarehouse = {},
                onGoStockLedger = {}, onGoProduction = {}, onGoFinishedSales = {},
                onGoDeliveryQueue = {}, onGoAttendance = {}, onGoPayroll = {},
                onGoPerformance = {}, onGoPurchasePlan = {}, onGoMyWork = {},
                onGoPay = {}, onGoReceive = {}, onGoDailyTrade = {},
                onGoCustomers = {}, onGoFinance = {}, onGoLedger = {},
                onGoDocuments = {}, onGoReports = {}, onGoAudit = {},
                onGoSearch = {}, onGoSettings = {}, onGoQuoteCalc = {},
                onGoGuide = {}, onGoWorkshopLink = {}, onGoBoard = {},
            )
        }
    }
}
