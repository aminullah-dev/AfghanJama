package com.afghanjama.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.afghanjama.desktop.data.dataDir
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.components.AppVersionCard

/**
 * «دربارهٔ خیاط‌یار» روی مک و ویندوز.
 *
 * کارتِ نسخه همان کارتِ گوشی است ([AppVersionCard] از `:core`). اینجا فقط
 * دو چیزِ مخصوصِ کامپیوتر کنارش می‌آید: کدام سکو، و دفتر کجاست — دو
 * سؤالی که پشتیبانی بعد از «کدام نسخه؟» می‌پرسد.
 */
@Composable
internal fun AboutSection(onBack: () -> Unit) {
    AppScreen(title = "دربارهٔ خیاط‌یار", onBack = onBack) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppVersionCard()

            AppCard {
                Text(
                    platformName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "دفتر اینجاست:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // مسیر در سطرِ خودش و چپ‌به‌راست: در پاراگرافِ راست‌به‌چپ،
                // `/`ِ اولش به آن سرِ سطر می‌پرید و مسیر غلط خوانده می‌شد
                // (در `screenSmoke` دیده شد). `End` در متنِ چپ‌به‌راست یعنی
                // راست، کنارِ بقیهٔ صفحه.
                Text(
                    dataDir().absolutePath,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
