@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import com.afghanjama.util.nowMillis
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import com.afghanjama.ui.platform.AppAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.components.BusyButton
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.data.Margin
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.DraftLine
import com.afghanjama.ui.vm.NewSaleViewModel

/**
 * فاکتور فروش — چند طرحِ مختلف در یک فاکتور.
 *
 * ساختارش عمداً شکلِ خودِ فاکتورِ کاغذی است: هدر (تاریخ، خریدار، فروشنده)،
 * بدنه (ردیف‌های کالا) و فوتر (دریافتی و مانده). کسی که با دفترِ کاغذی
 * کار کرده باشد بدونِ توضیح می‌فهمد کجاست.
 */
@Composable
fun NewSaleScreen(
    vm: NewSaleViewModel,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val stock by vm.stock.collectAsState()
    val busy by vm.busy.state.collectAsState()

    // کدام ردیف منتظرِ انتخابِ کالاست
    var pickerFor by remember { mutableStateOf<Long?>(null) }

    val customers by vm.customers.collectAsState()
    val customerIsNew by vm.customerIsNew.collectAsState()
    var showCustomerPicker by remember { mutableStateOf(false) }

    // ---------- انتخابِ خریدار از مشتریانِ ثبت‌شده ----------
    if (showCustomerPicker) {
        var search by remember { mutableStateOf("") }
        val shown = remember(search, customers) {
            val q = search.trim()
            if (q.isBlank()) customers
            else customers.filter { it.contains(q, ignoreCase = true) }
        }
        AppAlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            title = { Text("انتخاب خریدار") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("جست‌وجوی نام") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (customers.isEmpty()) {
                        Text(
                            "هنوز مشتری‌ای در «اطلاعات پایه» ثبت نشده. " +
                                "می‌توانید نام را همین‌جا مستقیم بنویسید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (shown.isEmpty()) {
                        Text(
                            "مشتری‌ای با این نام پیدا نشد. " +
                                "اگر خریدارِ تازه است، نامش را در کادرِ خریدار بنویسید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(shown, key = { it }) { name ->
                            TextButton(
                                onClick = {
                                    vm.setCustomer(name)
                                    showCustomerPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(name, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerPicker = false }) { Text("بستن") }
            }
        )
    }

    pickerFor?.let { key ->
        ItemPickerDialog(
            stock = stock,
            onDismiss = { pickerFor = null },
            onPick = { vm.setItem(key, it); pickerFor = null }
        )
    }

    AppScreen(title = "فروش جدید", onBack = onBack) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------------- هدرِ فاکتور ----------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "تاریخ فروش: ${PersianDate.shortWithTime(nowMillis())}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "شمارهٔ فاکتور هنگام ثبت ساخته می‌شود",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                PartyRow(
                    label = "خریدار",
                    value = ui.customer,
                    editable = true,
                    onValueChange = vm::setCustomer,
                    hint = when {
                        ui.prepay > 0 -> "بیعانهٔ نزدِ ما: ${ui.prepay.afn()}"
                        // فروشنده باید بداند دارد مشتریِ تازه می‌سازد یا روی
                        // حسابِ مشتریِ قبلی می‌نویسد — وقتی دو نفر نامِ نزدیک
                        // دارند همین یک خط جلوی اشتباه را می‌گیرد.
                        customerIsNew -> "خریدار جدید — با ثبتِ فاکتور ساخته می‌شود"
                        ui.customer.isNotBlank() -> "مشتریِ ثبت‌شده"
                        else -> null
                    }
                )
            }
            item {
                // نامِ دستی هم مجاز می‌مانَد: پیشخوان مشتریِ گذری دارد و
                // نباید مجبور شود اول در «اطلاعات پایه» ثبتش کند.
                OutlinedButton(
                    onClick = { showCustomerPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PersonSearch, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (customers.isEmpty()) "هنوز مشتری‌ای ثبت نشده"
                        else "انتخاب از مشتریانِ ثبت‌شده (${customers.size.fa()})"
                    )
                }
            }
            item {
                PartyRow(
                    label = "فروشنده",
                    value = CompanyPrefs.shopName(LocalSettings.current),
                    editable = false
                )
            }

            // ---------------- بدنه: ردیف‌های کالا ----------------
            item {
                SectionHeader(
                    title = "کالاها",
                    sub = "هر طرحِ متفاوت یک ردیف — یک فاکتور می‌تواند چند طرح داشته باشد."
                )
            }

            items(ui.lines, key = { it.key }) { line ->
                LineCard(
                    line = line,
                    onPick = { pickerFor = line.key },
                    onQty = { vm.setQty(line.key, it) },
                    onPrice = { vm.setPrice(line.key, it) },
                    onRemove = { vm.removeLine(line.key) },
                    removable = ui.lines.size > 1
                )
            }

            item {
                OutlinedButton(onClick = vm::addLine, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ردیف جدید")
                }
            }

            // ---------------- فوتر: پول ----------------
            item {
                SectionHeader(title = "دریافتی", sub = null)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TotalRow("جمع کل", ui.subtotal, bold = true)

                        // سودِ کلِ فاکتور، پیش از ثبت. اگر فاکتور روی‌هم
                        // زیان‌ده باشد باید همین‌جا دیده شود، نه در گزارشِ
                        // آخرِ ماه.
                        run {
                            val inv = Margin.Invoice(
                                lines = ui.lines.filter { it.ready }.map { l ->
                                    Margin.Line(
                                        cost = l.item?.avgCost ?: 0L,
                                        price = l.unitPrice,
                                        qty = l.qty
                                    )
                                }
                            )
                            if (inv.lines.isNotEmpty()) {
                                Text(
                                    buildString {
                                        if (inv.losing) append("زیانِ فاکتور: ") else append("سودِ فاکتور: ")
                                        append(inv.profit.afn())
                                        inv.percent?.let { append(" (${it.fa()}٪)") }
                                        if (inv.hasUnknownCost) {
                                            append(" — بهای بعضی ردیف‌ها ثبت نشده، پس کامل نیست")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (inv.losing) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (ui.prepay > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = ui.usePrepay, onCheckedChange = vm::setUsePrepay)
                                Text("استفاده از بیعانهٔ ${ui.prepay.afn()}")
                            }
                        }

                        OutlinedTextField(
                            value = ui.receivedText,
                            onValueChange = { vm.setReceived(it.digitsOnly()) },
                            label = { Text("نقدِ دریافتی (خالی = همه)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (ui.appliedPrepay > 0) TotalRow("از بیعانه", ui.appliedPrepay)
                        TotalRow("نقد", ui.received)
                        TotalRow(
                            "ماندهٔ طلب",
                            ui.remaining,
                            bold = true,
                            error = ui.remaining > 0
                        )
                    }
                }
            }

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
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                msg,
                                modifier = Modifier.weight(1f),
                                color = if (ui.isError) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            TextButton(onClick = vm::clearMessage) { Text("باشه") }
                        }
                    }
                }
            }

            item {
                BusyButton(
                    text = if (ui.canSave)
                        "ثبت فاکتور (${ui.readyLines.fa()} ردیف • ${ui.subtotal.afn()})"
                    else "دستِ‌کم یک ردیفِ کامل لازم است",
                    onClick = vm::save,
                    enabled = ui.canSave,
                    busy = busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, sub: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 16.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (sub != null) {
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PartyRow(
    label: String,
    value: String,
    editable: Boolean,
    onValueChange: (String) -> Unit = {},
    hint: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (editable) Icons.Default.Person else Icons.Default.Store,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f)) {
                if (editable) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, fontWeight = FontWeight.SemiBold)
                }
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun LineCard(
    line: DraftLine,
    onPick: () -> Unit,
    onQty: (String) -> Unit,
    onPrice: (String) -> Unit,
    onRemove: () -> Unit,
    removable: Boolean
) {
    val item = line.item
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            if (line.ready) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                    Text(
                        item?.let {
                            it.name + (if (it.size.isBlank()) "" else " • ${it.size}")
                        } ?: "انتخاب کالا",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (removable) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "حذف ردیف",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (item != null) {
                Text(
                    "موجودی: ${item.qty.fa()} عدد",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (line.qty > item.qty) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.qtyText,
                    onValueChange = { onQty(it.digitsOnly()) },
                    label = { Text("تعداد") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = line.priceText,
                    onValueChange = { onPrice(it.digitsOnly()) },
                    label = { Text("فی (؋)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            if (line.total > 0) {
                Text(
                    "جمعِ ردیف: ${line.total.afn()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // سود همان لحظه‌ای که قیمت زده می‌شود. بهای تمام‌شده را اپ از
            // قبل دقیق می‌داند (پارچه + خرج‌کار + دستمزد)، ولی تا امروز
            // هیچ‌جا کنارِ قیمت گذاشته نمی‌شد و فروشِ زیرِ بها ماه‌ها بعد
            // معلوم می‌گردید.
            line.item?.let { it2 ->
                val m = Margin.Line(cost = it2.avgCost, price = line.unitPrice, qty = line.qty)
                if (line.unitPrice > 0) {
                    Text(
                        when {
                            m.unknownCost ->
                                "بهای تمام‌شدهٔ این کالا ثبت نشده — سود معلوم نیست"
                            m.losing ->
                                "زیان! بهای تمام‌شده ${it2.avgCost.afn()} هر عدد — " +
                                    "${m.profit.afn()} (${m.percent?.fa().orEmpty()}٪)"
                            else ->
                                "بهای تمام‌شده ${it2.avgCost.afn()} هر عدد — " +
                                    "سود ${m.profit.afn()}" +
                                    (m.percent?.let { pc -> " (${pc.fa()}٪)" } ?: "")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (m.losing) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            m.losing -> MaterialTheme.colorScheme.error
                            m.unknownCost -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: Long, bold: Boolean = false, error: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value.afn(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ItemPickerDialog(
    stock: List<FinishedStock>,
    onDismiss: () -> Unit,
    onPick: (FinishedStock) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, stock) {
        val q = query.trim()
        stock.filter { it.qty > 0 && (q.isBlank() || it.name.contains(q) || it.size.contains(q)) }
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب کالا") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("جستجوی کالا") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(
                        if (stock.none { it.qty > 0 })
                            "انبارِ محصول خالی است. کارِ تأییدشده در نظارت خودش اینجا می‌آید."
                        else "چیزی با این نام پیدا نشد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.id }) { it0 ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(it0) }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    it0.name + (if (it0.size.isBlank()) "" else " • ${it0.size}"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${it0.qty.fa()} عدد",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
