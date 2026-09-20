package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.PaymentSource
import com.afghanjama.data.entities.RecurringExpense
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.vm.RecurringExpenseViewModel

/**
 * هزینه‌های ثابتِ ماهانه — کرایه، برق، اینترنت.
 *
 * **مسئله‌ای که حل می‌کند.** این هزینه‌ها هر ماه دستی وارد می‌شدند و
 * اگر ماهی فراموش می‌شد، هیچ‌چیز خبر نمی‌داد. نتیجه‌اش این بود که سودِ
 * آن ماه بیشتر از واقعیت نشان داده می‌شد — و کارفرما بر اساسِ عددی
 * تصمیم می‌گرفت که سالم به نظر می‌رسید.
 */
@Composable
fun RecurringExpenseScreen(
    vm: RecurringExpenseViewModel,
    onBack: () -> Unit,
) {
    val rows by vm.rows.collectAsState()
    val s by vm.ui.collectAsState()
    var editing by remember { mutableStateOf<RecurringExpense?>(null) }
    var adding by remember { mutableStateOf(false) }

    // فهرست که عوض شود، «این ماه چند قلم مانده» هم باید دوباره شمرده
    // شود — وگرنه عددِ بالای صفحه با فهرستِ زیرش نمی‌خوانَد.
    LaunchedEffect(rows) { vm.refreshDue() }

    AppScreen(title = "هزینه‌های ثابت", onBack = onBack) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            s.message?.let { msg ->
                AppCard {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = vm::clearMessage) { Text("باشه") }
                }
            }

            if (s.dueCount > 0) {
                AppCard {
                    Text(
                        "${s.dueCount.fa()} قلم این ماه ثبت نشده",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "جمعاً ${s.dueTotal.afn()} — تا وقتی ثبت نشود، " +
                            "سودِ این ماه بیشتر از واقعیت نشان داده می‌شود.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = vm::postThisMonth,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("ثبتِ هزینه‌های این ماه") }
                }
            } else if (rows.isNotEmpty()) {
                AppCard {
                    Text(
                        "هزینه‌های ثابتِ این ماه ثبت شده‌اند",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            HorizontalDivider()

            if (rows.isEmpty()) {
                Text(
                    "هنوز هزینهٔ ثابتی تعریف نشده. کرایه، برق، اینترنت — " +
                        "هر چیزی که هر ماه تکرار می‌شود.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            rows.forEach { row ->
                AppCard(onClick = { editing = row }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${row.amount.afn()} • روزِ ${row.dayOfMonth.fa()} • ${row.source}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = row.enabled,
                            onCheckedChange = { vm.save(row.copy(enabled = it)) },
                        )
                        IconButton(onClick = { vm.delete(row) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  افزودنِ هزینهٔ ثابت")
            }
        }
    }

    val target = editing ?: if (adding) RecurringExpense(
        title = "", amount = 0, dayOfMonth = 1, source = PaymentSource.WALLET.name
    ) else null

    if (target != null) {
        Editor(
            row = target,
            onDismiss = { editing = null; adding = false },
            onSave = { vm.save(it); editing = null; adding = false },
        )
    }
}

/**
 * صندوق‌هایی که هزینه از آن‌ها پرداخت می‌شود.
 *
 * برچسب‌ها همان‌هایی‌اند که `FinanceHubScreen` نشان می‌دهد، تا کاربر
 * دو نام برای یک صندوق نبیند.
 */
private val boxes = listOf(
    PaymentSource.WALLET.name to "کیف پول",
    PaymentSource.BANK.name to "بانک",
    PaymentSource.PROFIT.name to "فایده",
)

@Composable
private fun Editor(
    row: RecurringExpense,
    onDismiss: () -> Unit,
    onSave: (RecurringExpense) -> Unit,
) {
    var title by remember { mutableStateOf(row.title) }
    var amount by remember { mutableStateOf(if (row.amount == 0L) "" else row.amount.toString()) }
    var day by remember { mutableStateOf(row.dayOfMonth.toString()) }
    var source by remember { mutableStateOf(row.source) }

    val amountL = amount.toLongOrNull() ?: 0L
    val dayI = day.toIntOrNull() ?: 0
    val valid = title.isNotBlank() && amountL > 0 && dayI in 1..31

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (row.id == 0L) "هزینهٔ ثابتِ تازه" else "ویرایش") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان") },
                    placeholder = { Text("مثلاً: کرایهٔ دوکان") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.digitsOnly() },
                    label = { Text("مبلغِ ماهانه") },
                    suffix = { Text("؋") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.digitsOnly() },
                    label = { Text("روزِ ماه (۱ تا ۳۱)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("از کدام صندوق؟", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // `CUSTOMER` عمداً نیست: آن «نسیه از مشتری» است و
                    // صندوقِ کارگاه نیست. کرایه را نمی‌شود از حسابِ
                    // مشتری داد.
                    boxes.forEach { (value, label) ->
                        FilterChip(
                            selected = source == value,
                            onClick = { source = value },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        row.copy(
                            title = title.trim(),
                            amount = amountL,
                            dayOfMonth = dayI.coerceIn(1, 31),
                            source = source,
                        )
                    )
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } },
    )
}
