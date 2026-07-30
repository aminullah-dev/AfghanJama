@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.FinishedSale
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.isShortage
import com.afghanjama.ui.format.stockText
import com.afghanjama.ui.format.stockBadge
import com.afghanjama.ui.components.SinglePhotoPicker
import com.afghanjama.util.PhotoStore
import androidx.compose.ui.platform.LocalContext
import com.afghanjama.ui.vm.FinishedSaleViewModel

@Composable
fun FinishedWarehouseScreen(
    vm: FinishedSaleViewModel,
    onBack: () -> Unit,
    /** رفتن به صفحهٔ فاکتورِ چندقلمی. */
    onOpenInvoice: () -> Unit = {},
    /** افزودنِ یک کالا به فاکتورِ در دست، بی ترکِ این صفحه. */
    onAddToInvoice: (FinishedStock) -> Unit = {},
    /** چند قلم تا حالا در فاکتورِ در دست هست. */
    invoiceCount: Int = 0
) {
    val items by vm.items.collectAsState()
    val folders by vm.folders.collectAsState()
    val sales by vm.recentSales.collectAsState()
    val ui by vm.ui.collectAsState()
    val wallet by vm.wallet.collectAsState()
    val bank by vm.bank.collectAsState()
    val context = LocalContext.current

    var sellTarget by remember { mutableStateOf<FinishedStock?>(null) }
    var returnTarget by remember { mutableStateOf<FinishedSale?>(null) }

    // پوشهٔ بازِ فعلی — null یعنی فهرستِ پوشه‌ها. مثلِ فایل‌منیجر: یک طبقه
    // پایین می‌رویم و با دکمهٔ برگشت بالا می‌آییم.
    var openFolder by remember { mutableStateOf<String?>(null) }
    // اگر پوشهٔ باز خالی شد (آخرین کالایش فروخته شد) خودش بسته می‌شود،
    // وگرنه کاربر در صفحه‌ای خالی گیر می‌کرد.
    LaunchedEffect(folders, openFolder) {
        if (openFolder != null && folders.none { it.name == openFolder }) openFolder = null
    }
    val shown = openFolder?.let { vm.itemsOf(it) } ?: emptyList()

    // ---------- دیالوگ فروش جزئی ----------
    sellTarget?.let { item ->
        var qtyText by remember(item.id) { mutableStateOf("") }
        var priceText by remember(item.id) { mutableStateOf("") }
        var customer by remember(item.id) { mutableStateOf("") }
        var receivedText by remember(item.id) { mutableStateOf("") }
        var discountText by remember(item.id) { mutableStateOf("") }
        var usePrepay by remember(item.id) { mutableStateOf(true) }

        // با تایپِ نامِ مشتری، بیعانهٔ استفاده‌نشده‌اش پیدا می‌شود
        LaunchedEffect(customer) { vm.lookupPrepay(customer) }
        AlertDialog(
            onDismissRequest = { sellTarget = null },
            title = { Text("فروش «${item.name}»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stockText(item.qty) +
                            if (item.size.isNotBlank()) " • سایز ${item.size}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isShortage(item.qty)) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isShortage(item.qty)) {
                        Text(
                            "این طرح کسری دارد. فروشِ تازه کسری را بیشتر می‌کند؛ " +
                                "با ورودِ بعدیِ همین کالا خودش تسویه می‌شود.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    SinglePhotoPicker(
                        fileName = item.photoFile,
                        canEdit = true,
                        onPicked = { name ->
                            vm.setPhoto(item, name) { PhotoStore.delete(context, it) }
                        },
                        onCleared = {
                            vm.setPhoto(item, "") { PhotoStore.delete(context, it) }
                        }
                    )
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.digitsOnly() },
                        label = { Text("تعداد فروش") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.digitsOnly() },
                        label = { Text("قیمت هر عدد (؋)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = discountText,
                        onValueChange = { discountText = it.digitsOnly() },
                        label = { Text("تخفیف روی کلِ این ردیف (؋) — اختیاری") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customer,
                        onValueChange = { customer = it },
                        label = { Text("نام مشتری (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val q = qtyText.toIntOrNull() ?: 0
                    val p = priceText.toLongOrNull() ?: 0L
                    val gross = q.toLong() * p
                    // تخفیف هرگز بیشتر از خودِ ردیف نمی‌شود، وگرنه فروشِ منفی می‌شد
                    val disc = (discountText.toLongOrNull() ?: 0L).coerceIn(0L, gross)
                    val total = gross - disc
                    if (q > 0 && p > 0) {
                        Text(
                            if (disc > 0) "جمع فروش: ${total.afn()} (پس از ${disc.afn()} تخفیف)"
                            else "جمع فروش: ${total.afn()}",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // ---------- بیعانهٔ قبلیِ همین مشتری ----------
                    val prepay = if (usePrepay) minOf(ui.prepayOfCustomer, total) else 0L
                    if (ui.prepayOfCustomer > 0 && total > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Checkbox(checked = usePrepay, onCheckedChange = { usePrepay = it })
                            Text(
                                "بیعانهٔ قبلی: ${ui.prepayOfCustomer.afn()} — کم شود",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (total > 0) {
                        OutlinedTextField(
                            value = receivedText,
                            onValueChange = { receivedText = it.digitsOnly() },
                            label = { Text("نقدِ دریافتی همین حالا (خالی = باقی‌مانده کامل)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        val due = total - prepay
                        val cash = (receivedText.toLongOrNull() ?: due).coerceIn(0L, due)
                        val credit = due - cash
                        Text(
                            buildString {
                                if (prepay > 0) append("از بیعانه ${prepay.afn()} • ")
                                append("نقد ${cash.afn()}")
                                if (credit > 0) append(" • باقی‌ماندهٔ طلب ${credit.afn()}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (credit > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val q = qtyText.toIntOrNull() ?: 0
                    val p = priceText.toLongOrNull() ?: 0L
                    val gross = q.toLong() * p
                    val disc = (discountText.toLongOrNull() ?: 0L).coerceIn(0L, gross)
                    val total = gross - disc
                    val prepay = if (usePrepay) minOf(ui.prepayOfCustomer, total) else 0L
                    val due = total - prepay
                    vm.sell(
                        item, q, p, customer,
                        receivedNow = (receivedText.toLongOrNull() ?: due).coerceIn(0L, due),
                        applyPrepay = prepay,
                        discount = disc
                    )
                    sellTarget = null
                }) { Text("ثبت فروش") }
            },
            dismissButton = { TextButton(onClick = { sellTarget = null }) { Text("لغو") } }
        )
    }

    // ---------- دیالوگ برگشت از فروش ----------
    returnTarget?.let { sale ->
        var qtyText by remember(sale.id) { mutableStateOf(sale.returnableQty.toString()) }
        var refundCash by remember(sale.id) { mutableStateOf(true) }
        var cashBox by remember(sale.id) { mutableStateOf("WALLET") }
        val q = qtyText.toIntOrNull() ?: 0
        AlertDialog(
            onDismissRequest = { returnTarget = null },
            title = { Text("برگشت از فروش «${sale.productName}»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "فروش ${sale.code} • ${sale.qty.fa()} عدد • هر عدد ${sale.unitPrice.afn()}" +
                            (if (sale.returnedQty > 0) " • قبلاً ${sale.returnedQty.fa()} عدد برگشته" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.digitsOnly() },
                        label = { Text("تعداد برگشتی (حداکثر ${sale.returnableQty.fa()})") },
                        singleLine = true,
                        isError = q !in 1..sale.returnableQty,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "مبلغ برگشتی: ${(q.coerceAtLeast(0) * sale.unitPrice).afn()}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = refundCash,
                            onClick = { refundCash = true },
                            label = { Text("پول نقد پس داده شد") }
                        )
                        FilterChip(
                            selected = !refundCash,
                            onClick = { refundCash = false },
                            label = { Text("به حساب مشتری") }
                        )
                    }
                    if (refundCash) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = cashBox == "WALLET",
                                onClick = { cashBox = "WALLET" },
                                label = { Text("صندوق (${wallet.afn()})") }
                            )
                            FilterChip(
                                selected = cashBox == "BANK",
                                onClick = { cashBox = "BANK" },
                                label = { Text("بانک (${bank.afn()})") }
                            )
                        }
                    } else {
                        Text(
                            "مبلغ به‌صورت بستانکاری روی حساب مشتری می‌ماند و بعداً قابل تسویه است.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = q in 1..sale.returnableQty,
                    onClick = {
                        vm.returnSale(sale, q, refundCash, cashBox)
                        returnTarget = null
                    }
                ) { Text("ثبت برگشت") }
            },
            dismissButton = { TextButton(onClick = { returnTarget = null }) { Text("لغو") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("فروش از انبار محصول") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ui.message?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ui.isError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) { Text(msg, modifier = Modifier.padding(12.dp)) }
                }
            }

            if (items.isEmpty()) {
                item {
                    Text(
                        "انبار محصول خالی است. هر سفارشی که در مرحلهٔ «نظارت» تأیید شود، " +
                            "خودکار وارد این انبار می‌شود و از همین‌جا فروخته می‌رود.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            } else {
                // ---------- دکمهٔ فاکتور فروش ----------
                item {
                    Button(
                        onClick = onOpenInvoice,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (invoiceCount > 0)
                                "ادامهٔ فاکتور فروش (${invoiceCount.fa()} قلم)"
                            else "ایجاد فاکتور فروش"
                        )
                    }
                }
                item {
                    Text(
                        "برای فروشِ چند کالا به یک مشتری، فاکتور بسازید و از هر پوشه " +
                            "هرچه خواستید داخلش بریزید. دکمهٔ «فروش» کنارِ هر کالا برای " +
                            "فروشِ همان یک قلم است.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ---------- مسیرِ پوشه ----------
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            openFolder?.let { "موجودی انبار ‹ $it" } ?: "موجودی انبار محصول",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (openFolder != null) {
                            TextButton(onClick = { openFolder = null }) {
                                Text("↩ همهٔ پوشه‌ها")
                            }
                        }
                    }
                }

                // ---------- فهرستِ پوشه‌ها ----------
                if (openFolder == null) {
                    items(folders, key = { it.name }) { folder ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(folder.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${folder.designs.fa()} طرح • ${folder.totalQty.fa()} عدد",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { openFolder = folder.name }) {
                                    Text("باز کردن")
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "پوشه‌ها از «دستهٔ طرح» در اطلاعات پایه ساخته می‌شوند. هر " +
                                "محصولی که از نظارت تأیید شود خودکار سرِ پوشهٔ طرحش " +
                                "می‌نشیند؛ طرحِ بی‌دسته زیرِ «دسته‌بندی‌نشده» می‌مانَد.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ---------- کالاهای پوشهٔ باز ----------
                items(if (openFolder == null) emptyList() else shown, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.photoFile.isNotBlank()) {
                                SinglePhotoPicker(
                                    fileName = item.photoFile,
                                    canEdit = false,
                                    onPicked = {},
                                    onCleared = {}
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    stockBadge(item.qty) + " عدد" +
                                        (if (item.size.isNotBlank()) " • سایز ${item.size}" else "") +
                                        " • بهای هر عدد ${item.avgCost.afn()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    // ردیفِ کسری باید در نگاهِ اول قرمز دیده شود
                                    color = if (isShortage(item.qty)) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Button(onClick = { sellTarget = item }) {
                                    Icon(Icons.Default.Sell, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("فروش")
                                }
                                TextButton(onClick = { onAddToInvoice(item) }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("به فاکتور")
                                }
                            }
                        }
                    }
                }
            }

            if (sales.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(thickness = 0.5.dp)
                    Text(
                        "فروش‌های اخیر",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(sales, key = { it.id }) { sale ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${sale.qty.fa()} عدد «${sale.productName}»", style = MaterialTheme.typography.bodyMedium)
                            val sub = listOfNotNull(
                                sale.customerName.takeIf { it.isNotBlank() },
                                if (sale.returnedQty > 0) "${sale.returnedQty.fa()} عدد برگشت خورده" else null
                            ).joinToString(" • ")
                            if (sub.isNotBlank()) {
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(sale.total.afn(), fontWeight = FontWeight.SemiBold)
                        if (sale.returnableQty > 0) {
                            TextButton(onClick = { returnTarget = sale }) { Text("برگشت") }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}
