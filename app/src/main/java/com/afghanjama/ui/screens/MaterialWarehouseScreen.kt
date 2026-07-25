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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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

    // ---------- دیالوگ اصلاح موجودی ----------
    editTarget?.let { item ->
        var amountText by remember(item.id) { mutableStateOf(fmtAmountLatin(item.amount)) }
        var minText by remember(item.id) { mutableStateOf(fmtAmountLatin(item.minLevel)) }
        var wasteText by remember(item.id) { mutableStateOf("") }
        AlertDialog(
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it.decimalOnly() },
                        label = { Text("حد هشدار کمبود") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = wasteText,
                        onValueChange = { wasteText = it.decimalOnly() },
                        label = { Text("ثبت ضایعات (خروج از موجودی)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("لغو") } }
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
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
                        )
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
                        Modifier.fillMaxWidth().padding(14.dp),
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
                                    "⚠ موجودی کم",
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
                        if (canAdjust) {
                            IconButton(onClick = { editTarget = item }) {
                                Icon(Icons.Default.Edit, contentDescription = "اصلاح موجودی")
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
