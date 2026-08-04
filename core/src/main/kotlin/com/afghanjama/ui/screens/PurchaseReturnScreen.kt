@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.afghanjama.ui.platform.AppDropdownMenu
import com.afghanjama.ui.platform.AppDropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.BusyButton
import com.afghanjama.ui.components.EmptyState
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.decimalOnly
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.vm.PurchaseReturnViewModel

/** برگشتِ مواد به فروشنده: قلم، مقدار، مبلغ، و اینکه پول برمی‌گردد یا از بدهی کم می‌شود. */
@Composable
fun PurchaseReturnScreen(
    vm: PurchaseReturnViewModel,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val busy by vm.busy.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var supplier by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Pair<String, String>?>(null) }   // name to unit
    var qty by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var refundCash by remember { mutableStateOf(false) }
    var cashBox by remember { mutableStateOf("WALLET") }
    var itemMenu by remember { mutableStateOf(false) }
    var supMenu by remember { mutableStateOf(false) }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            val ok = ui.done
            vm.clearMessage()
            if (ok) { qty = ""; amount = ""; picked = null }
        }
    }

    val stockOf = ui.materials.firstOrNull { it.name == picked?.first && it.unit == picked?.second }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("برگشت از خرید") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (ui.materials.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Inventory2,
                        title = "چیزی در انبار نیست که برگردد",
                        hint = "برگشت فقط برای قلمی ممکن است که موجودی داشته باشد."
                    )
                }
                return@LazyColumn
            }

            // ---------- فروشنده ----------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(
                            onClick = { supMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (supplier.isBlank()) "انتخاب فروشنده" else supplier) }
                        AppDropdownMenu(expanded = supMenu, onDismissRequest = { supMenu = false }) {
                            ui.suppliers.forEach { s ->
                                AppDropdownMenuItem(
                                    text = { Text(s) },
                                    onClick = { supplier = s; supMenu = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = supplier,
                        onValueChange = { supplier = it },
                        label = { Text("نام فروشنده") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ---------- قلم ----------
            item {
                Box {
                    OutlinedButton(
                        onClick = { itemMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(picked?.let { "${it.first} (${it.second})" } ?: "انتخاب قلم از انبار")
                    }
                    AppDropdownMenu(expanded = itemMenu, onDismissRequest = { itemMenu = false }) {
                        ui.materials.forEach { m ->
                            AppDropdownMenuItem(
                                text = { Text("${m.name} • موجودی ${m.amount} ${m.unit}") },
                                onClick = {
                                    picked = m.name to m.unit
                                    // پیش‌فرضِ مبلغ از میانگینِ قیمتِ همان قلم
                                    if (amount.isBlank() && m.avgPrice > 0) {
                                        amount = (m.avgPrice).toLong().toString()
                                    }
                                    itemMenu = false
                                }
                            )
                        }
                    }
                }
            }

            if (picked != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "موجودی فعلی: ${stockOf?.amount ?: 0.0} ${picked!!.second}" +
                                (stockOf?.avgPrice?.takeIf { it > 0 }
                                    ?.let { " • میانگین قیمت هر واحد: ${it.toLong().afn()}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = qty,
                            onValueChange = { qty = it.decimalOnly() },
                            label = { Text("مقدار برگشتی (${picked!!.second})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it.digitsOnly() },
                            label = { Text("مبلغ کل برگشتی (؋)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ---------- پول چطور برمی‌گردد؟ ----------
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "پول چطور برگردد؟",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !refundCash,
                                onClick = { refundCash = false },
                                label = { Text("از بدهیِ ما کم شود") }
                            )
                            FilterChip(
                                selected = refundCash,
                                onClick = { refundCash = true },
                                label = { Text("نقد پس بگیریم") }
                            )
                        }
                        if (refundCash) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = cashBox == "WALLET",
                                    onClick = { cashBox = "WALLET" },
                                    label = { Text("به صندوق") }
                                )
                                FilterChip(
                                    selected = cashBox == "BANK",
                                    onClick = { cashBox = "BANK" },
                                    label = { Text("به بانک") }
                                )
                            }
                        }
                        Text(
                            if (refundCash)
                                "مواد از انبار کم و پول به صندوق اضافه می‌شود."
                            else "مواد از انبار کم و همین مبلغ از بدهیِ ما به فروشنده کم می‌شود.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    BusyButton(
                        text = "ثبت برگشت از خرید",
                        onClick = {
                            vm.submit(
                                supplier = supplier,
                                name = picked!!.first,
                                unit = picked!!.second,
                                qty = qty.toDoubleOrNull() ?: 0.0,
                                amount = amount.toLongOrNull() ?: 0L,
                                refundCash = refundCash,
                                cashBox = cashBox
                            )
                        },
                        enabled = (qty.toDoubleOrNull() ?: 0.0) > 0.0 &&
                            (amount.toLongOrNull() ?: 0L) > 0L,
                        busy = busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
