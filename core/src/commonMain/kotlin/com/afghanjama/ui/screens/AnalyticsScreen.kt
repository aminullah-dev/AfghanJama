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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.PartyProfit
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.AnalyticsViewModel

/**
 * «تحلیل» — سود به‌تفکیکِ مشتری و خیاط، که گزارش‌ها (محصول/سفارش) ندارند.
 * درآمد و بها از خودِ سفارش؛ فقط سفارشِ قیمت‌خورده. سودِ خیاط به نسبتِ
 * دستِ کارش شکسته می‌شود. چیزی که ERPهای پیشرفته دارند.
 */
@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val range by vm.range.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تحلیل") },
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(range.presetDays == null && !range.isCustom, { vm.setPeriod(null) }, label = { Text("همه") })
                        FilterChip(range.presetDays == 30, { vm.setPeriod(30) }, label = { Text("۳۰ روز") })
                        FilterChip(range.presetDays == 90, { vm.setPeriod(90) }, label = { Text("۹۰ روز") })
                        FilterChip(range.presetDays == 365, { vm.setPeriod(365) }, label = { Text("یک سال") })
                    }
                    Text(
                        "بازه: ${range.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!ui.hasData) {
                item {
                    AnalyticsCard("هنوز داده‌ای نیست") {
                        Text(
                            "در این بازه سفارشِ قیمت‌خورده‌ای نیست. وقتی سفارش‌ها قیمت بخورند، سودِ هر مشتری و هر خیاط این‌جا می‌آید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@LazyColumn
            }

            // خلاصه: درآمد، سود، حاشیه، تعداد سفارش — و بهترین مشتری/خیاط.
            item {
                AnalyticsCard("خلاصهٔ این بازه") {
                    AnalyticsStat("درآمدِ سفارش‌ها", ui.revenue.afn())
                    AnalyticsStat(
                        "سود",
                        ui.profit.afn() + (ui.marginPercent?.let { " • حاشیه ${it.fa()}٪" } ?: ""),
                        strong = true,
                        color = if (ui.profit < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    AnalyticsStat("تعداد سفارشِ قیمت‌خورده", ui.orderCount.fa())
                    ui.bestCustomer?.let { AnalyticsStat("ارزشمندترین مشتری", "${it.name} (${it.profit.afn()})") }
                    ui.bestTailor?.let { AnalyticsStat("سودآورترین خیاط", "${it.name} (${it.profit.afn()})") }
                }
            }

            item {
                AnalyticsCard("ارزشمندترین مشتری‌ها") {
                    RankedList(ui.customers, countLabel = "سفارش")
                }
            }

            item {
                AnalyticsCard("سودآورترین خیاط‌ها") {
                    RankedList(ui.tailors, countLabel = "کار")
                }
            }
        }
    }
}

@Composable
private fun RankedList(rows: List<PartyProfit.Row>, countLabel: String) {
    if (rows.isEmpty()) {
        Text(
            "در این بازه چیزی نیست.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    // طولِ میله فقط درآمد را نشان می‌دهد (یک مقیاس، یک رنگ)؛ سود با واژه و
    // رنگ در زیر می‌آید تا بدونِ تشخیصِ رنگ هم خوانا باشد.
    val maxRevenue = (rows.maxOfOrNull { it.revenue } ?: 0L).coerceAtLeast(1L)
    rows.take(10).forEach { r ->
        val loss = r.profit < 0
        RankBar(
            label = "${r.name} • ${r.count.fa()} $countLabel",
            valueText = r.revenue.afn(),
            fraction = r.revenue.toFloat() / maxRevenue,
            subText = (if (loss) "ضرر " else "سود ") +
                kotlin.math.abs(r.profit).afn() +
                (r.marginPercent?.let { " • حاشیه ${kotlin.math.abs(it).fa()}٪" } ?: "") +
                " • ${r.qty.fa()} عدد",
            subColor = if (loss) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnalyticsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun AnalyticsStat(label: String, value: String, strong: Boolean = false, color: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RankBar(label: String, valueText: String, fraction: Float, subText: String, subColor: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(8.dp)
                        .clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Text(subText, style = MaterialTheme.typography.labelSmall, color = subColor)
    }
}
