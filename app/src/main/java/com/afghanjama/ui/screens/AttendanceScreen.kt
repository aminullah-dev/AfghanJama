@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

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
import androidx.compose.runtime.Composable
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
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.AttendanceViewModel
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
    onBack: () -> Unit
) {
    val employees by vm.employees.collectAsState()
    val records by vm.records.collectAsState()
    val monthlyWork by vm.monthlyWork.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

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
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
