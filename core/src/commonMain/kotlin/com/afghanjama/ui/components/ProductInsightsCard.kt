package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.ProductInsights
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa

/** نام + سایز، همان‌طور که روی قفسه نوشته می‌شود. */
private fun ProductInsights.Row.label(): String = if (size.isBlank()) name else "$name • $size"

/** یک سطرِ «دوباره بدوز» به زبانِ کارگاه. */
fun restockLine(r: ProductInsights.Row): String = when {
    r.qty < 0 ->
        "${r.label()}: ${(-r.qty).fa()} عدد کسری (مشتری منتظر است) — ${r.suggestedMake.fa()} عدد بدوزید"
    else ->
        "${r.label()}: ${r.sold30.fa()} فروش در ۳۰ روز، ${r.qty.fa()} مانده" +
            (r.coverDays?.let { " (حدودِ ${it.fa()} روز)" } ?: "") +
            " — ${r.suggestedMake.fa()} عدد بدوزید"
}

/** یک سطرِ «راکد». */
fun deadLine(r: ProductInsights.Row): String =
    "${r.label()}: ${r.qty.fa()} عدد، ${r.idleDays.fa()} روز بی‌فروش — ${r.value.afn()} خوابیده"

/** متنِ فهرستِ دوخت برای فرستادن به سرخیاط. */
fun restockShareText(s: ProductInsights.Summary, shopName: String): String = buildString {
    appendLine("🧵 پیشنهادِ دوخت — $shopName")
    appendLine("از روی فروشِ ۳۰ روزِ اخیر:")
    s.restock.forEach { appendLine("• " + restockLine(it)) }
}.trimEnd()

/**
 * «هوشمند» — بالای انبارِ محصول.
 *
 * بسته شروع می‌شود و فقط یک خط می‌گوید؛ کارتِ همیشه‌باز بالای فهرستی که
 * کاربر برای فروش آمده، راه را می‌بندد. پنج قلم از هر دسته بس است —
 * فهرستِ بلندتر یعنی هیچ‌کدام خوانده نمی‌شود.
 */
@Composable
fun ProductInsightsCard(
    s: ProductInsights.Summary,
    onShareRestock: () -> Unit,
) {
    if (s.isEmpty) return
    var open by rememberSaveable { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    AppCard(onClick = { open = !open }) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = cs.primary)
            Column(Modifier.weight(1f)) {
                Text("پیشنهادِ هوشمند", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        s.restock.size.takeIf { it > 0 }?.let { "${it.fa()} طرح را دوباره بدوزید" },
                        s.dead.size.takeIf { it > 0 }?.let { "${it.fa()} کالای راکد (${s.deadValue.afn()})" },
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Icon(
                if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (open) "بستن" else "باز کردن",
            )
        }
        if (open) {
            if (s.restock.isNotEmpty()) {
                HorizontalDivider(thickness = 0.5.dp)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "دوباره بدوزید — پیش از آنکه مشتری دست‌خالی برگردد",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onShareRestock) {
                        Icon(Icons.Default.Share, contentDescription = "فرستادنِ فهرستِ دوخت")
                    }
                }
                s.restock.take(MAX_ROWS).forEach {
                    Text("• " + restockLine(it), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (s.dead.isNotEmpty()) {
                HorizontalDivider(thickness = 0.5.dp)
                Text(
                    "راکد — ${ProductInsights.DEAD_DAYS.fa()} روز بی‌فروش؛ تخفیف یا فروشِ یک‌جا را بسنجید",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                s.dead.take(MAX_ROWS).forEach {
                    Text("• " + deadLine(it), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

private const val MAX_ROWS = 5
