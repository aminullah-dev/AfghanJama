package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.afghanjama.data.CardScan
import com.afghanjama.pdf.EmployeeCardInfo
import com.afghanjama.pdf.employeeCardSheets
import com.afghanjama.platform.LocalCodeScanner
import com.afghanjama.platform.LocalDocs
import com.afghanjama.platform.LocalReminders
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.vm.AttendanceViewModel
import com.afghanjama.ui.vm.CardHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ایستگاهِ اسکن — بالای صفحهٔ حضور و غیاب.
 *
 * سه راه به یک جا می‌رسند: دوربینِ گوشی، اسکنرِ USBِ کمپیوتر (که خودش را
 * کیبورد جا می‌زند و آخرِ کد Enter می‌زند)، و تایپِ دستیِ کدِ زیرِ QR.
 * هر سه `vm.scanCard` را صدا می‌زنند و نتیجه در همین کارت نشان داده
 * می‌شود.
 *
 * **اسکنرِ USB چرا کار می‌کند بی هیچ کدِ سکو-ویژه‌ای.** اسکنر فقط تایپ
 * می‌کند و Enter می‌زند؛ فیلدِ تک‌خطی Enter را «انجام» می‌گیرد. تنها
 * شرطش این است که فیلد فوکوس داشته باشد — پس روی سکوی بی‌دوربین
 * (کمپیوتر) فیلد خودش فوکوس می‌گیرد و بعد از هر ثبت هم نگهش می‌دارد.
 * روی گوشی این کار را نمی‌کند، چون فوکوس یعنی کیبوردی که نیمی از صفحه
 * را می‌پوشاند.
 */
@Composable
fun AttendanceScanStation(
    vm: AttendanceViewModel,
    onOpenCards: () -> Unit,
) {
    val feedback by vm.scan.collectAsState()
    val reminders = LocalReminders.current
    val scanner = LocalCodeScanner.current
    var typed by remember { mutableStateOf("") }
    var continuous by remember { mutableStateOf(false) }
    var cameraMsg by remember { mutableStateOf<String?>(null) }
    // آخرین اسکن از دوربین بود؟ «پیاپی» فقط دوربین را دوباره باز می‌کند.
    var fromCamera by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun submit(raw: String, camera: Boolean) {
        fromCamera = camera
        cameraMsg = null
        vm.scanCard(raw) { r ->
            if (r.checkedIn) reminders?.scheduleShift(r.name) else reminders?.cancelShift(r.name)
        }
    }

    val startCamera = scanner?.rememberStart(
        prompt = "کارتِ کارمند را جلوی دوربین بگیرید",
        onCode = { submit(it, camera = true) },
        onFailed = { msg ->
            fromCamera = false
            cameraMsg = msg
        },
    )

    // کمپیوتر: فیلد همیشه آمادهٔ اسکنرِ USB.
    LaunchedEffect(scanner) {
        if (scanner == null) runCatching { focus.requestFocus() }
    }

    // اسکنِ پیاپی: نتیجه یک لحظه دیده شود، بعد دوربین دوباره باز شود.
    // کارتِ نامعتبر زنجیره را می‌بُرد تا مدیر ببیند چه شد.
    LaunchedEffect(feedback?.seq) {
        val f = feedback ?: return@LaunchedEffect
        if (scanner == null) runCatching { focus.requestFocus() }
        val ok = f.result is CardScan.Recorded || f.result is CardScan.TooSoon
        if (continuous && fromCamera && ok && startCamera != null) {
            delay(CONTINUOUS_PAUSE_MS)
            startCamera()
        }
    }

    // یک ستون، نه دو فرزندِ جدا: `LazyColumn` فرزندانِ یک ردیف را بی فاصله
    // روی هم می‌چیند و بنرِ نتیجه به کارت می‌چسبید.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "اسکنِ کارتِ کارمند",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (scanner != null) "کارت را با دوربین بخوانید؛ ورود یا خروج خودش تشخیص داده می‌شود."
                        else "اسکنرِ USB را وصل کنید و کارت را بخوانید — کد اینجا نوشته و ثبت می‌شود.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenCards) {
                    Icon(Icons.Default.Badge, contentDescription = null)
                    Text("  کارت‌ها")
                }
            }

            if (startCamera != null) {
                Button(
                    onClick = { startCamera() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Text("  اسکن با دوربین")
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("اسکنِ پیاپی", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "سرِ صبح: بعد از هر ثبت، دوربین برای نفرِ بعدی باز می‌شود.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = continuous, onCheckedChange = { continuous = it })
                }
            }

            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(if (scanner == null) "کدِ کارت (اسکنر یا تایپ)" else "یا کدِ زیرِ QR را بنویسید") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    submit(typed, camera = false)
                    typed = ""
                }),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            submit(typed, camera = false)
                            typed = ""
                        },
                        enabled = typed.isNotBlank(),
                    ) { Icon(Icons.Default.Check, contentDescription = "ثبت") }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )

            cameraMsg?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }

        feedback?.let { f -> ScanResultBanner(f.result, onDismiss = vm::clearScan) }
    }
}

@Composable
private fun ScanResultBanner(result: CardScan, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (container, onContainer) = when (result) {
        is CardScan.Recorded ->
            if (result.checkedIn) cs.primaryContainer to cs.onPrimaryContainer
            else cs.secondaryContainer to cs.onSecondaryContainer
        is CardScan.TooSoon -> cs.tertiaryContainer to cs.onTertiaryContainer
        CardScan.NotACard, CardScan.Unknown -> cs.errorContainer to cs.onErrorContainer
    }
    val (title, body) = when (result) {
        is CardScan.Recorded ->
            (if (result.checkedIn) "ورود ثبت شد" else "خروج ثبت شد") to
                "${result.name} — ساعت ${PersianDate.shortWithTime(result.at)}"
        is CardScan.TooSoon ->
            "همین حالا ثبت شده بود" to
                "${result.name} ${if (result.isIn) "داخل است" else "بیرون رفته"}؛ " +
                "اسکنِ دوباره تا یک دقیقه ثبت نمی‌شود."
        CardScan.NotACard ->
            "این کارتِ کارمند نیست" to
                "QRِ دیگری خوانده شد، یا یک رقمِ کد درست خوانده نشد. دوباره اسکن کنید."
        CardScan.Unknown ->
            "صاحبِ این کارت پیدا نشد" to
                "این نفر از «اطلاعات پایه» پاک شده است. اگر هنوز در کارگاه کار می‌کند، " +
                "دوباره اضافه‌اش کنید و کارتِ نو چاپ کنید — کارتِ قبلی به ردیفِ پاک‌شده اشاره می‌کند."
    }
    AccentCard(container = container, onClick = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onContainer)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = onContainer)
    }
}

/**
 * کارت‌های کارمندان — دیدن، و چاپ یا فرستادنِ PDF.
 *
 * PDF از چیدمانِ مشترک (`employeeCardSheets`) ساخته می‌شود، پس روی گوشی
 * و کمپیوتر یک کاغذ بیرون می‌آید.
 */
@Composable
fun EmployeeCardsDialog(
    vm: AttendanceViewModel,
    onDismiss: () -> Unit,
) {
    val holders by vm.cardHolders.collectAsState()
    val docs = LocalDocs.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<CardHolder?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun share(list: List<CardHolder>, fileName: String) {
        if (list.isEmpty() || busy) return
        busy = true
        error = null
        scope.launch {
            runCatching {
                val doc = employeeCardSheets(
                    list.map { EmployeeCardInfo(it.name, it.kind.label, it.detail, it.code) },
                    CompanyPrefs.shopName(settings),
                    docs.measurer,
                )
                docs.share(doc, fileName, "کارتِ کارمندان")
            }.onFailure { error = "PDF ساخته نشد: ${it.message ?: "خطای ناشناخته"}" }
            busy = false
        }
    }

    preview?.let { h ->
        AppAlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(h.name) },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QrImage(h.code, size = 220.dp)
                    Text(h.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(h.kind.label, h.detail).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { share(listOf(h), "card-${h.code}.pdf") }, enabled = !busy) {
                    Text("PDFِ این کارت")
                }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("بستن") } },
        )
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کارت‌های کارمندان") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (holders.isEmpty()) {
                    Text(
                        "هنوز کسی نیست. خیاط‌ها، ناظرها و کارکنان را از «اطلاعات پایه» اضافه کنید؛ " +
                            "کارتِ هر کدام خودش ساخته می‌شود.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "هر کارت یک QR دارد که نوع و شناسهٔ کارمند را می‌بَرد، نه نامش را — " +
                            "با عوض شدنِ نام باطل نمی‌شود. ${holders.size.fa()} کارت، هشت‌تا در هر برگهٔ A4.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { share(holders, "employee-cards.pdf") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text(if (busy) "  در حالِ ساخت…" else "  چاپ یا فرستادنِ همهٔ کارت‌ها (PDF)")
                    }
                    error?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    holders.forEach { h ->
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(h.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(h.kind.label, h.detail).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { preview = h }) { Text("نمایشِ بزرگ") }
                            }
                            QrImage(h.code, size = 72.dp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

/** مکثِ «اسکنِ پیاپی» — آن‌قدر که نامِ ثبت‌شده خوانده شود. */
private const val CONTINUOUS_PAUSE_MS = 1_600L
