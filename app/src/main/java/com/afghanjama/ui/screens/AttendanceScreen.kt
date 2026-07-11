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
import androidx.compose.material.icons.filled.Fingerprint
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.AttendanceViewModel
import com.afghanjama.util.BiometricAuth

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
    val activity = LocalContext.current as? FragmentActivity

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
                                Text(
                                    if (e.isIn && e.since != null) "داخل — از ${clock(e.since)}" else "بیرون",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (e.isIn) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (e.isIn) {
                                Button(
                                    onClick = { gate("ثبت خروج ${e.name}") { vm.checkOut(e.name) } },
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
                                Button(onClick = { gate("ثبت ورود ${e.name}") { vm.checkIn(e.name) } }) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("ورود")
                                }
                            }
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
