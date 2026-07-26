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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.content.Intent
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.AttendanceViewModel
import com.afghanjama.ui.vm.BreakTimeViewModel
import com.afghanjama.ui.format.elapsedHm
import com.afghanjama.util.BiometricAuth
import com.afghanjama.work.ShiftReminderWorker
import kotlinx.coroutines.delay

private fun clock(millis: Long): String = PersianDate.shortWithTime(millis)

private fun duration(fromMillis: Long, toMillis: Long): String {
    val mins = ((toMillis - fromMillis) / 60000L).coerceAtLeast(0)
    val h = mins / 60
    val m = mins % 60
    return if (h > 0) "${h.fa()} ساعت و ${m.fa()} دقیقه" else "${m.fa()} دقیقه"
}

@Composable
fun AttendanceScreen(
    vm: AttendanceViewModel,
    breakVm: BreakTimeViewModel,
    onBack: () -> Unit
) {
    val employees by vm.employees.collectAsState()
    val records by vm.records.collectAsState()
    val monthlyWork by vm.monthlyWork.collectAsState()
    val breakTimes by breakVm.times.collectAsState()
    val insideNow by breakVm.inside.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // شناسهٔ وقتی که در حالِ ویرایش است؛ NEW_BREAK یعنی «تازه»
    var breakEditing by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(records) { breakVm.refreshInside() }

    breakEditing?.let { editingId ->
        val existing = breakTimes.firstOrNull { it.id == editingId }
        var title by remember(editingId) { mutableStateOf(existing?.title ?: "") }
        var hourText by remember(editingId) { mutableStateOf(existing?.hour?.toString() ?: "") }
        var minuteText by remember(editingId) { mutableStateOf(existing?.minute?.toString() ?: "0") }
        val h = hourText.toIntOrNull()
        val m = minuteText.toIntOrNull()
        val valid = h != null && h in 0..23 && m != null && m in 0..59

        AlertDialog(
            onDismissRequest = { breakEditing = null },
            title = { Text(if (existing == null) "وقت تازه" else "ویرایش وقت") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("نام (مثلاً نان چاشت)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { hourText = it.digitsOnly().take(2) },
                            label = { Text("ساعت (۰ تا ۲۳)") },
                            singleLine = true,
                            isError = hourText.isNotBlank() && (h == null || h !in 0..23),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { minuteText = it.digitsOnly().take(2) },
                            label = { Text("دقیقه") },
                            singleLine = true,
                            isError = minuteText.isNotBlank() && (m == null || m !in 0..59),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "هر روز همین ساعت یادآوری می‌شود و می‌گوید چند نفر داخل‌اند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        breakVm.save(context, existing, title, h!!, m!!)
                        breakEditing = null
                    }
                ) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { breakEditing = null }) { Text("لغو") } }
        )
    }

    // ساعتِ زنده: مدتِ حضورِ هر کارمند هر ۳۰ ثانیه به‌روز می‌شود
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    fun gate(title: String, action: () -> Unit) {
        if (activity != null) {
            BiometricAuth.confirm(
                activity = activity,
                title = title,
                subtitle = "برای ثبت، اثر انگشت را تأیید کنید",
                onSuccess = action
            )
        } else action()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حضور و غیاب") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "کارمند را انتخاب کنید؛ ورود/خروج با تأیید اثر انگشت ثبت می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (employees.isEmpty()) {
                item {
                    Text(
                        "کارمندی ثبت نشده. خیاط‌ها، ناظرها و کارکنان را از «اطلاعات پایه» اضافه کنید.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            } else {
                item {
                    Text("کارمندان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(employees, key = { it.name }) { e ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, fontWeight = FontWeight.SemiBold)
                                val nearShiftEnd = e.isIn && e.since != null &&
                                    (now - e.since) >= ShiftReminderWorker.WARN_AFTER_MS
                                Text(
                                    if (e.isIn && e.since != null)
                                        "داخل — از ${clock(e.since)} • ${elapsedHm(e.since, now)} ساعت"
                                    else "بیرون",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        nearShiftEnd -> MaterialTheme.colorScheme.error
                                        e.isIn -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (nearShiftEnd) {
                                    Text(
                                        "⚠ نزدیک پایان شیفت ۸ ساعته — خروج را ثبت کنید",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            if (e.isIn) {
                                Button(
                                    onClick = {
                                        gate("ثبت خروج ${e.name}") {
                                            vm.checkOut(e.name)
                                            ShiftReminderWorker.cancel(context, e.name)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("خروج")
                                }
                            } else {
                                Button(onClick = {
                                    gate("ثبت ورود ${e.name}") {
                                        vm.checkIn(e.name)
                                        ShiftReminderWorker.schedule(context, e.name)
                                    }
                                }) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("ورود")
                                }
                            }
                        }
                    }
                }
            }

            // ---------- کارکرد ۳۰ روز اخیر (برای معاش/تسویه) ----------
            if (monthlyWork.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("کارکرد ۳۰ روز اخیر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = {
                            val report = buildString {
                                appendLine("📋 گزارش کارکرد ۳۰ روز اخیر — AfghanJama")
                                appendLine("تاریخ: ${PersianDate.short(System.currentTimeMillis())}")
                                appendLine("──────────────")
                                monthlyWork.forEach { w ->
                                    appendLine("• ${w.name}: ${(w.totalMinutes / 60).fa()}:${(w.totalMinutes % 60).toString().padStart(2, '0').toPersianDigits()} ساعت در ${w.daysWorked.fa()} روز")
                                }
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(Intent.createChooser(send, "اشتراک گزارش کارکرد"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "اشتراک گزارش")
                        }
                    }
                }
                items(monthlyWork, key = { "work-" + it.name }) { w ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(w.name, fontWeight = FontWeight.Medium)
                            Text(
                                "${(w.totalMinutes / 60).fa()}:${(w.totalMinutes % 60).toString().padStart(2, '0').toPersianDigits()} ساعت • ${w.daysWorked.fa()} روز",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ---------- وقت نان و چای ----------
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "وقت نان و چای",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (insideNow > 0) "همین حالا ${insideNow.fa()} نفر داخل‌اند"
                            else "همین حالا کسی ورود نزده",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { breakEditing = NEW_BREAK }) { Text("+ وقت تازه") }
                }
            }

            if (breakTimes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "هنوز وقتی ثبت نشده",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "در وقتِ تعیین‌شده گوشی یادآوری می‌کند و می‌گوید چند نفر " +
                                    "داخل‌اند — تا آشپز بداند برای چند نفر آماده کند.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            TextButton(onClick = { breakVm.addDefaults(context) }) {
                                Text("افزودن وقت‌های معمول (چای صبح، نان چاشت، چای عصر)")
                            }
                        }
                    }
                }
            }

            items(breakTimes, key = { "brk-${it.id}" }) { b ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier.weight(1f).clickable { breakEditing = b.id }
                        ) {
                            Text(
                                b.title,
                                fontWeight = FontWeight.Medium,
                                color = if (b.enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                b.clock.toPersianDigits(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = b.enabled,
                            onCheckedChange = { breakVm.setEnabled(context, b, it) }
                        )
                        IconButton(onClick = { breakVm.delete(context, b) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "حذف",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (records.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("سابقهٔ اخیر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(records, key = { it.id }) { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.employee, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "ورود: ${clock(r.checkIn)}" +
                                    (r.checkOut?.let { " • خروج: ${clock(it)}" } ?: " • هنوز داخل"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        r.checkOut?.let {
                            Text(duration(r.checkIn, it), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

/** شناسهٔ ساختگی برای «وقتِ تازه» — هیچ سطری این id را ندارد. */
private const val NEW_BREAK = -1L
