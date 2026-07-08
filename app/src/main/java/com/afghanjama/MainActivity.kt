// app/src/main/java/com/afghanjama/MainActivity.kt
package com.afghanjama

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.room.Room
import com.afghanjama.data.AppDatabase
import com.afghanjama.data.MIGRATION_19_20
import com.afghanjama.data.MIGRATION_20_21
import com.afghanjama.data.MIGRATION_21_22
import com.afghanjama.data.MIGRATION_22_23
import com.afghanjama.data.MIGRATION_23_24
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.nav.AppNav
import com.afghanjama.ui.theme.AfghanJamaTheme
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.BackupViewModel
import com.afghanjama.ui.vm.CustomerAccountsViewModel
import com.afghanjama.ui.vm.CuttingViewModel
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.HomeViewModel
import com.afghanjama.ui.vm.InventoryViewModel
import com.afghanjama.ui.vm.MasterDataViewModel
import com.afghanjama.ui.vm.OrderDetailViewModel
import com.afghanjama.ui.vm.OrderSearchViewModel
import com.afghanjama.ui.vm.ProcurementViewModel
import com.afghanjama.ui.vm.PurchaseViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SalesViewModel
import com.afghanjama.ui.vm.SewingViewModel
import com.afghanjama.ui.vm.StockViewModel
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

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "afghanjama.db"
        )
            .addMigrations(
                MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
                MIGRATION_22_23, MIGRATION_23_24
            )
            .fallbackToDestructiveMigration()
            .build()

        val repo = Repo(db)

        setContent {
            AfghanJamaTheme {
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
                    homeVm = homeVm
                )
            }
        }
    }
}
