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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.vm.CustomerDetailViewModel

@Composable
fun CustomerDetailScreen(
    vm: CustomerDetailViewModel,
    customerId: Long,
    onBack: () -> Unit,
    onOpenOrder: (String) -> Unit
) {
    LaunchedEffect(customerId) { vm.open(customerId) }

    val s by vm.summary.collectAsState()
    val measurements by vm.measurements.collectAsState()

    var showMeasure by remember { mutableStateOf(false) }
    var showPay by remember { mutableStateOf(false) }

    if (showMeasure) {
        var label by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMeasure = false },
            title = { Text("افزودن اندازه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label, onValueChange = { label = it },
                        label = { Text("عنوان (مثلاً دور سینه)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = value, onValueChange = { value = it },
                        label = { Text("مقدار (مثلاً ۹۸)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = label.isNotBlank() && value.isNotBlank(), onClick = {
                    vm.addMeasurement(label, value); showMeasure = false
                }) { Text("افزودن") }
            },
            dismissButton = { TextButton(onClick = { showMeasure = false }) { Text("لغو") } }
        )
    }

    if (showPay) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPay = false },
            title = { Text("ثبت دریافتی") },
            text = {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.digitsOnly() },
                    label = { Text("مبلغ دریافتی (؋)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.recordPayment(amount.toLongOrNull() ?: 0L); showPay = false }) {
                    Text("ثبت")
                }
            },
            dismissButton = { TextButton(onClick = { showPay = false }) { Text("لغو") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.customer?.name ?: "پروندهٔ مشتری") },
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
            // ---------- مشخصات + مانده ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (s.balance > 0) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        s.customer?.phone?.takeIf { it.isNotBlank() }?.let {
                            Text("تماس: $it", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            if (s.balance > 0) "بدهی مشتری: ${s.balance.afn()}"
                            else "حساب تسویه است",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "مجموع سفارش‌ها: ${s.totalDue.afn()} • دریافتی: ${s.totalPaid.afn()}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            item {
                OutlinedButton(onClick = { showPay = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("ثبت دریافتی از مشتری")
                }
            }

            // ---------- اندازه‌ها ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("اندازه‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Button(onClick = { showMeasure = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("افزودن")
                            }
                        }
                        if (measurements.isEmpty()) {
                            Text(
                                "اندازه‌ای ثبت نشده.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            measurements.forEach { m ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(m.label, style = MaterialTheme.typography.bodyMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(m.value, fontWeight = FontWeight.SemiBold)
                                        IconButton(onClick = { vm.deleteMeasurement(m.id) }) {
                                            Icon(
                                                Icons.Default.Delete, contentDescription = "حذف",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------- تاریخچهٔ سفارش‌ها ----------
            item {
                Text("سفارش‌های این مشتری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (s.orders.isEmpty()) {
                item {
                    Text(
                        "سفارشی برای این مشتری ثبت نشده.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(s.orders, key = { it.id }) { o ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(o.designTitle.ifBlank { o.orderCode }, fontWeight = FontWeight.Medium)
                                Text(
                                    (if (o.agreedPrice > 0) o.agreedPrice else o.fabricPrice + o.workCost).afn(),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                "${o.orderCode} • ${PersianDate.short(o.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { onOpenOrder(o.id.toString()) }) { Text("مشاهدهٔ سفارش") }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}
