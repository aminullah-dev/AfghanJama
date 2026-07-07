@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.FabricUnit
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.pdf.InvoicePdf
import com.afghanjama.ui.vm.OrderDetailViewModel
import com.afghanjama.util.ShareUtil
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
    val logs by vm.stageLogs.collectAsState()
    val payments by vm.payments.collectAsState()
    val fabrics by vm.fabrics.collectAsState()
    val workItems by vm.workItems.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val ui by vm.ui.collectAsState()

    var showReturn by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // بعد از حذف موفق، برگشت به صفحه قبلی
    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
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

        AlertDialog(
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
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateDetails(
                        designTitle = designTitle,
                        size = size,
                        customerName = customerName,
                        customerPhone = customerPhone,
                        agreedPrice = agreedText.toLongOrNull() ?: 0L
                    )
                    showEdit = false
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("لغو") } }
        )
    }

    // ---------- دیالوگ تأیید حذف ----------
    if (showDelete) order?.let { o ->
        AlertDialog(
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

    // ---------- دیالوگ برگشت فروش ----------
    if (showReturn) {
        val salePaid = payments.filter { it.source == "SALE" }.sumOf { it.amount }
        var refundText by remember { mutableStateOf(salePaid.takeIf { it > 0 }?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { showReturn = false },
            title = { Text("برگشت فروش") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مبلغ برگشتی از کیف پول خارج و سفارش به مرحله فروش برمی‌گردد.")
                    OutlinedTextField(
                        value = refundText,
                        onValueChange = { refundText = it.digitsOnly() },
                        label = { Text("مبلغ برگشتی (؋)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.returnSale(refundText.toLongOrNull() ?: 0L)
                    showReturn = false
                }) { Text("ثبت برگشتی") }
            },
            dismissButton = { TextButton(onClick = { showReturn = false }) { Text("لغو") } }
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        Text(
                            "${o.orderCode} • ${o.shortCode}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider(thickness = 0.5.dp)

                        val unitFa = when (o.fabricUnit.uppercase()) {
                            FabricUnit.METER.name -> "متر"
                            FabricUnit.YARD.name -> "یارد"
                            else -> o.fabricUnit
                        }
                        DetailRow("تعداد", "${o.qty} عدد")
                        DetailRow("پارچه", "${o.fabricType} • ${o.fabricColor} • ${o.fabricAmount} $unitFa")
                        DetailRow(
                            "منبع پارچه",
                            if (o.fabricSource == "STOCK") "از موجودی انبار" else "خرید جدید"
                        )
                        DetailRow("سایز", o.size.ifBlank { "-" })
                        DetailRow("قیمت پارچه", o.fabricPrice.afn())
                        DetailRow("خرج کار", o.workCost.afn())
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
                    }
                }
            }

            // ---------- فاکتور PDF ----------
            item {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    InvoicePdf.create(context, o, fabrics, workItems, payments)
                                }
                            }.onSuccess { file ->
                                ShareUtil.shareFile(context, file, "application/pdf", "اشتراک فاکتور")
                            }.onFailure {
                                android.widget.Toast.makeText(
                                    context,
                                    "ساخت فاکتور ناموفق بود: ${it.message}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("فاکتور PDF — چاپ / اشتراک")
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
                        )
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

            // ---------- برگشت فروش (فقط مدیر + سفارش تحویل‌شده) ----------
            if (canReturnSale && o.status == OrderStatus.SENT.name) {
                item {
                    Button(
                        onClick = { showReturn = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("برگشت فروش (مرجوعی)")
                    }
                }
            }

            // ---------- پارچه‌های سفارش ----------
            if (fabrics.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                            "${f.amount} $unitFa • " + if (f.source == "STOCK") "از موجودی" else "خرید جدید",
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
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            // ---------- تایم‌لاین مراحل ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "${statusFa(log.fromStatus)} ← ${statusFa(log.toStatus)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            fmtDate(log.at),
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
