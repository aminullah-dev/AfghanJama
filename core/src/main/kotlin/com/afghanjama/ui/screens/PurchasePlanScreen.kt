@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.EmptyState
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.BURN_WINDOW_DAYS
import com.afghanjama.ui.vm.PurchasePlanViewModel
import com.afghanjama.ui.vm.ReorderRow
import com.afghanjama.ui.vm.TARGET_COVER_DAYS

/** عددِ اعشاری کوتاه و فارسی — مثلاً «۱۲٫۵». */
private fun Double.qty(): String {
    val r = Math.round(this * 100.0) / 100.0
    val s = if (r == r.toLong().toDouble()) r.toLong().toString()
    else r.toString().trimEnd('0').trimEnd('.')
    return s.replace(".", "٫").toPersianDigits()
}

/**
 * پیشنهادِ خرید: چه چیزی، چقدر و چرا — بر اساسِ نرخِ مصرفِ واقعی،
 * نه فقط «موجودی کم است».
 */
@Composable
fun PurchasePlanScreen(
    vm: PurchasePlanViewModel,
    onGoProcurement: () -> Unit,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پیشنهاد خرید") },
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ui.runningOut.isNotEmpty())
                            MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val onColor = if (ui.runningOut.isNotEmpty())
                            MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                        Text(
                            if (ui.needed.isEmpty()) "فعلاً خریدی لازم نیست"
                            else "${ui.needed.size.fa()} قلم نیاز به خرید دارد",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onColor
                        )
                        if (ui.needed.isNotEmpty()) {
                            Text(
                                "برآوردِ هزینه: ${ui.totalCost.afn()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onColor
                            )
                        }
                        if (ui.runningOut.isNotEmpty()) {
                            Text(
                                "${ui.runningOut.size.fa()} قلم تا یک هفتهٔ دیگر تمام می‌شود",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = onColor
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "محاسبه بر اساسِ مصرفِ واقعیِ ${BURN_WINDOW_DAYS.fa()} روزِ اخیر است " +
                        "و هدف این است که موجودی برای ${TARGET_COVER_DAYS.fa()} روز کفایت کند. " +
                        "اصلاحِ دستیِ موجودی جزء مصرف حساب نمی‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!ui.hasData) {
                item {
                    EmptyState(
                        icon = Icons.Default.Inventory2,
                        title = "انبار مواد خالی است",
                        hint = "اول از بخش «خرید مواد» اقلام را وارد کنید تا " +
                            "مصرف‌شان ثبت شود و پیشنهاد خرید ساخته شود."
                    )
                }
            }

            items(ui.rows, key = { "${it.name}|${it.unit}" }) { r -> ReorderCard(r) }

            if (ui.needed.isNotEmpty()) {
                item {
                    Button(onClick = onGoProcurement, modifier = Modifier.fillMaxWidth()) {
                        Text("رفتن به خرید مواد")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderCard(r: ReorderRow) {
    val urgent = r.runsOutSoon
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(r.name, fontWeight = FontWeight.SemiBold)
                Text(
                    r.daysOfCover?.let {
                        if (urgent) "${it.fa()} روز باقی" else "${it.fa()} روز باقی"
                    } ?: "بدون مصرف",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (urgent) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "موجودی: ${r.stock.qty()} ${r.unit}" +
                    (if (r.minLevel > 0) " • حد هشدار: ${r.minLevel.qty()}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (r.consumed > 0)
                    "مصرفِ ${BURN_WINDOW_DAYS.fa()} روز: ${r.consumed.qty()} ${r.unit} " +
                        "(روزانه ${r.dailyRate.qty()})"
                else "در ${BURN_WINDOW_DAYS.fa()} روزِ اخیر مصرف نشده",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (r.needsPurchase) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "پیشنهاد خرید: ${r.suggestedQty.qty()} ${r.unit}" +
                        (if (r.estimatedCost > 0) " ≈ ${r.estimatedCost.afn()}" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
