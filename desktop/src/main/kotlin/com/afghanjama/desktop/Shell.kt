package com.afghanjama.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.screens.AuditScreen
import com.afghanjama.ui.screens.CustomersScreen
import com.afghanjama.ui.screens.FinanceHubScreen
import com.afghanjama.ui.screens.LedgerScreen
import com.afghanjama.ui.screens.MasterDataScreen
import com.afghanjama.ui.screens.MaterialWarehouseScreen
import com.afghanjama.ui.screens.ReportsScreen
import com.afghanjama.ui.vm.AuditViewModel
import com.afghanjama.ui.vm.CustomersViewModel
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.LedgerViewModel
import com.afghanjama.ui.vm.MasterDataViewModel
import com.afghanjama.ui.vm.ReportsViewModel
import com.afghanjama.ui.vm.WarehouseViewModel

/**
 * بخش‌های نوارِ کناری.
 *
 * `Overview` همان چیزی است که تا امروز کلِ پنجره بود: خودآزمایی و
 * وضعیتِ دفتر. حالا یکی از بخش‌هاست، نه همهٔ برنامه.
 */
private enum class Section(val title: String) {
    Overview("وضعیت"),
    Finance("مالی"),
    Ledger("دفترِ حساب"),
    Warehouse("انبارِ مواد"),
    Customers("خریداران"),
    Reports("گزارش‌ها"),
    Audit("رسیدگی"),
    MasterData("اطلاعات پایه")
}

/**
 * پنجرهٔ اصلیِ ویندوز.
 *
 * **چرا نوارِ کناری و نه همان ناوبریِ اندروید:** `androidx.navigation`
 * فقط اندروید است و به `:core` نمی‌آید. ولی این محدودیت به نفع کار
 * تمام شد: پی‌سی صفحهٔ بزرگ دارد و پشته‌ی «برو و برگرد»ِ گوشی آنجا
 * اضافه است. کارگاه همهٔ بخش‌ها را یک‌جا می‌بیند و با یک کلیک بینشان
 * می‌رود.
 *
 * صفحه‌ها همان‌هایی هستند که روی گوشی اجرا می‌شوند — **کلمه به کلمه
 * همان کد**، از `:core`. هیچ نسخهٔ ویندوزیِ جداگانه‌ای از آن‌ها نیست.
 */
@Composable
fun Shell(repo: Repo?) {
    var section by remember { mutableStateOf(Section.Overview) }

    // اگر دفتر باز نشد، فقط «وضعیت» می‌ماند — چون همان است که خطا را
    // نشان می‌دهد. بردنِ کاربر به صفحه‌ای که دادهٔ خالی نشان دهد بدتر
    // از نبودنِ آن صفحه است.
    if (repo == null) {
        Overview()
        return
    }

    Row(Modifier.fillMaxSize().background(Bg)) {
        // در چیدمانِ راست‌به‌چپ، اولین عضوِ Row سمتِ راست می‌نشیند.
        Sidebar(section) { section = it }

        Box(Modifier.fillMaxSize()) {
            when (section) {
                Section.Overview -> Overview()

                Section.Finance -> {
                    val financeVm: FinanceViewModel = viewModel { FinanceViewModel(repo) }
                    val dashVm: DashboardViewModel = viewModel { DashboardViewModel(repo) }
                    FinanceHubScreen(financeVm, dashVm, onBack = { section = Section.Overview })
                }

                Section.Ledger -> {
                    val vm: LedgerViewModel = viewModel { LedgerViewModel(repo) }
                    LedgerScreen(vm, onBack = { section = Section.Overview })
                }

                Section.Warehouse -> {
                    val vm: WarehouseViewModel = viewModel { WarehouseViewModel(repo) }
                    // روی پی‌سیِ کارگاه که دستِ کارفرماست، اصلاحِ موجودی
                    // مجاز است — همان چیزی که روی گوشیِ مدیر هم هست.
                    MaterialWarehouseScreen(vm, canAdjust = true, onBack = { section = Section.Overview })
                }

                Section.Customers -> {
                    val vm: CustomersViewModel = viewModel { CustomersViewModel(repo) }
                    CustomersScreen(
                        vm,
                        onBack = { section = Section.Overview },
                        // صفحهٔ جزئیاتِ مشتری هنوز در پوسته نیست؛ تا آن
                        // وقت کلیک بی‌اثر است نه اینکه به جای اشتباه برود.
                        onOpenCustomer = {}
                    )
                }

                Section.Reports -> {
                    val vm: ReportsViewModel = viewModel { ReportsViewModel(repo) }
                    ReportsScreen(vm, onBack = { section = Section.Overview })
                }

                Section.Audit -> {
                    val vm: AuditViewModel = viewModel { AuditViewModel(repo) }
                    AuditScreen(vm, onBack = { section = Section.Overview })
                }

                Section.MasterData -> {
                    val vm: MasterDataViewModel = viewModel { MasterDataViewModel(repo) }
                    MasterDataScreen(vm, onBack = { section = Section.Overview })
                }
            }
        }
    }
}

@Composable
private fun Sidebar(current: Section, onPick: (Section) -> Unit) {
    Column(
        Modifier
            .width(210.dp)
            .fillMaxHeight()
            .background(CardBg)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "خیاط‌یار",
                fontFamily = Vazirmatn,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Brand
            )
        }

        Section.entries.forEach { s ->
            val selected = s == current
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) BrandSoft else CardBg)
                    .clickable { onPick(s) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    s.title,
                    fontFamily = Vazirmatn,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Brand else Ink
                )
            }
        }

        Text(
            "این صفحه‌ها همان کدی‌اند که روی گوشی اجرا می‌شود.",
            fontFamily = Vazirmatn,
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.padding(top = 18.dp, start = 8.dp, end = 8.dp)
        )
    }
}
