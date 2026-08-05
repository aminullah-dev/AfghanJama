package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.LanHost
import com.afghanjama.prefs.Settings

/**
 * سازندهٔ ViewModelها — تا هیچ‌کدام پیش از باز شدنِ صفحه‌اش ساخته نشود.
 *
 * **وضعی که از آن آمدیم:** `MainActivity` هر ۳۷ ViewModel را در
 * `setContent` می‌ساخت. هرکدام سرِ ساخته شدن `stateIn` می‌زد روی
 * جریان‌هایی که **کلِ جدول** را می‌خوانند — و ۹ تای‌شان جدولِ سفارش‌ها
 * را. یعنی در ثانیهٔ اولِ اجرا، همان داده تا ۹ بار در حافظه می‌نشست،
 * برای صفحه‌هایی که کاربر شاید هرگز باز نکند.
 *
 * با چند صد ردیف دیده نمی‌شد. با پنج سال دادهٔ کارگاه همان چیزی است که
 * «اپ کند شده» می‌سازد — و علتش از روی هیچ صفحه‌ای معلوم نمی‌شود، چون
 * **همهٔ** صفحه‌ها همیشه زنده‌اند.
 *
 * **طولِ عمر عوض نشد.** هر ViewModel به `ViewModelStore`ِ خودِ اکتیویتی
 * بسته می‌شود، نه به مقصدِ ناوبری. یعنی مثلِ دیروز تا پایانِ عمرِ اپ
 * زنده می‌مانَد و حالتش با رفت‌وبرگشت بینِ صفحه‌ها از دست نمی‌رود — تنها
 * تفاوت این است که **دیرتر** ساخته می‌شود. اگر به مقصد بسته می‌شد، فرمِ
 * نیمه‌پرشده سرِ برگشت خالی می‌شد؛ آن یک تغییرِ رفتار بود، نه بهینه‌سازی.
 *
 * سودِ جانبی: حالا `onCleared` واقعاً صدا زده می‌شود. با
 * `remember { … }`ِ قبلی هیچ‌وقت نمی‌شد.
 */
class VmFactory(
    private val repo: Repo,
    private val settings: Settings,
    private val lanHost: LanHost
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm: ViewModel = when (modelClass) {
            AuthViewModel::class.java -> AuthViewModel(settings)
            FinanceViewModel::class.java -> FinanceViewModel(repo)
            InventoryViewModel::class.java -> InventoryViewModel(repo)
            CuttingViewModel::class.java -> CuttingViewModel(repo)
            SewingViewModel::class.java -> SewingViewModel(repo)
            ReviewViewModel::class.java -> ReviewViewModel(repo)
            MasterDataViewModel::class.java -> MasterDataViewModel(repo)
            DashboardViewModel::class.java -> DashboardViewModel(repo)
            OrderSearchViewModel::class.java -> OrderSearchViewModel(repo)
            BackupViewModel::class.java -> BackupViewModel(repo)
            OrderDetailViewModel::class.java -> OrderDetailViewModel(repo)
            ProcurementViewModel::class.java -> ProcurementViewModel(repo)
            WarehouseViewModel::class.java -> WarehouseViewModel(repo)
            HomeViewModel::class.java -> HomeViewModel(repo)
            ProductionViewModel::class.java -> ProductionViewModel(repo)
            LedgerViewModel::class.java -> LedgerViewModel(repo)
            DocumentsViewModel::class.java -> DocumentsViewModel(repo)
            ReportsViewModel::class.java -> ReportsViewModel(repo)
            FinishedSaleViewModel::class.java -> FinishedSaleViewModel(repo)
            CustomersViewModel::class.java -> CustomersViewModel(repo)
            CustomerDetailViewModel::class.java -> CustomerDetailViewModel(repo)
            AttendanceViewModel::class.java -> AttendanceViewModel(repo)
            AuditViewModel::class.java -> AuditViewModel(repo)
            ActionCenterViewModel::class.java -> ActionCenterViewModel(repo)
            PayrollViewModel::class.java -> PayrollViewModel(repo)
            PerformanceViewModel::class.java -> PerformanceViewModel(repo)
            DeliveryQueueViewModel::class.java -> DeliveryQueueViewModel(repo)
            NewSaleViewModel::class.java -> NewSaleViewModel(repo)
            BreakTimeViewModel::class.java -> BreakTimeViewModel(repo)
            WorkshopLinkViewModel::class.java -> WorkshopLinkViewModel(repo, lanHost)
            BoardViewModel::class.java -> BoardViewModel(repo)
            SelfTestViewModel::class.java -> SelfTestViewModel(repo)
            PurchasePlanViewModel::class.java -> PurchasePlanViewModel(repo)
            MyWorkViewModel::class.java -> MyWorkViewModel(repo)
            MoneyMoveViewModel::class.java -> MoneyMoveViewModel(repo)
            PurchaseReturnViewModel::class.java -> PurchaseReturnViewModel(repo)
            // نامِ ناشناخته یعنی کسی ViewModel تازه‌ای ساخته و اینجا
            // اضافه نکرده. بلند می‌شکند، چون سکوت اینجا یعنی صفحه‌ای که
            // سرِ باز شدن می‌ترکد.
            else -> error("ViewModel ناشناخته در VmFactory: ${modelClass.name}")
        }
        return vm as T
    }
}
