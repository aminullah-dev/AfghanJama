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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PictureAsPdf
import com.afghanjama.ui.platform.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.OrderCodeLine
import com.afghanjama.data.entities.FabricUnit
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.ui.components.DeliverDialog
import com.afghanjama.ui.components.MeasurementsBlock
import com.afghanjama.ui.components.BusyButton
import com.afghanjama.platform.LocalDocs
import com.afghanjama.platform.LocalPhotos
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.ui.components.SheetAction
import com.afghanjama.ui.components.OrderPhotoStrip
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.dueDaysLate
import com.afghanjama.ui.format.dueDaysLeft
import com.afghanjama.ui.format.isOverdue
import com.afghanjama.prefs.SalePrefs
import com.afghanjama.ui.vm.OrderDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private fun statusFa(s: String): String = when (s) {
    "NEW" -> "ایجاد سفارش"
    OrderStatus.IN_STOCK.name -> "انبار"
    OrderStatus.CUTTING.name -> "برش"
    OrderStatus.CUT_DONE.name -> "برش تمام"
    OrderStatus.SEWING.name -> "دوخت"
    OrderStatus.REVIEW.name -> "نظارت"
    OrderStatus.SALES.name -> "فروش"
    OrderStatus.STORED.name -> "در انبار محصول"
    OrderStatus.SENT.name -> "تحویل شد"
    else -> s
}

private fun fmtDate(millis: Long): String = PersianDate.shortWithTime(millis)

@Composable
fun OrderDetailScreen(
    vm: OrderDetailViewModel,
    orderIdText: String?,
    canReturnSale: Boolean,
    canEdit: Boolean,
    onBack: () -> Unit
) {
    LaunchedEffect(orderIdText) {
        orderIdText?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let(vm::open)
    }

    val order by vm.order.collectAsState()
    val designCodeByOrder by vm.designCodeByOrder.collectAsState()
    val logs by vm.stageLogs.collectAsState()
    val payments by vm.payments.collectAsState()
    val fabrics by vm.fabrics.collectAsState()
    val workItems by vm.workItems.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val cuttingRecords by vm.cuttingRecords.collectAsState()
    val measurements by vm.measurements.collectAsState()
    val photos by vm.photos.collectAsState()
    val prepay by vm.prepay.collectAsState()
    val stockAvailable by vm.stockAvailable.collectAsState()
    val qcRecords by vm.qcRecords.collectAsState()
    val ui by vm.ui.collectAsState()

    var showDeliver by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    // به‌جای `Context`: مرزهایی که هر دو سکو دارند.
    val docs = LocalDocs.current
    val system = LocalSystemActions.current
    val photoStore = LocalPhotos.current

    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()

    // بعد از حذف موفق، برگشت به صفحه قبلی
    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
    }

    // ---------- دیالوگ تحویل به مشتری ----------
    if (showDeliver) order?.let { o ->
        DeliverDialog(
            order = o,
            available = stockAvailable,
            prepay = prepay,
            onDismiss = { showDeliver = false },
            onConfirm = { qty, unit, received, applied ->
                vm.deliverToCustomer(qty, unit, received, applied)
                showDeliver = false
            },
            allowShortage = SalePrefs.allowNegativeStock(settings)
        )
    }

    // ---------- دیالوگ ویرایش مشخصات ----------
    if (showEdit) order?.let { o ->
        var designTitle by remember(o.id) { mutableStateOf(o.designTitle) }
        var size by remember(o.id) { mutableStateOf(o.size) }
        var customerName by remember(o.id) { mutableStateOf(o.customerName) }
        var customerPhone by remember(o.id) { mutableStateOf(o.customerPhone) }
        var agreedText by remember(o.id) {
            mutableStateOf(o.agreedPrice.takeIf { it > 0 }?.toString() ?: "")
        }
        // مهلت به شکلِ «چند روزِ دیگر» ویرایش می‌شود. مقدارِ اولیه نگه داشته
        // می‌شود تا اگر کاربر دست نزند، تاریخِ مهلت دقیقاً همان بماند — وگرنه
        // هر بار ذخیره، مهلت را از «امروز» دوباره می‌ساخت و جلو می‌انداخت.
        val initialDueDays = remember(o.id) {
            if (o.dueDate > 0 && !isOverdue(o.dueDate)) dueDaysLeft(o.dueDate).toString() else ""
        }
        var dueDaysText by remember(o.id) { mutableStateOf(initialDueDays) }

        AppAlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("ویرایش مشخصات سفارش") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "مرحله تولید، پارچه‌ها و حساب‌های ثبت‌شده تغییر نمی‌کنند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = designTitle,
                        onValueChange = { designTitle = it },
                        label = { Text("نام طرح") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = size,
                        onValueChange = { size = it },
                        label = { Text("سایز") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("نام مشتری") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("شماره مشتری") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = agreedText,
                        onValueChange = { agreedText = it.digitsOnly() },
                        label = { Text("قیمت توافقی (؋)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dueDaysText,
                        onValueChange = { dueDaysText = it.digitsOnly() },
                        label = { Text("مهلت تحویل: چند روز از امروز (۰ = حذف مهلت)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (o.dueDate > 0) {
                        Text(
                            "مهلت فعلی: ${PersianDate.long(o.dueDate)}" +
                                (if (isOverdue(o.dueDate)) " (گذشته)" else "") +
                                " — اگر این فیلد را تغییر ندهید، همین می‌ماند.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (dueDaysText != initialDueDays) {
                        val d = dueDaysText.toLongOrNull()?.takeIf { it > 0 }
                        Text(
                            if (d == null) "مهلت حذف می‌شود"
                            else "مهلت جدید: ${PersianDate.long(System.currentTimeMillis() + d * 86_400_000L)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateDetails(
                        designTitle = designTitle,
                        size = size,
                        customerName = customerName,
                        customerPhone = customerPhone,
                        agreedPrice = agreedText.toLongOrNull() ?: 0L,
                        // دست‌نخورده → همان مهلتِ قبلی، بدون جابه‌جایی
                        dueDate = if (dueDaysText == initialDueDays) o.dueDate
                        else dueDaysText.toLongOrNull()
                            ?.takeIf { it > 0 }
                            ?.let { System.currentTimeMillis() + it * 86_400_000L }
                            ?: 0L
                    )
                    showEdit = false
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("لغو") } }
        )
    }

    // ---------- دیالوگ تأیید حذف ----------
    if (showDelete) order?.let { o ->
        AppAlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("حذف سفارش ${o.shortCode}؟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("فقط سفارشی که هنوز در انبار است قابل حذف است.")
                    Text("پارچه‌های برداشته‌شده از موجودی، به انبار برگردانده می‌شوند.")
                    Text(
                        "توجه: تراکنش‌های مالی ثبت‌شده (خرید پارچه، پیش‌پرداخت) به‌صورت خودکار برگشت نمی‌خورند و در صورت نیاز باید از تب کیف پول اصلاح شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteOrder()
                    showDelete = false
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("لغو") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("جزئیات سفارش") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                actions = {
                    if (canEdit && order != null) {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "ویرایش سفارش")
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "حذف سفارش",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        val o = order
        if (o == null) {
            Column(Modifier.padding(pad).padding(16.dp)) {
                Text("سفارش پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- مشخصات ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                o.designTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                statusFa(o.status),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OrderCodeLine(
                            designCode = designCodeByOrder[o.orderCode].orEmpty(),
                            orderCode = "${o.orderCode} • ${o.shortCode}"
                        )

                        HorizontalDivider(thickness = 0.5.dp)

                        val unitFa = when (o.fabricUnit.uppercase()) {
                            FabricUnit.METER.name -> "متر"
                            FabricUnit.YARD.name -> "یارد"
                            else -> o.fabricUnit
                        }
                        DetailRow("تعداد", "${o.qty} عدد")
                        // تعدادِ سفارش یک عددِ ثابت است، ولی کار جاری است:
                        // بخشی در انبار، بخشی دستِ ناظر، بخشی هنوز در دوخت.
                        // تا وقتی این‌ها نوشته نشود، «تعداد: ۲۰» می‌گوید
                        // بیست عدد آماده است در حالی که شاید شش عدد باشد.
                        if (o.storedQty in 1 until o.qty) {
                            DetailRow("وارد انبار شده", "${o.storedQty} از ${o.qty} عدد")
                        }
                        if (o.reviewQty > 0) {
                            DetailRow("دستِ نظارت", "${o.reviewQty} عدد")
                        }
                        DetailRow("پارچه", "${o.fabricType} • ${o.fabricColor} • ${o.fabricAmount} $unitFa")
                        DetailRow(
                            "منبع",
                            when (o.fabricSource) {
                                "STOCK" -> "از موجودی انبار"
                                "MATERIAL" -> "از انبار مواد"
                                else -> "خرید جدید"
                            }
                        )
                        DetailRow("سایز", o.size.ifBlank { "-" })
                        DetailRow("قیمت پارچه", o.fabricPrice.afn())
                        DetailRow("خرج کار", o.workCost.afn())
                        if (o.sewingCost > 0) DetailRow("دستمزد دوخت", o.sewingCost.afn())
                        DetailRow("بهای تمام‌شده", (o.fabricPrice + o.workCost + o.sewingCost).afn())
                        if (o.agreedPrice > 0) DetailRow("قیمت توافقی", o.agreedPrice.afn())
                        if (o.customerName.isNotBlank()) {
                            DetailRow(
                                "مشتری",
                                o.customerName +
                                    (o.customerPhone.takeIf { it.isNotBlank() }?.let { " • $it" } ?: "")
                            )
                        }
                        o.assignedTailor?.takeIf { it.isNotBlank() }?.let { DetailRow("خیاط", it) }
                        o.assignedInspector?.takeIf { it.isNotBlank() }?.let { DetailRow("ناظر", it) }
                        DetailRow("تاریخ ثبت", fmtDate(o.createdAt))
                        if (o.dueDate > 0) {
                            val late = isOverdue(o.dueDate)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "مهلت تحویل",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    fmtDate(o.dueDate) + " • " + (
                                        if (late) "${dueDaysLate(o.dueDate).fa()} روز تأخیر"
                                        else "${dueDaysLeft(o.dueDate).fa()} روز مانده"
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (late) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // ---------- فاکتور سفارش ----------
            //
            // ساخت و چاپ و اشتراک از مرزِ `Docs` می‌گذرند. تا دیروز
            // همین‌جا `InvoicePdf`، `PrintKit` و `ShareUtil` مستقیم صدا
            // زده می‌شدند و همان سه خط، کلِ این صفحهٔ ۹۹۹ خطی را در
            // `:app` نگه داشته بود.
            //
            // کاغذِ اندروید عوض نشد: آن‌سوی مرز همان `InvoicePdf` اجرا
            // می‌شود. ویندوز رندرِ خودش را دارد.
            item {
                var sheetBusy by remember { mutableStateOf(false) }

                fun run(action: SheetAction) {
                    if (sheetBusy) return
                    sheetBusy = true
                    scope.launch {
                        val problem = docs.orderInvoice(o, fabrics, workItems, payments, action)
                        if (problem != null) vm.showMessage(problem)
                        sheetBusy = false
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BusyButton(
                        text = "چاپ",
                        onClick = { run(SheetAction.PRINT) },
                        modifier = Modifier.weight(1f),
                        busy = sheetBusy,
                        busyText = "…",
                        leading = { Icon(Icons.Default.Print, contentDescription = null) }
                    )
                    BusyButton(
                        text = "PDF",
                        onClick = { run(SheetAction.PDF) },
                        modifier = Modifier.weight(1f),
                        busy = sheetBusy,
                        busyText = "…",
                        leading = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                    )
                    BusyButton(
                        text = "تصویر",
                        onClick = { run(SheetAction.IMAGE) },
                        modifier = Modifier.weight(1f),
                        busy = sheetBusy,
                        busyText = "…",
                        leading = { Icon(Icons.Default.Image, contentDescription = null) }
                    )
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

            // ---------- تحویل به مشتری ----------
            //
            // سفارشِ تأییدشده وارد انبار محصول می‌شود. اگر مشتری داشته
            // باشد، تحویل از همین‌جا انجام می‌شود — نامش و تعدادش را
            // می‌دانیم و کسی نباید همان لباس را در انبار پیدا کند و
            // دوباره به همان آدم «بفروشد». اگر مشتری ندارد (تولید برای
            // انبار)، فروش از انبارِ محصول درست‌ترین جاست.
            if (canReturnSale && o.status == OrderStatus.STORED.name) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "این سفارش آمادهٔ تحویل است",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (o.customerName.isNotBlank()) {
                                Button(
                                    onClick = { vm.lookupPrepay(); vm.lookupStock(); showDeliver = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("تحویل به ${o.customerName}")
                                }
                                Text(
                                    "برگشتِ فروش از صفحهٔ «فروش از انبار محصول» انجام می‌شود.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            } else {
                                Text(
                                    "این سفارش مشتری ندارد؛ فروش و برگشتِ فروش از صفحهٔ " +
                                        "«فروش از انبار محصول» انجام می‌شود.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // ---------- رسیدِ تحویل ----------
            if (o.status == OrderStatus.SENT.name) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "تحویل ${o.customerName.ifBlank { "مشتری" }} شد",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            TextButton(
                                onClick = {
                                    system.shareText(
                                        title = "اشتراک رسید تحویل",
                                        text = deliveryReceipt(
                                            CompanyPrefs.shopName(settings),
                                            o,
                                            payments.sumOf { it.amount }
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("اشتراک رسید تحویل") }
                        }
                    }
                }
            }

            // ---------- پارچه‌های سفارش ----------
            if (fabrics.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "پارچه‌های سفارش (${fabrics.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            fabrics.forEach { f ->
                                val unitFa = when (f.fabricUnit.uppercase()) {
                                    FabricUnit.METER.name -> "متر"
                                    FabricUnit.YARD.name -> "یارد"
                                    else -> f.fabricUnit
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${f.fabricType} • ${f.fabricColor}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            "${f.amount} $unitFa • " + when (f.source) {
                                                "STOCK" -> "از موجودی"
                                                "MATERIAL" -> "از انبار مواد"
                                                else -> "خرید جدید"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(f.price.afn(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // ---------- خرج‌کارهای سفارش ----------
            if (workItems.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "خرج‌کارها (فی‌عدد)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            workItems.forEach { w ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(w.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(w.price.afn(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("جمع فی‌عدد × ${o.qty}", fontWeight = FontWeight.Medium)
                                Text(o.workCost.afn(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // ---------- تحویل به خیاط‌ها ----------
            if (assignments.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "تحویل به خیاط‌ها",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            assignments.forEach { a ->
                                val done = a.status == "DONE"
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${a.tailorLabel} — ${a.qty} عدد" + if (done) " ✔" else " (در حال دوخت)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (a.quality.isNotBlank()) {
                                            Text(
                                                "کیفیت: ${a.quality}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(a.totalWage.afn(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // ---------- پرداخت‌های این سفارش ----------
            if (payments.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "پرداخت‌های این سفارش",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            payments.forEach { pmt ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pmt.note, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            fmtDate(pmt.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        pmt.amount.afn(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (pmt.amount >= 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------- عکس‌های سفارش ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        OrderPhotoStrip(
                            photos = photos,
                            canEdit = canEdit,
                            onCaptured = { vm.addPhoto(it) },
                            onDelete = { p -> vm.deletePhoto(p) { photoStore.delete(it) } }
                        )
                    }
                }
            }

            // ---------- اندازه‌های مشتری ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        MeasurementsBlock(
                            items = measurements,
                            emptyHint = "برای این مشتری اندازه‌ای ثبت نشده — از صفحهٔ " +
                                "مشتری اضافه کنید تا در برش و دوخت هم دیده شود."
                        )
                    }
                }
            }

            // ---------- رکورد برش ----------
            if (cuttingRecords.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("برش", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            cuttingRecords.forEach { rec ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("مسئول برش: ${rec.cutter}", fontWeight = FontWeight.Medium)
                                        Text("${rec.pieces} دست")
                                    }
                                    if (rec.waste.isNotBlank()) {
                                        Text("ضایعات: ${rec.waste}", style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                    if (rec.note.isNotBlank()) {
                                        Text(rec.note, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(fmtDate(rec.createdAt), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // ---------- کنترل کیفیت ----------
            if (qcRecords.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("کنترل کیفیت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            qcRecords.forEach { rec ->
                                val approved = rec.result == "APPROVED"
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            if (approved) "تأیید شد" else "برگشت برای اصلاح",
                                            fontWeight = FontWeight.Medium,
                                            color = if (approved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                        if (rec.inspector.isNotBlank()) Text(rec.inspector, style = MaterialTheme.typography.labelMedium)
                                    }
                                    if (rec.problem.isNotBlank()) {
                                        Text("مشکل: ${rec.problem}", style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                    if (rec.tailor.isNotBlank()) {
                                        Text("کارِ خیاط: ${rec.tailor}", style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                    if (rec.note.isNotBlank()) {
                                        Text(rec.note, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(fmtDate(rec.createdAt), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // ---------- تایم‌لاین مراحل ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "تایم‌لاین مراحل",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (logs.isEmpty()) {
                            Text(
                                "تاریخچه‌ای ثبت نشده (سفارش‌های قدیمی تایم‌لاین ندارند).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            logs.forEach { log ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "${statusFa(log.fromStatus)} ← ${statusFa(log.toStatus)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        // «چه کسی» فقط وقتی می‌آید که واقعاً
                                        // ثبت شده باشد. سفارش‌های پیش از
                                        // نسخهٔ ۶۳ نام ندارند و جای خالی
                                        // بهتر از نامِ حدسی است — این
                                        // تایم‌لاین ممکن است سرِ اختلافِ
                                        // حساب خوانده شود.
                                        val who = log.user.trim()
                                        Text(
                                            if (who.isEmpty()) fmtDate(log.at)
                                            else "${fmtDate(log.at)} — $who",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * رسیدِ تحویل — چیزی که مشتری با خودش می‌برد: چه گرفت، چقدر پرداخت شد و
 * چقدر مانده. عمداً متنِ ساده است تا از هر پیام‌رسانی فرستاده شود.
 */
private fun deliveryReceipt(
    shop: String,
    o: com.afghanjama.data.entities.Order,
    paidTotal: Long
): String = buildString {
    appendLine("🧾 رسید تحویل — $shop")
    appendLine("تاریخ: ${PersianDate.short(System.currentTimeMillis())}")
    appendLine("مشتری: ${o.customerName}")
    appendLine("──────────────")
    appendLine("• ${o.designTitle} — ${o.qty.fa()} عدد • سایز ${o.size}")
    appendLine("کد سفارش: ${o.orderCode}")
    if (o.agreedPrice > 0) {
        appendLine("──────────────")
        appendLine("مبلغ سفارش: ${o.agreedPrice.afn()}")
        appendLine("پرداخت‌شده: ${paidTotal.afn()}")
        val left = (o.agreedPrice - paidTotal).coerceAtLeast(0L)
        appendLine(if (left > 0) "باقی‌مانده: ${left.afn()}" else "تسویه شد ✅")
    }
    appendLine("──────────────")
    appendLine("از اعتماد شما سپاسگزاریم.")
}
