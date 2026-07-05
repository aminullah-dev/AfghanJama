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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.FabricStock
import com.afghanjama.data.entities.FabricUnit
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.vm.StockViewModel

private fun unitLabel(v: String): String = when (v.trim().uppercase()) {
    FabricUnit.METER.name -> "متر"
    FabricUnit.YARD.name -> "یارد"
    else -> v
}

/** موجودی پارچه انبار: خرید، اصلاح دستی و حد هشدار کمبود. */
@Composable
fun FabricStockScreen(
    vm: StockViewModel,
    canAdjust: Boolean,
    onBack: () -> Unit
) {
    val stocks by vm.stocks.collectAsState()
    val types by vm.fabricTypes.collectAsState()
    val colors by vm.fabricColors.collectAsState()
    val ui by vm.ui.collectAsState()

    var showBuy by remember { mutableStateOf(false) }
    var adjustTarget by remember { mutableStateOf<FabricStock?>(null) }
    var minTarget by remember { mutableStateOf<FabricStock?>(null) }

    // ---------- دیالوگ خرید پارچه ----------
    if (showBuy) {
        var type by remember { mutableStateOf("") }
        var color by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var priceText by remember { mutableStateOf("") }
        var paySource by remember { mutableStateOf("WALLET") }

        var typeMenu by remember { mutableStateOf(false) }
        var colorMenu by remember { mutableStateOf(false) }
        var unitMenu by remember { mutableStateOf(false) }
        var payMenu by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showBuy = false },
            title = { Text("خرید پارچه برای انبار") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(type.ifBlank { "نوع پارچه" })
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        types.forEach { t ->
                            DropdownMenuItem(text = { Text(t.title) }, onClick = { type = t.title; typeMenu = false })
                        }
                    }

                    OutlinedButton(onClick = { colorMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(color.ifBlank { "رنگ" })
                    }
                    DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                        colors.forEach { c ->
                            DropdownMenuItem(text = { Text(c.title) }, onClick = { color = c.title; colorMenu = false })
                        }
                    }

                    OutlinedButton(onClick = { unitMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (unit.isBlank()) "واحد (متر/یارد)" else unitLabel(unit))
                    }
                    DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                        FabricUnit.entries.forEach { u ->
                            DropdownMenuItem(text = { Text(unitLabel(u.name)) }, onClick = { unit = u.name; unitMenu = false })
                        }
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { v -> amountText = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("مقدار") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter(Char::isDigit) },
                        label = { Text("قیمت کل (؋)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(onClick = { payMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "پرداخت از: " + when (paySource) {
                                "BANK" -> "بانک"
                                "PROFIT" -> "فایده"
                                else -> "کیف پول"
                            }
                        )
                    }
                    DropdownMenu(expanded = payMenu, onDismissRequest = { payMenu = false }) {
                        DropdownMenuItem(text = { Text("کیف پول") }, onClick = { paySource = "WALLET"; payMenu = false })
                        DropdownMenuItem(text = { Text("بانک") }, onClick = { paySource = "BANK"; payMenu = false })
                        DropdownMenuItem(text = { Text("فایده") }, onClick = { paySource = "PROFIT"; payMenu = false })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.buyFabric(
                            type = type,
                            color = color,
                            unit = unit,
                            amount = amountText.toDoubleOrNull() ?: 0.0,
                            totalPrice = priceText.toLongOrNull() ?: 0L,
                            paySource = paySource
                        )
                        showBuy = false
                    }
                ) { Text("ثبت خرید") }
            },
            dismissButton = { TextButton(onClick = { showBuy = false }) { Text("لغو") } }
        )
    }

    // ---------- دیالوگ اصلاح موجودی ----------
    adjustTarget?.let { st ->
        var newAmount by remember(st.id) { mutableStateOf(st.amount.toString()) }
        AlertDialog(
            onDismissRequest = { adjustTarget = null },
            title = { Text("اصلاح موجودی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${st.fabricType} • ${st.fabricColor} (${unitLabel(st.fabricUnit)})")
                    OutlinedTextField(
                        value = newAmount,
                        onValueChange = { v -> newAmount = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("موجودی جدید (بعد از شمارش)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    newAmount.toDoubleOrNull()?.let { vm.adjustStock(st, it) }
                    adjustTarget = null
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { adjustTarget = null }) { Text("لغو") } }
        )
    }

    // ---------- دیالوگ حد هشدار ----------
    minTarget?.let { st ->
        var minText by remember(st.id) { mutableStateOf(st.minLevel.toString()) }
        AlertDialog(
            onDismissRequest = { minTarget = null },
            title = { Text("حد هشدار کمبود") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("وقتی موجودی «${st.fabricType} ${st.fabricColor}» از این حد کمتر شود، هشدار نمایش داده می‌شود.")
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { v -> minText = v.filter { it.isDigit() || it == '.' } },
                        label = { Text("حد هشدار (${unitLabel(st.fabricUnit)})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    minText.toDoubleOrNull()?.let { vm.setMinLevel(st, it) }
                    minTarget = null
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { minTarget = null }) { Text("لغو") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("موجودی پارچه") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ui.message?.let { msg ->
                Text(
                    msg,
                    color = if (ui.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            Button(onClick = { showBuy = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("خرید پارچه")
            }

            if (stocks.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("هنوز موجودی پارچه‌ای ثبت نشده.", fontWeight = FontWeight.SemiBold)
                        Text(
                            "با «خرید پارچه» موجودی اضافه کنید. هنگام ثبت سفارش با گزینه «از موجودی انبار»، پارچه خودکار کم می‌شود.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(stocks, key = { it.id }) { st ->
                        val low = st.minLevel > 0 && st.amount < st.minLevel
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${st.fabricType} • ${st.fabricColor}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "حد هشدار: ${st.minLevel} ${unitLabel(st.fabricUnit)}" +
                                                if (st.avgPrice > 0)
                                                    " • میانگین خرید: ${st.avgPrice.toLong().afn()}/${unitLabel(st.fabricUnit)}"
                                                else "",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "${st.amount} ${unitLabel(st.fabricUnit)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (low) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (low) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "موجودی کمتر از حد هشدار است — خرید پارچه لازم است!",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                HorizontalDivider(thickness = 0.5.dp)

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (canAdjust) {
                                        OutlinedButton(
                                            onClick = { adjustTarget = st },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("اصلاح")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { minTarget = st },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("حد هشدار")
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}
