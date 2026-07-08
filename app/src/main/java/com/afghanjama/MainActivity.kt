// app/src/main/java/com/afghanjama/MainActivity.kt
package com.afghanjama

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.BackupViewModel
import com.afghanjama.ui.vm.CustomerAccountsViewModel
import com.afghanjama.ui.vm.CuttingViewModel
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.FinishedSaleViewModel
import com.afghanjama.ui.vm.HomeViewModel
import com.afghanjama.ui.vm.InventoryViewModel
import com.afghanjama.ui.vm.MasterDataViewModel
import com.afghanjama.ui.vm.OrderDetailViewModel
import com.afghanjama.ui.vm.OrderSearchViewModel
import com.afghanjama.ui.vm.ProcurementViewModel
import com.afghanjama.ui.vm.ProductionViewModel
import com.afghanjama.ui.vm.PurchaseViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SalesViewModel
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.StockViewModel
import com.afghanjama.ui.vm.SupplierViewModel
import com.afghanjama.ui.vm.WagesViewModel
import com.afghanjama.ui.vm.WarehouseViewModel

class MainActivity : ComponentActivity() {

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
                val purchaseVm = remember { PurchaseViewModel(repo) }
                val inventoryVm = remember { InventoryViewModel(repo) }
                val cuttingVm = remember { CuttingViewModel(repo) }
                val sewingVm = remember { SewingViewModel(repo) }
                val reviewVm = remember { ReviewViewModel(repo) }
                val salesVm = remember { SalesViewModel(repo) }
                val masterVm = remember { MasterDataViewModel(repo) }
                val wagesVm = remember { WagesViewModel(repo) }
                val customersVm = remember { CustomerAccountsViewModel(repo) }
                val dashboardVm = remember { DashboardViewModel(repo) }
                val searchVm = remember { OrderSearchViewModel(repo) }
                val stockVm = remember { StockViewModel(repo) }
                val backupVm = remember { BackupViewModel(repo) }
                val orderDetailVm = remember { OrderDetailViewModel(repo) }
                val procurementVm = remember { ProcurementViewModel(repo) }
                val warehouseVm = remember { WarehouseViewModel(repo) }
                val homeVm = remember { HomeViewModel(repo) }
                val productionVm = remember { ProductionViewModel(repo) }
                val finishedSaleVm = remember { FinishedSaleViewModel(repo) }
                val supplierVm = remember { SupplierViewModel(repo) }

                AppNav(
                    authVm = authVm,
                    purchaseVm = purchaseVm,
                    inventoryVm = inventoryVm,
                    cuttingVm = cuttingVm,
                    sewingVm = sewingVm,
                    reviewVm = reviewVm,
                    salesVm = salesVm,
                    financeVm = financeVm,
                    masterVm = masterVm,
                    wagesVm = wagesVm,
                    customersVm = customersVm,
                    dashboardVm = dashboardVm,
                    searchVm = searchVm,
                    stockVm = stockVm,
                    backupVm = backupVm,
                    orderDetailVm = orderDetailVm,
                    procurementVm = procurementVm,
                    warehouseVm = warehouseVm,
                    homeVm = homeVm,
                    productionVm = productionVm,
                    finishedSaleVm = finishedSaleVm,
                    supplierVm = supplierVm
                )
            }
        }
    }
}
