@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Box
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.platform.AppDropdownMenuItem
import com.afghanjama.ui.platform.AppDropdownMenu
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import com.afghanjama.ui.platform.AppAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.decimalOnly
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.WarehouseViewModel

private fun fmtAmount(v: Double): String =
    (if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()).toPersianDigits()

@Composable
fun MaterialWarehouseScreen(
    vm: WarehouseViewModel,
    canAdjust: Boolean,
    onBack: () -> Unit
) {
    val materials by vm.materials.collectAsState()
    val ui by vm.ui.collectAsState()

    var editTarget by remember { mutableStateOf<MaterialStock?>(null) }

    // کاردکسِ یک قلم — `null` یعنی بسته است.
    var historyOf by remember { mutableStateOf<MaterialStock?>(null) }

    historyOf?.let { item ->
        val moves by vm.movementsOf(item).collectAsState(initial = emptyList())
        AppAlertDialog(
            onDismissRequest = { historyOf = null },
            title = { Text("ورود و خروج — ${item.name}") },
            text = {
                if (moves.isEmpty()) {
                    Text(
                        "برای این قلم هنوز حرکتی ثبت نشده.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(moves, key = { it.id }) { m ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.reason, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        PersianDate.shortWithTime(m.createdAt) +
                                            (if (m.note.isBlank()) "" else " — ${m.note}"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // ورود و خروج باید در نگاهِ اول از هم جدا
                                // باشند؛ علامتِ تنها کافی نیست چون در
                                // فهرستِ بلند خوانده نمی‌شود.
                                Text(
                                    (if (m.delta >= 0) "+" else "") +
                                        "${fmtAmount(m.delta)} ${item.unit}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (m.delta >= 0)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { historyOf = null }) { Text("بستن") }
            },
            dismissButton = null
        )
    }

    // ---------- دیالوگ اصلاح موجودی ----------
    editTarget?.let { item ->
        var amountText by remember(item.id) { mutableStateOf(fmtAmountLatin(item.amount)) }
        var minText by remember(item.id) { mutableStateOf(fmtAmountLatin(item.minLevel)) }
        var wasteText by remember(item.id) { mutableStateOf("") }
        AppAlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("اصلاح «${item.name}»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "موجودی واقعی و حد هشدار کمبود را وارد کنید (واحد: ${item.unit}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.decimalOnly() },
                        label = { Text("موجودی فعلی") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it.decimalOnly() },
                        label = { Text("حد هشدار کمبود") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = wasteText,
                        onValueChange = { wasteText = it.decimalOnly() },
                        label = { Text("ثبت ضایعات (خروج از موجودی)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val waste = wasteText.toDoubleOrNull()
                    if (waste != null && waste > 0.0) {
                        // ثبت ضایعات: موجودی از رقمِ فعلی کم می‌شود (نه تنظیم مطلق)
                        vm.recordWaste(item, waste)
                    } else {
                        amountText.toDoubleOrNull()?.let { vm.setAmount(item, it) }
                    }
                    minText.toDoubleOrNull()?.let { vm.setMinLevel(item, it) }
                    editTarget = null
                }) { Text("ذخیره") }
            },
            dismissButton = {
                Row {
                    // حذف فقط برای ردیفِ خالی — ردیفی که موجودی دارد ارزش
                    // هم دارد و حذفِ مستقیمش حسابِ مواد را از انبار جدا
                    // می‌کرد. برای آن ردیف اول باید ضایعات ثبت شود.
                    if (item.amount <= 0.0) {
                        TextButton(onClick = {
                            vm.deleteRow(item)
                            editTarget = null
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("حذف", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editTarget = null }) { Text("لغو") }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("انبار مواد") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        if (materials.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "انبار خالی است. از بخش «خرید مواد» اقلام را وارد کنید.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val totalValue = materials.sumOf { it.amount * it.avgPrice }.toLong()

        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ارزش تقریبی انبار", fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(totalValue.afn(), fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(msg, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            items(materials, key = { it.id }) { item ->
                val low = item.minLevel > 0.0 && item.amount <= item.minLevel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "میانگین قیمت: ${item.avgPrice.toLong().afn()} / ${item.unit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (low) {
                                Text(
                                    "موجودی کم",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${fmtAmount(item.amount)} ${item.unit}",
                                fontWeight = FontWeight.Bold,
                                color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        /*
                         * «⋮» به‌جای یک آیکنِ تنها.
                         *
                         * کاردکسِ هر قلم از روزِ اول ثبت می‌شد ولی هیچ
                         * راهی برای دیدنش نبود. افزودنِ آیکنِ دوم کنارِ
                         * ویرایش، سطر را شلوغ می‌کرد و سومی را هم جایی
                         * نمی‌گذاشت؛ منو هرچقدر کنشِ تازه لازم شود جا
                         * دارد.
                         *
                         * تاریخچه برای همه باز است — دیدنِ اینکه یک قلم
                         * کِی کم شد اجازه نمی‌خواهد. فقط **تغییر دادن**
                         * `canAdjust` می‌خواهد.
                         */
                        var menu by remember(item.name, item.unit) { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "گزینه‌ها")
                            }
                            AppDropdownMenu(
                                expanded = menu,
                                onDismissRequest = { menu = false }
                            ) {
                                if (canAdjust) {
                                    AppDropdownMenuItem(
                                        text = { Text("اصلاح موجودی و ضایعات") },
                                        onClick = { menu = false; editTarget = item }
                                    )
                                }
                                AppDropdownMenuItem(
                                    text = { Text("لیست ورود و خروج این قلم") },
                                    onClick = { menu = false; historyOf = item }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

/** رشتهٔ لاتین برای مقداردهی اولیهٔ فیلد ورودی (تا toDoubleOrNull کار کند). */
private fun fmtAmountLatin(v: Double): String =
    if (v <= 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
