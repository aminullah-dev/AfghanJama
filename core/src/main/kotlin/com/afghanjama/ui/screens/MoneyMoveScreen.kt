@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.BusyButton
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.vm.MoneyMoveViewModel
import com.afghanjama.ui.vm.payeeTypes

/**
 * پرداخت و دریافتِ سریع — به هر کسی.
 *
 * عمداً یک صفحهٔ ساده است، نه یک فرمِ شلوغ: جهت، طرف، مبلغ، صندوق.
 * ماندهٔ فعلیِ طرف بالای فرم نوشته می‌شود تا معلوم باشد این پرداخت
 * «تسویهٔ طلب» است یا «پیش‌پرداخت» — چون خودِ عدد این را نمی‌گوید.
 */
@Composable
fun MoneyMoveScreen(
    vm: MoneyMoveViewModel,
    startAsPayment: Boolean,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val busy by vm.busy.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var isPayment by remember { mutableStateOf(startAsPayment) }
    var type by remember { mutableStateOf(payeeTypes.first().first) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("WALLET") }
    var note by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            val wasDone = ui.done
            vm.clearMessage()
            if (wasDone) { amount = ""; note = "" }
        }
    }

    val selected = ui.payees.firstOrNull { it.type == type && it.name == name }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (isPayment) "پرداخت" else "دریافت") },
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
            // ---------- جهت ----------
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { isPayment = true },
                        modifier = Modifier.weight(1f),
                        colors = if (isPayment) ButtonDefaults.buttonColors()
                        else ButtonDefaults.outlinedButtonColors()
                    ) { Text("پرداخت 🡐") }
                    Button(
                        onClick = { isPayment = false },
                        modifier = Modifier.weight(1f),
                        colors = if (!isPayment) ButtonDefaults.buttonColors()
                        else ButtonDefaults.outlinedButtonColors()
                    ) { Text("دریافت 🡒") }
                }
            }

            // ---------- طرف حساب ----------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        payeeTypes.take(3).forEach { (key, label) ->
                            FilterChip(
                                selected = type == key,
                                onClick = { type = key; name = "" },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        payeeTypes.drop(3).forEach { (key, label) ->
                            FilterChip(
                                selected = type == key,
                                onClick = { type = key; name = "" },
                                label = { Text(label) }
                            )
                        }
                    }

                    Box {
                        OutlinedButton(
                            onClick = { pickerOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (name.isBlank()) "انتخاب از فهرست" else name)
                        }
                        DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                            val list = ui.of(type)
                            if (list.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("کسی ثبت نشده — نام را دستی بنویسید") },
                                    onClick = { pickerOpen = false }
                                )
                            }
                            list.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(p.name)
                                            Text(
                                                when {
                                                    p.net > 0 -> "به ما بدهکار: ${p.net.afn()}"
                                                    p.net < 0 -> "ما بدهکاریم: ${(-p.net).afn()}"
                                                    else -> "تسویه"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = { name = p.name; pickerOpen = false }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام (یا هر کسِ دیگر را دستی بنویسید)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ---------- وضعیتِ فعلیِ طرف: تسویه یا پیش‌پرداخت؟ ----------
            if (name.isNotBlank()) {
                item {
                    val net = selected?.net ?: 0L
                    val settling = if (isPayment) net < 0 else net > 0
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (settling) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            val onColor = if (settling) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer
                            Text(
                                when {
                                    net > 0 -> "$name به ما ${net.afn()} بدهکار است"
                                    net < 0 -> "ما به $name ${(-net).afn()} بدهکاریم"
                                    else -> "حساب $name تسویه است"
                                },
                                fontWeight = FontWeight.SemiBold,
                                color = onColor
                            )
                            Text(
                                if (settling) "این مبلغ از همان حساب کم می‌شود."
                                else if (isPayment)
                                    "طلبی ندارد — این یک پیش‌پرداخت است و به حساب او بدهکار می‌نشیند."
                                else "بدهی‌ای ندارد — این یک دریافتِ زودهنگام (پیش‌دریافت) است.",
                                style = MaterialTheme.typography.bodySmall,
                                color = onColor
                            )
                        }
                    }
                }
            }

            // ---------- مبلغ و صندوق ----------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.digitsOnly() },
                        label = { Text("مبلغ (؋)") },
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
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("توضیح (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                BusyButton(
                    text = if (isPayment) "ثبت پرداخت" else "ثبت دریافت",
                    onClick = {
                        vm.submit(
                            type = type, name = name.trim(),
                            amount = amount.toLongOrNull() ?: 0L,
                            isPayment = isPayment, source = source, note = note
                        )
                    },
                    enabled = name.isNotBlank() && (amount.toLongOrNull() ?: 0L) > 0,
                    busy = busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    "با هر ثبت، صندوق، دفتر کل و رسید همزمان به‌روز می‌شوند. " +
                        "پرداختِ بیشتر از موجودیِ صندوق پذیرفته نمی‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
