@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.QrCodeScanner
import com.afghanjama.ui.platform.AppAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.afghanjama.ui.components.WorkStage
import com.afghanjama.ui.components.StageTabs
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
import com.afghanjama.ui.components.OrderCodeLine
import com.afghanjama.data.entities.Order
import com.afghanjama.platform.LocalCodeScanner
import com.afghanjama.ui.components.MeasurementsBlock
import com.afghanjama.ui.components.OrderPhotoStrip
import com.afghanjama.ui.format.STAGE_WARN_DAYS
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.stageDays
import com.afghanjama.ui.vm.CuttingViewModel

@Composable
fun CuttingScreen(
    vm: CuttingViewModel,
    onBack: () -> Unit,
    onGoSewing: () -> Unit,
    /**
     * جابه‌جایی به مرحلهٔ دیگر — فقط روی گوشی.
     *
     * `null` یعنی تب کشیده نشود. پنجرهٔ ویندوز فهرستِ کنار
     * دارد و هر سه مرحله آنجا جداگانه‌اند.
     */
    onGoStage: ((WorkStage) -> Unit)? = null
) {
    val orders by vm.ordersCutting.collectAsState(initial = emptyList())
    val designCodeByOrder by vm.designCodeByOrder.collectAsState()
    val cutters by vm.cutters.collectAsState(initial = emptyList())
    val measurements by vm.measurements.collectAsState(initial = emptyMap())
    val photos by vm.photos.collectAsState(initial = emptyMap())

    var cutTarget by remember { mutableStateOf<Order?>(null) }
    var scanMsg by remember { mutableStateOf<String?>(null) }
    var manualCodeOpen by remember { mutableStateOf(false) }

    // یافتنِ سفارش در صف برش از روی کدِ اسکن‌شده/تایپ‌شده و بازکردنِ دیالوگ
    fun handleCode(code: String) {
        val c = code.trim()
        if (c.isBlank()) return
        val match = orders.firstOrNull { it.shortCode == c || it.orderCode == c }
        if (match != null) {
            cutTarget = match; scanMsg = null
        } else {
            scanMsg = "سفارشی با این کد در صف برش نیست: $c"
        }
    }

    /*
     * اسکنِ QR — از سکو می‌آید، نه از اینجا.
     *
     * تا دیروز کلِ این صفحه به‌خاطرِ همین چند خط در `:app` مانده بود و
     * ویندوز اصلاً برش نداشت. حالا `null` بودنِ اسکنر یعنی «این سکو
     * دوربین ندارد» و صفحه فقط دکمه‌اش را نشان نمی‌دهد؛ ورودِ دستیِ
     * کد که از قبل بود، آنجا تنها راه است.
     */
    val scanner = LocalCodeScanner.current
    val startScan = scanner?.rememberStart(
        onCode = { handleCode(it) },
        onFailed = { msg ->
            scanMsg = msg
            // پیشنهادِ راهِ دوم، نه فقط خبرِ بد.
            manualCodeOpen = true
        },
    )

    // ---------- ورود دستی کد (وقتی دوربین/اسکنر در دسترس نیست) ----------
    if (manualCodeOpen) {
        var typed by remember { mutableStateOf("") }
        AppAlertDialog(
            onDismissRequest = { manualCodeOpen = false },
            title = { Text("کد سفارش") },
            text = {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("کد کوتاه یا کد کامل سفارش") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = typed.isNotBlank(),
                    onClick = { manualCodeOpen = false; handleCode(typed) }
                ) { Text("باز کردن") }
            },
            dismissButton = { TextButton(onClick = { manualCodeOpen = false }) { Text("لغو") } }
        )
    }

    // ---------- دیالوگ ثبت برش ----------
    cutTarget?.let { o ->
        var cutter by remember(o.id) { mutableStateOf("") }
        var pieces by remember(o.id) { mutableStateOf(o.qty.toString()) }
        var waste by remember(o.id) { mutableStateOf("") }
        var note by remember(o.id) { mutableStateOf("") }
        AppAlertDialog(
            onDismissRequest = { cutTarget = null },
            title = { Text("ثبت برش — ${o.designTitle.ifBlank { o.orderCode }}") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // عکسِ طرح اول از همه — برشکار قبل از عدد، شکل را می‌خواهد
                    val orderPhotos = photos[o.id.toString()].orEmpty()
                    if (orderPhotos.isNotEmpty()) {
                        OrderPhotoStrip(
                            photos = orderPhotos,
                            canEdit = false,
                            onCaptured = {},
                            onDelete = {},
                            title = "عکس طرح"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    // اندازه‌ها قبل از هر کادرِ دیگری، چون برش با همین‌ها زده می‌شود
                    MeasurementsBlock(
                        items = measurements[o.customerName.trim()].orEmpty()
                            .map { it.label to it.value },
                        title = "اندازه‌های ${o.customerName.ifBlank { "مشتری" }}",
                        emptyHint = "برای این مشتری اندازه‌ای ثبت نشده — " +
                            "در صفحهٔ مشتری اضافه کنید تا اینجا و در دوخت دیده شود."
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedTextField(
                        value = cutter,
                        onValueChange = { cutter = it },
                        label = { Text("مسئول برش") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (cutters.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            cutters.take(8).forEach { name ->
                                FilterChip(
                                    selected = cutter == name,
                                    onClick = { cutter = if (cutter == name) "" else name },
                                    label = { Text(name) }
                                )
                            }
                        }
                    } else {
                        Text(
                            "نامِ برشکار را تایپ کنید؛ از دفعهٔ بعد همین‌جا " +
                                "به‌صورت دکمه می‌آید و لازم نیست دوباره بنویسید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            StageTabs(WorkStage.CUT, onGoStage)
            // دکمهٔ اسکن فقط جایی که دوربینی هست. روی ویندوز نبودنش
            // صادق‌تر از دکمه‌ای است که می‌گوید «اسکنر باز نشد».
            if (startScan != null) {
                androidx.compose.material3.Button(
                    onClick = { startScan() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Text("  اسکن QR سفارش")
                }
            }
            androidx.compose.material3.TextButton(
                onClick = { manualCodeOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("ورود دستی کد سفارش") }
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(orders, key = { it.id }) { o ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = o.designTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                OrderCodeLine(
                                    designCode = designCodeByOrder[o.orderCode].orEmpty(),
                                    orderCode = o.orderCode,
                                    trailing = "تعداد: ${o.qty.fa()}"
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
                                    Text("برگشت به انبار (اصلاح اشتباه)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
