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
import com.afghanjama.prefs.AndroidSettings
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.nav.AppNav
import com.afghanjama.ui.screens.PinLockScreen
import com.afghanjama.util.AppLock
import com.afghanjama.ui.theme.KhayatYarTheme
import com.afghanjama.ui.vm.ActionCenterViewModel
import com.afghanjama.ui.vm.AttendanceViewModel
import com.afghanjama.ui.vm.AuditViewModel
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

        setContent {
          CompositionLocalProvider(LocalSettings provides settings) {
            KhayatYarTheme {
                // قفل اپ: اگر رمز تنظیم شده باشد، اول باید باز شود
                var unlocked by remember { mutableStateOf(!AppLock.isPinSet(applicationContext)) }
                if (!unlocked) {
                    PinLockScreen(onUnlock = { unlocked = true })
                    return@KhayatYarTheme
                }

                val authVm = remember { AuthViewModel(application) }
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
                val linkVm = remember { WorkshopLinkViewModel(repo) }
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
