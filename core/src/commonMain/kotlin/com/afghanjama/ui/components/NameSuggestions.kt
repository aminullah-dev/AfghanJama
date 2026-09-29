@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afghanjama.util.NameMatch

/**
 * نام‌های موجودِ نزدیک به آنچه تایپ می‌شود — زیرِ همان خانهٔ نام.
 *
 * **چرا.** بی این، کاربر نمی‌داند «حاجی نصیر» از قبل هست و دوباره ثبتش
 * می‌کند؛ حسابِ یک نفر دو تکه می‌شود. از حرفِ اول، نام‌های نزدیک دیده
 * می‌شوند و با یک لمس انتخاب می‌شوند.
 *
 * اگر همین نام (با هر شکلِ نوشتنِ ی و ک و نیم‌فاصله) از قبل باشد، صریح
 * می‌گوید — [existsNote] متنِ آن است؛ `null` یعنی گفتنش لازم نیست (مثلاً
 * جایی که انتخابِ نامِ موجود همان کارِ درست است).
 *
 * چیزی نمی‌کشد وقتی نه پیشنهادی هست نه نامِ یکسانی — یعنی جا نمی‌گیرد.
 */
@Composable
fun NameSuggestions(
    query: String,
    names: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    existsNote: ((String) -> String)? = { "«$it» از قبل ثبت است — همان را انتخاب کنید." }
) {
    val picks = remember(query, names) { NameMatch.suggest(query, names) }
    val exact = remember(query, names) { NameMatch.exact(query, names) }
    // نامی که عیناً همان است که نوشته شده، پیشنهاد نمی‌شود: چیزی
    // برای انتخاب نمانده.
    val shown = picks.filterNot { it == query.trim() }
    if (shown.isEmpty() && (exact == null || existsNote == null)) return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (exact != null && existsNote != null) {
            Text(
                existsNote(exact),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (shown.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { n ->
                    AssistChip(onClick = { onPick(n) }, label = { Text(n) })
                }
            }
        }
    }
}
