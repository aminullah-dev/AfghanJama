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
import androidx.compose.material3.AlertDialog
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.afghanjama.data.entities.Order
import com.afghanjama.ui.components.MeasurementsBlock
import com.afghanjama.ui.components.OrderPhotoStrip
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

    // اسکنِ QR سفارش با دوربین
    val context = LocalContext.current

    val scanOptions = {
        ScanOptions().apply {
            setPrompt("QR سفارش را اسکن کنید")
            setBeepEnabled(true)
            setOrientationLocked(true)
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        // نتیجهٔ خالی یعنی کاربر لغو کرده یا دوربین بالا نیامده — قبلاً
        // بی‌صدا نادیده گرفته می‌شد و اسکنر «کار نمی‌کرد» بدون هیچ پیغامی.
        val code = result.contents
        if (code.isNullOrBlank()) {
            val camOk = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            scanMsg = if (!camOk)
                "اجازهٔ دوربین داده نشده است. از تنظیماتِ گوشی اجازهٔ دوربین را بدهید، یا کد را دستی وارد کنید."
            else
                "اسکن انجام نشد. می‌توانید کد را دستی وارد کنید."
        } else {
            handleCode(code)
        }
    }

    /**
     * اجازهٔ دوربین عمداً از اینجا درخواست نمی‌شود.
     *
     * MainActivity یک FragmentActivity است و نسخهٔ fragment ای که
     * androidx.biometric می‌آورد، requestCodeهای بزرگ‌تر از ۱۶ بیت را رد
     * می‌کند — در حالی که ActivityResultRegistry عمداً کدِ بزرگ می‌سازد.
     * پس هر launch(RequestPermission()) از این اکتیویتی با
     * «Can only use lower 16 bits for requestCode» می‌ترکد.
     *
     * خودِ CaptureActivity کتابخانهٔ اسکنر (که FragmentActivity نیست)
     * اجازهٔ دوربین را با کدِ کوچکِ خودش می‌گیرد، پس فقط اسکنر را باز
     * می‌کنیم و نتیجه را — چه موفق چه ناموفق — به کاربر می‌گوییم.
     */
    fun startScan() {
        runCatching { scanLauncher.launch(scanOptions()) }
            .onFailure {
                scanMsg = "اسکنر باز نشد؛ کد را دستی وارد کنید."
                manualCodeOpen = true
            }
    }

    // ---------- ورود دستی کد (وقتی دوربین/اسکنر در دسترس نیست) ----------
    if (manualCodeOpen) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
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
        AlertDialog(
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            androidx.compose.material3.Button(
                onClick = { startScan() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Text("  اسکن QR سفارش")
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
