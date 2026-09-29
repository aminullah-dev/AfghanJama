@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.afghanjama.ui.screens

import com.afghanjama.ui.components.NameSuggestions
import com.afghanjama.util.nowMillis
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.platform.AppDropdownMenu
import com.afghanjama.ui.platform.AppDropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.afghanjama.data.PriceAdvisor
import com.afghanjama.ui.vm.OrderPricing
import com.afghanjama.ui.vm.ProductionUi
import com.afghanjama.ui.vm.estimatedUnitCost
import com.afghanjama.work.DeliveryForecast
import com.afghanjama.work.WorkshopLoad
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.ProductionViewModel

private fun fmtNum(v: Double): String =
    (if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()).toPersianDigits()

@Composable
fun ProductionOrderScreen(
    vm: ProductionViewModel,
    onBack: () -> Unit,
    /**
     * رفتن به «خرید مواد».
     *
     * `null` یعنی این صفحه راهی به آنجا ندارد و آن‌وقت فقط جمله را
     * می‌گوید — نه دکمه‌ای که هیچ نکند.
     */
    onGoProcurement: (() -> Unit)? = null
) {
    val ui by vm.ui.collectAsState()
    val customerNames by vm.customerNames.collectAsState()
    var showAddWorkCost by remember { mutableStateOf(false) }
    val materials by vm.materials.collectAsState()
    val designs by vm.designs.collectAsState()
    val sizes by vm.sizes.collectAsState()
    val designCounts by vm.designCounts.collectAsState()
    val workCosts by vm.workCosts.collectAsState()
    val forecast by vm.forecast.collectAsState()
    val orderPricing by vm.pricing.collectAsState()

    var pickerOpen by remember { mutableStateOf(false) }
    var designMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var addDesignOpen by remember { mutableStateOf(false) }
    var newDesignCode by remember { mutableStateOf("") }

    // ---------- ثبت طرح جدید در کاتالوگ با کدِ اختصاصیِ کارگاه ----------
    if (addDesignOpen) {
        AppAlertDialog(
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        AppDropdownMenu(expanded = designMenu, onDismissRequest = { designMenu = false }) {
                            designs.forEach { d ->
                                val made = designCounts[d.title] ?: 0
                                AppDropdownMenuItem(
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
                        AppDropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                            sizes.forEach { sz ->
                                AppDropdownMenuItem(
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
                        // مشتریِ ثبت‌شده از حرفِ اول — تا «حاجی نصیر» و «حاجي
                        // نصير» دو حسابِ جدا نشوند.
                        NameSuggestions(
                            query = ui.customerName,
                            names = customerNames,
                            onPick = vm::setCustomerName,
                            existsNote = null
                        )
                        OutlinedTextField(
                            value = ui.agreedPrice,
                            onValueChange = vm::setAgreedPrice,
                            label = { Text("قیمت توافقی (اختیاری، ؋)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        AgreedPriceHint(
                            ui = ui,
                            pricing = orderPricing,
                            onPick = { vm.setAgreedPrice(it.toString()) }
                        )

                        // ---------- مهلت تحویل ----------
                        Text(
                            "مهلت تحویل (اختیاری)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                "تحویل تا ${PersianDate.long(nowMillis() + d * 86_400_000L)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        // پیش‌بینیِ واقعی کنارِ قول — پیش از آنکه به مشتری گفته شود.
                        ui.qty.toIntOrNull()?.takeIf { it > 0 }?.let { q ->
                            DeliveryHint(
                                forecast = forecast,
                                qty = q,
                                dueDays = ui.dueDays.toLongOrNull()?.takeIf { it > 0 },
                                onUseDays = { vm.setDueDays(it.toString()) }
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
                                "بیعانه به صندوق می‌رود و رسیدش صادر می‌شود" +
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("مصرف مواد از انبار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (materials.isEmpty()) {
                            Text(
                                "انبار مواد خالی است. اول از بخش «خرید مواد» مواد را وارد کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            // جمله‌ای که می‌گوید کجا برو، باید ببرد.
                            if (onGoProcurement != null) {
                                OutlinedButton(
                                    onClick = onGoProcurement,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("رفتن به خرید مواد") }
                            }
                        }
                        OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (ui.pickedName.isBlank()) "انتخاب ماده از انبار"
                                else "${ui.pickedName} • موجودی به واحد ${ui.pickedUnit}"
                            )
                        }
                        AppDropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                            materials.forEach { m ->
                                AppDropdownMenuItem(
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            // **شرطِ «فهرست خالی نباشد» برداشته شد.** تا دیروز کارگاهی
            // که هنوز هیچ خرج‌کاری تعریف نکرده بود این کارت را اصلاً
            // نمی‌دید — یعنی دکمهٔ افزودن هم پنهان می‌مانْد و تنها راه،
            // ترکِ صفحه بود. حالا کارت همیشه هست و وقتی فهرست خالی است
            // خودش می‌گوید چه کند.
            run {
                item {
                    val qtyForPreview = ui.qty.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val perPiece = ui.workItems.sumOf { it.price }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                workCosts.forEach { w ->
                                    FilterChip(
                                        selected = ui.workItems.any { it.title == w.title },
                                        onClick = { vm.toggleWorkItem(w) },
                                        label = { Text("${w.title} — ${w.price.afn()}") }
                                    )
                                }
                                // افزودن بی ترکِ صفحه. `AssistChip` و نه
                                // `FilterChip`: این یکی انتخاب نمی‌شود،
                                // کاری می‌کند — و شکلِ متفاوتش همین را
                                // می‌گوید.
                                AssistChip(
                                    onClick = { showAddWorkCost = true },
                                    label = { Text("افزودن") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                            if (workCosts.isEmpty()) {
                                Text(
                                    "هنوز خرج‌کاری تعریف نشده. با «افزودن» " +
                                        "اولی را بسازید — در «اطلاعات پایه» هم " +
                                        "می‌مانَد و دفعهٔ بعد آماده است.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // قیمتِ هر خرج‌کارِ انتخاب‌شده همین‌جا قابلِ
                            // تغییر است — فقط برای همین سفارش. فهرستِ
                            // «اطلاعات پایه» دست نمی‌خورد، وگرنه قیمتِ
                            // سفارش‌های قبلی هم در گزارش‌ها جابه‌جا می‌شد.
                            if (ui.workItems.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "قیمتِ این سفارش (اگر بازار فرق کرده)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ui.workItems.forEach { line ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            line.title,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        OutlinedTextField(
                                            value = if (line.price == 0L) "" else line.price.toString(),
                                            onValueChange = {
                                                vm.setWorkItemPrice(
                                                    line.title,
                                                    it.filter { c -> c.isDigit() }.toLongOrNull() ?: 0L
                                                )
                                            },
                                            modifier = Modifier.width(140.dp),
                                            singleLine = true,
                                            suffix = { Text("؋") },
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number
                                            ),
                                            shape = MaterialTheme.shapes.medium
                                        )
                                    }
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

                                HorizontalDivider(thickness = 0.5.dp)

                                // خرج‌کار پولِ واقعی است و باید از جایی برود.
                                // تا امروز همیشه بدهیِ بی‌صاحب می‌شد که هرگز
                                // تسویه نمی‌شد، پس صندوق واقعیت را نشان
                                // نمی‌داد و بهای تمام‌شده فقط روی کاغذ درست بود.
                                Text(
                                    "خرج کار از کجا پرداخت می‌شود؟",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        "WALLET" to "کیف پول",
                                        "BANK" to "بانک",
                                        "PROFIT" to "فایده",
                                        "CREDIT" to "نسیه"
                                    ).forEach { (code, label) ->
                                        FilterChip(
                                            selected = ui.workCostSource.equals(code, true),
                                            onClick = { vm.setWorkCostSource(code) },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                                if (ui.workCostSource.equals("CREDIT", true)) {
                                    OutlinedTextField(
                                        value = ui.workCostPayee,
                                        onValueChange = vm::setWorkCostPayee,
                                        label = { Text("طرفِ حساب (لازم)") },
                                        singleLine = true,
                                        isError = ui.workCostPayee.isBlank(),
                                        supportingText = {
                                            Text("بی نام، بدهی بی‌صاحب می‌مانَد و هرگز تسویه نمی‌شود.")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        "همین حالا از ${
                                            when (ui.workCostSource.uppercase()) {
                                                "BANK" -> "بانک"
                                                "PROFIT" -> "فایده"
                                                else -> "کیف پول"
                                            }
                                        } کم می‌شود و در بهای تمام‌شدهٔ سفارش می‌نشیند.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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

    /*
     * افزودنِ خرج‌کار از دلِ همین صفحه.
     *
     * عمداً فقط دو کادر دارد — عنوان و قیمت — چون همین دو چیز است که
     * `WorkCost` می‌خواهد. هر چیزِ بیشتری اینجا یعنی کاربر وسطِ ثبتِ
     * سفارش باید دربارهٔ چیزِ دیگری فکر کند.
     *
     * دکمهٔ ذخیره تا وقتی هر دو کادر معنا نداشته باشند خاموش است، نه
     * اینکه بزنی و هیچ نشود.
     */
    if (showAddWorkCost) {
        var newTitle by remember { mutableStateOf("") }
        var newPrice by remember { mutableStateOf("") }
        val price = newPrice.toLongOrNull() ?: 0L
        val duplicate = workCosts.any { it.title.trim().equals(newTitle.trim(), ignoreCase = true) }
        val valid = newTitle.isNotBlank() && price > 0L

        AppAlertDialog(
            onDismissRequest = { showAddWorkCost = false },
            title = { Text("خرج کار تازه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("عنوان") },
                        placeholder = { Text("مثلاً: نوار زیبایی") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPrice,
                        onValueChange = { newPrice = it.digitsOnly() },
                        label = { Text("قیمت فی‌عدد") },
                        suffix = { Text("؋") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // صریح، چون در غیرِ این صورت کاربر فکر می‌کند قیمتی
                    // که نوشته ثبت شده — و نشده.
                    if (duplicate) {
                        Text(
                            "این نام از قبل هست. همان انتخاب می‌شود و " +
                                "قیمتِ پایه دست نمی‌خورد؛ اگر برای این " +
                                "سفارش فرق دارد، پایین عوضش کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "در «اطلاعات پایه» هم ذخیره می‌شود تا دفعهٔ بعد آماده باشد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        vm.addWorkCost(newTitle, price)
                        showAddWorkCost = false
                    }
                ) { Text("افزودن") }
            },
            dismissButton = {
                TextButton(onClick = { showAddWorkCost = false }) { Text("لغو") }
            }
        )
    }

}

/**
 * «کِی واقعاً آماده می‌شود؟» — زیرِ مهلتِ سفارشِ تازه.
 *
 * **چرا اینجا و نه فقط در «بارِ کارگاه».** قول سرِ همین فرم داده
 * می‌شود؛ صفحه‌ای که باید جداگانه باز شود، سرِ صحبت با مشتری باز
 * نمی‌شود. تا دیروز مهلت عددی دلخواه بود — «۷ روز» — و هیچ‌چیز
 * نمی‌گفت کارگاه با کارهای در دستش واقعاً به آن می‌رسد یا نه.
 *
 * بی تاریخچهٔ دوخت چیزی نشان نمی‌دهد؛ حدس نمی‌زند.
 */
@Composable
private fun DeliveryHint(
    forecast: DeliveryForecast.Result,
    qty: Int,
    dueDays: Long?,
    onUseDays: (Int) -> Unit
) {
    if (!forecast.hasData) return
    // مبدأ همان لحظهٔ خودِ پیش‌بینی است، نه ساعتِ هر بار کشیدنِ صفحه —
    // وگرنه `remember` زیرش با هر ضربهٔ کلید از نو حساب می‌کرد.
    val due = dueDays?.let { forecast.now + it * 86_400_000L } ?: 0L
    val hit = remember(forecast, qty, due) { forecast.impactOf(qty, due) } ?: return
    val safe = remember(forecast, qty) { forecast.earliestSafeDays(qty) }
    val late = hit.lateDays > 0
    val pushes = hit.pushedLate

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (late) "با کارهای فعلی حدودِ ${PersianDate.short(hit.readyAt)} آماده می‌شود — " +
                "${hit.lateDays.fa()} روز بعد از این مهلت."
            else "با کارهای فعلی حدودِ ${PersianDate.short(hit.readyAt)} آماده می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (late) FontWeight.SemiBold else FontWeight.Normal,
            color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        if (pushes.isNotEmpty()) {
            Text(
                "این مهلت ${pushes.size.fa()} سفارشِ دیگر را دیر می‌کند: " +
                    pushes.take(3).joinToString("، ") { it.order.orderCode } +
                    if (pushes.size > 3) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (safe != null && (late || pushes.isNotEmpty() || dueDays == null) && safe.toLong() != dueDays) {
            AssistChip(
                onClick = { onUseDays(safe) },
                label = { Text("مهلتِ مطمئن: ${safe.fa()} روز") }
            )
        }
        Text(
            "بر پایهٔ سرعتِ واقعیِ کارگاه در ${WorkshopLoad.WINDOW_DAYS.fa()} روزِ گذشته و " +
                "${forecast.queuePieces.fa()} دستِ در صف.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * بهای برآوردی و پیشنهادِ قیمت زیرِ «قیمت توافقی».
 *
 * تا دیروز قیمتِ توافقی کاملاً دستی بود و بها بعد از ثبت معلوم می‌شد؛
 * سفارشِ زیرِ بها وقتی دیده می‌شد که لباس دوخته شده بود. حالا همان
 * لحظه کنارِ هم‌اند.
 */
@Composable
private fun AgreedPriceHint(ui: ProductionUi, pricing: OrderPricing, onPick: (Long) -> Unit) {
    val q = ui.qty.toIntOrNull()?.takeIf { it > 0 } ?: return
    if (ui.designTitle.isBlank()) return
    val key = PriceAdvisor.key(ui.designTitle)
    val wage = pricing.wageByDesign[key] ?: 0L
    val unitCost = ui.estimatedUnitCost() + wage
    val advice = remember(pricing, key, unitCost) { pricing.book.advise(key, unitCost) }
    val agreed = ui.agreedPrice.toLongOrNull() ?: 0L
    val total = unitCost * q

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (unitCost > 0L) {
            Text(
                "بهای برآوردی: ${total.afn()} (${unitCost.afn()} هر عدد" +
                    (if (wage > 0L) "، با دستمزدِ دوختِ معمولِ ${wage.afn()})" else "، بی دستمزدِ دوخت)"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PriceSuggestions(
            advice = advice,
            typed = agreed,
            onPick = onPick,
            perPieceLabel = " برای ${q.fa()} عدد",
            scale = q
        )
        if (total > 0L && agreed in 1 until total) {
            Text(
                "این قیمت ${(total - agreed).afn()} زیرِ بهای برآوردی است — زیان.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
