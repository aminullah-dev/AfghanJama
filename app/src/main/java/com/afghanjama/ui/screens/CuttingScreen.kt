@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.afghanjama.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.Order
import com.afghanjama.ui.format.STAGE_WARN_DAYS
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.stageDays
import com.afghanjama.ui.vm.CuttingViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun CuttingScreen(
    vm: CuttingViewModel,
    onBack: () -> Unit,
    onGoSewing: () -> Unit
) {
    val orders by vm.ordersCutting.collectAsState(initial = emptyList())
    val tailors by vm.tailors.collectAsState(initial = emptyList())

    var cutTarget by remember { mutableStateOf<Order?>(null) }
    var scanMsg by remember { mutableStateOf<String?>(null) }

    // اسکنِ QR سفارش با دوربین → یافتنِ سفارش در صف برش و بازکردنِ دیالوگ
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents
        if (code != null) {
            val match = orders.firstOrNull { it.shortCode == code || it.orderCode == code }
            if (match != null) {
                cutTarget = match; scanMsg = null
            } else {
                scanMsg = "سفارشی با این کد در صف برش نیست: $code"
            }
        }
    }

    // ---------- دیالوگ ثبت برش ----------
    cutTarget?.let { o ->
        var cutter by remember(o.id) { mutableStateOf("") }
        var pieces by remember(o.id) { mutableStateOf(o.qty.toString()) }
        var waste by remember(o.id) { mutableStateOf("") }
        var note by remember(o.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cutTarget = null },
            title = { Text("ثبت برش — ${o.designTitle.ifBlank { o.orderCode }}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cutter,
                        onValueChange = { cutter = it },
                        label = { Text("مسئول برش") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tailors.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tailors.take(8).forEach { t ->
                                FilterChip(
                                    selected = cutter == t.name,
                                    onClick = { cutter = t.name },
                                    label = { Text(t.name) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = pieces,
                        onValueChange = { pieces = it.digitsOnly() },
                        label = { Text("تعداد دستِ برش‌شده") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = waste,
                        onValueChange = { waste = it },
                        label = { Text("ضایعات (اختیاری، مثلاً ۲ متر)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("یادداشت (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = cutter.isNotBlank(),
                    onClick = {
                        vm.markCutDone(o.id, cutter, pieces.toIntOrNull() ?: o.qty, waste, note)
                        cutTarget = null
                        onGoSewing()
                    }
                ) { Text("ثبت و انتقال به دوخت") }
            },
            dismissButton = { TextButton(onClick = { cutTarget = null }) { Text("لغو") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بخش برش") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ContentCut, contentDescription = "برگشت")
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
            androidx.compose.material3.Button(
                onClick = {
                    scanLauncher.launch(
                        ScanOptions().apply {
                            setPrompt("QR سفارش را اسکن کنید")
                            setBeepEnabled(true)
                            setOrientationLocked(false)
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Text("  اسکن QR سفارش")
            }
            scanMsg?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "سفارش‌های در حال برش",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (orders.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("فعلاً سفارشی برای برش وجود ندارد.", fontWeight = FontWeight.SemiBold)
                        Text(
                            "از انبار «ارسال به برش» را بزن.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(orders, key = { it.id }) { o ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = o.designTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${o.orderCode} • تعداد: ${o.qty}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "پارچه: ${o.fabricType} • رنگ: ${o.fabricColor} • سایز: ${o.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                val days = stageDays(o.stageChangedAt, o.createdAt)
                                Text(
                                    text = "⏱ ${days.fa()} روز در این مرحله" +
                                        if (days >= STAGE_WARN_DAYS) " — معطل مانده!" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (days >= STAGE_WARN_DAYS) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(2.dp))

                                androidx.compose.material3.Button(
                                    onClick = { cutTarget = o },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Done, contentDescription = null)
                                    Spacer(Modifier.height(0.dp))
                                    Text("ثبت برش → انتقال به دوخت")
                                }

                                androidx.compose.material3.TextButton(
                                    onClick = { vm.backToStock(o.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("↩ برگشت به انبار (اصلاح اشتباه)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
