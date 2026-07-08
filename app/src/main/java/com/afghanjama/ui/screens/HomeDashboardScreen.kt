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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
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

@Composable
fun HomeDashboardScreen(
    vm: HomeViewModel,
    onGoProcurement: () -> Unit,
    onGoWarehouse: () -> Unit,
    onGoProduction: () -> Unit,
    onGoStartProduction: () -> Unit,
    onGoFinishedSales: () -> Unit,
    onGoSales: () -> Unit,
    onGoFinance: () -> Unit,
    onGoSearch: () -> Unit,
    onGoSettings: () -> Unit
) {
    val s by vm.summary.collectAsState()

    val actions = listOf(
        HomeAction("خرید مواد", Icons.Default.ShoppingCart, onGoProcurement),
        HomeAction("انبار مواد", Icons.Default.Warehouse, onGoWarehouse),
        HomeAction("شروع تولید", Icons.Default.ContentCut, onGoStartProduction),
        HomeAction("خط تولید", Icons.Default.Checkroom, onGoProduction),
        HomeAction("فروش انبار", Icons.Default.Sell, onGoFinishedSales),
        HomeAction("فروش سفارش", Icons.Default.Storefront, onGoSales),
        HomeAction("مالی", Icons.Default.Payments, onGoFinance),
        HomeAction("جستجو", Icons.Default.Search, onGoSearch),
        HomeAction("تنظیمات", Icons.Default.Settings, onGoSettings)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Column {
                Text(
                    "کارگاه خیاطی AfghanJama",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "خلاصهٔ امروز کارگاه",
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

        // ---------- دسترسی سریع ----------
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Text(
                "دسترسی سریع",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(actions) { action ->
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

        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Spacer(Modifier.height(24.dp))
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
