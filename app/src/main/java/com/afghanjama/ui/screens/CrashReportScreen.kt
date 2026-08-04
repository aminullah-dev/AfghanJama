package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * صفحه‌ای که بعد از یک خرابی نشان داده می‌شود.
 *
 * **چرا در `:app` ماند و به `:core` نرفت:** این صفحه باید حتی وقتی
 * چیزهای مشترک خرابند بالا بیاید. هرچه کمتر به کدِ دیگری تکیه کند،
 * احتمالِ اینکه خودش هم بترکد کمتر است. به همین دلیل هم آیکون ندارد و
 * هیچ ViewModelی نمی‌گیرد.
 */
@Composable
fun CrashReportScreen(
    report: String,
    onSend: () -> Unit,
    onContinue: () -> Unit
) {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "اپ بارِ قبل بسته شد",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "متنِ زیر علتِ بسته شدن را می‌گوید. لطفاً دکمهٔ " +
                    "«فرستادنِ گزارش» را بزنید و آن را برای سازنده بفرستید — " +
                    "بدونِ این متن، علت فقط حدس زده می‌شود.\n\n" +
                    "داده‌های کارگاه دست‌نخورده‌اند؛ این فقط متنِ خطاست.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = report,
                    // تکْ‌عرض چون این متن **کد** است نه جمله؛ با قلمِ
                    // معمولی خط‌های پشته به هم می‌ریزند و خواندنش سخت
                    // می‌شود.
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = onSend, modifier = Modifier.weight(1f)) {
                    Text("فرستادنِ گزارش")
                }
                OutlinedButton(onClick = onContinue, modifier = Modifier.weight(1f)) {
                    Text("ادامه")
                }
            }
        }
    }
}
