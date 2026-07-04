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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.vm.CustomerAccount
import com.afghanjama.ui.vm.CustomerAccountsViewModel
import com.afghanjama.ui.vm.DashboardViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.TailorWageGroup
import com.afghanjama.ui.vm.WagesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * بخش مالی: گزارش‌ها + کیف پول و تراکنش‌ها + کارمزد خیاط (تسویه هفتگی)
 * + حساب فروشگاه‌ها.
 */
@Composable
fun FinanceHubScreen(
    financeVm: FinanceViewModel,
    wagesVm: WagesViewModel,
    customersVm: CustomerAccountsViewModel,
    dashboardVm: DashboardViewModel
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("گزارش", "کیف پول", "کارمزد خیاط", "حساب فروشگاه‌ها")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بخش مالی") },
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
                selectedTabIndex = tab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }

            when (tab) {
                0 -> DashboardTab(dashboardVm)
                1 -> WalletTab(financeVm)
                2 -> WagesTab(wagesVm)
                3 -> CustomersTab(customersVm)
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
        // وضعیت تولید
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                        StageStat("فروش", s.readyForSale)
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                    Text(
                        "تحویل‌شده تا امروز: ${s.sentTotal} سفارش",
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
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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

        // کارمزد باز
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
            count.toString(),
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
    val txList by vm.tx.collectAsState()

    var showExpense by remember { mutableStateOf(false) }
    if (showExpense) {
        var category by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var catMenu by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showExpense = false },
            title = { Text("ثبت هزینه کارگاه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { catMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(category.ifBlank { "انتخاب دسته هزینه" })
                    }
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        expenseCategories.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catMenu = false })
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter(Char::isDigit) },
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
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                        style = MaterialTheme.typography.titleLarge,
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                                    "${if (t.source == "WALLET") "کیف پول" else "فایده"}" +
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
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ======================================================
// تب ۲: کارمزد خیاط — جمع می‌شود و هفته‌وار تسویه می‌شود
// ======================================================
@Composable
private fun WagesTab(vm: WagesViewModel) {
    val groups by vm.pendingGroups.collectAsState()
    val settled by vm.settledWages.collectAsState()

    var confirmGroup by remember { mutableStateOf<TailorWageGroup?>(null) }

    confirmGroup?.let { g ->
        AlertDialog(
            onDismissRequest = { confirmGroup = null },
            title = { Text("تسویه کارمزد") },
            text = {
                Text("مجموع ${g.total.afn()} برای «${g.tailorLabel}» از کیف پول پرداخت و در بخش مالی عمومی ثبت می‌شود. ادامه می‌دهید؟")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.settle(g)
                    confirmGroup = null
                }) { Text("تسویه") }
            },
            dismissButton = {
                TextButton(onClick = { confirmGroup = null }) { Text("لغو") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "کارمزدهای باز (آماده تسویه هفتگی)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (groups.isEmpty()) {
            item { EmptyHint("کارمزد تسویه‌نشده‌ای وجود ندارد. با تمام‌شدن دوختِ هر سفارش، کارمزد خیاط اینجا جمع می‌شود.") }
        } else {
            items(groups, key = { it.tailorLabel }) { g ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                g.tailorLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                g.total.afn(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        g.items.forEach { w ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${w.orderCode} • ${formatDate(w.createdAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    w.amount.afn(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(thickness = 0.5.dp)

                        Button(
                            onClick = { confirmGroup = g },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("تسویه حساب (${g.total.afn()})")
                        }
                    }
                }
            }
        }

        if (settled.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "تاریخچه تسویه‌شده",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(settled, key = { it.id }) { w ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(w.tailorLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "${w.orderCode} • تسویه: ${w.settledAt?.let(::formatDate) ?: "-"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(w.amount.afn(), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ======================================================
// تب ۳: حساب فروشگاه‌ها / مشتری‌ها + ثبت دریافتی دستی
// ======================================================
@Composable
private fun CustomersTab(vm: CustomerAccountsViewModel) {
    val accounts by vm.accounts.collectAsState()

    // دیالوگ ثبت دریافتی دستی
    var payTarget by remember { mutableStateOf<CustomerAccount?>(null) }
    var payAmountText by remember { mutableStateOf("") }
    var payNote by remember { mutableStateOf("") }

    // دیالوگ تاریخچه پرداخت‌ها
    var historyTarget by remember { mutableStateOf<CustomerAccount?>(null) }

    historyTarget?.let { acc ->
        AlertDialog(
            onDismissRequest = { historyTarget = null },
            title = { Text("پرداخت‌های «${acc.name}»") },
            text = {
                if (acc.payments.isEmpty()) {
                    Text("هنوز پرداختی ثبت نشده است.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        acc.payments.take(15).forEach { pmt ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pmt.note.ifBlank {
                                            when (pmt.source) {
                                                "ADVANCE" -> "پیش‌پرداخت"
                                                "SALE" -> "دریافتی فروش"
                                                else -> "دریافتی"
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        formatDate(pmt.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    pmt.amount.afn(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { historyTarget = null }) { Text("بستن") }
            }
        )
    }

    payTarget?.let { acc ->
        AlertDialog(
            onDismissRequest = { payTarget = null },
            title = { Text("ثبت دریافتی از «${acc.name}»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (acc.balance > 0) {
                        Text(
                            "باقی‌مانده حساب: ${acc.balance.afn()}",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedTextField(
                        value = payAmountText,
                        onValueChange = { payAmountText = it.filter(Char::isDigit) },
                        label = { Text("مبلغ (؋)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = payNote,
                        onValueChange = { payNote = it },
                        label = { Text("یادداشت (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "مبلغ وارد کیف پول و در حساب مشتری ثبت می‌شود.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amount = payAmountText.toLongOrNull() ?: 0L
                        if (amount > 0L) {
                            vm.addManualPayment(acc.name, amount, payNote)
                        }
                        payTarget = null
                        payAmountText = ""
                        payNote = ""
                    }
                ) { Text("ثبت") }
            },
            dismissButton = {
                TextButton(onClick = {
                    payTarget = null
                    payAmountText = ""
                    payNote = ""
                }) { Text("لغو") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "حساب فروشگاه‌ها و مشتری‌ها",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (accounts.isEmpty()) {
            item { EmptyHint("هنوز سفارشی با نام مشتری ثبت نشده است.") }
        } else {
            items(accounts, key = { it.name }) { acc ->
                CustomerAccountCard(
                    acc = acc,
                    onAddPayment = { payTarget = acc },
                    onShowHistory = { historyTarget = acc }
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun CustomerAccountCard(
    acc: CustomerAccount,
    onAddPayment: () -> Unit,
    onShowHistory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        acc.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (acc.phone.isNotBlank()) {
                        Text(
                            acc.phone,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "${acc.ordersCount} سفارش" +
                        if (acc.openOrdersCount > 0) " (${acc.openOrdersCount} باز)" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(thickness = 0.5.dp)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MoneyStat("مبلغ سفارش‌ها", acc.totalDue.afn())
                MoneyStat("دریافتی", acc.totalPaid.afn())
                MoneyStat(
                    label = if (acc.balance > 0) "باقی‌مانده" else "تسویه",
                    value = if (acc.balance > 0) acc.balance.afn() else "✔",
                    highlight = acc.balance > 0
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShowHistory,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("تاریخچه")
                }
                OutlinedButton(
                    onClick = onAddPayment,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ثبت دریافتی")
                }
            }
        }
    }
}

@Composable
private fun MoneyStat(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(millis))
