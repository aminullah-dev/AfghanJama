@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.pdf.FinancialStatementsPdf
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.ReportsViewModel
import com.afghanjama.util.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, strong: Boolean = false, color: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * یک سطرِ میله‌ای: طولِ میله فقط «درآمد» را نشان می‌دهد (یک مقیاس، یک رنگ
 * برای همهٔ سطرها). مقدارها بیرونِ میله نوشته می‌شوند و سود همیشه با
 * واژهٔ «سود/زیان» می‌آید، نه فقط با رنگ — تا بدونِ تشخیصِ رنگ هم خوانا باشد.
 */
@Composable
private fun BarRow(
    label: String,
    valueText: String,
    fraction: Float,
    subText: String,
    subColor: Color
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Text(
            subText,
            style = MaterialTheme.typography.labelSmall,
            color = subColor
        )
    }
}

/** یک انتخابگرِ تاریخِ شمسی: روز / ماه / سال به‌صورت منوی کشویی. */
@Composable
private fun JalaliDateRow(
    title: String,
    year: Int, month: Int, day: Int,
    onChange: (Int, Int, Int) -> Unit
) {
    var yOpen by remember { mutableStateOf(false) }
    var mOpen by remember { mutableStateOf(false) }
    var dOpen by remember { mutableStateOf(false) }
    val thisYear = remember { PersianDate.todayJalali()[0] }
    val years = remember(thisYear) { (thisYear - 4..thisYear).toList().reversed() }
    val maxDay = PersianDate.daysInJalaliMonth(year, month)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                OutlinedButton(onClick = { dOpen = true }) { Text(day.fa()) }
                DropdownMenu(expanded = dOpen, onDismissRequest = { dOpen = false }) {
                    (1..maxDay).forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d.fa()) },
                            onClick = { onChange(year, month, d); dOpen = false }
                        )
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { mOpen = true }) {
                    Text(PersianDate.afghanMonths[month - 1])
                }
                DropdownMenu(expanded = mOpen, onDismissRequest = { mOpen = false }) {
                    PersianDate.afghanMonths.forEachIndexed { i, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                val m = i + 1
                                // روزِ ۳۱ در ماهِ ۳۰ روزه نامعتبر می‌شود
                                val d = day.coerceAtMost(PersianDate.daysInJalaliMonth(year, m))
                                onChange(year, m, d); mOpen = false
                            }
                        )
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { yOpen = true }) { Text(year.fa()) }
                DropdownMenu(expanded = yOpen, onDismissRequest = { yOpen = false }) {
                    years.forEach { y ->
                        DropdownMenuItem(
                            text = { Text(y.fa()) },
                            onClick = {
                                val d = day.coerceAtMost(PersianDate.daysInJalaliMonth(y, month))
                                onChange(y, month, d); yOpen = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsScreen(
    vm: ReportsViewModel,
    onBack: () -> Unit
) {
    val r by vm.report.collectAsState()
    val range by vm.range.collectAsState()
    val tb by vm.trialBalance.collectAsState()
    val income by vm.incomeStatement.collectAsState()
    val sheet by vm.balanceSheet.collectAsState()
    val trend by vm.trend.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { vm.exportCsv(context, it) } }
    val periodLabel = range.label

    // ---------- بازهٔ دلخواه ----------
    var customOpen by remember { mutableStateOf(false) }
    if (customOpen) {
        val today = remember { PersianDate.todayJalali() }
        var f by remember { mutableStateOf(Triple(today[0], today[1], 1)) }
        var t by remember { mutableStateOf(Triple(today[0], today[1], today[2])) }
        val fromMs = PersianDate.startOfJalaliDay(f.first, f.second, f.third)
        val toMs = PersianDate.endOfJalaliDay(t.first, t.second, t.third)
        val valid = fromMs <= toMs

        AlertDialog(
            onDismissRequest = { customOpen = false },
            title = { Text("بازهٔ دلخواه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    JalaliDateRow("از تاریخ", f.first, f.second, f.third) { y, m, d ->
                        f = Triple(y, m, d)
                    }
                    JalaliDateRow("تا تاریخ", t.first, t.second, t.third) { y, m, d ->
                        t = Triple(y, m, d)
                    }
                    Text(
                        if (valid) "از ${PersianDate.long(fromMs)} تا ${PersianDate.long(toMs)}"
                        else "⚠ تاریخِ شروع بعد از تاریخِ پایان است",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (valid) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        vm.setCustomRange(
                            fromMs, toMs,
                            "${PersianDate.short(fromMs)} تا ${PersianDate.short(toMs)}"
                        )
                        customOpen = false
                    }
                ) { Text("اعمال") }
            },
            dismissButton = {
                TextButton(onClick = { customOpen = false }) { Text("انصراف") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("گزارش‌ها") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                FinancialStatementsPdf.create(context, income, sheet, periodLabel)
                            }
                            ShareUtil.shareFile(
                                context, file, "application/pdf", "اشتراک صورت‌های مالی"
                            )
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "صورت‌های مالی PDF")
                    }
                    IconButton(onClick = {
                        csvLauncher.launch("گزارش-افغان‌جامه.csv")
                    }) {
                        Icon(Icons.Default.TableChart, contentDescription = "خروجی اکسل")
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            !range.isCustom && range.presetDays == null,
                            { vm.setPeriod(null) }, label = { Text("همه") }
                        )
                        FilterChip(range.presetDays == 7, { vm.setPeriod(7) }, label = { Text("۷ روز") })
                        FilterChip(range.presetDays == 30, { vm.setPeriod(30) }, label = { Text("۳۰ روز") })
                        FilterChip(range.presetDays == 90, { vm.setPeriod(90) }, label = { Text("۹۰ روز") })
                        FilterChip(range.isCustom, { customOpen = true }, label = { Text("دلخواه…") })
                    }
                    Text(
                        "بازه: ${range.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard("موجودی نقد") {
                    StatRow("صندوق (کیف پول)", r.wallet.afn())
                    StatRow("بانک", r.bank.afn())
                    StatRow("صندوق فایده", r.profitBox.afn())
                }
            }

            item {
                SectionCard("فروش دوره") {
                    StatRow("تعداد فروش", r.salesCount.fa())
                    StatRow("درآمد فروش", r.revenue.afn())
                    StatRow("بهای تمام‌شدهٔ فروش", r.cogs.afn())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatRow(
                        "سود ناخالص",
                        r.grossProfit.afn(),
                        strong = true,
                        color = if (r.grossProfit >= 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }

            item {
                SectionCard("حساب اشخاص") {
                    StatRow(
                        "طلب ما (${r.debtorCount.fa()} بدهکار)",
                        r.receivable.afn(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatRow(
                        "بدهی ما (${r.creditorCount.fa()} بستانکار)",
                        r.payable.afn(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item {
                SectionCard("جریان نقد دوره") {
                    StatRow("ورودی نقد", r.incomeTotal.afn(), color = MaterialTheme.colorScheme.primary)
                    StatRow("خروجی نقد", r.expenseTotal.afn(), color = MaterialTheme.colorScheme.error)
                    if (r.expenseByCategory.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "هزینه بر اساس دسته",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        r.expenseByCategory.forEach { (cat, amount) ->
                            StatRow(cat, amount.afn())
                        }
                    }
                    // انتقالِ داخلی در دو عددِ بالا شمرده نشده — ولی پنهان هم
                    // نمی‌شود، وگرنه کاربر نمی‌فهمد چرا جمعِ تراکنش‌ها با
                    // گزارش نمی‌خوانَد.
                    if (r.internalMoves > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StatRow("انتقال بین صندوق‌ها", r.internalMoves.afn())
                        Text(
                            "این مبلغ بین صندوق‌های خودِ کارگاه جابه‌جا شده " +
                                "(مثلاً سودِ فروش به صندوق فایده)، پس نه درآمد " +
                                "است و نه هزینه و در دو عددِ بالا شمرده نشده.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------- روندِ ۶ ماهِ اخیر ----------
            if (trend.hasMonths) {
                item {
                    SectionCard("روندِ ۶ ماهِ اخیر") {
                        trend.profitChangePercent?.let { pct ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (pct >= 0)
                                        "📈 سود ${pct.fa()}٪ بیشتر شده".toPersianDigits()
                                    else
                                        "📉 سود ${(-pct).fa()}٪ کمتر شده".toPersianDigits(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pct >= 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "مقایسهٔ ${trend.dayOfMonth.fa()} روزِ اولِ این ماه با ${trend.dayOfMonth.fa()} روزِ اولِ ماهِ قبل"
                                        .toPersianDigits(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            "طولِ میله = درآمدِ آن ماه",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val max = trend.maxMonthRevenue
                        trend.months.forEach { m ->
                            BarRow(
                                label = m.label,
                                valueText = m.revenue.afn(),
                                fraction = if (max > 0) m.revenue.toFloat() / max else 0f,
                                subText = if (m.salesCount == 0) "فروشی ثبت نشده"
                                else (if (m.profit >= 0) "سود ${m.profit.afn()}" else "زیان ${(-m.profit).afn()}") +
                                    " • ${m.salesCount.fa()} فروش",
                                subColor = if (m.salesCount == 0) MaterialTheme.colorScheme.onSurfaceVariant
                                else if (m.profit >= 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // ---------- سودآوریِ محصولات ----------
            if (trend.hasProducts) {
                item {
                    SectionCard("سودآوریِ محصولات — $periodLabel") {
                        trend.bestProduct?.takeIf { it.profit > 0 }?.let { best ->
                            Text(
                                "🏆 پرسودترین: ${best.name} — ${best.profit.afn()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            "طولِ میله = درآمدِ آن محصول",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val max = trend.maxProductRevenue
                        trend.products.take(8).forEach { p ->
                            BarRow(
                                label = p.name,
                                valueText = "${p.qty.fa()} عدد • ${p.revenue.afn()}",
                                fraction = if (max > 0) p.revenue.toFloat() / max else 0f,
                                subText = (
                                    if (p.profit >= 0) "سود ${p.profit.afn()}"
                                    else "زیان ${(-p.profit).afn()}"
                                    ) + " • حاشیه ${p.marginPercent.fa()}٪".toPersianDigits(),
                                subColor = if (p.profit >= 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        if (trend.products.size > 8) {
                            Text(
                                "و ${(trend.products.size - 8).fa()} محصولِ دیگر",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---------- صورتِ سود و زیان ----------
            if (income.hasData) {
                item {
                    SectionCard("صورتِ سود و زیان — $periodLabel") {
                        income.revenues.forEach { StatRow(it.label, it.amount.afn()) }
                        StatRow("جمعِ درآمد", income.totalRevenue.afn(), strong = true)
                        StatRow("کسر: بهای تمام‌شدهٔ فروش", income.cogs.afn())
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StatRow(
                            "سودِ ناخالص",
                            income.grossProfit.afn(),
                            strong = true,
                            color = if (income.grossProfit >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )

                        if (income.expenses.isNotEmpty()) {
                            Text(
                                "هزینه‌های عملیاتی",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            income.expenses.forEach { StatRow(it.label, it.amount.afn()) }
                        }
                        StatRow("جمعِ هزینه‌ها", income.totalExpense.afn(), strong = true)

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StatRow(
                            if (income.netProfit >= 0) "سودِ خالصِ دوره" else "زیانِ خالصِ دوره",
                            income.netProfit.afn(),
                            strong = true,
                            color = if (income.netProfit >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        if (income.totalRevenue > 0) {
                            Text(
                                "حاشیهٔ سودِ خالص: ${income.marginPercent}٪".toPersianDigits(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---------- ترازنامه ----------
            if (sheet.hasData) {
                item {
                    SectionCard("ترازنامه — تا ${PersianDate.short(System.currentTimeMillis())}") {
                        Text(
                            "دارایی‌ها",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        sheet.assets.forEach { StatRow(it.label, it.amount.afn()) }
                        StatRow("جمعِ دارایی‌ها", sheet.totalAssets.afn(), strong = true)

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "بدهی‌ها",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (sheet.liabilities.isEmpty()) {
                            StatRow("بدهی ثبت نشده", 0L.afn())
                        } else {
                            sheet.liabilities.forEach { StatRow(it.label, it.amount.afn()) }
                        }
                        StatRow("جمعِ بدهی‌ها", sheet.totalLiabilities.afn(), strong = true)

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "سرمایه",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatRow("سرمایهٔ اولیه", sheet.capital.afn())
                        StatRow(
                            "سودِ انباشته",
                            sheet.retained.afn(),
                            color = if (sheet.retained >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        StatRow("جمعِ سرمایه", sheet.totalEquity.afn(), strong = true)

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            if (sheet.balanced)
                                "✓ ترازنامه متوازن است — دارایی = بدهی + سرمایه"
                            else
                                "⚠ ترازنامه متوازن نیست — دفترها را بررسی کنید",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (sheet.balanced) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ---------- حسابداری دوطرفه: تراز آزمایشی ----------
            if (tb.hasData) {
                item {
                    SectionCard("حسابداری دوطرفه — تراز آزمایشی") {
                        Text(
                            if (tb.balanced)
                                "✓ دفترها تراز است — جمع هر طرف: ${tb.totalDebit.afn()}"
                            else
                                "⚠ عدم تراز! بدهکار ${tb.totalDebit.afn()} ≠ بستانکار ${tb.totalCredit.afn()}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (tb.balanced) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        tb.rows.forEach { row ->
                            StatRow(
                                "${row.label} (${row.code})",
                                row.shown.afn(),
                                color = if (row.shown < 0) MaterialTheme.colorScheme.error else null
                            )
                        }
                    }
                }
            }

            if (r.orderProfits.isNotEmpty()) {
                item {
                    Text(
                        "سود هر سفارش (برآوردی)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(r.orderProfits, key = { it.code }) { op ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    op.title.ifBlank { op.code },
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    op.profit.afn(),
                                    fontWeight = FontWeight.Bold,
                                    color = if (op.profit >= 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                "قیمت: ${op.agreed.afn()} • بهای تمام‌شده: ${op.cost.afn()} • ${PersianDate.short(op.at)}",
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
