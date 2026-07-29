@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
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
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.ProductionViewModel

private fun fmtNum(v: Double): String =
    (if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()).toPersianDigits()

@Composable
fun ProductionOrderScreen(
    vm: ProductionViewModel,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val materials by vm.materials.collectAsState()
    val designs by vm.designs.collectAsState()
    val sizes by vm.sizes.collectAsState()
    val designCounts by vm.designCounts.collectAsState()
    val workCosts by vm.workCosts.collectAsState()

    var pickerOpen by remember { mutableStateOf(false) }
    var designMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var addDesignOpen by remember { mutableStateOf(false) }
    var newDesignCode by remember { mutableStateOf("") }

    // ---------- ثبت طرح جدید در کاتالوگ با کدِ اختصاصیِ کارگاه ----------
    if (addDesignOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { addDesignOpen = false },
            title = { Text("ثبت طرح در کاتالوگ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نام طرح: ${ui.designTitle}")
                    OutlinedTextField(
                        value = newDesignCode,
                        onValueChange = { newDesignCode = it },
                        label = { Text("کد اختصاصی طرح (کد کارگاه؛ خالی = خودکار)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.addDesignToCatalog(ui.designTitle, newDesignCode)
                    addDesignOpen = false; newDesignCode = ""
                }) { Text("ثبت") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { addDesignOpen = false }) { Text("لغو") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("شروع تولید") },
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
            // ---------- مشخصات سفارش ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("مشخصات سفارش", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = ui.designTitle,
                            onValueChange = vm::setDesignTitle,
                            label = { Text("نام طرح / محصول") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { designMenu = true },
                                modifier = Modifier.weight(1f),
                                enabled = designs.isNotEmpty()
                            ) {
                                Text(if (designs.isEmpty()) "فهرست طرح خالی است" else "انتخاب از طرح‌های ثبت‌شده")
                            }
                            IconButton(
                                onClick = { addDesignOpen = true },
                                enabled = ui.designTitle.isNotBlank()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "افزودن طرح به کاتالوگ")
                            }
                        }
                        DropdownMenu(expanded = designMenu, onDismissRequest = { designMenu = false }) {
                            designs.forEach { d ->
                                val made = designCounts[d.title] ?: 0
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(d.title)
                                            Text(
                                                (if (d.code.isNotBlank()) "${d.code} • " else "") +
                                                    "تولید تاکنون: ${made.toString().toPersianDigits()} عدد",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = { vm.setDesignTitle(d.title); designMenu = false }
                                )
                            }
                        }
                        // کد اختصاصی و شمارندهٔ طرحِ انتخاب‌شده
                        designs.firstOrNull { it.title == ui.designTitle }?.let { d ->
                            val made = designCounts[d.title] ?: 0
                            Text(
                                (if (d.code.isNotBlank()) "کد طرح: ${d.code} • " else "") +
                                    "تولید تا این لحظه: ${made.toString().toPersianDigits()} عدد",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ui.qty,
                                onValueChange = vm::setQty,
                                label = { Text("تعداد") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = ui.size,
                                onValueChange = vm::setSize,
                                label = { Text("سایز") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { sizeMenu = true },
                                modifier = Modifier.weight(1f),
                                enabled = sizes.isNotEmpty()
                            ) {
                                Text(if (sizes.isEmpty()) "فهرست سایز خالی است" else "انتخاب سایز از فهرست")
                            }
                            IconButton(
                                onClick = { vm.addSizeToCatalog(ui.size) },
                                enabled = ui.size.isNotBlank()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "افزودن سایز به کاتالوگ")
                            }
                        }
                        DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                            sizes.forEach { sz ->
                                DropdownMenuItem(
                                    text = { Text(sz.title) },
                                    onClick = { vm.setSize(sz.title); sizeMenu = false }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = ui.customerName,
                            onValueChange = vm::setCustomerName,
                            label = { Text("نام مشتری (اختیاری)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ui.agreedPrice,
                            onValueChange = vm::setAgreedPrice,
                            label = { Text("قیمت توافقی (اختیاری، ؋)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // ---------- مهلت تحویل ----------
                        Text(
                            "مهلت تحویل (اختیاری)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(7L, 15L, 30L).forEach { d ->
                                FilterChip(
                                    selected = ui.dueDays == d.toString(),
                                    onClick = {
                                        vm.setDueDays(if (ui.dueDays == d.toString()) "" else d.toString())
                                    },
                                    label = { Text("${d.fa()} روز") }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = ui.dueDays,
                            onValueChange = vm::setDueDays,
                            label = { Text("یا تعداد روز دلخواه") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        ui.dueDays.toLongOrNull()?.takeIf { it > 0 }?.let { d ->
                            Text(
                                "📅 تحویل تا ${PersianDate.long(System.currentTimeMillis() + d * 86_400_000L)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // ---------- بیعانه ----------
                        OutlinedTextField(
                            value = ui.deposit,
                            onValueChange = vm::setDeposit,
                            label = { Text("بیعانهٔ دریافتی (اختیاری، ؋)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        ui.deposit.toLongOrNull()?.takeIf { it > 0 }?.let { dep ->
                            val agreed = ui.agreedPrice.toLongOrNull() ?: 0L
                            Text(
                                "💰 بیعانه به صندوق می‌رود و رسیدش صادر می‌شود" +
                                    (if (agreed > 0) " • باقی‌ماندهٔ مشتری: ${(agreed - dep).afn()}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (agreed in 1 until dep) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ---------- انتخاب ماده از انبار ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("مصرف مواد از انبار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (materials.isEmpty()) {
                            Text(
                                "انبار مواد خالی است. اول از بخش «خرید مواد» مواد را وارد کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (ui.pickedName.isBlank()) "انتخاب ماده از انبار"
                                else "${ui.pickedName} • موجودی به واحد ${ui.pickedUnit}"
                            )
                        }
                        DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                            materials.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text("${m.name} — ${fmtNum(m.amount)} ${m.unit}") },
                                    onClick = { vm.pickMaterial(m); pickerOpen = false }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = ui.amount,
                            onValueChange = vm::setAmount,
                            label = { Text("مقدار مصرف" + if (ui.pickedUnit.isNotBlank()) " (${ui.pickedUnit})" else "") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !ui.amountPerPiece,
                                onClick = { vm.setAmountPerPiece(false) },
                                label = { Text("کل سفارش") }
                            )
                            FilterChip(
                                selected = ui.amountPerPiece,
                                onClick = { vm.setAmountPerPiece(true) },
                                label = { Text("فی‌عدد") }
                            )
                        }
                        Button(onClick = vm::addLine, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("افزودن ماده")
                        }
                    }
                }
            }

            // ---------- مواد افزوده‌شده ----------
            if (ui.lines.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "مواد این سفارش (${ui.lines.size.toString().toPersianDigits()})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            ui.lines.forEachIndexed { index, line ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(line.name, fontWeight = FontWeight.Medium)
                                        Text(
                                            "${fmtNum(line.amount)} ${line.unit}" +
                                                if (line.perPiece) " (فی‌عدد)" else " (کل)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { vm.removeLine(index) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "حذف",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------- خرج‌کار ----------
            // قیمتِ هر الگو فی‌عدد است، پس جمعِ سفارش در تعداد ضرب می‌شود.
            // اگر تعداد هنوز وارد نشده، ۱ فرض می‌شود تا عدد بی‌معنا نشود.
            if (workCosts.isNotEmpty()) {
                item {
                    val qtyForPreview = ui.qty.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val perPiece = ui.workItems.sumOf { it.price }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "خرج کار (اختیاری)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "دکمه، زیپ، لایی… — قیمتِ هر کدام فی‌عدد است. " +
                                    "فهرست از «اطلاعات پایه» می‌آید.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                workCosts.forEach { w ->
                                    FilterChip(
                                        selected = ui.workItems.any { it.title == w.title },
                                        onClick = { vm.toggleWorkItem(w) },
                                        label = { Text("${w.title} — ${w.price.afn()}") }
                                    )
                                }
                            }
                            if (perPiece > 0) {
                                Text(
                                    "جمع فی‌عدد: ${perPiece.afn()}  •  " +
                                        "خرج کار این سفارش: ${(perPiece * qtyForPreview).afn()}" +
                                        if (ui.qty.toIntOrNull() == null) " (برای ۱ عدد)" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ---------- پیام ----------
            ui.message?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ui.isError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(12.dp),
                            color = if (ui.isError) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item {
                Button(onClick = vm::completeProduction, modifier = Modifier.fillMaxWidth()) {
                    Text("ثبت سفارش تولید و ارسال به انبار")
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}
