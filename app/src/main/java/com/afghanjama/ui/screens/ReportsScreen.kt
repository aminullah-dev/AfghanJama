@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.ReportsViewModel

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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

@Composable
fun ReportsScreen(
    vm: ReportsViewModel,
    onBack: () -> Unit
) {
    val r by vm.report.collectAsState()
    val period by vm.period.collectAsState()
    val tb by vm.trialBalance.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("گزارش‌ها") },
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
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(period == null, { vm.setPeriod(null) }, label = { Text("همه") })
                    FilterChip(period == 30, { vm.setPeriod(30) }, label = { Text("۳۰ روز") })
                    FilterChip(period == 7, { vm.setPeriod(7) }, label = { Text("۷ روز") })
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
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
