@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.afghanjama.data.DebtFollowUp
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.components.EmptyState
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.vm.DebtFollowUpViewModel

/**
 * پیگیریِ طلب — «امروز به چه کسی زنگ بزنم؟».
 *
 * هر سطر یک نفر با **یک دلیل** است، نه جدولِ مانده‌ها. دفترِ کل مانده‌ها
 * را از قبل نشان می‌داد؛ چیزی که نبود ترتیب بود و جایی برای نوشتنِ
 * «قول داد پنج‌شنبه».
 *
 * کسی که امروز به او سر زده شد پایینِ فهرست می‌رود ولی بیرون نمی‌رود —
 * تا کارفرما ببیند کارِ امروز چقدر مانده.
 */
@Composable
fun DebtFollowUpScreen(
    vm: DebtFollowUpViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
) {
    val plan by vm.plan.collectAsState()
    val ids by vm.customerIds.collectAsState()
    val message by vm.message.collectAsState()
    val system = LocalSystemActions.current
    val shop = CompanyPrefs.name(LocalSettings.current)
    var recording by remember { mutableStateOf<DebtFollowUp.Row?>(null) }

    message?.let { msg ->
        AppAlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text("ثبت نشد") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("باشه") } }
        )
    }

    recording?.let { row ->
        OutcomeDialog(
            row = row,
            onDismiss = { recording = null },
            onPick = { outcome, days ->
                vm.record(row, outcome, days)
                recording = null
            }
        )
    }

    AppScreen(title = "پیگیریِ طلب", onBack = onBack) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (plan.today.isEmpty() && plan.later.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = "کسی به کارگاه بدهکار نیست",
                        hint = "فروشِ نسیه، «حساب قبلی» یا قسطی که روزش بگذرد، همین‌جا می‌آید."
                    )
                }
            } else {
                item {
                    AppCard {
                        Text(
                            if (plan.today.isEmpty()) "امروز کسی برای پیگیری نیست"
                            else "امروز: ${plan.today.size.fa()} نفر — ${plan.todayTotal.afn()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (plan.today.isNotEmpty()) {
                            Text(
                                if (plan.pending == 0) "به همه سر زده شد."
                                else "${plan.pending.fa()} نفر هنوز مانده.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (plan.later.isNotEmpty()) {
                            Text(
                                "بعداً: ${plan.later.size.fa()} نفر — ${plan.laterTotal.afn()} " +
                                    "(بدهیِ تازه یا قولی که روزش نرسیده)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (plan.today.isNotEmpty()) {
                item { SectionTitle("امروز") }
                items(plan.today, key = { "t_" + it.debtor.name }) { row ->
                    DebtRow(
                        row = row,
                        onCall = {
                            system.dial(row.debtor.phone)
                            // بعد از تماس، نتیجه همان‌جا پرسیده می‌شود.
                            recording = row
                        },
                        onMessage = {
                            system.shareText(
                                "یادآوری به ${row.debtor.name}",
                                DebtFollowUp.message(
                                    shop, row, row.debtor.amount.afn(), reasonDate(row)
                                )
                            )
                            vm.record(row, DebtFollowUp.Outcome.MESSAGED)
                        },
                        onRecord = { recording = row },
                        onOpen = ids[row.debtor.name.trim()]?.let { id -> { onOpenCustomer(id) } }
                    )
                }
            }

            if (plan.later.isNotEmpty()) {
                item { SectionTitle("بعداً") }
                items(plan.later, key = { "l_" + it.debtor.name }) { row ->
                    DebtRow(
                        row = row,
                        onCall = {
                            system.dial(row.debtor.phone)
                            recording = row
                        },
                        onMessage = {
                            system.shareText(
                                "یادآوری به ${row.debtor.name}",
                                DebtFollowUp.message(
                                    shop, row, row.debtor.amount.afn(), reasonDate(row)
                                )
                            )
                            vm.record(row, DebtFollowUp.Outcome.MESSAGED)
                        },
                        onRecord = { recording = row },
                        onOpen = ids[row.debtor.name.trim()]?.let { id -> { onOpenCustomer(id) } }
                    )
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** تاریخی که در متنِ پیام می‌آید: روزِ قول یا سررسیدِ قسط. */
private fun reasonDate(row: DebtFollowUp.Row): String = when (row.reason) {
    DebtFollowUp.Reason.BROKEN_PROMISE, DebtFollowUp.Reason.WAITING_PROMISE, DebtFollowUp.Reason.PROMISE_DUE ->
        row.debtor.lastContact?.until?.takeIf { it > 0L }?.let { PersianDate.short(it) }.orEmpty()
    DebtFollowUp.Reason.LATE_INSTALLMENT ->
        row.debtor.lateInstallmentDue.takeIf { it > 0L }?.let { PersianDate.short(it) }.orEmpty()
    else -> ""
}

/** جملهٔ دلیل — کوتاه، با عدد. */
private fun reasonLine(row: DebtFollowUp.Row): String {
    val d = row.days.fa()
    val date = reasonDate(row)
    return when (row.reason) {
        DebtFollowUp.Reason.BROKEN_PROMISE -> "قول داده بود تا $date — $d روز گذشته و چیزی نرسیده"
        DebtFollowUp.Reason.PROMISE_DUE -> "امروز روزِ قولش است"
        DebtFollowUp.Reason.LATE_INSTALLMENT ->
            "قسطِ ${row.debtor.lateInstallment.afn()} از $date — $d روز گذشته"
        DebtFollowUp.Reason.OLD_DEBT -> "قدیمی‌ترین بدهیِ پرداخت‌نشده $d روز پیش"
        DebtFollowUp.Reason.WAITING_PROMISE -> "قول داده تا $date — $d روزِ دیگر"
        DebtFollowUp.Reason.RECENT -> if (row.days > 0) "بدهیِ $d روزه" else "بدهیِ امروز"
    }
}

@Composable
private fun DebtRow(
    row: DebtFollowUp.Row,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onRecord: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    val d = row.debtor
    val urgent = row.reason == DebtFollowUp.Reason.BROKEN_PROMISE || row.reason == DebtFollowUp.Reason.LATE_INSTALLMENT
    AppCard(onClick = onOpen) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                d.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                d.amount.afn(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (urgent) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            reasonLine(row),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (urgent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (urgent) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        d.lastContact?.let { c ->
            Text(
                "آخرین پیگیری: ${c.outcome.label} — ${PersianDate.short(c.at)}" +
                    if (row.contactedToday) " (امروز)" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (row.contactedToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.phone.isNotBlank()) {
                OutlinedButton(
                    onClick = onCall,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تماس", maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = onMessage,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("پیام", maxLines = 1)
            }
            OutlinedButton(
                onClick = onRecord,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("نتیجه", maxLines = 1)
            }
        }
    }
}

/**
 * «چه شد؟» — بعد از تماس، یا با دکمهٔ «نتیجه».
 *
 * قول با روزش ثبت می‌شود؛ تا آن روز این نفر در «بعداً» می‌ماند و اگر
 * گذشت و چیزی نرسید، بالای فهرست برمی‌گردد.
 */
@Composable
private fun OutcomeDialog(
    row: DebtFollowUp.Row,
    onDismiss: () -> Unit,
    onPick: (DebtFollowUp.Outcome, Int) -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پیگیریِ ${row.debtor.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("قول داد تا:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "امروز", 1 to "فردا", 3 to "۳ روز", 7 to "یک هفته", 14 to "دو هفته")
                        .forEach { (days, label) ->
                            AssistChip(
                                onClick = { onPick(DebtFollowUp.Outcome.PROMISED, days) },
                                label = { Text(label) }
                            )
                        }
                }
                Text("یا:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onPick(DebtFollowUp.Outcome.NO_ANSWER, 0) },
                        label = { Text(DebtFollowUp.Outcome.NO_ANSWER.label) }
                    )
                    AssistChip(
                        onClick = { onPick(DebtFollowUp.Outcome.TALKED, 0) },
                        label = { Text("صحبت شد، قولی نداد") }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
