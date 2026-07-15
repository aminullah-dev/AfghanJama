@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.elapsedHm
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.HomeViewModel
import com.afghanjama.work.ShiftReminderWorker
import kotlinx.coroutines.delay

private data class HomeAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

private fun fullSpan() = androidx.compose.foundation.lazy.grid.GridItemSpan(2)

@Composable
fun HomeDashboardScreen(
    vm: HomeViewModel,
    isManager: Boolean,
    onGoProcurement: () -> Unit,
    onGoWarehouse: () -> Unit,
    onGoStockLedger: () -> Unit,
    onGoProduction: () -> Unit,
    onGoFinishedSales: () -> Unit,
    onGoAttendance: () -> Unit,
    onGoCustomers: () -> Unit,
    onGoFinance: () -> Unit,
    onGoLedger: () -> Unit,
    onGoDocuments: () -> Unit,
    onGoReports: () -> Unit,
    onGoSearch: () -> Unit,
    onGoSettings: () -> Unit
) {
    val s by vm.summary.collectAsState()
    val insideNow by vm.insideNow.collectAsState()

    // ساعتِ شیفت: هر ۳۰ ثانیه تیک می‌خورد تا مدتِ حضورِ باز دیده شود
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(insideNow.isNotEmpty()) {
        while (insideNow.isNotEmpty()) {
            nowTick = System.currentTimeMillis()
            delay(30_000)
        }
    }

    // جریان اصلی «تولید انبار» (make-to-stock)
    val stockFlow = buildList {
        add(HomeAction("خرید مواد", Icons.Default.ShoppingCart, onGoProcurement))
        add(HomeAction("انبار مواد", Icons.Default.Warehouse, onGoWarehouse))
        if (isManager) add(HomeAction("گردش انبار", Icons.Default.History, onGoStockLedger))
        if (isManager) add(HomeAction("خط تولید", Icons.Default.Checkroom, onGoProduction))
        if (isManager) add(HomeAction("فروش انبار", Icons.Default.Sell, onGoFinishedSales))
    }

    // مشتریان و سفارش
    val customerFlow = buildList {
        add(HomeAction("مشتریان", Icons.Default.Group, onGoCustomers))
    }

    // عمومی
    val general = buildList {
        if (isManager) add(HomeAction("حضور و غیاب", Icons.Default.Fingerprint, onGoAttendance))
        if (isManager) add(HomeAction("مالی", Icons.Default.Payments, onGoFinance))
        if (isManager) add(HomeAction("دفتر کل", Icons.Default.AccountBalance, onGoLedger))
        if (isManager) add(HomeAction("اسناد", Icons.Default.Description, onGoDocuments))
        if (isManager) add(HomeAction("گزارش‌ها", Icons.Default.Assessment, onGoReports))
        add(HomeAction("جستجو", Icons.Default.Search, onGoSearch))
        add(HomeAction("تنظیمات", Icons.Default.Settings, onGoSettings))
    }

    val sections = listOf(
        "موجودی و تولید" to stockFlow,
        "مشتریان و سفارش" to customerFlow,
        "عمومی" to general
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- ساعتِ شیفت (وقتی ورودی باز است) ----------
        if (insideNow.isNotEmpty()) {
            item(span = { fullSpan() }) {
                val oldest = insideNow.minByOrNull { it.checkIn }!!
                val nearEnd = insideNow.any {
                    (nowTick - it.checkIn) >= ShiftReminderWorker.WARN_AFTER_MS
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (nearEnd) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "🕐 ${insideNow.size.fa()} کارمند در کارگاه — قدیمی‌ترین ورود: ${elapsedHm(oldest.checkIn, nowTick)} ساعت پیش",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (nearEnd) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            if (nearEnd) "⚠ شیفت ۸ ساعته رو به پایان است — خروج‌ها را ثبت کنید"
                            else "یادآور پایان شیفت (۸ ساعت) فعال است؛ ۳۰ دقیقه قبل با لرزش خبر می‌دهد.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (nearEnd) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }


        item(span = { fullSpan() }) {
            val ctx = LocalContext.current
            val coName = remember { CompanyPrefs.name(ctx).ifBlank { "کارگاه خیاطی AfghanJama" } }
            val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
            val greeting = when {
                hour < 12 -> "صبح بخیر"
                hour < 17 -> "روز بخیر"
                else -> "عصر بخیر"
            }
            Column {
                Text(
                    "$greeting 👋",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    coName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    PersianDate.long(System.currentTimeMillis()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ---------- کارت‌های خلاصه ----------
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "انبار مواد",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "${s.materialItems.fa()} قلم • ارزش ${s.materialValue.afn()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (s.lowStockCount > 0) {
                        Text(
                            "⚠ ${s.lowStockCount.fa()} قلم موجودی کم دارد",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item { StatCard("در تولید", s.inProduction.fa(), "سفارش در جریان") }
        item { StatCard("انبار محصول", s.finishedPieces.fa(), "عدد آماده فروش") }
        item { StatCard("کیف پول", s.wallet.afn(), "موجودی نقد") }
        item { StatCard("بانک", s.bank.afn(), "موجودی بانک") }

        // ---------- ضربان خط تولید ----------
        if (isManager && s.inProduction > 0) {
            item(span = { fullSpan() }) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onGoProduction() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "ضربان خط تولید",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "✂ برش ${s.cutting.fa()}   🧵 دوخت ${s.sewing.fa()}   🛡 نظارت ${s.review.fa()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (s.stuck > 0) {
                            Text(
                                "⚠ ${s.stuck.fa()} سفارش بیش از حد معطل مانده — بررسی کنید",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ---------- بخش‌های دسترسی سریع ----------
        sections.forEach { (title, list) ->
            if (list.isNotEmpty()) {
                item(span = { fullSpan() }) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(list) { action -> ActionCard(action) }
            }
        }

        item(span = { fullSpan() }) {
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionCard(action: HomeAction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clickable(onClick = action.onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                action.icon,
                contentDescription = action.label,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(action.label, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, sub: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
