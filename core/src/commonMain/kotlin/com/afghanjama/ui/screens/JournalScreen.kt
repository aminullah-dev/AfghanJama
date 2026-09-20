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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.Accounts
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.JournalRow
import com.afghanjama.ui.vm.JournalViewModel

/**
 * دفتر روزنامه — پشتِ هر عددِ مالیِ اپ.
 *
 * **چرا این صفحه هست.** ژورنال از روزِ اول نوشته می‌شد و هیچ‌جا دیده
 * نمی‌شد. وقتی عددی در گزارش‌ها باورنکردنی به نظر می‌رسید، هیچ راهی
 * در خودِ اپ نبود که ببینی از کجا آمده — و یک بار همین شد: پولی که
 * در صندوق گذاشته می‌شد به‌جای سرمایه، درآمد ثبت می‌شد و سودِ
 * گزارش‌شده را از منفی به مثبت می‌بُرد.
 *
 * فقط خواندنی است. اصلاحِ سند از راهِ خودش انجام می‌شود، نه با دست
 * بردن در دفتر — دفتری که بشود دستی عوضش کرد، دیگر سند نیست.
 */
@Composable
fun JournalScreen(
    vm: JournalViewModel,
    onBack: () -> Unit
) {
    val rows by vm.rows.collectAsState()
    var query by remember { mutableStateOf("") }

    val shown = rows.filter { JournalViewModel.matches(it, query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دفتر روزنامه") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        if (rows.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "هنوز سندی ثبت نشده. با هر خرید، فروش، کارمزد و تسویه یک سندِ " +
                        "دوطرفه اینجا نوشته می‌شود.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("جستجوی شرح، نوع یا نام حساب") },
                        singleLine = true,
                        trailingIcon = if (query.isNotBlank()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "پاک کردنِ جستجو")
                                }
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "۲۰۰ سندِ آخر. هر سند دو طرف دارد و جمعِ بدهکار و بستانکارش " +
                            "همیشه برابر است.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (shown.isEmpty()) {
                item {
                    Text(
                        "سندی با «${query.trim()}» پیدا نشد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(shown, key = { it.entry.id }) { row -> JournalCard(row) }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

@Composable
private fun JournalCard(row: JournalRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            // سندِ نامتراز نباید ممکن باشد؛ اگر شد، باید در نگاهِ اول
            // دیده شود نه اینکه لای دویست سند گم شود.
            if (row.balanced) MaterialTheme.colorScheme.outlineVariant
            else MaterialTheme.colorScheme.error
        )
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(row.entry.memo, fontWeight = FontWeight.SemiBold)
            Text(
                PersianDate.shortWithTime(row.entry.at) +
                    (row.entry.refType.takeIf { it.isNotBlank() }?.let { " • $it" } ?: "") +
                    (row.entry.refId.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            row.lines.forEach { line ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Accounts.label(line.account),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            line.account,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // بدهکار و بستانکار در دو ستون نمی‌نشینند چون روی
                    // گوشه‌ی گوشی جا نمی‌شوند؛ به‌جایش برچسب می‌گیرند.
                    if (line.debit > 0) {
                        Text(
                            "بدهکار ${line.debit.afn()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "بستانکار ${line.credit.afn()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            if (!row.balanced) {
                Text(
                    "این سند تراز نیست: بدهکار ${row.debit.afn()} و بستانکار " +
                        "${row.credit.afn()}. با ${row.lines.size.fa()} سطر ثبت شده.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
