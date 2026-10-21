package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.LanDevice
import com.afghanjama.data.entities.LegacyMode
import com.afghanjama.data.entities.RequestTime
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.entities.clockSuspect
import com.afghanjama.data.entities.syncRequestLabel
import com.afghanjama.lan.HubOff
import com.afghanjama.lan.HubProtocol
import com.afghanjama.lan.HubRole
import com.afghanjama.lan.PairVia
import com.afghanjama.lan.PairingV2
import com.afghanjama.ui.components.BusyButton
import com.afghanjama.ui.components.QrImage
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.HubAdminViewModel
import com.afghanjama.ui.vm.WorkshopLinkAccess

/*
 * تکه‌های «کارگاهِ وصل» از چشمِ مرکز — هم کامپیوتر (صفحهٔ پهنِ خودش) و هم
 * گوشیِ اصلیِ اندرویدی (همان صفحهٔ «اشتراکِ کارگاه»). شیشهٔ نرم، قرص، رنگ‌های
 * برند از پالتِ مشترک، و هیچ رنگِ دستی (`palette`).
 */

/** کارتِ شیشه‌ایِ مشترکِ این صفحه‌ها. */
@Composable
fun HubGlass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun HubPill(text: String, strong: Boolean = false, warn: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = when {
            warn -> MaterialTheme.colorScheme.secondaryContainer
            strong -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = when {
                warn -> MaterialTheme.colorScheme.onSecondaryContainer
                strong -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
        )
    }
}

@Composable
private fun Dot(on: Boolean) {
    Box(
        Modifier.size(10.dp).background(
            if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            CircleShape,
        )
    )
}

@Composable
fun HubMessage(vm: HubAdminViewModel) {
    val m by vm.message.collectAsState()
    val (text, error) = m ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = vm::clearMessage) { Text("باشه") }
        }
    }
}

// ================================================================ «مرکز اینجاست»

/** دفتر از دستگاهِ دیگری آمده (یا همتایی با همان دفتر جواب می‌دهد). */
@Composable
fun HubMovedCard(vm: HubAdminViewModel, access: Access) {
    val st by vm.hubState.collectAsState()
    if (st.off != HubOff.MOVED && st.off != HubOff.DUPLICATE_HUB) return
    val busy by vm.busy.collectAsState()
    HubGlass {
        Text("این دفتر از دستگاهِ دیگری آمده", fontWeight = FontWeight.Bold)
        Text(
            "تا وقتی نگویید این دستگاه حالا مرکز است، هیچ گوشی‌ای جواب نمی‌گیرد. اول دستگاهِ قبلی را خاموش کنید یا از حالتِ " +
                "«اصلی» بیرون بیاورید؛ اگر هنوز با همین دفتر جواب بدهد، این‌جا روشن نمی‌شود — دو مرکز با یک دفتر یعنی دو حسابِ جدا.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (WorkshopLinkAccess.canRunHub(access)) {
            BusyButton("مرکز اینجاست", onClick = { vm.confirmHere() }, busy = busy, busyText = "در حالِ گشتنِ شبکه…")
        }
    }
}

// ================================================================ «افزودنِ گوشی»

@Composable
fun HubPairPanel(vm: HubAdminViewModel, access: Access, qrSize: Dp = 220.dp) {
    val view by vm.pairing.collectAsState()
    val st by vm.hubState.collectAsState()
    val now by vm.now.collectAsState()
    val people by vm.people.collectAsState()
    val busy by vm.busy.collectAsState()
    val canRun = WorkshopLinkAccess.canRunHub(access)

    HubGlass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("افزودنِ گوشی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (view.open) HubPill("باز تا ${mmss(view.expiresAt - now)}", strong = true)
        }
        if (!view.open) {
            Text(
                "گوشیِ کارگر، گوشیِ صاحبِ کار یا تابلوی دیوار: یک بار با QR جفت می‌شود و بعد خودش وصل می‌ماند. پنجره ده دقیقه " +
                    "و برای یک گوشی باز است.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (view.closedReason.isNotBlank()) {
                Text(view.closedReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (canRun) {
                BusyButton("افزودنِ گوشی", onClick = { vm.openPairing() }, enabled = st.serving)
                if (!st.serving) Text("اول کارگاه را روشن کنید.", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("جفت کردنِ گوشی با مدیر است.", style = MaterialTheme.typography.labelSmall)
            }
            return@HubGlass
        }

        val waiting = view.waiting
        if (view.approvedName.isNotBlank()) {
            Text("«${view.approvedName}» جفت شد و کلیدش را می‌گیرد. برای گوشیِ بعدی دوباره «افزودنِ گوشی» را بزنید.")
        } else if (waiting == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                QrImage(view.qrText, size = qrSize)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("روی گوشی «کارگاهِ وصل ← اسکنِ QRِ کامپیوتر» را بزنید.", style = MaterialTheme.typography.bodyMedium)
                    Text("آیفون: دوربینِ خودِ گوشی را رو به QR بگیرید و «باز کن در خیاط‌یار» را بزنید.", style = MaterialTheme.typography.bodySmall)
                    if (view.allowCode) {
                        Text("بی دوربین (فقط کارگر و تابلو):", style = MaterialTheme.typography.labelMedium)
                        val host = PairingV2.parse(view.qrText)?.host.orEmpty()
                        Text("نشانی: ${host.toPersianDigits()}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "رمز: ${PairingV2.groupManualCode(view.code).toPersianDigits()}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else {
            Text("«${waiting.name}» می‌خواهد وصل شود", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${waiting.platform.ifBlank { "گوشی" }} • نسخهٔ ${waiting.appVersion.ifBlank { "؟" }.toPersianDigits()} • " +
                    "از ${view.waitingIp.toPersianDigits()} • با ${if (view.waitingVia == PairVia.QR) "QR" else "رمزِ دستی"} • " +
                    "می‌خواهد «${waiting.wantRole.label}» باشد",
                style = MaterialTheme.typography.bodySmall,
            )
            if (canRun) ApproveBox(
                wanted = waiting.wantRole,
                viaQr = view.waitingVia == PairVia.QR,
                people = people,
                busy = busy,
                onApprove = { role, person -> vm.approvePairing(role, person) },
                onReject = vm::rejectPairing,
            )
        }
        if (canRun) TextButton(onClick = vm::closePairing) { Text("بستنِ پنجره") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApproveBox(
    wanted: HubRole,
    viaQr: Boolean,
    people: List<String>,
    busy: Boolean,
    onApprove: (HubRole, String) -> Unit,
    onReject: () -> Unit,
) {
    var role by remember(wanted) { mutableStateOf(if (!viaQr && wanted == HubRole.OWNER) HubRole.WORKER else wanted) }
    var person by remember { mutableStateOf("") }
    Text("نقشِ این گوشی را شما تعیین می‌کنید:", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in HubRole.entries) {
            // گوشیِ صاحبِ کار فقط با QR — رمزِ دستیِ ۴۰ بیتی برای آن کافی نیست.
            if (r == HubRole.OWNER && !viaQr) continue
            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.label) }, shape = RoundedCornerShape(50))
        }
    }
    if (role == HubRole.WORKER) {
        Text("این گوشی دستِ کیست؟", style = MaterialTheme.typography.labelMedium)
        if (people.isEmpty()) {
            Text("هنوز خیاط، ناظر یا کارمندی در اطلاعاتِ پایه نیست.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (p in people) FilterChip(selected = person == p, onClick = { person = p }, label = { Text(p) }, shape = RoundedCornerShape(50))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BusyButton(
            "اجازه",
            onClick = { onApprove(role, person) },
            enabled = role != HubRole.WORKER || person.isNotBlank(),
            busy = busy,
            busyText = "در حالِ ثبت…",
        )
        OutlinedButton(onClick = onReject) { Text("رد") }
    }
}

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${(s / 60).fa()}:${(s % 60).toString().padStart(2, '0').toPersianDigits()}"
}

// ================================================================ دستگاه‌ها

@Composable
fun HubDevicesPanel(vm: HubAdminViewModel, access: Access) {
    val devices by vm.devices.collectAsState()
    val now by vm.now.collectAsState()
    val people by vm.people.collectAsState()
    var revoke by remember { mutableStateOf<LanDevice?>(null) }
    var edit by remember { mutableStateOf<LanDevice?>(null) }
    val canRun = WorkshopLinkAccess.canRunHub(access)
    HubGlass {
        val online = devices.count { it.active && (it.lastSeenAt ?: 0) > now - HubProtocol.ONLINE_MS }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("دستگاه‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            HubPill("${online.fa()} آنلاین")
        }
        if (devices.isEmpty()) Text("هنوز هیچ گوشی‌ای جفت نشده.", style = MaterialTheme.typography.bodySmall)
        for (d in devices.sortedWith(compareBy({ !it.active }, { -(it.lastSeenAt ?: 0) }))) {
            val on = d.active && (d.lastSeenAt ?: 0) > now - HubProtocol.ONLINE_MS
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dot(on)
                    Text(d.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    HubPill(HubRole.of(d.role)?.label ?: d.role)
                    if (d.personLabel.isNotBlank()) HubPill(d.personLabel)
                }
                Text(
                    if (!d.active) "دسترسی برداشته شد ${d.revokedAt?.let { PersianDate.shortWithTime(it) }.orEmpty()}"
                    else buildString {
                        append(if (on) "آنلاین" else "آخرین دیدار: ${d.lastSeenAt?.let { PersianDate.shortWithTime(it) } ?: "هنوز نه"}")
                        if (d.lastIp.isNotBlank()) append(" • ${d.lastIp.toPersianDigits()}")
                        if (d.appVersion.isNotBlank()) append(" • نسخهٔ ${d.appVersion.toPersianDigits()}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (d.active && HubAdminViewModel.outdated(d.appVersion)) {
                    Text(
                        "نسخهٔ این گوشی کهنه است (${d.appVersion.toPersianDigits()})؛ برنامه‌اش را به‌روز کنید.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (d.active && canRun) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { edit = d }) { Text("تغییرِ نقش") }
                        TextButton(onClick = { revoke = d }) { Text("قطعِ دسترسی") }
                    }
                }
            }
        }
    }
    revoke?.let { d ->
        AppAlertDialog(
            onDismissRequest = { revoke = null },
            title = { Text("دسترسیِ «${d.name}» برداشته شود؟") },
            text = { Text("کلیدش همین حالا پاک می‌شود و همگام‌سازیِ بعدی‌اش رد می‌شود. برای وصل شدنِ دوباره باید از نو جفت شود.") },
            confirmButton = { TextButton(onClick = { vm.revoke(d); revoke = null }) { Text("قطعِ دسترسی") } },
            dismissButton = { TextButton(onClick = { revoke = null }) { Text("نه") } },
        )
    }
    edit?.let { d ->
        var role by remember(d.deviceId) { mutableStateOf(HubRole.of(d.role) ?: HubRole.WORKER) }
        var person by remember(d.deviceId) { mutableStateOf(d.personLabel) }
        AppAlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("نقشِ «${d.name}»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in HubRole.entries) {
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.label) }, shape = RoundedCornerShape(50))
                        }
                    }
                    if (role == HubRole.WORKER) {
                        for (p in people) TextButton(onClick = { person = p }) { Text(if (person == p) "● $p" else p) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.changeRole(d, role, person); edit = null }) { Text("ثبت") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("نه") } },
        )
    }
}

// ================================================================ صندوقِ درخواست‌ها

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HubInboxPanel(vm: HubAdminViewModel, access: Access) {
    val pending by vm.pending.collectAsState()
    val busy by vm.busy.collectAsState()
    var worker by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf<String?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    if (!WorkshopLinkAccess.seesRequests(access)) return
    val shown = pending.filter { (worker == null || it.worker == worker) && (type == null || it.type == type) }
    HubGlass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("درخواست‌های گوشی‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            HubPill(pending.size.fa(), warn = pending.isNotEmpty())
        }
        if (pending.isEmpty()) {
            Text("چیزی منتظرِ تأیید نیست.", style = MaterialTheme.typography.bodySmall)
            return@HubGlass
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = worker == null, onClick = { worker = null }, label = { Text("همه") }, shape = RoundedCornerShape(50))
            for (w in pending.map { it.worker }.distinct()) {
                FilterChip(selected = worker == w, onClick = { worker = w }, label = { Text(w) }, shape = RoundedCornerShape(50))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = type == null, onClick = { type = null }, label = { Text("هر نوع") }, shape = RoundedCornerShape(50))
            for (t in pending.map { it.type }.distinct()) {
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(syncRequestLabel(t)) }, shape = RoundedCornerShape(50))
            }
        }
        val attendance = shown.count { (it.type == "ATTENDANCE_IN" || it.type == "ATTENDANCE_OUT") && !it.clockSuspect() }
        if (attendance > 0 && WorkshopLinkAccess.canDecide(access, "ATTENDANCE_IN")) {
            OutlinedButton(onClick = { confirmAll = true }, enabled = !busy) { Text("تأییدِ همهٔ حضورها (${attendance.fa()})") }
        }
        for (r in shown) RequestCard(r, access, busy, vm)
    }
    if (confirmAll) {
        AppAlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("همهٔ ورود و خروج‌ها تأیید شود؟") },
            text = {
                Text(
                    "به ترتیبِ زدنِ دکمه ثبت می‌شوند. آنهایی که ساعتِ گوشی‌شان معتبر نیست جدا می‌مانند تا خودتان یکی از دو ساعت را انتخاب کنید."
                )
            },
            confirmButton = { TextButton(onClick = { vm.approveAllAttendance(worker); confirmAll = false }) { Text("تأییدِ همه") } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("نه") } },
        )
    }
}

@Composable
private fun RequestCard(r: SyncRequest, access: Access, busy: Boolean, vm: HubAdminViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${syncRequestLabel(r.type)} — ${r.worker}", fontWeight = FontWeight.SemiBold)
        Text(r.summary + (r.note.takeIf { it.isNotBlank() && r.type == "NOTE" }?.let { ": $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
        Text(
            "${r.deviceName} • رسید ${PersianDate.shortWithTime(r.receivedAt ?: r.createdAt)}" +
                (r.clientAt?.let { " • زده‌شده ${PersianDate.shortWithTime(it)}" } ?: "") +
                if (r.protocol < 2) " • نسخهٔ قدیم" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!WorkshopLinkAccess.canDecide(access, r.type)) {
            Text(
                if (r.type == "SEWING_DONE") "تأییدِ «تمام شد» با مدیر است، یا با کسی که تیکِ «تسویهٔ کارمزدِ خیاط» دارد."
                else "تأییدِ این درخواست با مدیر است، یا با کسی که تیکِ «حضور و غیاب» دارد.",
                style = MaterialTheme.typography.labelSmall,
            )
            return@Column
        }
        if (r.clockSuspect()) {
            Text("ساعتِ گوشی معتبر نیست؛ کدام ساعت ثبت شود؟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.approve(r, RequestTime.PHONE) }, enabled = !busy && r.clientAt != null) { Text("ساعتِ گوشی") }
                OutlinedButton(onClick = { vm.approve(r, RequestTime.ARRIVAL) }, enabled = !busy) { Text("ساعتِ رسیدن") }
                TextButton(onClick = { vm.reject(r) }, enabled = !busy) { Text("رد") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BusyButton("تأیید و ثبت", onClick = { vm.approve(r) }, busy = busy)
                OutlinedButton(onClick = { vm.reject(r) }, enabled = !busy) { Text("رد") }
            }
        }
    }
}

// ================================================================ گوشی‌های نسخهٔ قدیم

@Composable
fun HubLegacyPanel(vm: HubAdminViewModel, access: Access, ip: String?, code: String) {
    val row by vm.hubRow.collectAsState()
    val now by vm.now.collectAsState()
    val mode = LegacyMode.of(row?.legacyV1)
    val allowed = LegacyMode.allowed(mode, row?.lastV1At, now)
    HubGlass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("گوشی‌های نسخهٔ قدیم (رمزِ شش‌رقمی)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            HubPill(if (allowed) "پذیرفته" else "بسته", warn = allowed)
        }
        Text(
            "گوشیِ ۱.۹ و ۱.۱۰ با نشانی و رمزِ شش‌رقمی وصل می‌شد؛ آن رمز روی وای‌فای بی‌رمز می‌رود. «خودکار» تا ۳۰ روز پس از " +
                "آخرین گوشیِ قدیم باز است و بعد خودش بسته می‌شود؛ گوشیِ قدیمِ بسته پیامِ «برنامه را به‌روز کنید» می‌گیرد." +
                (row?.lastV1At?.let { " آخرین گوشیِ قدیم: ${PersianDate.shortWithTime(it)}." } ?: " هنوز هیچ گوشیِ قدیمی نیامده."),
            style = MaterialTheme.typography.bodySmall,
        )
        if (WorkshopLinkAccess.canRunHub(access)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((m, label) in listOf(LegacyMode.AUTO to "خودکار", LegacyMode.ON to "باز", LegacyMode.OFF to "بسته")) {
                    FilterChip(selected = mode == m, onClick = { vm.setLegacy(m) }, label = { Text(label) }, shape = RoundedCornerShape(50))
                }
            }
        }
        if (allowed && ip != null && code.isNotBlank()) {
            Text("برای گوشیِ قدیم: نشانی ${ip.toPersianDigits()} • رمز ${code.toPersianDigits()}", fontWeight = FontWeight.SemiBold)
        }
    }
}
