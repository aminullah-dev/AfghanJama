@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.Order
import java.util.UUID
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.ui.components.MeasurementsBlock
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.OrderHandout
import com.afghanjama.ui.vm.SewingViewModel

@Composable
fun SewingScreen(
    vm: SewingViewModel,
    onBack: () -> Unit,
    onGoReview: () -> Unit
) {
    val shop = CompanyPrefs.shopName(LocalContext.current)
    val handouts by vm.handouts.collectAsState()
    val measurementsByOrder by vm.measurementsByOrder.collectAsState(initial = emptyMap())
    val inProgress by vm.inProgress.collectAsState()
    val sewMessage by vm.message.collectAsState()
    val sewnReady by vm.sewnReady.collectAsState()
    val tailors by vm.tailors.collectAsState()
    val allAssignments by vm.allAssignments.collectAsState()
    val pendingWages by vm.pendingWages.collectAsState()
    val goReview by vm.goReview.collectAsState()

    // فقط وقتی چیزی واقعاً به نظارت رفت. اگر بی‌قیدوشرط جابه‌جا شویم،
    // کاربر پیامِ دلیلِ نرفتن را — که در همین تب نوشته می‌شود — نمی‌بیند.
    LaunchedEffect(goReview) {
        if (goReview) {
            vm.consumeGoReview()
            onGoReview()
        }
    }

    var tab by rememberSaveable { mutableStateOf(0) }
    // ترتیبِ تب‌ها همان مسیرِ واقعیِ کار است:
    // تحویل به خیاط ← در حال دوخت ← دوخته شده ← (نظارت)
    val tabs = listOf("تحویل به خیاط", "در حال دوخت", "دوخته شده")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مدیریت دوخت") },
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
        ) {
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }

            Spacer(Modifier.height(10.dp))

            if (tab == 0) {
                if (handouts.isEmpty()) {
                    EmptyCard("موردی برای تحویل نیست.", "وقتی برش تمام شد، سفارش اینجا می‌آید.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(handouts, key = { it.order.id }) { h ->
                            HandoutCard(
                                handout = h,
                                tailorLabels = tailors.map { "[${it.code}] ${it.name}" },
                                measurements = measurementsByOrder[h.order.orderCode].orEmpty(),
                                onHandout = { label, qty, wage -> vm.handout(h.order.id, label, qty, wage) },
                                onCancelAssignment = { vm.cancelAssignment(it) },
                                onBackToCutting = { vm.backToCutting(h.order.id) }
                            )
                        }
                        item { Spacer(Modifier.height(40.dp)) }
                    }
                }
            } else if (tab == 1) {
                // وقتی ارسال انجام نشد (چیزِ تازه‌ای دوخته نشده) دلیلش
                // همین‌جا نوشته می‌شود — وگرنه کاربر دکمه را می‌زند و هیچ
                // اتفاقی نمی‌افتد و نمی‌فهمد چرا.
                sewMessage?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.clearMessage() }) { Text("باشه") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (inProgress.isEmpty()) {
                    EmptyCard("موردی در حال دوخت نیست.", "پس از تحویل به خیاط، اینجا نمایش داده می‌شود.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // «ارسال به نظارت» جایی می‌آید که کارِ تمام‌شده
                        // هست، نه جایی که کار به خیاط سپرده می‌شود. تبِ
                        // اول کارِ **بیرون‌رفتنی** را نشان می‌دهد؛ فرستادنِ
                        // کارِ آماده به نظارت قدمِ بعدِ همین تب است.
                        //
                        // یک بار برای هر سفارش، نه برای هر خیاط: نظارت
                        // سفارش را تحویل می‌گیرد نه سهمِ یک خیاط را.
                        items(inProgress, key = { it.id }) { a ->
                                InProgressCard(
                                assignment = a,
                                measurements = measurementsByOrder[a.orderCode].orEmpty(),
                                receiptText = handoverReceipt(
                                    shop, a, allAssignments,
                                    pendingWages.filter { it.tailorLabel == a.tailorLabel }
                                        .sumOf { it.amount },
                                    measurementsByOrder[a.orderCode].orEmpty()
                                ),
                                onDone = { quality, delivered ->
                                    vm.completeAssignment(a.id, quality, delivered)
                                },
                                    onCancel = { vm.cancelAssignment(a.id) }
                                )
                            }
                        item { Spacer(Modifier.height(40.dp)) }
                    }
                }
            } else {
                // ---------- دوخته شده ----------
                // کارِ تمام‌شدهٔ خیاط تا امروز از «در حال دوخت» ناپدید
                // می‌شد و هیچ‌جا دیده نمی‌شد. اینجا معلوم است کدام خیاط
                // چند عدد تحویل داده، و همان‌ها — نه کلِ سفارش — به
                // نظارت می‌روند.
                sewMessage?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.clearMessage() }) { Text("باشه") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (sewnReady.isEmpty()) {
                    EmptyCard(
                        "چیزی آمادهٔ نظارت نیست.",
                        "وقتی خیاط کارش را تحویل داد، همان‌جا اینجا می‌آید."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sewnReady, key = { it.order.id }) { g ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            g.order.designTitle,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "آمادهٔ نظارت: ${g.readyQty.fa()}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        "${g.order.orderCode} • کل: ${g.order.qty.fa()} عدد" +
                                            (if (g.order.size.isNotBlank()) " • سایز ${g.order.size}" else ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    HorizontalDivider(thickness = 0.5.dp)

                                    // جزئیات: کدام خیاط، چند عدد، با چه کارمزدی
                                    // فقط باقی‌ماندهٔ هر تحویل؛ آنچه از قبل
                                    // به نظارت رفته اینجا نمی‌مانَد.
                                    g.pending.forEach { (a, n) ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${a.tailorLabel} — ${n.fa()} عدد" +
                                                    (if (n < a.qty) " (از ${a.qty.fa()})" else ""),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Text(
                                                "کارمزد: ${(a.unitWage * n).afn()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    HorizontalDivider(thickness = 0.5.dp)

                                    Button(
                                        onClick = { vm.sendToReview(g.order.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("ارسال ${g.readyQty.fa()} عدد به نظارت")
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(40.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HandoutCard(
    handout: OrderHandout,
    tailorLabels: List<String>,
    measurements: List<Pair<String, String>>,
    onHandout: (String, Int, Long) -> Unit,
    onCancelAssignment: (Long) -> Unit,
    onBackToCutting: () -> Unit
) {
    val order = handout.order
    val unitWage = if (order.qty > 0) order.workCost / order.qty else order.workCost

    var tailor by remember(order.id) { mutableStateOf("") }
    var qtyText by remember(order.id) { mutableStateOf(handout.remaining.toString()) }
    var wageText by remember(order.id) { mutableStateOf(unitWage.toString()) }
    var menuOpen by remember(order.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(order.designTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${order.orderCode} • کل: ${order.qty} عدد", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "باقی: ${handout.remaining}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (handout.remaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "پارچه: ${order.fabricType} • رنگ: ${order.fabricColor} • سایز: ${order.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // اندازه‌ها همین‌جا، چون خیاط دقیقاً موقعِ گرفتنِ کار به آن‌ها نیاز دارد
            MeasurementsBlock(
                items = measurements,
                title = "اندازه‌های ${order.customerName.ifBlank { "مشتری" }}",
                emptyHint = "برای این مشتری اندازه‌ای ثبت نشده."
            )

            // ---- تحویل‌های ثبت‌شده ----
            if (handout.assignments.isNotEmpty()) {
                HorizontalDivider(thickness = 0.5.dp)
                handout.assignments.forEach { a ->
                    val done = a.status == "DONE"
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${a.tailorLabel} — ${a.qty} عدد" + if (done) " ✔" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text("کارمزد: ${a.totalWage.afn()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!done) {
                            TextButton(onClick = { onCancelAssignment(a.id) }) { Text("لغو") }
                        }
                    }
                }
            }

            // ---- فرم تحویل جدید ----
            if (handout.remaining > 0) {
                HorizontalDivider(thickness = 0.5.dp)
                Text("📋 تحویل به خیاط", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ContentCut, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tailor.ifBlank { "انتخاب خیاط" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (tailorLabels.isEmpty()) {
                        DropdownMenuItem(text = { Text("هیچ خیاطی ثبت نشده (اطلاعات پایه)") }, onClick = { menuOpen = false })
                    } else {
                        tailorLabels.forEach { label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { tailor = label; menuOpen = false })
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.digitsOnly() },
                        label = { Text("تعداد (حداکثر ${handout.remaining})") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = wageText,
                        onValueChange = { wageText = it.digitsOnly() },
                        label = { Text("کارمزد فی‌عدد") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                val qty = qtyText.toIntOrNull() ?: 0
                val wage = wageText.toLongOrNull() ?: 0L
                val valid = tailor.isNotBlank() && qty in 1..handout.remaining

                Button(
                    onClick = {
                        onHandout(tailor, qty, wage)
                        tailor = ""
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            tailor.isBlank() -> "اول خیاط را انتخاب کنید"
                            qty !in 1..handout.remaining -> "تعداد نامعتبر"
                            else -> "تحویل ($qty عدد به خیاط)"
                        }
                    )
                }

                if (handout.assignments.isEmpty()) {
                    TextButton(onClick = onBackToCutting, modifier = Modifier.fillMaxWidth()) {
                        Text("↩ برگشت به برش (اصلاح اشتباه)")
                    }
                }
            }

        }
    }
}

@Composable
private fun InProgressCard(
    assignment: SewingAssignment,
    measurements: List<Pair<String, String>>,
    receiptText: String,
    onDone: (String, Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var showDone by remember { mutableStateOf(false) }

    if (showDone) {
        var deliveredText by remember { mutableStateOf(assignment.qty.toString()) }
        var quality by remember { mutableStateOf("") }
        val delivered = deliveredText.toIntOrNull() ?: 0
        AlertDialog(
            onDismissRequest = { showDone = false },
            title = { Text("تحویل دوختِ ${assignment.tailorLabel}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("از ${assignment.qty} عدد، چند عدد دوخته و تحویل شد؟ (باقی‌مانده در حال دوخت می‌ماند)")
                    OutlinedTextField(
                        value = deliveredText,
                        onValueChange = { deliveredText = it.digitsOnly() },
                        label = { Text("تعداد تحویل‌شده") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("کیفیت کار (اختیاری):", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("خوب", "متوسط", "ضعیف").forEach { q ->
                            FilterChip(
                                selected = quality == q,
                                onClick = { quality = if (quality == q) "" else q },
                                label = { Text(q) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = delivered in 1..assignment.qty,
                    onClick = { showDone = false; onDone(quality, delivered) }
                ) { Text("ثبت تحویل") }
            },
            dismissButton = { TextButton(onClick = { showDone = false }) { Text("لغو") } }
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(assignment.tailorLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${assignment.orderCode} • ${assignment.qty} عدد", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(assignment.totalWage.afn(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }

            Text(
                "کارمزد: ${assignment.unitWage.afn()} فی‌عدد × ${assignment.qty}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (measurements.isNotEmpty()) {
                MeasurementsBlock(items = measurements)
            }

            HorizontalDivider(thickness = 0.5.dp)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, receiptText)
                        }
                        context.startActivity(Intent.createChooser(intent, "اشتراک رسید تحویل به خیاط"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("رسید")
                }
                Button(onClick = { showDone = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("تحویل دوخت")
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("↩ لغو تحویل")
            }
        }
    }
}

/** امتیازِ کیفیِ خیاط از سابقهٔ کارگاه: خوب=۵، متوسط=۳، ضعیف=۱ → ★ از ۵. */
private fun tailorStars(history: List<SewingAssignment>): Pair<String, Int> {
    val scores = history
        .filter { it.status == "DONE" && it.quality.isNotBlank() }
        .map { if (it.quality == "خوب") 5 else if (it.quality == "متوسط") 3 else 1 }
    if (scores.isEmpty()) return "بدون سابقهٔ امتیاز" to 0
    val avg = (scores.sum().toDouble() / scores.size).toInt().coerceIn(1, 5)
    return ("★".repeat(avg) + "☆".repeat(5 - avg)) to scores.size
}

/**
 * رسیدِ کاملِ تحویل به خیاط: کارِ جدید + کارهای زیرِ دست + آخرین کارِ
 * تکمیل‌شده + امتیازِ کیفی + یادداشتِ حسابِ کارمزد + یادآوریِ نظارت.
 */
private fun handoverReceipt(
    shop: String,
    a: SewingAssignment,
    all: List<SewingAssignment>,
    pendingWageTotal: Long,
    measurements: List<Pair<String, String>>
): String = buildString {
    val mine = all.filter { it.tailorLabel == a.tailorLabel }
    val (stars, ratedCount) = tailorStars(mine)
    val inHand = mine.filter { it.status == "SEWING" && it.id != a.id }
    val lastDone = mine.filter { it.status == "DONE" }.maxByOrNull { it.doneAt ?: 0L }

    appendLine("🧵 رسید تحویل کار — $shop")
    appendLine("خیاط: ${a.tailorLabel}")
    appendLine("تاریخ: ${PersianDate.short(System.currentTimeMillis())}")
    if (ratedCount > 0) appendLine("امتیاز کیفیت در کارگاه: $stars (از ${ratedCount.fa()} کار)")
    appendLine("──────────────")
    appendLine("✅ این کار به شما تحویل شد:")
    appendLine("• ${a.orderCode} — ${a.qty.fa()} عدد • کارمزد فی‌عدد ${a.unitWage.afn()} • جمع ${a.totalWage.afn()}")
    if (measurements.isNotEmpty()) {
        appendLine("📏 اندازه‌ها:")
        measurements.forEach { (label, value) -> appendLine("   • $label: $value") }
    }
    if (inHand.isNotEmpty()) {
        appendLine("──────────────")
        appendLine("🧷 کارهای دیگرِ زیرِ دست شما:")
        inHand.forEach {
            appendLine("• ${it.orderCode} — ${it.qty.fa()} عدد (از ${PersianDate.short(it.createdAt)})")
        }
    }
    lastDone?.let {
        appendLine("──────────────")
        appendLine(
            "کار قبلی شما: ${it.orderCode} — ${it.qty.fa()} عدد" +
                (if (it.quality.isNotBlank()) " • کیفیت: ${it.quality}" else "")
        )
    }
    appendLine("──────────────")
    appendLine("💰 حساب کارمزد: طلبِ تسویه‌نشده تاکنون ${pendingWageTotal.afn()}")
    appendLine("──────────────")
    appendLine("⏰ یادآوری: هر کاری که تمام شد، همان لحظه بخش نظارت را در جریان بگذارید.")
}
