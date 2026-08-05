// app/src/main/java/com/afghanjama/MainActivity.kt
package com.afghanjama

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.afghanjama.data.buildAppDatabase
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.AndroidLanHost
import com.afghanjama.prefs.AndroidSettings
import com.afghanjama.platform.AndroidDocs
import com.afghanjama.platform.AndroidFileExport
import com.afghanjama.platform.AndroidScreenBehavior
import com.afghanjama.platform.AndroidSystemActions
import com.afghanjama.platform.LocalDocs
import com.afghanjama.platform.LocalFileExport
import com.afghanjama.platform.LocalScreenBehavior
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.ui.platform.AndroidWidgets
import com.afghanjama.ui.platform.LocalWidgets
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.nav.AppNav
import com.afghanjama.ui.screens.CrashReportScreen
import com.afghanjama.ui.screens.PinLockScreen
import com.afghanjama.util.AppLock
import com.afghanjama.util.CrashLog
import com.afghanjama.ui.theme.KhayatYarTheme
import com.afghanjama.ui.vm.ActionCenterViewModel
import com.afghanjama.ui.vm.AttendanceViewModel
import com.afghanjama.ui.vm.AuditViewModel
import com.afghanjama.prefs.settings
import com.afghanjama.platform.AndroidPhotos
import com.afghanjama.platform.LocalPhotos
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.BackupViewModel
import com.afghanjama.ui.vm.CustomerDetailViewModel
import com.afghanjama.ui.vm.CustomersViewModel
import com.afghanjama.ui.vm.CuttingViewModel
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.FinishedSaleViewModel
import com.afghanjama.ui.vm.HomeViewModel
import com.afghanjama.ui.vm.DocumentsViewModel
import com.afghanjama.ui.vm.InventoryViewModel
import com.afghanjama.ui.vm.LedgerViewModel
import com.afghanjama.ui.vm.MasterDataViewModel
import com.afghanjama.ui.vm.MoneyMoveViewModel
import com.afghanjama.ui.vm.MyWorkViewModel
import com.afghanjama.ui.vm.ReportsViewModel
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
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.StockViewModel
import com.afghanjama.ui.vm.WarehouseViewModel

private const val REQ_NOTIFICATIONS = 1001

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // مجوز نوتیفیکیشن برای یادآوری تسویه هفتگی (اندروید ۱۳+).
        //
        // عمداً از ActivityResultContracts استفاده نمی‌شود: این اکتیویتی
        // یک FragmentActivity است و نسخهٔ fragment ای که biometric می‌آورد
        // فقط requestCodeهای ۱۶ بیتی را می‌پذیرد، در حالی که
        // ActivityResultRegistry کدِ بزرگ‌تر می‌سازد. مسیرِ قدیمی با کدِ
        // کوچک امن است. (تا امروز پنهان مانده بود چون اگر مجوز از قبل
        // داده شده باشد اصلاً درخواستی ارسال نمی‌شود.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS
            )
        }

        val db = buildAppDatabase(applicationContext)
        val repo = Repo(db)

        // تنظیمات یک بار ساخته می‌شود و از اینجا به کلِ درخت می‌رود.
        // صفحه‌هایی که به `:core` رفته‌اند به‌جای `LocalContext` این را
        // می‌گیرند — همان یک خط بود که نگهشان می‌داشت در `:app`.
        val settings = AndroidSettings(applicationContext)
        val system = AndroidSystemActions(applicationContext)
        val docs = AndroidDocs(applicationContext)
        // پوستهٔ نازکی روی `PhotoStore` — رفتارِ گوشی همان است که بود.
        val photos = AndroidPhotos(applicationContext)

        setContent {
          CompositionLocalProvider(
            LocalSettings provides settings,
            LocalSystemActions provides system,
            LocalDocs provides docs,
            LocalFileExport provides AndroidFileExport,
            LocalScreenBehavior provides AndroidScreenBehavior,
            LocalPhotos provides photos,
            // بی این خط، هر صفحه‌ای که پنجرهٔ تأیید یا منوی بازشو
            // دارد سرِ باز شدن می‌شکند.
            LocalWidgets provides AndroidWidgets
          ) {
            KhayatYarTheme {
                /*
                 * گزارشِ خرابیِ بارِ قبل — پیش از هر چیزِ دیگر.
                 *
                 * عمداً بالاتر از قفلِ اپ و از کلِ درختِ صفحه‌هاست: اگر
                 * خرابی در ساختِ همان درخت باشد، هر جای دیگری بگذاریمش
                 * پیش از دیده شدن دوباره می‌ترکد و کاربر هیچ‌وقت متن را
                 * نمی‌بیند.
                 *
                 * ۳۸ ViewModelِ پایین‌تر هم تا وقتی این صفحه سرِ پاست
                 * ساخته نمی‌شوند، چون `return@KhayatYarTheme` جلوی
                 * رسیدن به آن‌ها را می‌گیرد. یعنی اپی که سرِ باز شدن
                 * می‌ترکید، دستِ‌کم یک بار باز می‌شود و می‌گوید چرا.
                 */
                var crash by remember { mutableStateOf(CrashLog.pending(applicationContext)) }
                val report = crash
                if (report != null) {
                    CrashReportScreen(
                        report = report,
                        onSend = { system.shareText("گزارشِ خرابیِ خیاط‌یار", report) },
                        onContinue = {
                            CrashLog.clear(applicationContext)
                            crash = null
                        }
                    )
                    return@KhayatYarTheme
                }

                // قفل اپ: اگر رمز تنظیم شده باشد، اول باید باز شود
                var unlocked by remember { mutableStateOf(!AppLock.isPinSet(applicationContext)) }
                if (!unlocked) {
                    PinLockScreen(onUnlock = { unlocked = true })
                    return@KhayatYarTheme
                }

                val authVm = remember { AuthViewModel(applicationContext.settings) }
                val financeVm = remember { FinanceViewModel(repo) }
                val inventoryVm = remember { InventoryViewModel(repo) }
                val cuttingVm = remember { CuttingViewModel(repo) }
                val sewingVm = remember { SewingViewModel(repo) }
                val reviewVm = remember { ReviewViewModel(repo) }
                val masterVm = remember { MasterDataViewModel(repo) }
                val dashboardVm = remember { DashboardViewModel(repo) }
                val searchVm = remember { OrderSearchViewModel(repo) }
                val stockVm = remember { StockViewModel(repo) }
                val backupVm = remember { BackupViewModel(repo) }
                val orderDetailVm = remember { OrderDetailViewModel(repo) }
                val procurementVm = remember { ProcurementViewModel(repo) }
                val warehouseVm = remember { WarehouseViewModel(repo) }
                val homeVm = remember { HomeViewModel(repo) }
                val productionVm = remember { ProductionViewModel(repo) }
                val ledgerVm = remember { LedgerViewModel(repo) }
                val documentsVm = remember { DocumentsViewModel(repo) }
                val reportsVm = remember { ReportsViewModel(repo) }
                val finishedSaleVm = remember { FinishedSaleViewModel(repo) }
                val customerDirVm = remember { CustomersViewModel(repo) }
                val customerDetailVm = remember { CustomerDetailViewModel(repo) }
                val attendanceVm = remember { AttendanceViewModel(repo) }
                val auditVm = remember { AuditViewModel(repo) }
                val actionVm = remember { ActionCenterViewModel(repo) }
                val payrollVm = remember { PayrollViewModel(repo) }
                val performanceVm = remember { PerformanceViewModel(repo) }
                val deliveryQueueVm = remember { DeliveryQueueViewModel(repo) }
                val newSaleVm = remember { NewSaleViewModel(repo) }
                val breakVm = remember { BreakTimeViewModel(repo) }
                val linkVm = remember { WorkshopLinkViewModel(repo, AndroidLanHost(applicationContext)) }
                val boardVm = remember { BoardViewModel(repo) }
                val selfTestVm = remember { SelfTestViewModel(repo) }
                val purchasePlanVm = remember { PurchasePlanViewModel(repo) }
                val myWorkVm = remember { MyWorkViewModel(repo) }
                val moneyVm = remember { MoneyMoveViewModel(repo) }
                val purchaseReturnVm = remember { PurchaseReturnViewModel(repo) }

                AppNav(
                    authVm = authVm,
                    inventoryVm = inventoryVm,
                    cuttingVm = cuttingVm,
                    sewingVm = sewingVm,
                    reviewVm = reviewVm,
                    financeVm = financeVm,
                    masterVm = masterVm,
                    dashboardVm = dashboardVm,
                    searchVm = searchVm,
                    stockVm = stockVm,
                    backupVm = backupVm,
                    orderDetailVm = orderDetailVm,
                    procurementVm = procurementVm,
                    warehouseVm = warehouseVm,
                    homeVm = homeVm,
                    productionVm = productionVm,
                    ledgerVm = ledgerVm,
                    documentsVm = documentsVm,
                    reportsVm = reportsVm,
                    finishedSaleVm = finishedSaleVm,
                    customerDirVm = customerDirVm,
                    customerDetailVm = customerDetailVm,
                    attendanceVm = attendanceVm,
                    auditVm = auditVm,
                    actionVm = actionVm,
                    payrollVm = payrollVm,
                    performanceVm = performanceVm,
                    deliveryQueueVm = deliveryQueueVm,
                    newSaleVm = newSaleVm,
                    breakVm = breakVm,
                    linkVm = linkVm,
                    boardVm = boardVm,
                    selfTestVm = selfTestVm,
                    purchasePlanVm = purchasePlanVm,
                    myWorkVm = myWorkVm,
                    moneyVm = moneyVm,
                    purchaseReturnVm = purchaseReturnVm
                )
            }
          }
        }
    }
}
