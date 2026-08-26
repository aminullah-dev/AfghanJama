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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.syncRequestLabel
import com.afghanjama.lan.HostAddress
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.WorkerPrefs
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.components.BusyButton
import com.afghanjama.ui.components.OrderCodeLine
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.WorkshopLinkViewModel

/**
 * اشتراکِ کارگاه — مدلِ «یک نویسنده».
 *
 * یک گوشی مالکِ دفتر است و فقط همان می‌نویسد. بقیه می‌بینند و درخواست
 * می‌دهند. این‌طوری دو گوشی هرگز سرِ یک عدد با هم دعوا نمی‌کنند.
 */
@Composable
fun WorkshopLinkScreen(
    vm: WorkshopLinkViewModel,
    isManager: Boolean,
    onBack: () -> Unit
) {
    val settings = LocalSettings.current
    val ui by vm.ui.collectAsState()
    val designCodeByOrder by vm.designCodeByOrder.collectAsState()
    val pending by vm.pending.collectAsState()
    val busy by vm.busy.state.collectAsState()
    val myLabel = remember { WorkerPrefs.myLabel(settings) }

    LaunchedEffect(Unit) { vm.load(settings) }

    AppScreen(title = "اشتراک کارگاه", onBack = onBack) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "یک گوشی دفتر را دارد",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "گوشیِ کارفرما «اصلی» می‌شود و تنها همان می‌فروشد، می‌خرد و " +
                                "پرداخت می‌کند. گوشیِ کارگر کارش را می‌بیند و «تمام شد» " +
                                "می‌زند — که یک درخواست است و روی گوشیِ اصلی تأیید می‌شود. " +
                                "هر دو باید روی وای‌فای یا هات‌اسپاتِ کارگاه باشند.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            ui.message?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ui.isError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                msg,
                                modifier = Modifier.weight(1f),
                                color = if (ui.isError) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            TextButton(onClick = vm::clearMessage) { Text("باشه") }
                        }
                    }
                }
            }

            // ---------------- نقشِ این گوشی ----------------
            when (ui.mode) {
                DeviceMode.STANDALONE -> {
                    item { SectionTitleRow("این گوشی چه نقشی دارد؟") }
                    if (isManager) {
                        item {
                            OutlinedButton(
                                onClick = { vm.becomeMain(settings) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("این گوشیِ اصلیِ کارگاه است") }
                        }
                    }
                    item { WorkerConnectCard(vm, busy) }
                }

                DeviceMode.MAIN -> {
                    item { MainCard(vm, ui.serving, ui.ip, ui.code) }
                    item { SectionTitleRow("درخواست‌های منتظر (${pending.size.fa()})") }
                    if (pending.isEmpty()) {
                        item {
                            Text(
                                "چیزی منتظرِ تأیید نیست.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(pending, key = { it.id }) { r ->
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
                                Text(
                                    "${syncRequestLabel(r.type)} — ${r.worker}",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    r.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${r.deviceName} • ${PersianDate.shortWithTime(r.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BusyButton(
                                        text = "تأیید و ثبت",
                                        onClick = { vm.approve(r.id) },
                                        busy = busy,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = { vm.reject(r.id) },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("رد") }
                                }
                            }
                        }
                    }
                    item {
                        TextButton(onClick = { vm.disconnect(settings) }, modifier = Modifier.fillMaxWidth()) {
                            Text("خاموش‌کردن اشتراک و برگشت به حالتِ تک‌گوشی")
                        }
                    }
                }

                DeviceMode.WORKER -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("وصل به گوشیِ اصلی", fontWeight = FontWeight.SemiBold)
                                Text(
                                    ui.host.toPersianDigits(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (myLabel.isBlank())
                                        "هنوز مشخص نکرده‌اید این گوشی دستِ کدام خیاط است — از تنظیمات انتخاب کنید."
                                    else "کارگرِ این گوشی: $myLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (myLabel.isBlank()) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (myLabel.isNotBlank()) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { vm.refreshMyWork(settings, myLabel) },
                                    enabled = !ui.checking,
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (ui.checking) "…" else "تازه‌سازی کارها") }
                                OutlinedButton(
                                    onClick = {
                                        vm.sendRequest(
                                            settings, myLabel, "ATTENDANCE_IN",
                                            summary = "درخواستِ ثبتِ ورود"
                                        )
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f)
                                ) { Text("ورود") }
                                OutlinedButton(
                                    onClick = {
                                        vm.sendRequest(
                                            settings, myLabel, "ATTENDANCE_OUT",
                                            summary = "درخواستِ ثبتِ خروج"
                                        )
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f)
                                ) { Text("خروج") }
                            }
                        }

                        item { SectionTitleRow("کارِ زیرِ دستِ من") }
                        if (ui.myWork.isEmpty()) {
                            item {
                                Text(
                                    "کاری نشان داده نشد. «تازه‌سازی کارها» را بزنید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(ui.myWork, key = { it.id }) { w ->
                            var doneText by remember(w.id) { mutableStateOf(w.qty.toString()) }
                            val done = doneText.toIntOrNull() ?: 0
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
                                    OrderCodeLine(
                                        designCode = designCodeByOrder[w.orderCode].orEmpty(),
                                        orderCode = w.orderCode,
                                        trailing = "${w.qty.fa()} عدد",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "کارمزد فی‌عدد ${w.unitWage.afn()} • از ${PersianDate.short(w.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = doneText,
                                        onValueChange = { doneText = it.digitsOnly() },
                                        label = { Text("چند عدد تمام شد؟") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    BusyButton(
                                        text = "اعلامِ تحویل به کارفرما",
                                        onClick = {
                                            vm.sendRequest(
                                                settings, myLabel, "SEWING_DONE",
                                                summary = "${w.orderCode} — ${done} عدد از ${w.qty} عدد",
                                                refId = w.id, amount = done
                                            )
                                        },
                                        enabled = done in 1..w.qty,
                                        busy = busy,
                                        busyText = "در حال فرستادن…",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        "این فقط یک اعلام است؛ تا کارفرما تأیید نکند در حسابِ کارمزد نمی‌نشیند.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        TextButton(onClick = { vm.disconnect(settings) }, modifier = Modifier.fillMaxWidth()) {
                            Text("قطعِ اتصال")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SectionTitleRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun MainCard(vm: WorkshopLinkViewModel, serving: Boolean, ip: String?, code: String) {
    val settings = LocalSettings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (serving) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (serving) "اشتراک روشن است" else "اشتراک خاموش است",
                fontWeight = FontWeight.Bold
            )
            Text(
                "روی گوشیِ کارگر این دو را وارد کنید:",
                style = MaterialTheme.typography.labelSmall
            )
            Text("نشانی: ${(ip ?: "—").toPersianDigits()}", fontWeight = FontWeight.SemiBold)
            Text("رمز: ${code.toPersianDigits()}", fontWeight = FontWeight.SemiBold)
            if (ip == null) {
                Text(
                    "نشانی پیدا نشد — وای‌فای یا هات‌اسپاتِ کارگاه را روشن کنید.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { if (serving) vm.stopServing() else vm.startServing(settings) },
                    modifier = Modifier.weight(1f)
                ) { Text(if (serving) "خاموش" else "روشن") }
                OutlinedButton(
                    onClick = { vm.newCode(settings) },
                    modifier = Modifier.weight(1f)
                ) { Text("رمز تازه") }
            }
        }
    }
}

@Composable
private fun WorkerConnectCard(vm: WorkshopLinkViewModel, busy: Boolean) {
    val settings = LocalSettings.current
    var host by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    // تا وقتی کاربر چیزی ننوشته ایراد نشان نمی‌دهیم؛ کادرِ قرمز سرِ
    // حرفِ اول آزاردهنده است.
    val hostCheck = remember(host) {
        if (host.isBlank()) null else HostAddress.check(host)
    }
    val hostBad = (hostCheck as? HostAddress.Result.Bad)?.reason
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("این گوشیِ یک کارگر است", fontWeight = FontWeight.SemiBold)
            // **چپ‌به‌راست، حتی وسطِ صفحهٔ راست‌به‌چپ.** نشانیِ عددی در
            // کادرِ راست‌به‌چپ جای مکان‌نما و ترتیبِ نقطه‌ها را گیج
            // می‌کند و همان‌جاست که یک نقطه جا می‌افتد.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = host,
                    // ارقامِ فارسی همان‌جا لاتین می‌شوند: گوشیِ اصلی نشانی
                    // را فارسی نشان می‌دهد، پس کاربر فارسی تایپ می‌کند.
                    onValueChange = { host = HostAddress.clean(it) },
                    label = { Text("نشانیِ گوشیِ اصلی") },
                    placeholder = { Text("10.0.0.101") },
                    singleLine = true,
                    isError = hostBad != null,
                    supportingText = hostBad?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.digitsOnly().take(6) },
                label = { Text("رمزِ اتصال") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            BusyButton(
                text = "اتصال",
                onClick = { vm.becomeWorker(settings, host, code) },
                enabled = hostCheck is HostAddress.Result.Ok && code.length >= 4,
                busy = busy,
                busyText = "در حال اتصال…",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
