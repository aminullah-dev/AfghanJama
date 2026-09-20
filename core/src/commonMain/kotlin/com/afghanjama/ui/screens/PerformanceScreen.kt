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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.afghanjama.ui.vm.PerformanceViewModel
import com.afghanjama.ui.vm.TailorScore

/**
 * کارنامهٔ کارکنانِ تولید: چه کسی چقدر کار تحویل داده، با چه کیفیتی و
 * چقدر برگشت خورده. ستاره همیشه کنارِ عددهایی می‌آید که از آن‌ها ساخته شده.
 */
@Composable
fun PerformanceScreen(
    vm: PerformanceViewModel,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کارنامهٔ کارکنان") },
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
            if (!ui.hasData) {
                item {
                    EmptyState(
                        icon = Icons.Default.ContentCut,
                        title = "هنوز کارنامه‌ای ساخته نشده",
                        hint = "با اولین تحویلِ کار به خیاط و اولین نظارت، " +
                            "کارنامه خودش پر می‌شود — چیزی لازم نیست وارد کنید."
                    )
                }
            }

            // ---------- خیاطان ----------
            if (ui.tailors.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "خیاطان",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        ui.topTailor?.let { top ->
                            Text(
                                "بهترین کارنامه: ${top.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            "ستاره از دو چیز ساخته می‌شود: کیفیتِ ثبت‌شدهٔ تحویل‌ها و " +
                                "نرخِ برگشت از نظارت. هر دو زیرِ هر نام نوشته شده‌اند.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(ui.tailors, key = { "t-${it.name}" }) { t -> TailorCard(t) }
            }

            // ---------- ناظران ----------
            if (ui.inspectors.isNotEmpty()) {
                item {
                    Column(
                        Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "ناظران",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "برای ناظر عمداً ستاره نمی‌گذاریم: ناظری که بیشتر برگشت می‌زند " +
                                "لزوماً بدتر نیست، شاید سخت‌گیرتر است.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(ui.inspectors, key = { "i-${it.name}" }) { i ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(i.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${i.total.fa()} بازرسی • ${i.approved.fa()} تأیید • ${i.rejected.fa()} برگشت",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "نرخ برگشت: ${i.rejectPercent.fa()}٪".toPersianDigits(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (ui.sharedOrdersExcluded > 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "${ui.sharedOrdersExcluded.fa()} برگشت بدونِ نامِ خیاط مانده",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "این سفارش‌ها بینِ چند خیاط تقسیم شده بودند و ناظر ثبت نکرده " +
                                    "کارِ کدام‌شان برگشت خورده، پس در نرخِ برگشتِ هیچ‌کس حساب نشدند.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "از این به بعد در صفحهٔ «نظارت / بازرسی»، هنگامِ برگشتِ یک سفارشِ " +
                                    "چندخیاطه، نامِ خیاط پرسیده می‌شود؛ با انتخابِ آن، برگشت در " +
                                    "کارنامهٔ همان خیاط ثبت می‌شود و دیگر چیزی کنار گذاشته نمی‌شود.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TailorCard(t: TailorScore) {
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
                Text(t.name, fontWeight = FontWeight.SemiBold)
                if (t.stars > 0) {
                    Text(
                        "★".repeat(t.stars) + "☆".repeat(5 - t.stars),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        "بدون امتیاز",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                "${t.deliveries.fa()} تحویل • ${t.pieces.fa()} عدد • کارمزد ${t.wages.afn()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (t.inProgress > 0) {
                Text(
                    "${t.inProgress.fa()} کار زیرِ دست",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (t.avgHours > 0) {
                Text(
                    "میانگین زمانِ تحویل: ${t.avgHours.fa()} ساعت",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                t.qualityScore?.let {
                    "کیفیت: ${it.fa()} از ۱۰۰ (${t.good.fa()} خوب، ${t.fair.fa()} متوسط، ${t.poor.fa()} ضعیف)"
                        .toPersianDigits()
                } ?: "کیفیت: هنوز ثبت نشده",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                t.rejectPercent?.let {
                    "برگشت از نظارت: ${it.fa()}٪ (${t.rejectedOrders.fa()} از ${t.judgedOrders.fa()} سفارش)"
                        .toPersianDigits()
                } ?: "برگشت از نظارت: سفارشِ قابلِ سنجش ندارد",
                style = MaterialTheme.typography.labelSmall,
                color = if ((t.rejectPercent ?: 0) > 25) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
