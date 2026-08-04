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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import com.afghanjama.ui.platform.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.afghanjama.ui.platform.AppDropdownMenu
import com.afghanjama.ui.platform.AppDropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.Transaction
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.FinanceViewModel
/**
 * بخش مالی: گزارش‌ها + کیف پول و تراکنش‌ها + کارمزد خیاط (تسویه هفتگی)
 * + حساب فروشگاه‌ها.
 */
@Composable
fun FinanceHubScreen(
    financeVm: FinanceViewModel,
    dashboardVm: DashboardViewModel,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("گزارش", "کیف پول")

    // پیامِ ردشدن (موجودیِ ناکافی) — بی این، کاربر فکر می‌کرد ثبت شد
    val financeMessage by financeVm.message.collectAsState()
    financeMessage?.let { msg ->
        AppAlertDialog(
            onDismissRequest = { financeVm.clearMessage() },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { financeVm.clearMessage() }) { Text("باشه") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بخش مالی") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            ScrollableTabRow(
                selectedTabIndex = tab.coerceIn(0, tabs.lastIndex),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }

            when (tab.coerceIn(0, tabs.lastIndex)) {
                0 -> DashboardTab(dashboardVm)
                1 -> WalletTab(financeVm)
            }
        }
    }
}

// ======================================================
// تب ۰: گزارش‌ها — آمار زنده کارگاه
// ======================================================
@Composable
private fun DashboardTab(vm: DashboardViewModel) {
    val s by vm.stats.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ⏰ یادآوری تسویه هفتگی کارمزد
        if (s.wageReminderDue) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "⏰ یادآوری تسویه هفتگی",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "قدیمی‌ترین کارمزد باز ${s.oldestPendingWageDays} روز پیش ثبت شده. مجموع ${s.openWagesTotal.afn()} در تب «کارمزد خیاط» منتظر تسویه است.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // وضعیت تولید
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "وضعیت تولید",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StageStat("انبار", s.inStock)
                        StageStat("برش", s.cutting)
                        StageStat("دوخت", s.sewing)
                        StageStat("نظارت", s.review)
                        StageStat("آمادهٔ فروش", s.readyPieces)
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                    Text(
                        "فروش‌های ثبت‌شده تا امروز: ${s.salesCount.fa()} مورد",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // فروش
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "فروش",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MoneyBlock("۷ روز اخیر", s.sales7.afn())
                        MoneyBlock("۳۰ روز اخیر", s.sales30.afn())
                    }
                }
            }
        }

        // فایده خالص
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "فایده (تغییر خالص)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MoneyBlock("۷ روز اخیر", s.profitNet7.afn(), highlight = true)
                        MoneyBlock("۳۰ روز اخیر", s.profitNet30.afn(), highlight = true)
                    }
                }
            }
        }

        // هزینه‌های عمومی
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "هزینه‌های کارگاه (۳۰ روز)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        s.expenses30.afn(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // فروش‌های اخیر با سود واقعی
        if (s.recentSales.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "فروش‌های اخیر (سود واقعی)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        s.recentSales.forEach { rs ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        rs.designTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${rs.orderCode} • فروش: ${rs.revenue.afn()} • هزینه: ${rs.cost.afn()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    (if (rs.profit >= 0) "+" else "") + rs.profit.afn(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (rs.profit >= 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // بهره‌وری خیاط‌ها
        if (s.tailorStats.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "بهره‌وری خیاط‌ها (۳۰ روز)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        s.tailorStats.forEach { t ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${t.ordersDone} سفارش • ${t.piecesDone} عدد لباس",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    t.earned.afn(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // کارمزد باز
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "کارمزد تسویه‌نشده خیاط‌ها",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${s.openWagesCount} مورد",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        s.openWagesTotal.afn(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (s.openWagesTotal > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun StageStat(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.fa(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MoneyBlock(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ======================================================
// تب ۱: کیف پول و تراکنش‌ها (مالی عمومی)
// ======================================================
private val expenseCategories = listOf(
    "کرایه", "برق و آب", "معاش کارمند", "ترانسپورت", "مواد و لوازم", "خرید پارچه", "متفرقه"
)

@Composable
private fun WalletTab(vm: FinanceViewModel) {
    val wallet by vm.walletBalance.collectAsState()
    val profit by vm.profitBalance.collectAsState()
    val bank by vm.bankBalance.collectAsState()
    val txList by vm.tx.collectAsState()

    var showExpense by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Transaction?>(null) }

    fun boxLabel(v: String) = when (v) {
        "BANK" -> "بانک"
        "PROFIT" -> "فایده"
        else -> "کیف پول"
    }

    // ---------- دیالوگ حذف تراکنش ----------
    deleteTarget?.let { t ->
        AppAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف تراکنش؟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t.note.ifBlank { if (t.type == "IN") "دریافت" else "پرداخت" })
                    Text(
                        (if (t.type == "IN") "+" else "−") + t.amount.afn() +
                            " • " + boxLabel(t.source),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "موجودی صندوق‌ها بازمحاسبه می‌شود. رکوردهای مرتبط (حساب مشتری، کارمزد خیاط) دست نمی‌خورند — این حذف فقط برای اصلاح تراکنش اشتباه است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTx(t.id)
                    deleteTarget = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("لغو") } }
        )
    }

    if (showTransfer) {
        var fromBox by remember { mutableStateOf("WALLET") }
        var toBox by remember { mutableStateOf("BANK") }
        var amountText by remember { mutableStateOf("") }
        var fromMenu by remember { mutableStateOf(false) }
        var toMenu by remember { mutableStateOf(false) }
        val boxes = listOf("WALLET", "BANK", "PROFIT")

        AppAlertDialog(
            onDismissRequest = { showTransfer = false },
            title = { Text("انتقال بین صندوق‌ها") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { fromMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("از: " + boxLabel(fromBox))
                    }
                    AppDropdownMenu(expanded = fromMenu, onDismissRequest = { fromMenu = false }) {
                        boxes.forEach { b ->
                            AppDropdownMenuItem(text = { Text(boxLabel(b)) }, onClick = { fromBox = b; fromMenu = false })
                        }
                    }
                    OutlinedButton(onClick = { toMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("به: " + boxLabel(toBox))
                    }
                    AppDropdownMenu(expanded = toMenu, onDismissRequest = { toMenu = false }) {
                        boxes.forEach { b ->
                            AppDropdownMenuItem(text = { Text(boxLabel(b)) }, onClick = { toBox = b; toMenu = false })
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.digitsOnly() },
                        label = { Text("مبلغ (؋)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    if (amount > 0 && fromBox != toBox) {
                        vm.transfer(fromBox, toBox, amount, "انتقال از ${boxLabel(fromBox)} به ${boxLabel(toBox)}")
                    }
                    showTransfer = false
                }) { Text("انتقال") }
            },
            dismissButton = { TextButton(onClick = { showTransfer = false }) { Text("لغو") } }
        )
    }
    if (showExpense) {
        var category by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var catMenu by remember { mutableStateOf(false) }

        AppAlertDialog(
            onDismissRequest = { showExpense = false },
            title = { Text("ثبت هزینه کارگاه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { catMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(category.ifBlank { "انتخاب دسته هزینه" })
                    }
                    AppDropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        expenseCategories.forEach { c ->
                            AppDropdownMenuItem(text = { Text(c) }, onClick = { category = c; catMenu = false })
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.digitsOnly() },
                        label = { Text("مبلغ (؋)") },
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
                    Text(
                        "مبلغ از کیف پول کم و با دسته انتخابی در گزارش‌ها حساب می‌شود.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    if (amount > 0 && category.isNotBlank()) {
                        vm.addExpense(category, amount, note)
                    }
                    showExpense = false
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { showExpense = false }) { Text("لغو") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "کیف پول",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        wallet.afn(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "بانک",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        bank.afn(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "فایده",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        profit.afn(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        OutlinedButton(onClick = { showTransfer = true }, modifier = Modifier.fillMaxWidth()) {
            Text("⇄ انتقال بین صندوق‌ها")
        }

        Button(onClick = { showExpense = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Payments, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ثبت هزینه کارگاه (کرایه، برق، معاش...)")
        }

        Text(
            "تراکنش‌ها",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (txList.isEmpty()) {
            EmptyHint("هنوز تراکنشی ثبت نشده است.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(txList, key = { it.id }) { t ->
                    val isIn = t.type == "IN"
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.note.ifBlank { if (isIn) "دریافت" else "پرداخت" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    boxLabel(t.source) +
                                        (t.category.takeIf { it.isNotBlank() }?.let { " • $it" } ?: "") +
                                        " • ${formatDate(t.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                (if (isIn) "+" else "−") + t.amount.afn(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isIn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                            IconButton(onClick = { deleteTarget = t }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "حذف تراکنش",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDate(millis: Long): String = PersianDate.short(millis)
