@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.EmptyState
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.PayrollRow
import com.afghanjama.ui.vm.PayrollViewModel

/**
 * حقوقِ ماهانهٔ کارکنان: چه کسی چقدر می‌گیرد، حقوقِ این ماه پرداخت شده
 * یا نه، و پرداخت با یک ضربه (که رسید، سندِ هزینه و لاگ می‌سازد).
 */
@Composable
fun PayrollScreen(
    vm: PayrollViewModel,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<PayrollRow?>(null) }
    var paying by remember { mutableStateOf<PayrollRow?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // ---------- افزودن / ویرایش کارمند ----------
    if (addOpen || editing != null) {
        val row = editing
        var name by remember(row) { mutableStateOf(row?.name.orEmpty()) }
        var role by remember(row) { mutableStateOf(row?.role.orEmpty()) }
        var salary by remember(row) {
            mutableStateOf(if ((row?.monthlySalary ?: 0) > 0) row!!.monthlySalary.toString() else "")
        }
        AlertDialog(
            onDismissRequest = { addOpen = false; editing = null },
            title = { Text(if (row == null) "کارمند جدید" else "ویرایش ${row.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام کارمند") },
                        singleLine = true,
                        enabled = row == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = role,
                        onValueChange = { role = it },
                        label = { Text("سمت (آشپز، حسابدار، مدیر…)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = salary,
                        onValueChange = { salary = it.digitsOnly() },
                        label = { Text("حقوق ماهانه (؋) — خالی یعنی حقوق‌بگیر نیست") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveStaff(name, role, salary.toLongOrNull() ?: 0L)
                    addOpen = false; editing = null
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { addOpen = false; editing = null }) { Text("انصراف") }
            }
        )
    }

    // ---------- پرداخت حقوق ----------
    paying?.let { row ->
        var amount by remember(row) {
            mutableStateOf(row.remaining.takeIf { it > 0 }?.toString() ?: row.monthlySalary.toString())
        }
        var source by remember(row) { mutableStateOf("WALLET") }
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("پرداخت حقوق ${row.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "دورهٔ ${ui.monthLabel} • حقوق توافقی: ${row.monthlySalary.afn()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (row.paidThisMonth > 0) {
                        Text(
                            "قبلاً در این ماه پرداخت شده: ${row.paidThisMonth.afn()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.digitsOnly() },
                        label = { Text("مبلغ پرداخت (؋)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = source == "WALLET",
                            onClick = { source = "WALLET" },
                            label = { Text("صندوق (${ui.wallet.afn()})") }
                        )
                        FilterChip(
                            selected = source == "BANK",
                            onClick = { source = "BANK" },
                            label = { Text("بانک (${ui.bank.afn()})") }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.pay(row.name, amount.toLongOrNull() ?: 0L, source)
                    paying = null
                }) { Text("پرداخت") }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("انصراف") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("حقوق کارکنان") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { addOpen = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "کارمند جدید")
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
            // ---------- خلاصهٔ ماه ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ui.unpaidCount > 0)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val onColor = if (ui.unpaidCount > 0)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                        Text(
                            "حقوق ${ui.monthLabel}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onColor
                        )
                        Text(
                            "جمعِ ماهانه ${ui.monthlyTotal.afn()} • پرداخت‌شده ${ui.paidTotal.afn()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = onColor
                        )
                        Text(
                            if (ui.unpaidCount > 0)
                                "باقی‌مانده ${ui.remainingTotal.afn()} برای ${ui.unpaidCount.fa()} نفر"
                            else "✓ حقوقِ همه پرداخت شده است",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onColor
                        )
                    }
                }
            }

            if (ui.rows.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Groups,
                        title = "هنوز کارمندی ثبت نشده",
                        hint = "با دکمهٔ ➕ بالای صفحه کارمند اضافه کنید و حقوقِ " +
                            "ماهانه‌اش را بنویسید تا یادآورِ پرداخت فعال شود."
                    )
                }
            }

            items(ui.rows, key = { it.name }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(row.name, fontWeight = FontWeight.SemiBold)
                                if (row.role.isNotBlank()) {
                                    Text(
                                        row.role,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (row.monthlySalary > 0) {
                                Text(
                                    if (row.isPaid) "✓ پرداخت شد" else "در انتظار پرداخت",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.isPaid) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        if (row.monthlySalary > 0) {
                            Text(
                                "حقوق ماهانه: ${row.monthlySalary.afn()}" +
                                    (if (row.paidThisMonth > 0) " • پرداختِ این ماه: ${row.paidThisMonth.afn()}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "حقوقِ ماهانه ثبت نشده (کارمزدی)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { editing = row }) { Text("ویرایش") }
                            if (row.monthlySalary > 0 && !row.isPaid) {
                                Button(onClick = { paying = row }) { Text("پرداخت حقوق") }
                            }
                        }
                    }
                }
            }

            // ---------- تاریخچهٔ پرداخت ----------
            if (ui.payments.isNotEmpty()) {
                item {
                    Text(
                        "تاریخچهٔ پرداخت‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(ui.payments, key = { "pay-${it.id}" }) { p ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${p.employee} — ${p.periodLabel}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                p.amount.afn(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            PersianDate.shortWithTime(p.at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
