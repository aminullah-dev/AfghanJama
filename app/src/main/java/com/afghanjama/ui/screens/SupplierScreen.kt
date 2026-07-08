@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.vm.SupplierBalance
import com.afghanjama.ui.vm.SupplierViewModel
import com.afghanjama.ui.format.digitsOnly

@Composable
fun SupplierScreen(
    vm: SupplierViewModel,
    onBack: () -> Unit
) {
    val balances by vm.balances.collectAsState()
    val ledger by vm.ledger.collectAsState()
    val ui by vm.ui.collectAsState()

    var settleTarget by remember { mutableStateOf<SupplierBalance?>(null) }

    settleTarget?.let { b ->
        var amountText by remember(b.supplier) { mutableStateOf(b.owed.toString()) }
        var paySource by remember(b.supplier) { mutableStateOf("WALLET") }
        AlertDialog(
            onDismissRequest = { settleTarget = null },
            title = { Text("تسویه با «${b.supplier}»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("بدهی فعلی: ${b.owed.afn()}", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.digitsOnly() },
                        label = { Text("مبلغ تسویه (؋)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("پرداخت از:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SrcChip(paySource, "WALLET", "کیف پول") { paySource = it }
                        SrcChip(paySource, "BANK", "بانک") { paySource = it }
                        SrcChip(paySource, "PROFIT", "فایده") { paySource = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.settle(b.supplier, amountText.toLongOrNull() ?: 0L, paySource)
                    settleTarget = null
                }) { Text("تسویه") }
            },
            dismissButton = { TextButton(onClick = { settleTarget = null }) { Text("لغو") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قرض فروشنده") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            item {
                val totalOwed = balances.sumOf { it.owed }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (totalOwed > 0) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("مجموع بدهی به فروشنده‌ها", fontWeight = FontWeight.Medium)
                        Text(totalOwed.afn(), fontWeight = FontWeight.Bold)
                    }
                }
            }

            ui.message?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ui.isError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) { Text(msg, modifier = Modifier.padding(12.dp)) }
                }
            }

            if (balances.isEmpty()) {
                item {
                    Text(
                        "بدهی بازی به فروشنده‌ای نیست. خرید نسیه از «خرید مواد» با انتخاب «نسیه» ثبت می‌شود.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                items(balances, key = { it.supplier }) { b ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(b.supplier, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "بدهی: ${b.owed.afn()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Button(onClick = { settleTarget = b }) { Text("تسویه") }
                        }
                    }
                }
            }

            if (ledger.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("سابقهٔ اخیر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(ledger.take(20), key = { it.id }) { row ->
                    val credit = row.type == "CREDIT"
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (credit) "نسیه" else "تسویه") + " • ${row.supplier}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                PersianDate.short(row.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            (if (credit) "+" else "−") + row.amount.afn(),
                            fontWeight = FontWeight.SemiBold,
                            color = if (credit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

@Composable
private fun SrcChip(current: String, value: String, label: String, onPick: (String) -> Unit) {
    FilterChip(selected = current == value, onClick = { onPick(value) }, label = { Text(label) })
}
