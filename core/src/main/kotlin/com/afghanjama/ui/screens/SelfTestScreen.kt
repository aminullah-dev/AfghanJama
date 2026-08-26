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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.AppInfo
import com.afghanjama.selftest.CheckResult
import com.afghanjama.selftest.CheckStatus
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.SelfTestViewModel

/**
 * خودآزمایی و سلامتِ داده — از تنظیمات باز می‌شود.
 *
 * دو کار می‌کند: ریاضیِ پول و کارنامه را می‌سنجد (که همیشه یک جواب دارد)،
 * و دفترِ واقعیِ همین گوشی را وارسی می‌کند. **هیچ چیزی نمی‌نویسد** — نه
 * سفارشِ آزمایشی می‌سازد نه سندی ثبت می‌کند، پس اجرا کردنش روی دادهٔ
 * واقعیِ کارگاه بی‌خطر است و هر بار می‌شود دوباره زد.
 */
@Composable
fun SelfTestScreen(
    vm: SelfTestViewModel,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val system = LocalSystemActions.current

    LaunchedEffect(Unit) { if (ui.results.isEmpty()) vm.run() }

    AppScreen(
        title = "خودآزمایی",
        onBack = onBack,
        actions = {
            if (ui.results.isNotEmpty()) {
                IconButton(onClick = {
                    system.shareText("اشتراک نتیجهٔ خودآزمایی", reportText(ui.results, ui.ranAt))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "اشتراک نتیجه")
                }
            }
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
                        containerColor = when {
                            ui.running -> MaterialTheme.colorScheme.surfaceVariant
                            ui.results.isEmpty() -> MaterialTheme.colorScheme.surfaceVariant
                            ui.allGood -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (ui.running) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("در حال بررسی…", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Text(
                                if (ui.allGood) "همه‌چیز درست است"
                                else "${ui.failed.fa()} مورد ایراد دارد",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${ui.passed.fa()} قبول • ${ui.failed.fa()} رد" +
                                    if (ui.skipped > 0) " • ${ui.skipped.fa()} بی‌داده" else "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (ui.ranAt > 0) {
                                Text(
                                    "آخرین اجرا: ${PersianDate.shortWithTime(ui.ranAt)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Text(
                            "این بررسی هیچ چیزی در دفتر نمی‌نویسد؛ فقط می‌خواند و می‌سنجد.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { vm.run() },
                    enabled = !ui.running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("اجرای دوباره")
                }
            }

            ui.results.groupBy { it.group }.forEach { (group, rows) ->
                item {
                    Text(
                        group,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                // کلید از شمارهٔ ردیف می‌آید، نه از نامش.
                // با نام، دو بررسیِ هم‌نام کلیدِ تکراری می‌ساختند و
                // LazyColumn کلِ اپ را می‌انداخت — صفحه‌ای که کارش گزارشِ
                // خرابی است، خودش نباید خرابی بسازد.
                itemsIndexed(rows) { i, r -> ResultRow(r) }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun ResultRow(r: CheckResult) {
    val (icon, tint) = when (r.status) {
        CheckStatus.PASS -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        CheckStatus.FAIL -> Icons.Default.Cancel to MaterialTheme.colorScheme.error
        CheckStatus.SKIP -> Icons.Default.RemoveCircleOutline to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (r.status == CheckStatus.FAIL)
                MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (r.status == CheckStatus.FAIL) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.name, style = MaterialTheme.typography.bodyMedium)
                if (r.detail.isNotBlank()) {
                    Text(
                        r.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (r.status == CheckStatus.FAIL)
                            MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** متنِ قابلِ فرستادن — تا اگر چیزی رد شد بتوانید همان را بفرستید. */
private fun reportText(results: List<CheckResult>, ranAt: Long): String = buildString {
    val failed = results.count { it.status == CheckStatus.FAIL }
    appendLine("نتیجهٔ خودآزمایی ${AppInfo.NAME}")
    appendLine("تاریخ: ${PersianDate.shortWithTime(if (ranAt > 0) ranAt else System.currentTimeMillis())}")
    appendLine("قبول: ${results.count { it.status == CheckStatus.PASS }} • رد: $failed")
    appendLine("──────────────")
    results.groupBy { it.group }.forEach { (group, rows) ->
        appendLine("[$group]")
        rows.forEach { r ->
            val mark = when (r.status) {
                CheckStatus.PASS -> "OK  "
                CheckStatus.FAIL -> "RED "
                CheckStatus.SKIP -> "--  "
            }
            appendLine("$mark${r.name}${if (r.detail.isBlank()) "" else " — ${r.detail}"}")
        }
        appendLine()
    }
}
