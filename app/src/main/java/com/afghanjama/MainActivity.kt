// app/src/main/java/com/afghanjama/MainActivity.kt
package com.afghanjama

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.afghanjama.data.buildAppDatabase
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.nav.AppNav
import com.afghanjama.ui.screens.PinLockScreen
import com.afghanjama.util.AppLock
import com.afghanjama.ui.theme.AfghanJamaTheme
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
import com.afghanjama.ui.vm.MyWorkViewModel
import com.afghanjama.ui.vm.ReportsViewModel
import com.afghanjama.ui.vm.OrderDetailViewModel
import com.afghanjama.ui.vm.OrderSearchViewModel
import com.afghanjama.ui.vm.PayrollViewModel
import com.afghanjama.ui.vm.PerformanceViewModel
import com.afghanjama.ui.vm.PurchasePlanViewModel
import com.afghanjama.ui.vm.ProcurementViewModel
import com.afghanjama.ui.vm.ProductionViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.StockViewModel
import com.afghanjama.ui.vm.WarehouseViewModel

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // مجوز نوتیفیکیشن برای یادآوری تسویه هفتگی (اندروید ۱۳+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val db = buildAppDatabase(applicationContext)
        val repo = Repo(db)

        setContent {
            AfghanJamaTheme {
                // قفل اپ: اگر رمز تنظیم شده باشد، اول باید باز شود
                var unlocked by remember { mutableStateOf(!AppLock.isPinSet(applicationContext)) }
                if (!unlocked) {
                    PinLockScreen(onUnlock = { unlocked = true })
                    return@AfghanJamaTheme
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
                val purchasePlanVm = remember { PurchasePlanViewModel(repo) }
                val myWorkVm = remember { MyWorkViewModel(repo) }

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
                    purchasePlanVm = purchasePlanVm,
                    myWorkVm = myWorkVm
                )
            }
        }
    }
}
