package com.afghanjama.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.FinancialHealth
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa

/** برچسبِ هر سطح — کوتاه، چون روی نشانِ کوچک می‌نشیند. */
fun levelLabel(l: FinancialHealth.Level): String = when (l) {
    FinancialHealth.Level.GOOD -> "سالم"
    FinancialHealth.Level.WATCH -> "زیرِ نظر"
    FinancialHealth.Level.RISK -> "خطر"
}

/**
 * متنِ هر نشانه — یک جا، تا کارتِ «وضعیت مالی»، متنِ اشتراک و «مرکزِ
 * هشدار» یک حرف بزنند.
 */
fun signalText(s: FinancialHealth.Signal): String = when (s.kind) {
    FinancialHealth.Kind.NET_NEGATIVE ->
        "بدهی‌ها ${s.value.afn()} از همهٔ دارایی بیشتر است."
    FinancialHealth.Kind.DEBT_OVER_CASH_AND_RECEIVABLE ->
        "حتی با وصولِ همهٔ طلب‌ها، ${s.value.afn()} از بدهی‌ها بی‌پول می‌ماند."
    FinancialHealth.Kind.DEBT_OVER_CASH ->
        "نقد ${s.value.afn()} از بدهی‌ها کمتر است — وصولِ طلب لازم است."
    FinancialHealth.Kind.COVER_SHORT ->
        "نقدِ موجود فقط هزینهٔ ${s.value.fa()} روزِ کارگاه را می‌دهد."
    FinancialHealth.Kind.COVER_LOW ->
        "نقدِ موجود هزینهٔ ${s.value.fa()} روز را می‌دهد — کمتر از یک ماه."
    FinancialHealth.Kind.SALES_DROP ->
        "فروشِ ۳۰ روزِ اخیر ${s.value.fa()}٪ کمتر از ۳۰ روزِ پیش از آن است."
    FinancialHealth.Kind.SALES_RISE ->
        "فروشِ ۳۰ روزِ اخیر ${s.value.fa()}٪ بیشتر از ۳۰ روزِ پیش از آن است."
}

/** متنِ آمادهٔ فرستادن — برای شریک یا حسابدار، در واتس‌اپ. */
fun financialStatusText(h: FinancialHealth.Snapshot, shopName: String, date: String): String =
    buildString {
        val f = h.figures
        appendLine("📊 وضعیت مالی — $shopName")
        appendLine("تاریخ: $date • وضعیت: ${levelLabel(h.level)}")
        appendLine("──────────────")
        appendLine("نقد (صندوق، بانک، فایده): ${f.cash.afn()}")
        appendLine("طلب از دیگران: ${f.receivable.afn()}")
        appendLine("موجودیِ کالا: ${f.stock.afn()}")
        appendLine("بدهی‌ها: ${f.debts.afn()}")
        appendLine("خالصِ دارایی: ${h.netWorth.afn()}")
        appendLine("──────────────")
        appendLine("فروشِ ۳۰ روز: ${f.sales30.afn()}")
        appendLine("هزینهٔ ۳۰ روز: ${f.expense30.afn()}")
        h.coverDays?.let { appendLine("نقد، هزینهٔ ${it.fa()} روز را می‌دهد.") }
        h.signals.forEach { appendLine("• ${signalText(it)}") }
    }.trimEnd()

/**
 * «وضعیت مالی» — بالای داشبوردِ مالی.
 *
 * چهار عدد که کارفرما را شب بیدار نگه می‌دارند (نقد، طلب، کالا، بدهی)،
 * یک جمع، و یک جملهٔ صریح دربارهٔ هر چیزی که باید دیده شود.
 */
@Composable
fun FinancialStatusCard(
    h: FinancialHealth.Snapshot,
    onShare: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val (chipBg, chipFg) = when (h.level) {
        FinancialHealth.Level.GOOD -> cs.primaryContainer to cs.onPrimaryContainer
        FinancialHealth.Level.WATCH -> cs.tertiaryContainer to cs.onTertiaryContainer
        FinancialHealth.Level.RISK -> cs.errorContainer to cs.onErrorContainer
    }
    AppCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "وضعیت مالی",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                levelLabel(h.level),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = chipFg,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(chipBg)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "فرستادنِ وضعیت مالی")
            }
        }

        val f = h.figures
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure("نقد (صندوق‌ها و بانک)", f.cash, cs.primary, Modifier.weight(1f))
            Figure("طلب از دیگران", f.receivable, cs.onSurface, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure("موجودیِ کالا", f.stock, cs.onSurface, Modifier.weight(1f))
            Figure("بدهی‌ها", f.debts, if (f.debts > 0) cs.error else cs.onSurface, Modifier.weight(1f))
        }
        HorizontalDivider(thickness = 0.5.dp)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "خالصِ دارایی",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            Text(
                h.netWorth.afn(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (h.netWorth < 0) cs.error else cs.onSurface,
            )
        }
        h.coverDays?.let {
            Text(
                "نقد، هزینهٔ ${it.fa()} روزِ کارگاه را می‌دهد (از روی هزینهٔ ۳۰ روزِ اخیر).",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
        }
        h.salesChangePct?.let { pct ->
            val up = pct >= 0
            Text(
                (if (up) "▲ " else "▼ ") + "فروش ${kotlin.math.abs(pct).fa()}٪ " +
                    (if (up) "بیشتر" else "کمتر") + " از ۳۰ روزِ قبل",
                style = MaterialTheme.typography.labelMedium,
                color = if (up) cs.primary else cs.error,
            )
        }
        h.signals.filter { it.level != FinancialHealth.Level.GOOD }.forEach { sig ->
            Text(
                "• " + signalText(sig),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (sig.level == FinancialHealth.Level.RISK) cs.error else cs.onSurface,
            )
        }
    }
}

@Composable
private fun Figure(label: String, amount: Long, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            amount.afn(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
