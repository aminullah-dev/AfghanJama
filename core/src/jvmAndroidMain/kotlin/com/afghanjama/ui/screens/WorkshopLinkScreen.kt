@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import com.afghanjama.ui.components.QrImage
import com.afghanjama.platform.LocalCodeScanner
import com.afghanjama.lan.Pairing
import com.afghanjama.lan.PairingV2
import com.afghanjama.lan.ProtocolChoice
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.Icons
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
import com.afghanjama.lan.DeviceKind
import com.afghanjama.lan.HostAddress
import com.afghanjama.lan.HubOff
import com.afghanjama.lan.LanPeers
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
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.WorkshopLinkAccess
import com.afghanjama.ui.vm.WorkshopLinkViewModel
import com.afghanjama.ui.vm.hubOffText
import com.afghanjama.ui.vm.phonesSeenText

/**
 * اشتراکِ کارگاه — مدلِ «یک نویسنده».
 *
 * یک گوشی مالکِ دفتر است و فقط همان می‌نویسد. بقیه می‌بینند و درخواست
 * می‌دهند. این‌طوری دو گوشی هرگز سرِ یک عدد با هم دعوا نمی‌کنند.
 */
@Composable
fun WorkshopLinkScreen(
    vm: WorkshopLinkViewModel,
    /**
     * دسترسیِ کسی که وارد شده. روی دستگاهِ اصلی، روشن و خاموش و رمزِ تازه
     * و قطعِ اشتراک فقط با مدیر است، و هر درخواست را کسی تأیید می‌کند که
     * تیکِ همان کار را دارد ([WorkshopLinkAccess]).
     */
    access: Access,
    onBack: () -> Unit,
    /**
     * «کارگاهِ وصل» (قراردادِ ۲): پیوندِ `kylink2:` اگر اسکن شد، یا `null` برای
     * باز کردنِ صفحهٔ جفت شدن. `null`ِ خودِ این پارامتر یعنی این سکو مشتریِ
     * قراردادِ ۲ نیست (کامپیوتر).
     */
    onUseHub: ((String?) -> Unit)? = null,
    /** «کارگاهِ وصل» از چشمِ مرکز (قراردادِ ۲) — وقتی این دستگاه اصلی است. */
    hubAdmin: com.afghanjama.ui.vm.HubAdminViewModel? = null,
) {
    val settings = LocalSettings.current
    val canRunHub = WorkshopLinkAccess.canRunHub(access)
    val ui by vm.ui.collectAsState()
    val designCodeByOrder by vm.designCodeByOrder.collectAsState()
    val pending by vm.pending.collectAsState()
    val seen by vm.seen.collectAsState()
    val busy by vm.busy.state.collectAsState()
    val myLabel = remember { WorkerPrefs.myLabel(settings) }

    LaunchedEffect(Unit) { vm.load(settings) }

    AppScreen(title = "کارگاهِ وصل", onBack = onBack) { pad ->
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
                            "یک دستگاه دفتر را دارد",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            if (ui.kind == DeviceKind.COMPUTER)
                                "دستگاهِ کارفرما — همین کامپیوتر یا یک گوشی — «اصلی» می‌شود و تنها " +
                                    "همان می‌فروشد، می‌خرد و پرداخت می‌کند. گوشیِ کارگر کارش را می‌بیند " +
                                    "و «تمام شد» می‌زند — که یک درخواست است و روی دستگاهِ اصلی تأیید " +
                                    "می‌شود. کامپیوتر و گوشی‌ها باید روی یک وای‌فای یا شبکهٔ کارگاه باشند."
                            else
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
                    item { SectionTitleRow("این ${ui.kind.noun} چه نقشی دارد؟") }
                    if (canRunHub) {
                        item {
                            OutlinedButton(
                                onClick = { vm.becomeMain(settings) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(mainDeviceLine(ui.kind)) }
                        }
                    } else {
                        item {
                            Text(
                                "«دستگاهِ اصلی» کردنِ این ${ui.kind.noun} با مدیر است.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    onUseHub?.let { use ->
                        item { HubOfferCard(use) }
                    }
                    item { WorkerConnectCard(vm, busy, ui.kind, onUseHub) }
                }

                DeviceMode.MAIN -> {
                    item { MainCard(vm, ui.kind, ui.serving, ui.hubOff, ui.ip, ui.code, ui.alternatives, canRunHub, seen) }
                    if (hubAdmin != null) {
                        // قراردادِ ۲: جفت کردنِ گوشی‌ها، فهرستِ دستگاه‌ها و صندوق — همان
                        // تکه‌هایی که «کارگاهِ وصل»ِ کامپیوتر دارد.
                        item { HubMessage(hubAdmin) }
                        item { HubMovedCard(hubAdmin, access) }
                        item { HubPairPanel(hubAdmin, access, qrSize = 200.dp) }
                        item { HubInboxPanel(hubAdmin, access) }
                        item { HubDevicesPanel(hubAdmin, access) }
                        item { HubLegacyPanel(hubAdmin, access, ui.ip, ui.code) }
                    } else if (!WorkshopLinkAccess.seesRequests(access)) {
                        // فروشنده یا خیاطی که پشتِ دستگاهِ اصلی نشسته: درخواست‌ها کارِ او نیست.
                        item {
                            Text(
                                "درخواست‌های گوشیِ کارگران را مدیر تأیید می‌کند، یا کسی که تیکِ " +
                                    "«حضور و غیاب» یا «تسویهٔ کارمزدِ خیاط» دارد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
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
                                    if (WorkshopLinkAccess.canDecide(access, r.type)) {
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
                                    } else {
                                        Text(
                                            if (r.type == "SEWING_DONE")
                                                "تأییدِ «تمام شد» کارمزد می‌نشاند؛ با مدیر است، یا با کسی که تیکِ «تسویهٔ کارمزدِ خیاط» دارد."
                                            else "تأییدِ این درخواست با مدیر است، یا با کسی که تیکِ «حضور و غیاب» دارد.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (canRunHub) {
                        item {
                            TextButton(onClick = { vm.disconnect(settings) }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    if (ui.kind == DeviceKind.COMPUTER) "خاموش‌کردن اشتراک و برگشت به حالتِ تک‌دستگاه"
                                    else "خاموش‌کردن اشتراک و برگشت به حالتِ تک‌گوشی"
                                )
                            }
                        }
                    }
                }

                DeviceMode.WORKER -> {
                    if (ui.offerUpgrade && onUseHub != null) {
                        item { HubOfferCard(onUseHub, upgrade = true) }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // گوشیِ کارگر نمی‌داند دستگاهِ اصلی گوشی است یا کامپیوتر.
                                Text("وصل به دستگاهِ اصلی", fontWeight = FontWeight.SemiBold)
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
                                        busyText = "در حالِ فرستادن…",
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

/**
 * «این … دستگاهِ اصلیِ کارگاه است» — دکمهٔ حالتِ تک‌دستگاه و برچسبِ کارتِ
 * دستگاهِ اصلی، به زبانِ همان دستگاه.
 */
private fun mainDeviceLine(kind: DeviceKind): String = when (kind) {
    DeviceKind.PHONE -> "این گوشیِ اصلیِ کارگاه است"
    DeviceKind.COMPUTER -> "این کامپیوتر دستگاهِ اصلیِ کارگاه است"
}

@Composable
private fun MainCard(
    vm: WorkshopLinkViewModel,
    kind: DeviceKind,
    serving: Boolean,
    off: HubOff?,
    ip: String?,
    code: String,
    alternatives: List<String>,
    canRunHub: Boolean,
    seen: LanPeers.Seen,
) {
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
                mainDeviceLine(kind),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (serving) "اشتراک روشن است" else "اشتراک خاموش است",
                fontWeight = FontWeight.Bold
            )
            // چرا خاموش است — اشتراک دیگر با صفحه روشن و خاموش نمی‌شود، پس
            // کارفرما باید بداند چرا خودش روشن نشد (شبکهٔ دیگر، درگاهِ گرفته…).
            if (!serving) {
                hubOffText(off)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (off == HubOff.STOPPED || off == HubOff.WAITING)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
            // آیا گوشیِ کارگر اصلاً رسید؟ پس از اسکنِ QR همین‌جا یکی بالا می‌رود.
            Text(
                phonesSeenText(seen, kind),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // QR فقط وقتی اشتراک روشن است: QRی که به سرورِ خاموش اشاره کند،
            // کارگر را به همان پیامِ گمراه‌کنندهٔ «وای‌فای را ببینید» می‌رساند.
            if (kind == DeviceKind.COMPUTER) {
                ComputerPairing(ip, code, showQr = serving && ip != null && code.isNotBlank())
            } else {
                if (serving && ip != null && code.isNotBlank()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QrImage(Pairing.encode(ip, code), size = 200.dp)
                        Text(
                            "روی گوشیِ کارگر «اسکنِ QRِ دستگاهِ اصلی» را بزنید.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    "یا روی گوشیِ کارگر این دو را دستی وارد کنید:",
                    style = MaterialTheme.typography.labelSmall
                )
                Text("نشانی: ${(ip ?: "—").toPersianDigits()}", fontWeight = FontWeight.SemiBold)
                Text("رمز: ${code.toPersianDigits()}", fontWeight = FontWeight.SemiBold)
            }
            if (alternatives.isNotEmpty()) {
                Text(
                    "اگر وصل نشد، این نشانی‌های همین دستگاه را امتحان کنید: " +
                        alternatives.joinToString("، ") { it.toPersianDigits() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (ip == null) {
                Text(
                    if (kind == DeviceKind.COMPUTER) "نشانی پیدا نشد — این کامپیوتر را به وای‌فای یا شبکهٔ کارگاه وصل کنید."
                    else "نشانی پیدا نشد — وای‌فای یا هات‌اسپاتِ کارگاه را روشن کنید.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (canRunHub) {
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
            } else {
                Text(
                    "روشن و خاموش کردنِ اشتراک و ساختنِ رمزِ تازه با مدیر است.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * نشانی، رمز و QR روی کامپیوتر — کنارِ هم و درشت.
 *
 * روی گوشی همه زیرِ هم‌اند و صفحه باریک است. روی پنجرهٔ پهنِ کامپیوتر همان
 * چیدمان QR را وسطِ یک کارتِ هزارپیکسلی تنها می‌گذاشت و نشانی را با قلمِ
 * متن در گوشهٔ دیگر؛ کارفرما باید نشانی را از سه متر آن‌طرف‌تر برای کارگر
 * بخوانَد. اینجا نشانی و رمز درشت‌اند و QR بزرگ‌تر کنارشان.
 */
@Composable
private fun ComputerPairing(ip: String?, code: String, showQr: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (showQr) "روی گوشیِ کارگر «اسکنِ QRِ دستگاهِ اصلی» را بزنید و دوربین را رو به این QR بگیرید. " +
                    "اگر دوربین نبود، این دو را روی گوشی دستی وارد کنید:"
                else "روی گوشیِ کارگر این دو را دستی وارد کنید:",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "نشانیِ این کامپیوتر",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                (ip ?: "—").toPersianDigits(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "رمزِ اتصال",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                code.ifBlank { "—" }.toPersianDigits(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        if (showQr && ip != null) QrImage(Pairing.encode(ip, code), size = 240.dp)
    }
}

/**
 * «اتصال به کامپیوترِ کارگاه» با قراردادِ ۲ — کامپیوترِ ۱.۱۱ و تازه‌تر. برای
 * گوشیِ رمزِ شش‌رقمی که کامپیوترش به‌روز شده، همان پیامِ «یک بار دوباره وصل
 * شوید» ([ProtocolChoice.UPGRADE_OFFER]).
 */
@Composable
private fun HubOfferCard(onUseHub: (String?) -> Unit, upgrade: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (upgrade) "کامپیوترِ کارگاه نسخهٔ تازه دارد" else "اتصال به کامپیوترِ کارگاه (کارگاهِ وصل)",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                if (upgrade) ProtocolChoice.UPGRADE_OFFER
                else "اگر کامپیوترِ کارگاه نسخهٔ ۱.۱۱ یا تازه‌تر دارد، این راهِ امن‌تر است: گوشی یک بار با QR جفت می‌شود، " +
                    "کارها حتی بی وای‌فای در صف می‌مانند و همه‌چیز روی شبکه رمز شده است.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Button(onClick = { onUseHub(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (upgrade) "جفت شدنِ دوباره" else "اتصال به کامپیوترِ کارگاه")
            }
        }
    }
}

@Composable
private fun WorkerConnectCard(
    vm: WorkshopLinkViewModel,
    busy: Boolean,
    kind: DeviceKind,
    onUseHub: ((String?) -> Unit)? = null,
) {
    val settings = LocalSettings.current
    var host by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    // تا وقتی کاربر چیزی ننوشته ایراد نشان نمی‌دهیم؛ کادرِ قرمز سرِ
    // حرفِ اول آزاردهنده است.
    val hostCheck = remember(host) {
        if (host.isBlank()) null else HostAddress.check(host)
    }
    val hostBad = (hostCheck as? HostAddress.Result.Bad)?.reason

    // اسکنِ QRِ دستگاهِ اصلی — بی تایپِ نشانی، پس بی آن نقطهٔ جاافتاده.
    var scanMsg by remember { mutableStateOf<String?>(null) }

    fun onScanned(raw: String) {
        // QRِ «افزودنِ گوشی»ِ کامپیوترِ ۱.۱۱ — به صفحهٔ «کارگاهِ وصل».
        if (onUseHub != null && PairingV2.parse(raw) != null) {
            scanMsg = null
            onUseHub(raw)
            return
        }
        val link = Pairing.parse(raw)
        if (link == null) {
            scanMsg = Pairing.refusal(raw)
            return
        }
        scanMsg = null
        host = link.host
        code = link.code
        vm.becomeWorker(settings, link.host, link.code)
    }

    val startScan = LocalCodeScanner.current?.rememberStart(
        prompt = "QRِ صفحهٔ «کارگاهِ وصل» روی دستگاهِ اصلی (گوشی یا کامپیوتر) را اسکن کنید",
        onCode = { onScanned(it) },
        onFailed = { msg -> scanMsg = msg },
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (kind == DeviceKind.COMPUTER) "این کامپیوتر به دستگاهِ اصلیِ کارگاه وصل می‌شود"
                else "این گوشیِ یک کارگر است",
                fontWeight = FontWeight.SemiBold
            )
            if (startScan != null) {
                Button(
                    onClick = { startScan() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Text("  اسکنِ QRِ دستگاهِ اصلی")
                }
                scanMsg?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "یا دستی وارد کنید:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // **چپ‌به‌راست، حتی وسطِ صفحهٔ راست‌به‌چپ.** نشانیِ عددی در
            // کادرِ راست‌به‌چپ جای مکان‌نما و ترتیبِ نقطه‌ها را گیج
            // می‌کند و همان‌جاست که یک نقطه جا می‌افتد.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = host,
                    // ارقامِ فارسی همان‌جا لاتین می‌شوند: دستگاهِ اصلی نشانی
                    // را فارسی نشان می‌دهد، پس کاربر فارسی تایپ می‌کند.
                    onValueChange = { host = HostAddress.clean(it) },
                    label = { Text("نشانیِ دستگاهِ اصلی") },
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
                busyText = "در حالِ اتصال…",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
