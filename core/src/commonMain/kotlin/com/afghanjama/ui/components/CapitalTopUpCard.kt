package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.vm.FinanceViewModel

/**
 * افزودنِ پولی که از بیرون وارد کارگاه می‌شود — سرمایهٔ صاحب‌کار، وامِ
 * شخصی، یا پولی که از جای دیگری آورده شده.
 *
 * بی این، موجودیِ صندوق در اپ با پولِ واقعیِ کشو نمی‌خواند و هر گزارشی
 * از همان‌جا کج می‌شود.
 *
 * **چرا اینجا و نه داخلِ صفحهٔ تنظیمات.** این کارت سال‌ها در
 * `SettingsScreen` بود، ولی آن صفحه در `:app` است و به `Intent` و
 * انتخابگرِ فایلِ اندروید وابسته — پس هرگز به ویندوز نرسید و دکمهٔ
 * «تنظیمات» آنجا تابعِ خالی بود. کارفرما روی ویندوز دنبالِ ثبتِ سرمایهٔ
 * اولیه گشت و پیدایش نکرد.
 *
 * خودِ این کارت هیچ‌چیزِ اندرویدی ندارد: فقط دو کادر و
 * [FinanceViewModel] که از روزِ اول در `:core` بوده. پس همین‌جا نشست تا
 * هر دو سکو از یک کد بخوانند و دیگر از هم نیفتند.
 */
@Composable
fun CapitalTopUpCard(financeVm: FinanceViewModel, modifier: Modifier = Modifier) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var box by remember { mutableStateOf("WALLET") }
    var done by remember { mutableStateOf(false) }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "افزودن پول به صندوق یا بانک",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "پولی که از بیرون وارد کارگاه می‌شود — سرمایه، یا پولی که خودتان گذاشته‌اید.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("WALLET" to "کیف پول", "BANK" to "بانک").forEach { (code, label) ->
                    FilterChip(
                        selected = box == code,
                        onClick = { box = code; done = false },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.digitsOnly(); done = false },
                label = { Text("مبلغ") },
                suffix = { Text("؋") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it; done = false },
                label = { Text("بابت (مثلاً سرمایهٔ اولیه)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            val value = amount.toLongOrNull() ?: 0L
            Button(
                enabled = value > 0,
                onClick = {
                    val text = note.trim().ifBlank { "افزودن دستیِ پول" }
                    if (box == "BANK") financeVm.incomeBank(value, text)
                    else financeVm.incomeWallet(value, text)
                    amount = ""
                    note = ""
                    done = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (done) "ثبت شد" else "افزودن به موجودی")
            }
        }
    }
}
