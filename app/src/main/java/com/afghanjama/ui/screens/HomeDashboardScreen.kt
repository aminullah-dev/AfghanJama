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
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.HomeViewModel

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
    onGoProduction: () -> Unit,
    onGoStartProduction: () -> Unit,
    onGoFinishedSales: () -> Unit,
    onGoSuppliers: () -> Unit,
    onGoCustomers: () -> Unit,
    onGoCustomerOrder: () -> Unit,
    onGoSales: () -> Unit,
    onGoFinance: () -> Unit,
    onGoSearch: () -> Unit,
    onGoSettings: () -> Unit
) {
    val s by vm.summary.collectAsState()

    // جریان اصلی «تولید انبار» (make-to-stock)
    val stockFlow = buildList {
        add(HomeAction("خرید مواد", Icons.Default.ShoppingCart, onGoProcurement))
        add(HomeAction("انبار مواد", Icons.Default.Warehouse, onGoWarehouse))
        add(HomeAction("قرض فروشنده", Icons.Default.ReceiptLong, onGoSuppliers))
        if (isManager) add(HomeAction("شروع تولید", Icons.Default.ContentCut, onGoStartProduction))
        if (isManager) add(HomeAction("خط تولید", Icons.Default.Checkroom, onGoProduction))
        if (isManager) add(HomeAction("فروش انبار", Icons.Default.Sell, onGoFinishedSales))
    }

    // مشتریان و سفارش
    val customerFlow = buildList {
        add(HomeAction("مشتریان", Icons.Default.Group, onGoCustomers))
        add(HomeAction("سفارش مشتری", Icons.Default.PersonAdd, onGoCustomerOrder))
        if (isManager) add(HomeAction("فروش سفارش", Icons.Default.Storefront, onGoSales))
    }

    // عمومی
    val general = buildList {
        if (isManager) add(HomeAction("مالی", Icons.Default.Payments, onGoFinance))
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
        item(span = { fullSpan() }) {
            Column {
                Text(
                    "کارگاه خیاطی AfghanJama",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "جریان اصلی: خرید مواد ← شروع تولید ← فروش انبار",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
