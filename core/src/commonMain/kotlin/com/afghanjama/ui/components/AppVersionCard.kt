package com.afghanjama.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.afghanjama.AppInfo
import com.afghanjama.AppVersion

/**
 * «دربارهٔ خیاط‌یار» — نسخه، شمارهٔ ساخت، کامیت و کانال.
 *
 * یک کارت برای هر سه سکو: تنظیماتِ اندروید، تنظیماتِ آیفون، و بخشِ
 * «دربارهٔ خیاط‌یار» در نوارِ کنارِ مک و ویندوز. اگر هر سکو متنِ خودش را
 * می‌نوشت، دوباره همان چیزی می‌شد که ۱.۹.۱ برایش آمد: سه جا، سه حرف.
 *
 * سطرِ لاتینِ آخر انتخاب‌شدنی است تا کارفرما بتواند کپی‌اش کند و برای
 * پشتیبانی بفرستد.
 */
@Composable
fun AppVersionCard(modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Text(
            "${AppInfo.NAME} — ${AppVersion.title()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            AppVersion.detail(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            AppVersion.channelNote(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (AppVersion.isRelease) FontWeight.Normal else FontWeight.SemiBold,
            color = if (AppVersion.isRelease) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
        SelectionContainer {
            Text(
                AppVersion.supportLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "وقتی پشتیبانی پرسید کدام نسخه را دارید، سطرِ بالا را بفرستید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
