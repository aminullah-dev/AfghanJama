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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import com.afghanjama.ui.platform.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import com.afghanjama.data.PayrollSmart
import com.afghanjama.data.ShiftPolicy
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.PayrollPrefs
import com.afghanjama.ui.components.EmptyState
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.PayrollRow
import com.afghanjama.ui.vm.PayrollViewModel
import com.afghanjama.util.withTime

/**
 * حقوقِ ماهانهٔ کارکنان: چه کسی چقدر می‌گیرد، حقوقِ این ماه پرداخت شده
 * یا نه، و پرداخت با یک ضربه (که رسید، سندِ هزینه و لاگ می‌سازد).
 */
@Composable
fun PayrollScreen(
    vm: PayrollViewModel,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val message by vm.message.collectAsState()
    val shifts by vm.shiftsThisMonth.collectAsState()
    val settings = LocalSettings.current
    val snackbar = remember { SnackbarHostState() }

    // روزهای کاریِ ماه — پایهٔ پیشنهادِ حقوق؛ در همین صفحه قابلِ تغییر و
    // در تنظیماتِ دستگاه ذخیره می‌شود.
    var workingDays by remember { mutableStateOf(PayrollPrefs.workingDays(settings)) }
    val shiftHours = (ShiftPolicy.SHIFT_MS / 3_600_000L).toInt()

    // پیشنهادِ حقوقِ هر کارمند از حضورِ همین ماه و روزهای کاری — هر بار
    // حضور یا تنظیم عوض شود دوباره ساخته می‌شود.
    val suggestionOf: (PayrollRow) -> PayrollSmart.Suggestion = { row ->
        PayrollSmart.analyze(
            shifts[row.name.trim()].orEmpty(),
            PayrollSmart.Config(row.monthlySalary, workingDays, shiftHours),
            dayOf = { withTime(it, 0, 0) }
        )
    }

    var editing by remember { mutableStateOf<PayrollRow?>(null) }
    var paying by remember { mutableStateOf<PayrollRow?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PayrollRow?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // ---------- افزودن / ویرایش کارمند ----------
    if (addOpen || editing != null) {
        val row = editing
        var name by remember(row) { mutableStateOf(row?.name.orEmpty()) }
        var role by remember(row) { mutableStateOf(row?.role.orEmpty()) }
        var salary by remember(row) {
            mutableStateOf(if ((row?.monthlySalary ?: 0) > 0) row!!.monthlySalary.toString() else "")
        }
        AppAlertDialog(
            onDismissRequest = { addOpen = false; editing = null },
            title = { Text(if (row == null) "کارمند جدید" else "ویرایش ${row.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام کارمند") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = role,
                        onValueChange = { role = it },
                        label = { Text("سمت (آشپز، حسابدار، مدیر…)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = salary,
                        onValueChange = { salary = it.digitsOnly() },
                        label = { Text("حقوق ماهانه (؋) — خالی یعنی حقوق‌بگیر نیست") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (row != null) {
                        TextButton(onClick = { deleting = row; editing = null }) {
                            Text("حذفِ این کارمند", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pay = salary.toLongOrNull() ?: 0L
                    if (row == null) vm.saveStaff(name, role, pay) else vm.updateStaff(row, name, role, pay)
                    addOpen = false; editing = null
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { addOpen = false; editing = null }) { Text("انصراف") }
            }
        )
    }

    deleting?.let { row ->
        AppAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("«${row.name}» حذف شود؟") },
            text = { Text("فقط کارمندی حذف می‌شود که هنوز حضور، حقوق یا حسابی ندارد.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteStaff(row); deleting = null }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("انصراف") } }
        )
    }

    // ---------- پرداخت حقوق ----------
    paying?.let { row ->
        var amount by remember(row) {
            mutableStateOf(row.remaining.takeIf { it > 0 }?.toString() ?: row.monthlySalary.toString())
        }
        var source by remember(row) { mutableStateOf("WALLET") }
        // پیش‌پرداختِ تسویه‌نشده پیش‌فرض کسر می‌شود — همان کاری که کارگاه در
        // عمل می‌کند. کاربر می‌تواند خاموشش کند.
        var deduct by remember(row) { mutableStateOf(row.advance > 0) }
        val salary = amount.toLongOrNull() ?: 0L
        val deducted = if (deduct) minOf(row.advance, salary) else 0L
        val cashOut = (salary - deducted).coerceAtLeast(0)
        AppAlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("پرداخت حقوق ${row.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "دورهٔ ${ui.monthLabel} • حقوق توافقی: ${row.monthlySalary.afn()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (row.paidThisMonth > 0) {
                        Text(
                            "قبلاً در این ماه پرداخت شده: ${row.paidThisMonth.afn()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.digitsOnly() },
                        label = { Text("حقوقِ کامل (؋)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // پیشنهادِ حقوق از حضورِ همین ماه — زدنی، نه خودکار.
                    SalarySuggestions(
                        s = suggestionOf(row),
                        typed = salary,
                        onPick = { amount = it.toString() }
                    )
                    // ---------- پیش‌پرداختِ تسویه‌نشده ----------
                    if (row.advance > 0) {
                        FilterChip(
                            selected = deduct,
                            onClick = { deduct = !deduct },
                            label = { Text("کسرِ پیش‌پرداخت (${row.advance.afn()})") }
                        )
                        Text(
                            if (deducted > 0)
                                "از حقوقِ ${salary.afn()} مبلغِ ${deducted.afn()} پیش‌پرداختِ " +
                                    "قبلی تهاتر می‌شود و ${cashOut.afn()} نقد داده می‌شود."
                            else
                                "پیش‌پرداختِ ${row.advance.afn()} کسر نمی‌شود و روی حسابِ " +
                                    "${row.name} باقی می‌ماند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (deducted > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = source == "WALLET",
                            onClick = { source = "WALLET" },
                            label = { Text("صندوق (${ui.wallet.afn()})") }
                        )
                        FilterChip(
                            selected = source == "BANK",
                            onClick = { source = "BANK" },
                            label = { Text("بانک (${ui.bank.afn()})") }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.pay(row.name, salary, source, deductAdvance = deducted)
                    paying = null
                }) { Text("پرداخت") }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("انصراف") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("حقوق کارکنان") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { addOpen = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "کارمند جدید")
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
            // ---------- خلاصهٔ ماه ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ui.unpaidCount > 0)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val onColor = if (ui.unpaidCount > 0)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                        Text(
                            "حقوق ${ui.monthLabel}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onColor
                        )
                        Text(
                            "جمعِ ماهانه ${ui.monthlyTotal.afn()} • پرداخت‌شده ${ui.paidTotal.afn()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = onColor
                        )
                        Text(
                            if (ui.unpaidCount > 0)
                                "باقی‌مانده ${ui.remainingTotal.afn()} برای ${ui.unpaidCount.fa()} نفر"
                            else "حقوقِ همه پرداخت شده است",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onColor
                        )
                    }
                }
            }

            // ---------- روزهای کاریِ ماه (پایهٔ پیشنهادِ حقوق) ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("روزهای کاریِ ماه", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "پایهٔ «حقوقِ هر روز» و اضافه‌کاری. جمعه‌ها و رخصتی را کم کنید.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = workingDays.toString(),
                            onValueChange = { v ->
                                val d = v.digitsOnly().toIntOrNull()?.coerceIn(1, 31) ?: workingDays
                                workingDays = d
                                PayrollPrefs.setWorkingDays(settings, d)
                            },
                            singleLine = true,
                            modifier = Modifier.widthIn(max = 96.dp)
                        )
                    }
                }
            }

            if (ui.rows.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Groups,
                        title = "هنوز کارمندی ثبت نشده",
                        hint = "با دکمهٔ ➕ بالای صفحه کارمند اضافه کنید و حقوقِ " +
                            "ماهانه‌اش را بنویسید تا یادآورِ پرداخت فعال شود."
                    )
                }
            }

            items(ui.rows, key = { it.name }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(row.name, fontWeight = FontWeight.SemiBold)
                                if (row.role.isNotBlank()) {
                                    Text(
                                        row.role,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (row.monthlySalary > 0) {
                                Text(
                                    if (row.isPaid) "پرداخت شد" else "در انتظار پرداخت",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.isPaid) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        if (row.monthlySalary > 0) {
                            Text(
                                "حقوق ماهانه: ${row.monthlySalary.afn()}" +
                                    (if (row.paidThisMonth > 0) " • پرداختِ این ماه: ${row.paidThisMonth.afn()}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // حضورِ همین ماه — از دفترِ حضور، نه حدس.
                            val sug = suggestionOf(row)
                            if (sug.hasData) {
                                Text(
                                    "این ماه: ${sug.daysPresent.fa()} روز حاضر از ${sug.workingDays.fa()}" +
                                        (if (sug.overtimeHours > 0) " • ${sug.overtimeHours.fa()} ساعت اضافه‌کاری" else "") +
                                        (if (sug.hasDeduction) " • حقوقِ روزهای حاضر: ${sug.proratedSalary.afn()}" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sug.hasDeduction || sug.overtimeHours > 0)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                "حقوقِ ماهانه ثبت نشده (کارمزدی)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // پیش‌پرداختِ تسویه‌نشده باید پیش از پرداختِ حقوق دیده
                        // شود، وگرنه دو بار پول داده می‌شود.
                        if (row.advance > 0) {
                            Text(
                                "پیش‌پرداختِ تسویه‌نشده: ${row.advance.afn()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editing = row }) { Text("ویرایش") }
                            if (row.monthlySalary > 0 && !row.isPaid) {
                                Button(onClick = { paying = row }) { Text("پرداخت حقوق") }
                            }
                        }
                    }
                }
            }

            // ---------- تاریخچهٔ پرداخت ----------
            if (ui.payments.isNotEmpty()) {
                item {
                    Text(
                        "تاریخچهٔ پرداخت‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(ui.payments, key = { "pay-${it.id}" }) { p ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${p.employee} — ${p.periodLabel}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                p.amount.afn(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            PersianDate.shortWithTime(p.at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/**
 * چند مبلغِ زدنی زیرِ فیلدِ حقوق — بر پایهٔ حضورِ همین ماه.
 *
 * فیلد خودش پر نمی‌شود (تصمیمِ کارفرماست، مثلِ پیشنهادِ قیمت)؛ اینجا فقط
 * چند دکمه است، هر کدام با آنچه می‌سازدش. مبلغی که با عددِ تایپ‌شده یکی
 * است دوباره نشان داده نمی‌شود.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalarySuggestions(
    s: PayrollSmart.Suggestion,
    typed: Long,
    onPick: (Long) -> Unit,
) {
    if (!s.hasData) return
    val chips = buildList {
        if (s.hasDeduction) {
            add("حقوقِ ${s.daysPresent.fa()} روز حاضر" to s.proratedSalary)
        }
        if (s.overtimePay > 0) {
            add("+ اضافه‌کاریِ ${s.overtimeHours.fa()} ساعت" to s.suggestedTotal)
        }
        add("حقوقِ کامل" to s.monthlySalary)
    }.filter { (_, v) -> v != typed }
    if (chips.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { (label, v) ->
                AssistChip(
                    onClick = { onPick(v) },
                    label = { Text("$label: ${v.afn()}", maxLines = 1) }
                )
            }
        }
        Text(
            "بر پایهٔ ${s.daysPresent.fa()} روز حاضر از ${s.workingDays.fa()} روزِ کاری" +
                (if (s.overtimeHours > 0) " و ${s.overtimeHours.fa()} ساعت اضافه‌کاری" else "") +
                " — پیشنهاد است، نه کسرِ خودکار.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
