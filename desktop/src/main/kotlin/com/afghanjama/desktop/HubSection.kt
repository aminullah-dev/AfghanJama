package com.afghanjama.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afghanjama.data.repo.Repo
import com.afghanjama.desktop.data.DesktopBackup
import com.afghanjama.desktop.data.DesktopLanHost
import com.afghanjama.desktop.data.backupsDir
import com.afghanjama.desktop.data.dataDir
import com.afghanjama.desktop.platform.DesktopHubSystem
import com.afghanjama.desktop.platform.HubDeskPrefs
import com.afghanjama.lan.HubProtocol
import com.afghanjama.lan.LanHub
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.components.BusyButton
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.screens.HubDevicesPanel
import com.afghanjama.ui.screens.HubGlass
import com.afghanjama.ui.screens.HubInboxPanel
import com.afghanjama.ui.screens.HubLegacyPanel
import com.afghanjama.ui.screens.HubMessage
import com.afghanjama.ui.screens.HubMovedCard
import com.afghanjama.ui.screens.HubPairPanel
import com.afghanjama.ui.screens.HubPill
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.HubAdminViewModel
import com.afghanjama.ui.vm.WorkshopLinkAccess
import com.afghanjama.ui.vm.WorkshopLinkViewModel
import com.afghanjama.ui.vm.hubOffText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * «کارگاهِ وصل» — کامپیوتر به‌عنوانِ مادرِ کارگاه (طرح، B.10).
 *
 * سرِ صفحه یک قرص: «کارگاه روشن است — n دستگاه وصل» یا «کارگاه خاموش است» با
 * دلیل. سمتِ راست (آغازِ راست‌به‌چپ، جایی که چشم شروع می‌کند): «افزودنِ گوشی» و
 * صندوقِ درخواست‌ها. سمتِ چپ: دستگاه‌ها، گوشی‌های نسخهٔ قدیم، دیوارِ آتش و
 * عیب‌یابی، بیدار ماندن (مک) و انتقالِ دفتر از گوشی. پنجرهٔ باریک همه را زیرِ
 * هم می‌چیند.
 */
@Composable
internal fun HubSection(
    repo: Repo,
    access: Access,
    onBack: () -> Unit,
    /** برنامه همیشه [LanHub.shared]؛ عکس‌برداری هابِ خودش را روی loopback می‌دهد. */
    hub: LanHub = LanHub.shared,
    lan: com.afghanjama.lan.LanHost = DesktopLanHost(),
) {
    val settings = LocalSettings.current
    val link: WorkshopLinkViewModel = viewModel { WorkshopLinkViewModel(repo, lan, hub) }
    val admin: HubAdminViewModel = viewModel { HubAdminViewModel(repo, hub) }
    val ui by link.ui.collectAsState()
    LaunchedEffect(Unit) { link.load(settings) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1000.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(link, admin, access, ui.mode, ui.serving, ui.hubOff, ui.ip, ui.alternatives)
            link.ui.value.message?.let { m ->
                HubGlass {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m, Modifier.weight(1f), color = if (ui.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        TextButton(onClick = link::clearMessage) { Text("باشه") }
                    }
                }
            }
            HubMessage(admin)
            HubMovedCard(admin, access)
            if (ui.mode != DeviceMode.MAIN) {
                HubGlass {
                    Text(
                        "این کامپیوتر هنوز مرکزِ کارگاه نیست. وقتی باشد، گوشیِ کارگر، گوشیِ صاحبِ کار و تابلوی دیوار به آن وصل " +
                            "می‌شوند و دفتر فقط همین‌جا نوشته می‌شود.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                MoveLedgerPanel(repo, access)
                return@Column
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HubPairPanel(admin, access, qrSize = 240.dp)
                        HubInboxPanel(admin, access)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HubDevicesPanel(admin, access)
                        FirewallPanel(admin)
                        if (DesktopHubSystem.onMac) PowerPanel()
                        HubLegacyPanel(admin, access, ui.ip, ui.code)
                        MoveLedgerPanel(repo, access)
                    }
                }
            } else {
                HubPairPanel(admin, access, qrSize = 220.dp)
                HubInboxPanel(admin, access)
                HubDevicesPanel(admin, access)
                FirewallPanel(admin)
                if (DesktopHubSystem.onMac) PowerPanel()
                HubLegacyPanel(admin, access, ui.ip, ui.code)
                MoveLedgerPanel(repo, access)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(
    link: WorkshopLinkViewModel,
    admin: HubAdminViewModel,
    access: Access,
    mode: DeviceMode,
    serving: Boolean,
    off: com.afghanjama.lan.HubOff?,
    ip: String?,
    alternatives: List<String>,
) {
    val settings = LocalSettings.current
    val devices by admin.devices.collectAsState()
    val now by admin.now.collectAsState()
    // همان شمارِ کارتِ «دستگاه‌ها» — یک تعریف برای «آنلاین».
    val online = devices.count { it.active && (it.lastSeenAt ?: 0) > now - HubProtocol.ONLINE_MS }
    val canRun = WorkshopLinkAccess.canRunHub(access)
    // نشانیِ تازه (طرح، B.5): گوشی‌ها با hubId خودشان پیدایش می‌کنند؛ فقط گفته می‌شود.
    var moved by remember { mutableStateOf(false) }
    LaunchedEffect(ip) {
        val last = HubDeskPrefs.lastIp(settings)
        if (ip != null) {
            moved = last.isNotBlank() && last != ip && serving
            HubDeskPrefs.setLastIp(settings, ip)
        }
    }
    HubGlass {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("کارگاهِ وصل", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            when {
                mode != DeviceMode.MAIN -> HubPill("مرکز نیست")
                serving -> HubPill("کارگاه روشن است — ${online.fa()} دستگاه وصل", strong = true)
                else -> HubPill("کارگاه خاموش است", warn = true)
            }
        }
        if (mode == DeviceMode.MAIN) {
            Text(
                "نشانیِ این کامپیوتر: ${(ip ?: "—").toPersianDigits()} • درگاه ${HubProtocol.PORT.fa()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (alternatives.isNotEmpty()) {
                Text(
                    "نشانی‌های دیگر: " + alternatives.joinToString("، ") { it.toPersianDigits() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!serving) hubOffText(off)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (moved) {
                Text(
                    "نشانیِ این کامپیوتر عوض شد؛ گوشی‌ها خودشان دوباره پیدایش می‌کنند. برای پایداری، در مودم برای این کامپیوتر " +
                        "نشانیِ ثابت (DHCP reservation) بگذارید.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (canRun) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode != DeviceMode.MAIN) {
                    BusyButton("این کامپیوتر مرکزِ کارگاه است", onClick = { link.becomeMain(settings) })
                } else {
                    OutlinedButton(onClick = { if (serving) link.stopServing() else link.startServing(settings) }) {
                        Text(if (serving) "خاموش کردنِ کارگاه" else "روشن کردنِ کارگاه")
                    }
                    TextButton(onClick = { link.disconnect(settings) }) { Text("برگشت به حالتِ تک‌دستگاه") }
                }
            }
        } else {
            Text("روشن و خاموش کردنِ کارگاه و جفت کردنِ گوشی با مدیر است.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** دیوارِ آتش و عیب‌یابی (طرح، B.9) — آنچه سیستم می‌تواند ببندد، صادقانه. */
@Composable
private fun FirewallPanel(admin: HubAdminViewModel) {
    val seen by admin.seen.collectAsState()
    val pairing by admin.pairing.collectAsState()
    val now by admin.now.collectAsState()
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    HubGlass {
        Text("دیوارِ آتش و عیب‌یابی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val last = seen.lastAt
        Text(
            "آخرین تلاشِ گوشی برای وصل شدن: " + (last?.let { PersianDate.shortWithTime(it) } ?: "هرگز"),
            style = MaterialTheme.typography.bodyMedium,
        )
        // پنجره دو دقیقه باز است و هیچ گوشی‌ای نرسیده: به احتمالِ زیاد دیوارِ آتش.
        val openedAt = pairing.expiresAt - HubProtocol.PAIR_WINDOW_MS
        if (pairing.open && now - openedAt > 2 * 60_000L && (last == null || last < openedAt)) {
            Text(
                "هیچ گوشی‌ای به این کامپیوتر نرسیده — دیوارِ آتش را ببینید، و اینکه گوشی و کامپیوتر روی یک وای‌فای باشند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        when {
            DesktopHubSystem.onWindows -> {
                Text(
                    "ویندوز بارِ اول می‌پرسد که خیاط‌یار به شبکه دسترسی داشته باشد یا نه. «شبکه‌های خصوصی» باید تیک داشته باشد. " +
                        "اگر وای‌فایِ کارگاه در ویندوز «عمومی» (Public) است، گوشی‌ها بسته می‌مانند: در تنظیماتِ وای‌فای آن را «خصوصی» " +
                        "(Private) کنید. اگر آن پرسش رد شده، دکمهٔ پایین دو قاعدهٔ اجازه (درگاهِ ۸۷۹۷ و خودِ برنامه) می‌سازد — ویندوز " +
                        "یک بار برای مدیر می‌پرسد.",
                    style = MaterialTheme.typography.bodySmall,
                )
                BusyButton("اجازه در دیوارِ آتش", busy = busy, busyText = "منتظرِ ویندوز…", onClick = {
                    busy = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { DesktopHubSystem.allowOnWindowsFirewall() }
                        busy = false
                        result = r.fold({ "قاعده‌ها ساخته شدند. حالا از گوشی دوباره امتحان کنید." }, { "ساخته نشد: ${it.message}" })
                    }
                })
            }
            DesktopHubSystem.onMac -> {
                Text(
                    "دیوارِ آتشِ مک معمولاً خاموش است. اگر روشن باشد، مک بارِ اول می‌پرسد «اتصال‌های ورودی پذیرفته شود؟» — «اجازه» " +
                        "بزنید. نسخهٔ امضاشدهٔ خیاط‌یار خودبه‌خود پذیرفته می‌شود؛ نسخهٔ آزمایشیِ بی‌امضا ممکن است پس از هر به‌روزرسانی " +
                        "دوباره بپرسد.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    result = if (DesktopHubSystem.openMacFirewallSettings()) null else "تنظیماتِ مک باز نشد؛ از «تنظیماتِ سیستم ← شبکه ← دیوارِ آتش» ببینید."
                }) { Text("باز کردنِ تنظیماتِ دیوارِ آتش") }
            }
        }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/** «تا وقتی کارگاه روشن است، کامپیوتر نخوابد» — فقط مک. */
@Composable
private fun PowerPanel() {
    val settings = LocalSettings.current
    var on by remember { mutableStateOf(HubDeskPrefs.keepAwake(settings)) }
    HubGlass {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("تا وقتی کارگاه روشن است، کامپیوتر نخوابد", fontWeight = FontWeight.SemiBold)
                Text(
                    "مکِ خوابیده به گوشی‌ها جواب نمی‌دهد؛ کارهایشان در صفِ گوشی می‌ماند تا بیدار شود. صفحه می‌تواند خاموش شود.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = on, onCheckedChange = {
                on = it
                HubDeskPrefs.setKeepAwake(settings, it)
                DesktopHubSystem.keepAwake(it && LanHub.shared.state.value.serving)
            })
        }
    }
}

/**
 * «انتقالِ دفتر از گوشی به کامپیوتر» (طرح، B.8؛ نقدِ ۷).
 *
 * دفترِ پُرِ این کامپیوتر هرگز بی‌صدا جایگزین نمی‌شود: پیش از بازیابی، از همین
 * دفتر یک پشتیبانِ کامل در پوشهٔ پشتیبان‌ها گرفته می‌شود؛ اگر نشد، بازیابی
 * انجام نمی‌شود. سرورِ کارگاه پیش از جایگزینی خاموش می‌شود.
 */
@Composable
private fun MoveLedgerPanel(repo: Repo, access: Access) {
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    if (!WorkshopLinkAccess.canRunHub(access)) return
    HubGlass {
        Text("انتقالِ دفتر از گوشی به کامپیوتر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "۱. روی گوشیِ اصلیِ امروز: تنظیمات ← «پشتیبان‌گیری کامل (با عکس‌ها)»، و فایل را به این کامپیوتر بیاورید.\n" +
                "۲. همین‌جا آن فایل را باز کنید. اگر این کامپیوتر خودش دفتر دارد، اول از آن پشتیبان گرفته می‌شود.\n" +
                "۳. برنامه را ببندید و باز کنید؛ اگر دفترِ گوشی قبلاً مرکز بود، «مرکز اینجاست» را بزنید.\n" +
                "۴. روی گوشی «کارگاهِ وصل ← گوشیِ صاحبِ کار» را بزنید و QRِ همین کامپیوتر را اسکن کنید؛ دفترِ گوشی " +
                "فقط‌خواندنی می‌شود. لایسنس روی دستگاهِ اصلی لازم است: این کامپیوتر کلیدِ خودش را می‌خواهد.",
            style = MaterialTheme.typography.bodySmall,
        )
        BusyButton("باز کردنِ پشتیبانِ گوشی…", busy = busy, busyText = "در حالِ انتقال…", onClick = {
            val chooser = JFileChooser(dataDir())
            chooser.dialogTitle = "پشتیبانِ کاملِ گوشی را انتخاب کنید"
            chooser.fileFilter = FileNameExtensionFilter("پشتیبانِ خیاط‌یار (*.ajb)", "ajb")
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) ask = chooser.selectedFile
        })
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    ask?.let { source ->
        AppAlertDialog(
            onDismissRequest = { ask = null },
            title = { Text("دفترِ این کامپیوتر با دفترِ گوشی جایگزین شود؟") },
            text = {
                Text(
                    "«${source.name}» جای دفترِ این کامپیوتر را می‌گیرد. اگر این کامپیوتر داده دارد، اول یک پشتیبانِ کامل از آن در " +
                        "پوشهٔ پشتیبان‌ها گرفته می‌شود؛ اگر نشد، چیزی جایگزین نمی‌شود."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ask = null
                    busy = true
                    scope.launch {
                        message = withContext(Dispatchers.IO) { moveLedger(repo, source) }
                        busy = false
                    }
                }) { Text("انتقال") }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("نه") } },
        )
    }
}

/** خودِ انتقال؛ متنِ نتیجه برای صفحه. */
private suspend fun moveLedger(repo: Repo, source: File): String {
    if (runCatching { repo.hasAnyWorkshopData() }.getOrDefault(true)) {
        val dir = backupsDir().apply { mkdirs() }
        val safety = File(dir, "KhayatYar-before-move-${System.currentTimeMillis()}.ajb")
        val saved = DesktopBackup.backupWithPhotos(safety)
        if (saved.isFailure) {
            return "پشتیبانِ دفترِ این کامپیوتر گرفته نشد (${saved.exceptionOrNull()?.message}); چیزی جایگزین نشد."
        }
    }
    // سرورِ کارگاه همین دفتر را می‌نویسد؛ پیش از جایگزینی خاموش.
    LanHub.shared.stopForRestore()
    return DesktopBackup.restoreFrom(source).fold(
        { photos ->
            "دفترِ گوشی منتقل شد" + (if (photos > 0) " با ${photos.fa()} عکس" else "") +
                ". برنامه را ببندید و دوباره باز کنید."
        },
        { "انتقال نشد: ${it.message}" + if (DesktopBackup.ledgerClosed) " برنامه را ببندید و دوباره باز کنید." else "" },
    )
}
