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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.PaymentSource
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.ProcurementViewModel

private fun fmtQty(v: Double): String =
    (if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()).toPersianDigits()

@Composable
fun ProcurementScreen(
    vm: ProcurementViewModel,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خرید مواد خام") },
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
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- ویرایشگر قلم ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("افزودن قلم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = ui.name,
                            onValueChange = vm::setName,
                            label = { Text("نام قلم (پارچه، دکمه، زیپ، نخ...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ui.unit,
                                onValueChange = vm::setUnit,
                                label = { Text("واحد") },
                                placeholder = { Text("متر/عدد/بسته") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = ui.qty,
                                onValueChange = vm::setQty,
                                label = { Text("تعداد") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = ui.unitPrice,
                            onValueChange = vm::setUnitPrice,
                            label = { Text("قیمت هر واحد (؋)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        // دکمه و زیپ و نخ بسته‌ای خریده می‌شوند ولی عددی
                        // مصرف؛ با پر کردنِ این خانه انبار عددی نگه داشته
                        // می‌شود تا برداشتِ روزانه با واحدِ درست حساب شود.
                        OutlinedTextField(
                            value = ui.perPack,
                            onValueChange = vm::setPerPack,
                            label = { Text("داخلِ هر بسته چند عدد است؟ (اختیاری)") },
                            placeholder = { Text("مثلاً ۱۰۰") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        run {
                            val per = ui.perPack.toIntOrNull() ?: 0
                            val n = ui.qty.toDoubleOrNull() ?: 0.0
                            if (per > 1 && n > 0) {
                                Text(
                                    "وارد انبار می‌شود: ${(n * per).toLong().fa()} عدد " +
                                        "(${n.toLong().fa()} ${ui.unit.ifBlank { "بسته" }} × ${per.fa()})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Button(onClick = vm::addLine, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("افزودن به فاکتور")
                        }
                    }
                }
            }

            // ---------- اقلام افزوده‌شده ----------
            if (ui.items.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "اقلام فاکتور (${ui.items.size.toString().toPersianDigits()})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            ui.items.forEachIndexed { index, line ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(line.name, fontWeight = FontWeight.Medium)
                                        Text(
                                            "${fmtQty(line.qty)} ${line.unit} × ${line.unitPrice.afn()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(line.total.afn(), fontWeight = FontWeight.SemiBold)
                                    IconButton(onClick = { vm.removeLine(index) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "حذف",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("جمع کل", fontWeight = FontWeight.Medium)
                                Text(
                                    ui.itemsTotal.afn(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // ---------- اطلاعات فاکتور ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isCredit = ui.paymentSource.equals("CREDIT", true)
                        OutlinedTextField(
                            value = ui.supplier,
                            onValueChange = vm::setSupplier,
                            label = { Text(if (isCredit) "فروشنده (برای نسیه لازم است)" else "فروشنده / تأمین‌کننده (اختیاری)") },
                            singleLine = true,
                            isError = isCredit && ui.supplier.isBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ui.note,
                            onValueChange = vm::setNote,
                            label = { Text("یادداشت (اختیاری)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("پرداخت از:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaySourceChip(ui.paymentSource, PaymentSource.WALLET.name, "کیف پول", vm::setPaymentSource)
                            PaySourceChip(ui.paymentSource, PaymentSource.BANK.name, "بانک", vm::setPaymentSource)
                            PaySourceChip(ui.paymentSource, PaymentSource.PROFIT.name, "فایده", vm::setPaymentSource)
                            PaySourceChip(ui.paymentSource, "CREDIT", "نسیه (قرض)", vm::setPaymentSource)
                        }
                        if (isCredit) {
                            Text(
                                "خرید نسیه: پول اکنون کم نمی‌شود و به‌عنوان بدهی فروشنده ثبت می‌شود. بعداً از «دفتر کل» تسویه کنید.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

            // ---------- اتمام خرید ----------
            item {
                Button(
                    onClick = vm::completePurchase,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("اتمام خرید و ارسال به انبار")
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

@Composable
private fun PaySourceChip(
    current: String,
    value: String,
    label: String,
    onPick: (String) -> Unit
) {
    FilterChip(
        selected = current == value,
        onClick = { onPick(value) },
        label = { Text(label) }
    )
}
