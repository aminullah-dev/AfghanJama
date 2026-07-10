@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.dao.PartyBalance
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.vm.LedgerViewModel

private fun typeLabel(t: String): String = when (t) {
    "SUPPLIER" -> "تأمین‌کننده"
    "CUSTOMER" -> "مشتری"
    "TAILOR" -> "خیاط"
    "INSPECTOR" -> "ناظر"
    "EMPLOYEE" -> "کارمند"
    else -> t
}

private fun refLabel(r: String): String = when (r) {
    "WAGE" -> "کارمزد دوخت"
    "WAGE_PAID" -> "پرداخت کارمزد"
    "PURCHASE_CREDIT" -> "خرید نسیه"
    "SUPPLIER_PAYMENT" -> "پرداخت به فروشنده"
    "CUSTOMER_ADVANCE" -> "پیش‌پرداخت مشتری"
    "CUSTOMER_SALE" -> "فروش"
    "CUSTOMER_MANUAL" -> "دریافت دستی"
    "MANUAL" -> "سند دستی"
    else -> r
}

/** متنِ ماندهٔ حساب: مثبت = طرف بدهکار است، منفی = ما بدهکاریم. */
private fun balanceText(net: Long): String = when {
    net > 0 -> "بدهکار ${net.afn()}"
    net < 0 -> "بستانکار ${(-net).afn()}"
    else -> "تسویه"
}

@Composable
private fun balanceColor(net: Long): Color = when {
    net > 0 -> MaterialTheme.colorScheme.primary
    net < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun LedgerScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit
) {
    val balances by vm.balances.collectAsState()
    val entries by vm.entries.collectAsState()

    var typeFilter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<PartyBalance?>(null) }

    val receivable = balances.filter { it.net > 0 }.sumOf { it.net }
    val payable = balances.filter { it.net < 0 }.sumOf { -it.net }

    val types = balances.map { it.type }.distinct()
    val shown = balances
        .filter { typeFilter == null || it.type == typeFilter }
        .sortedByDescending { if (it.net < 0) -it.net else it.net }

    // ---------- دیالوگ گردش حساب ----------
    selected?.let { p ->
        val rows = entries.filter { it.partyType == p.type && it.partyName == p.name }
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("بستن") } },
            title = { Text("${p.name} • ${typeLabel(p.type)}") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = balanceText(p.net),
                        color = balanceColor(p.net),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (rows.isEmpty()) {
                        Text("سندی ثبت نشده.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(rows, key = { it.id }) { e ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(refLabel(e.refType), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            PersianDate.short(e.at) +
                                                (if (e.refId.isNotBlank()) " • ${e.refId}" else ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = if (e.debit > 0) "بدهکار ${e.debit.afn()}"
                                        else "بستانکار ${e.credit.afn()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (e.debit > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دفتر کل") },
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
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(Modifier.weight(1f), "طلب ما (بدهکاران)", receivable, MaterialTheme.colorScheme.primary)
                SummaryCard(Modifier.weight(1f), "بدهی ما (بستانکاران)", payable, MaterialTheme.colorScheme.error)
            }

            if (types.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = typeFilter == null,
                        onClick = { typeFilter = null },
                        label = { Text("همه") }
                    )
                    types.forEach { t ->
                        FilterChip(
                            selected = typeFilter == t,
                            onClick = { typeFilter = t },
                            label = { Text(typeLabel(t)) }
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                Text(
                    "هنوز حسابی در دفتر کل ثبت نشده.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.type + "|" + it.name }) { p ->
                        Card(
                            onClick = { selected = p },
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
                                    Text(p.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        typeLabel(p.type),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    balanceText(p.net),
                                    color = balanceColor(p.net),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier,
    title: String,
    amount: Long,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount.afn(), fontWeight = FontWeight.Bold, color = color)
        }
    }
}
