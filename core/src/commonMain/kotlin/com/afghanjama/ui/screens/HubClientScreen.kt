package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import com.afghanjama.lan.DebtLine
import com.afghanjama.lan.HostAddress
import com.afghanjama.lan.HubClientState
import com.afghanjama.lan.HubRole
import com.afghanjama.lan.Outbox
import com.afghanjama.lan.OutboxState
import com.afghanjama.lan.PairingV2
import com.afghanjama.lan.RequestLine
import com.afghanjama.lan.WorkLine
import com.afghanjama.platform.LocalCodeScanner
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.components.BusyButton
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.HubClientViewModel
import com.afghanjama.ui.vm.HubDeepLink
import com.afghanjama.ui.vm.LedgerChoice
import com.afghanjama.data.entities.syncRequestLabel

/**
 * «کارگاهِ وصل» روی گوشی — کارگر، صاحب‌کار یا تابلوی دیوار.
 *
 * گوشی دفتر را نمی‌نویسد: هر دکمه یک کار در صف است که به کامپیوترِ کارگاه
 * می‌رود. وقتی وصل نیست، آخرین تصویر می‌ماند و صف شمرده می‌شود («در صفِ
 * ارسال: ۳»)؛ هیچ کاری بی‌صدا گم نمی‌شود.
 */
@Composable
fun HubClientScreen(vm: HubClientViewModel, onBack: (() -> Unit)?) {
    val ui by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val localData by vm.localData.collectAsState()
    val deepLink by HubDeepLink.pending.collectAsState()

    // تا وقتی صفحه باز است، هر ۱۵ ثانیه — و همین که باز شد، صفِ منتظر می‌رود.
    if (ui.paired) LaunchedEffect(ui.paired) { vm.poll() }

    AppScreen(title = "کارگاهِ وصل", onBack = onBack) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            message?.let { (text, error) ->
                item { MessageCard(text, error, vm::clearMessage) }
            }
            if (!ui.paired || ui.waitingApproval) {
                item { PairCard(vm, ui, busy, localData, deepLink) }
            } else {
                item { StatusCard(ui, onRefresh = { vm.refresh() }) }
                when (ui.role) {
                    HubRole.WORKER -> workerItems(this, vm, ui)
                    HubRole.OWNER -> ownerItems(this, vm, ui)
                    HubRole.WALL, null -> boardItems(this, ui)
                }
                if (ui.outbox.isNotEmpty()) {
                    item { Title("صفِ ارسال") }
                    items(ui.outbox.sortedByDescending { it.createdAt }, key = { it.key }) { o ->
                        Line(
                            o.summary,
                            "${PersianDate.shortWithTime(o.createdAt)} — ${Outbox.label(o)}",
                            warn = o.state == OutboxState.REJECTED || o.state == OutboxState.EXPIRED ||
                                o.state == OutboxState.ORPHANED,
                        )
                    }
                    if (ui.outbox.any { it.state != OutboxState.PENDING }) {
                        item { TextButton(onClick = vm::clearFinished) { Text("پاک کردنِ تمام‌شده‌ها از فهرست") } }
                    }
                }
                item { ForgetButton(ui.pending, vm::forget) }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ================================================================ جفت شدن

@Composable
private fun PairCard(vm: HubClientViewModel, ui: HubClientState, busy: Boolean, localData: Boolean?, deepLink: String?) {
    var role by remember { mutableStateOf(HubRole.WORKER) }
    var choice by remember { mutableStateOf(LedgerChoice.MOVE) }
    var host by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    val ledger = if (localData == true) choice else LedgerChoice.EMPTY

    if (ui.waitingApproval) {
        Glass {
            Text("منتظرِ اجازهٔ کارفرما…", fontWeight = FontWeight.Bold)
            Text(
                "روی کامپیوترِ کارگاه، در «کارگاهِ وصل»، نامِ این گوشی آمده است. کارفرما نقش و نفر را انتخاب می‌کند و «اجازه» می‌زند.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = vm::cancelPairing) { Text("لغو") }
        }
        return
    }

    fun onScanned(raw: String) {
        if (PairingV2.parse(raw) == null) {
            scanError = PairingV2.refusal(raw)
            return
        }
        scanError = null
        vm.pairWithLink(raw, role, ledger)
    }

    Glass {
        Text("اتصال به کامپیوترِ کارگاه", fontWeight = FontWeight.Bold)
        Text(
            "روی کامپیوتر «کارگاهِ وصل ← افزودنِ گوشی» را بزنید. این گوشی چه کاره است؟",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in HubRole.entries) {
                FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.label) }, shape = RoundedCornerShape(50))
            }
        }
        if (localData == true && role != HubRole.WALL) {
            LedgerChoiceBox(choice) { choice = it }
        }
    }

    deepLink?.let { raw ->
        val link = PairingV2.parse(raw)
        Glass {
            Text("پیوندِ جفت شدن رسید", fontWeight = FontWeight.Bold)
            Text(
                if (link == null) PairingV2.refusal(raw)
                else "با کامپیوترِ کارگاه در نشانیِ ${link.host.toPersianDigits()} جفت شود؟ (${role.label})",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (link != null) {
                    BusyButton("جفت شدن", onClick = { HubDeepLink.take(); vm.pairWithLink(raw, role, ledger) }, busy = busy, busyText = "در حالِ جفت شدن…")
                }
                OutlinedButton(onClick = { HubDeepLink.take() }) { Text("نه") }
            }
        }
    }

    val startScan = LocalCodeScanner.current?.rememberStart(
        prompt = "QRِ «افزودنِ گوشی» را روی کامپیوترِ کارگاه اسکن کنید",
        onCode = { onScanned(it) },
        onFailed = { scanError = it },
    )
    Glass {
        if (startScan != null) {
            BusyButton("اسکنِ QRِ کامپیوتر", onClick = { startScan() }, busy = busy, busyText = "در حالِ جفت شدن…", modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                "دوربینِ گوشی را رو به QRِ کامپیوتر بگیرید و «باز کن در خیاط‌یار» را بزنید.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        scanError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
    }

    Glass {
        Text("بی دوربین: نشانی و رمزِ ۱۲ رقمی", fontWeight = FontWeight.SemiBold)
        if (role == HubRole.OWNER) {
            Text(
                "گوشیِ صاحبِ کار فقط با QR جفت می‌شود — رمزِ دستی برای کارگر و تابلوست.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val hostBad = host.takeIf { it.isNotBlank() }?.let { (HostAddress.check(it) as? HostAddress.Result.Bad)?.reason }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = HostAddress.clean(it) },
                    label = { Text("نشانیِ کامپیوتر") },
                    placeholder = { Text("192.168.1.7") },
                    singleLine = true,
                    isError = hostBad != null,
                    supportingText = hostBad?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.digitsOnly().take(12) },
                label = { Text("رمزِ جفت شدن (۱۲ رقم)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            BusyButton(
                "جفت شدن",
                onClick = { vm.pairWithCode(host, code, role, ledger) },
                enabled = hostBad == null && host.isNotBlank() && PairingV2.normalizeManualCode(code) != null,
                busy = busy,
                busyText = "در حالِ جفت شدن…",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LedgerChoiceBox(choice: LedgerChoice, onPick: (LedgerChoice) -> Unit) {
    Text(
        "این گوشی دفترِ خودش را دارد. وقتی به کامپیوتر وصل شود:",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = choice == LedgerChoice.MOVE, onClick = { onPick(LedgerChoice.MOVE) })
        Text(
            "دادهٔ این گوشی را به کامپیوتر ببر — اول از تنظیمات «پشتیبان‌گیری کامل (با عکس‌ها)» را بگیرید و روی کامپیوتر در " +
                "«کارگاهِ وصل ← انتقالِ دفتر» باز کنید. دفترِ این گوشی پس از جفت شدن فقط‌خواندنی می‌شود.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = choice == LedgerChoice.SEPARATE, onClick = { onPick(LedgerChoice.SEPARATE) })
        Text(
            "جدا بماند — دفترِ این گوشی همان‌طور می‌ماند و با کامپیوتر یکی نمی‌شود.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// ================================================================ وضعیت

@Composable
private fun StatusCard(ui: HubClientState, onRefresh: () -> Unit) {
    Glass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(10.dp).background(
                    if (ui.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    CircleShape,
                )
            )
            Text(
                if (ui.connected) "وصل به ${ui.hubName.ifBlank { "کامپیوترِ کارگاه" }}" else "وصل نیست",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Pill(ui.role?.label ?: "")
        }
        if (ui.person.isNotBlank()) Text("به نامِ ${ui.person}", style = MaterialTheme.typography.bodySmall)
        ui.problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (ui.hubReadOnly) {
            Text("کامپیوترِ کارگاه فقط‌خواندنی است؛ کارِ تازه پذیرفته نمی‌شود.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            buildString {
                append("در صفِ ارسال: ${ui.pending.fa()}")
                ui.lastSyncAt?.let { append(" • آخرین داده: ${PersianDate.shortWithTime(it)}") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onRefresh, enabled = !ui.syncing) { Text(if (ui.syncing) "…" else "تازه‌سازی") }
    }
}

// ================================================================ کارگر

private fun workerItems(scope: androidx.compose.foundation.lazy.LazyListScope, vm: HubClientViewModel, ui: HubClientState) = with(scope) {
    val w = ui.snapshot?.worker
    item {
        Glass {
            Text(
                w?.insideSince?.let { "داخل از ${PersianDate.time(it)}" } ?: "بیرون",
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BusyButton("ورود", onClick = vm::checkIn, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = vm::checkOut, modifier = Modifier.weight(1f)) { Text("خروج") }
            }
            Text(
                "ساعتِ همین لحظه ثبت می‌شود، حتی اگر الان وصل نباشد؛ کارفرما تأیید می‌کند.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item { Title("کارِ زیرِ دستِ من") }
    if (w == null || w.inHand.isEmpty()) item { Hint(if (w == null) "هنوز چیزی از کامپیوتر نرسیده." else "کاری زیرِ دستِ شما نیست.") }
    items(w?.inHand.orEmpty(), key = { "w${it.id}" }) { job -> WorkCard(job) { qty -> vm.reportDone(job, qty) } }
    item { NoteCard(vm::sendNote) }
    if (!w?.requests.isNullOrEmpty()) {
        item { Title("درخواست‌های من") }
        items(w!!.requests, key = { "r${it.id}" }) { r ->
            Line("${syncRequestLabel(r.type)} — ${r.summary}", "${statusFa(r.status)}${r.note.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""}")
        }
    }
}

@Composable
private fun WorkCard(w: WorkLine, onDone: (Int) -> Unit) {
    var qty by remember(w.id) { mutableStateOf(w.qty.toString()) }
    val n = qty.toIntOrNull() ?: 0
    Glass {
        Text("${w.orderCode} ${w.designCode.takeIf { it.isNotBlank() }?.let { "($it)" } ?: ""} — ${w.qty.fa()} عدد", fontWeight = FontWeight.SemiBold)
        Text("کارمزدِ فی‌عدد ${w.unitWage.afn()} • از ${PersianDate.short(w.createdAt)}", style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = qty,
            onValueChange = { qty = it.digitsOnly() },
            label = { Text("چند عدد تمام شد؟") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        BusyButton("اعلامِ تحویل", onClick = { onDone(n) }, enabled = n in 1..w.qty, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NoteCard(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Glass {
        OutlinedTextField(value = text, onValueChange = { text = it.take(300) }, label = { Text("پیام به کارفرما") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { onSend(text); text = "" }, enabled = text.isNotBlank()) { Text("فرستادن") }
    }
}

// ================================================================ صاحب‌کار

private fun ownerItems(scope: androidx.compose.foundation.lazy.LazyListScope, vm: HubClientViewModel, ui: HubClientState) = with(scope) {
    val o = ui.snapshot?.owner
    if (o == null) {
        item { Hint("هنوز چیزی از کامپیوتر نرسیده.") }
        return@with
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("سفارشِ باز: ${o.openCount.fa()}")
            Pill("آمادهٔ تحویل: ${o.readyCount.fa()}")
            Pill("داخل: ${o.insideNow.size.fa()}")
        }
    }
    item { Title("درخواست‌های منتظر (${o.pending.size.fa()})") }
    if (o.pending.isEmpty()) item { Hint("چیزی منتظرِ تأیید نیست.") }
    items(o.pending, key = { "p${it.id}" }) { r -> PendingCard(r, vm) }
    if (o.overdue.isNotEmpty()) {
        item { Title("دیرکرده (${o.overdue.size.fa()})") }
        items(o.overdue, key = { "o${it.code}" }) { l -> Line("${l.code} — ${l.customer}", "${l.stage} • موعد ${PersianDate.short(l.dueDate)}", warn = true) }
    }
    item { Title("موعدِ امروز (${o.dueToday.size.fa()})") }
    if (o.dueToday.isEmpty()) item { Hint("امروز سفارشی موعد ندارد.") }
    items(o.dueToday, key = { "d${it.code}" }) { l -> Line("${l.code} — ${l.customer}", l.stage) }
    if (o.debtToday.isNotEmpty()) {
        item { Title("پیگیریِ طلبِ امروز") }
        items(o.debtToday, key = { "debt${it.customer}" }) { d -> DebtCard(d, vm) }
    }
    if (o.insideNow.isNotEmpty()) item { Hint("داخل: " + o.insideNow.joinToString("، ")) }
}

@Composable
private fun PendingCard(r: RequestLine, vm: HubClientViewModel) {
    Glass {
        Text("${syncRequestLabel(r.type)} — ${r.worker}", fontWeight = FontWeight.SemiBold)
        Text(r.summary, style = MaterialTheme.typography.bodySmall)
        if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall)
        Text(
            "${r.device} • رسید ${PersianDate.shortWithTime(r.receivedAt)}" +
                (r.pressedAt?.let { " • زده‌شده ${PersianDate.shortWithTime(it)}" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
        )
        if (r.clockSuspect) {
            Text("ساعتِ گوشیِ کارگر معتبر نیست؛ کدام ساعت ثبت شود؟", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.approve(r, "PHONE") }, enabled = r.pressedAt != null) { Text("ساعتِ گوشی") }
                OutlinedButton(onClick = { vm.approve(r, "ARRIVAL") }) { Text("ساعتِ رسیدن") }
                TextButton(onClick = { vm.reject(r) }) { Text("رد") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BusyButton("تأیید و ثبت", onClick = { vm.approve(r) }, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.reject(r) }, modifier = Modifier.weight(1f)) { Text("رد") }
            }
        }
    }
}

@Composable
private fun DebtCard(d: DebtLine, vm: HubClientViewModel) {
    Glass {
        Text("${d.customer} — ${d.amount.afn()}", fontWeight = FontWeight.SemiBold)
        Text(d.reason + (if (d.contactedToday) " • امروز پیگیری شد" else ""), style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.followUp(d, "PROMISED", 3) }) { Text("قول داد") }
            OutlinedButton(onClick = { vm.followUp(d, "NO_ANSWER") }) { Text("جواب نداد") }
            OutlinedButton(onClick = { vm.followUp(d, "TALKED") }) { Text("صحبت شد") }
        }
    }
}

// ================================================================ تابلو

private fun boardItems(scope: androidx.compose.foundation.lazy.LazyListScope, ui: HubClientState) = with(scope) {
    val rows = ui.snapshot?.board.orEmpty()
    item { Title("در حالِ دوخت (${rows.size.fa()})") }
    if (rows.isEmpty()) item { Hint("کاری روی تابلو نیست.") }
    items(rows, key = { "b${it.orderCode}${it.tailor}" }) { r ->
        Line(
            "${r.orderCode} — ${r.design} — ${r.qty.fa()} عدد",
            "${r.tailor} • ${r.days.fa()} روز" + (r.dueIn?.let { if (it < 0) " • ${(-it).fa()} روز دیر" else " • ${it.fa()} روز تا موعد" } ?: ""),
            warn = r.late,
        )
    }
}

// ================================================================ تکه‌ها

@Composable
private fun Glass(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun Pill(text: String) {
    if (text.isBlank()) return
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Line(title: String, sub: String, warn: Boolean = false) {
    Glass {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageCard(text: String, error: Boolean, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = onDismiss) { Text("باشه") }
        }
    }
}

@Composable
private fun ForgetButton(pending: Int, onForget: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    TextButton(onClick = { ask = true }, modifier = Modifier.fillMaxWidth()) { Text("جدا کردنِ این گوشی از کامپیوترِ کارگاه") }
    if (ask) {
        AppAlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("جدا شود؟") },
            text = {
                Text(
                    "کلیدِ اتصالِ این گوشی پاک می‌شود و برای وصل شدنِ دوباره باید از نو جفت شود." +
                        if (pending > 0) " ${pending.fa()} کار هنوز فرستاده نشده؛ در فهرست می‌ماند و پس از جفت شدنِ دوباره با همان نفر فرستاده می‌شود." else ""
                )
            },
            confirmButton = { TextButton(onClick = { ask = false; onForget() }) { Text("جدا کن") } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("نه") } },
        )
    }
}

private fun statusFa(s: String): String = when (s) {
    "PENDING" -> "منتظرِ تأیید"
    "APPROVED" -> "تأیید شد"
    "REJECTED" -> "رد شد"
    else -> s
}
