package com.afghanjama.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.afghanjama.AppInfo
import com.afghanjama.AppVersion
import com.afghanjama.data.CsvExport
import com.afghanjama.data.ResetPlan
import com.afghanjama.data.repo.Repo
import com.afghanjama.desktop.data.DeskPrefs
import com.afghanjama.desktop.data.DesktopReset
import com.afghanjama.platform.LocalFileExport
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.SalePrefs
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.nav.allowedFor
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.Permissions
import com.afghanjama.ui.vm.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLEncoder

/**
 * «تنظیمات» روی مک و ویندوز.
 *
 * **چرا لازم شد.** تا ۱.۹ کامپیوتر بخشِ تنظیمات نداشت: هشدارهایی که به
 * `Routes.SETTINGS` می‌بردند (از جمله «هنوز هیچ بکاپی گرفته نشده») و
 * چرخ‌دندهٔ «سفارش‌ها» هیچ کاری نمی‌کردند، خروجیِ اکسل فقط روی گوشی بود،
 * و «پاک‌کردنِ کارها و حساب‌ها» هم.
 *
 * هر کارت همان قاعدهٔ دسترسیِ گوشی را دارد: سیاستِ فروش، قفلِ خودکار و
 * پاک‌کردن فقط برای مدیر؛ خروجی با تیکِ «پشتیبان‌گیری و خروجی»؛ حساب و
 * پشتیبانی برای همه.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsSection(
    repo: Repo,
    role: UserRole,
    access: Access,
    auth: AuthViewModel?,
    go: (Section) -> Unit,
    onBack: () -> Unit,
) {
    val manager = access.isManager
    AppScreen(title = "تنظیمات", onBack = onBack) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxWidth()) {
            val wide = maxWidth >= 980.dp
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val first: @Composable ColumnScope.() -> Unit = {
                    if (manager) SalePolicyCard()
                    if (Permissions.canBackup(role)) ExportCard(repo)
                    if (Section.Backup.allowedFor(access)) BackupLinkCard(go)
                    ManageLinksCard(access, go)
                }
                val second: @Composable ColumnScope.() -> Unit = {
                    if (auth != null) AccountCard(auth)
                    if (manager) IdleLockCard()
                    SupportCard()
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = first)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = second)
                    }
                } else {
                    first()
                    second()
                }
                // آخرین کارت، چون برگشت ندارد — همان جای گوشی.
                if (Permissions.canResetData(role)) ResetCard(repo)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CardHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** «فروشِ بیشتر از موجودی» — همان کلید و همان متنِ تنظیماتِ گوشی. */
@Composable
private fun SalePolicyCard() {
    val settings = LocalSettings.current
    var allow by remember { mutableStateOf(SalePrefs.allowNegativeStock(settings)) }
    AppCard {
        CardTitle("فروشِ بیشتر از موجودی (کسری)")
        CardHint(
            if (allow)
                "فروش و تحویل حتی وقتی انبار خالی است انجام می‌شود و کمبود " +
                    "به‌عنوانِ «کسری» با رنگِ سرخ نشان داده می‌شود. بهای تمام‌شده " +
                    "با آخرین میانگین برآورد می‌شود و با ورودِ بعدیِ همان کالا " +
                    "خودش اصلاح می‌گردد."
            else
                "فروشِ بیشتر از موجودی رد می‌شود. امن‌تر است، ولی اگر جنسی " +
                    "پیش از ثبتِ ورودش فروخته شود، فروشنده گیر می‌کند."
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(
                checked = allow,
                onCheckedChange = {
                    allow = it
                    SalePrefs.setAllowNegativeStock(settings, it)
                }
            )
            Text(if (allow) "اجازه هست" else "اجازه نیست", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * خروجیِ اکسل — همان دو فایلِ گوشی، از همان سازنده ([CsvExport]).
 *
 * فایل در پوشهٔ «اسناد» کنارِ دفتر ذخیره و با برنامهٔ پیش‌فرضِ سیستم
 * (اکسل یا Numbers) باز می‌شود.
 */
@Composable
private fun ExportCard(repo: Repo) {
    val export = LocalFileExport.current
    val saveOrders = export.rememberTextSaver("text/csv") {
        CsvExport.orders(repo.observeAllOrders().first())
    }
    val saveTx = export.rememberTextSaver("text/csv") {
        CsvExport.transactions(repo.observeTx().first())
    }
    AppCard {
        CardTitle("خروجیِ داده‌ها برای اکسل (CSV)")
        CardHint(
            "فایل در پوشهٔ «اسناد» کنارِ دفتر ذخیره می‌شود و خودش باز می‌شود. " +
                "تاریخ‌ها هجری شمسی‌اند و ستون‌ها همان ستون‌های خروجیِ گوشی."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                // نتیجه خودش دیده می‌شود: فایل پس از ذخیره باز می‌شود.
                onClick = { saveOrders("orders-${stamp()}.csv") },
                shape = PillShape,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("سفارش‌ها")
            }
            OutlinedButton(
                onClick = { saveTx("transactions-${stamp()}.csv") },
                shape = PillShape,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تراکنش‌ها")
            }
        }
    }
}

private fun stamp(): String =
    java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())

/** پیوند به «پشتیبان و بازیابی» — هشدارِ پشتیبان اول اینجا می‌آورد. */
@Composable
private fun BackupLinkCard(go: (Section) -> Unit) {
    AppCard(onClick = { go(Section.Backup) }) {
        CardTitle("پشتیبان و بازیابی")
        CardHint("ساختنِ فایلِ پشتیبان از کلِ دفتر، و بازیابی از آن. همان قالبِ گوشی است.")
    }
}

/** بخش‌های مدیریتی که گوشی از دلِ تنظیمات باز می‌کند. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManageLinksCard(access: Access, go: (Section) -> Unit) {
    val links = listOf(
        Section.MasterData, Section.ShopProfile, Section.Users, Section.Licence,
        Section.SelfTest, Section.SampleShop, Section.About
    ).filter { it.allowedFor(access) }
    if (links.isEmpty()) return
    AppCard {
        CardTitle("بخش‌های دیگر")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            links.forEach { s ->
                Text(
                    s.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Brand,
                    modifier = Modifier
                        .border(1.dp, Hairline, PillShape)
                        .background(Bg, PillShape)
                        .clickable { go(s) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/** حسابِ همین نفر: نام و نقش، تغییرِ رمز، خروج. */
@Composable
private fun AccountCard(auth: AuthViewModel) {
    val ui by auth.ui.collectAsState()
    var changing by remember { mutableStateOf(false) }
    AppCard {
        CardTitle("حسابِ کاربری")
        CardHint("${accountName(ui)} — ${accountRole(ui)}")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { changing = true }, shape = PillShape, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تغییرِ رمز")
            }
            Button(
                onClick = { auth.logout() },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = BadSoft, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("خروج از حساب")
            }
        }
    }
    if (changing) ChangePinDialog(auth, onDismiss = { changing = false })
}

/**
 * قفلِ خودکار — پس از چند دقیقه بی‌کاری، برنامه به درِ ورود برمی‌گردد.
 * پیش‌فرض خاموش ([DeskPrefs.idleLockMinutes]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleLockCard() {
    val settings = LocalSettings.current
    val minutes by DeskIdle.minutes.collectAsState()
    AppCard {
        CardTitle("قفلِ خودکار")
        CardHint(
            "اگر کامپیوتر چند دقیقه بی‌کار بماند، برنامه به درِ ورود برمی‌گردد و " +
                "رمز می‌خواهد. کاری که نیمه‌کاره مانده ذخیره نمی‌شود."
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeskPrefs.IDLE_LOCK_CHOICES.forEach { m ->
                val on = m == minutes
                Text(
                    if (m == 0) "خاموش" else "${m.fa()} دقیقه",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) Brand else Ink,
                    modifier = Modifier
                        .border(1.dp, if (on) Brand.copy(alpha = 0.3f) else Hairline, PillShape)
                        .background(if (on) BrandSoft else Bg, PillShape)
                        .clickable {
                            DeskPrefs.setIdleLockMinutes(settings, m)
                            DeskIdle.minutes.value = m
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/**
 * پشتیبانی — نشانی، و ایمیلی که نسخه و کامیت را در عنوانش دارد تا
 * پشتیبانی نپرسد «کدام نسخه؟».
 */
@Composable
private fun SupportCard() {
    AppCard {
        CardTitle("پشتیبانی")
        CardHint("برای گزارشِ مشکل، پیشنهاد یا سؤال با ما در تماس شوید.")
        Text(AppInfo.SUPPORT_EMAIL, style = MaterialTheme.typography.bodyMedium, color = Brand)
        Text(AppVersion.supportLine(), style = MaterialTheme.typography.labelSmall, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    if (!openSupportMail()) {
                        copyText(AppInfo.SUPPORT_EMAIL)
                        DeskNotices.show("برنامهٔ ایمیل باز نشد؛ نشانی کپی شد.")
                    }
                },
                shape = PillShape,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("نوشتنِ ایمیل")
            }
            OutlinedButton(
                onClick = {
                    copyText(AppInfo.SUPPORT_EMAIL)
                    DeskNotices.show("نشانیِ پشتیبانی کپی شد.")
                },
                shape = PillShape,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("کپیِ نشانی")
            }
        }
    }
}

/** `true` اگر برنامهٔ ایمیلِ سیستم باز شد. */
private fun openSupportMail(): Boolean = runCatching {
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
        return false
    }
    // فاصله «%20» می‌ماند نه «+»: برنامه‌های ایمیل «+» را در عنوان همان «+» می‌خوانند.
    val subject = URLEncoder.encode(
        "پشتیبانی ${AppInfo.NAME} — ${AppVersion.supportLine()}", Charsets.UTF_8
    ).replace("+", "%20")
    Desktop.getDesktop().mail(URI("mailto:${AppInfo.SUPPORT_EMAIL}?subject=$subject"))
    true
}.getOrDefault(false)

internal fun copyText(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}

/**
 * «پاک‌کردنِ کارها و حساب‌ها» — فقط مدیر، با نوشتنِ «پاک کن»، و پس از یک
 * پشتیبانِ ایمنیِ کامل ([DesktopReset]).
 */
@Composable
private fun ResetCard(repo: Repo) {
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    AppCard(container = BadSoft, bordered = false) {
        Text(
            "پاک‌کردنِ کارها و حساب‌ها",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Text(
            "برای وقتی که آزمایش تمام شده و می‌خواهید با دفترِ تمیز شروع کنید. " +
                "سفارش‌ها، فروش‌ها، خریدها، دفتر کل، اسناد، انبار، حضور و غیاب و " +
                "عکس‌ها پاک می‌شوند. خیاط‌ها، ناظرها، کارکنان، پارچه‌ها، رنگ‌ها، " +
                "سایزها، طرح‌ها، خرج‌کارها و مشتری‌ها با اندازه‌هایشان می‌مانند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        result?.let { (msg, isError) ->
            Text(
                msg,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isError) Bad else MaterialTheme.colorScheme.onErrorContainer
            )
        }
        OutlinedButton(
            onClick = { asking = true },
            enabled = !working,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Bad, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (working) "در حالِ پاک‌کردن…" else "پاک‌کردنِ کارها و حساب‌ها", color = Bad)
        }
    }

    if (asking) {
        var typed by remember { mutableStateOf("") }
        val phrase = ResetPlan.CONFIRM_PHRASE
        AppAlertDialog(
            onDismissRequest = { if (!working) asking = false },
            title = { Text("پاک‌کردنِ کارها و حساب‌ها") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("این کار برگشت ندارد.", fontWeight = FontWeight.SemiBold, color = Bad)
                    Text(
                        "پیش از پاک‌کردن، یک پشتیبانِ کامل خودکار در پوشهٔ «backups» کنارِ " +
                            "دفتر نوشته می‌شود. اگر آن نوشته نشود، هیچ چیزی پاک نمی‌شود.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("برای تأیید بنویسید: $phrase") },
                        singleLine = true,
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.trim() == phrase && !working,
                    onClick = {
                        asking = false
                        working = true
                        result = null
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { DesktopReset.run(repo, settings) }
                            working = false
                            result = r.fold(
                                {
                                    "${it.tablesCleared.fa()} جدول پاک شد. اطلاعات پایه و مشتریان " +
                                        "سرِ جایشان‌اند. پشتیبانِ پیش از پاک‌کردن: ${it.safetyBackup.name}" to false
                                },
                                { (it.message ?: "پاک‌کردن انجام نشد.") to true }
                            )
                        }
                    }
                ) { Text("پاک کن", color = Bad) }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }, enabled = !working) { Text("لغو") }
            }
        )
    }
}
