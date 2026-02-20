// app/src/main/java/com/afghanjama/MainActivity.kt
package com.afghanjama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.room.Room
import com.afghanjama.data.AppDatabase
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.nav.AppNav
import com.afghanjama.ui.theme.AfghanJamaTheme
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.CuttingViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.InventoryViewModel
import com.afghanjama.ui.vm.MasterDataViewModel
import com.afghanjama.ui.vm.PurchaseViewModel
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.ui.vm.SalesViewModel
import com.afghanjama.ui.vm.SewingViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "afghanjama.db"
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

                AppNav(
                    authVm = authVm,
                    purchaseVm = purchaseVm,
                    inventoryVm = inventoryVm,
                    cuttingVm = cuttingVm,
                    sewingVm = sewingVm,
                    reviewVm = reviewVm,
                    salesVm = salesVm,
                    financeVm = financeVm,
                    masterVm = masterVm
                )
            }
        }
    }
}
