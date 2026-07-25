@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.FinishedSaleViewModel

@Composable
fun FinishedWarehouseScreen(
    vm: FinishedSaleViewModel,
    onBack: () -> Unit
) {
    val items by vm.items.collectAsState()
    val sales by vm.recentSales.collectAsState()
    val ui by vm.ui.collectAsState()

    var sellTarget by remember { mutableStateOf<FinishedStock?>(null) }

    // ---------- دیالوگ فروش جزئی ----------
    sellTarget?.let { item ->
        var qtyText by remember(item.id) { mutableStateOf("") }
        var priceText by remember(item.id) { mutableStateOf("") }
        var customer by remember(item.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { sellTarget = null },
            title = { Text("فروش «${item.name}»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "موجودی: ${item.qty.fa()} عدد" +
                            if (item.size.isNotBlank()) " • سایز ${item.size}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.digitsOnly() },
                        label = { Text("تعداد فروش") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.digitsOnly() },
                        label = { Text("قیمت هر عدد (؋)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customer,
                        onValueChange = { customer = it },
                        label = { Text("نام مشتری (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val q = qtyText.toIntOrNull() ?: 0
                    val p = priceText.toLongOrNull() ?: 0L
                    if (q > 0 && p > 0) {
                        Text(
                            "جمع فروش: ${(q * p).afn()}",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val q = qtyText.toIntOrNull() ?: 0
                    val p = priceText.toLongOrNull() ?: 0L
                    vm.sell(item, q, p, customer)
                    sellTarget = null
                }) { Text("ثبت فروش") }
            },
            dismissButton = { TextButton(onClick = { sellTarget = null }) { Text("لغو") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فروش از انبار محصول") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ui.message?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ui.isError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) { Text(msg, modifier = Modifier.padding(12.dp)) }
                }
            }

            if (items.isEmpty()) {
                item {
                    Text(
                        "انبار محصول خالی است. سفارش آمادهٔ فروش را از صفحهٔ جزئیات سفارش به انبار محصول تحویل دهید.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            } else {
                item {
                    Text("موجودی انبار محصول", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(items, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "موجودی: ${item.qty.fa()} عدد" +
                                        (if (item.size.isNotBlank()) " • سایز ${item.size}" else "") +
                                        " • بهای هر عدد ${item.avgCost.afn()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = { sellTarget = item }) {
                                Icon(Icons.Default.Sell, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("فروش")
                            }
                        }
                    }
                }
            }

            if (sales.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(thickness = 0.5.dp)
                    Text(
                        "فروش‌های اخیر",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(sales, key = { it.id }) { sale ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${sale.qty.fa()} عدد «${sale.productName}»", style = MaterialTheme.typography.bodyMedium)
                            if (sale.customerName.isNotBlank()) {
                                Text(
                                    sale.customerName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(sale.total.afn(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}
