@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.afghanjama.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.Document
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.vm.DocumentsViewModel

private fun docTypeLabel(t: String): String = when (t) {
    "PURCHASE" -> "فاکتور خرید"
    "SALE" -> "فاکتور فروش"
    "SUPPLIER_PAYMENT" -> "رسید پرداخت به فروشنده"
    "WAGE_RECEIPT" -> "رسید کارمزد دوخت"
    "CUSTOMER_RECEIPT" -> "رسید دریافت از مشتری"
    "RETURN" -> "سند برگشت"
    "PROFORMA" -> "پیش‌فاکتور"
    "PAYMENT" -> "رسید پرداخت"
    "RECEIPT" -> "رسید دریافت"
    else -> "سند"
}

private fun receiptText(d: Document): String = buildString {
    appendLine("افغان‌جامه — ${docTypeLabel(d.type)}")
    appendLine("شماره: ${d.number}")
    appendLine("تاریخ: ${PersianDate.short(d.at)}")
    if (d.partyName.isNotBlank()) appendLine("طرف حساب: ${d.partyName}")
    appendLine("مبلغ: ${d.amount.afn()}")
    if (d.refId.isNotBlank()) appendLine("مرجع: ${d.refId}")
    if (d.note.isNotBlank()) appendLine("توضیح: ${d.note}")
}

@Composable
fun DocumentsScreen(
    vm: DocumentsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val documents by vm.documents.collectAsState()

    var typeFilter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Document?>(null) }

    val types = documents.map { it.type }.distinct()
    val shown = documents.filter { typeFilter == null || it.type == typeFilter }

    // ---------- دیالوگ سند ----------
    selected?.let { d ->
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = {
                Button(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "سند ${d.number}")
                        putExtra(Intent.EXTRA_TEXT, receiptText(d))
                    }
                    context.startActivity(Intent.createChooser(send, "اشتراک‌گذاری سند"))
                }) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  اشتراک‌گذاری")
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("بستن") } },
            title = { Text("${docTypeLabel(d.type)} • ${d.number}") },
            text = {
                Column {
                    Text(receiptText(d))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اسناد") },
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
                            label = { Text(docTypeLabel(t)) }
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                Text(
                    "هنوز سندی ثبت نشده. با هر خرید، فروش یا تسویه یک سند خودکار ساخته می‌شود.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.id }) { d ->
                        Card(
                            onClick = { selected = d },
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
                                    Text(
                                        "${docTypeLabel(d.type)} • ${d.number}",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        (if (d.partyName.isNotBlank()) "${d.partyName} • " else "") +
                                            PersianDate.short(d.at),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    d.amount.afn(),
                                    fontWeight = FontWeight.Bold,
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
