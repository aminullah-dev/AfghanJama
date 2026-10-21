package com.afghanjama.desktop

import com.afghanjama.ui.theme.PillShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afghanjama.desktop.data.DeskPrefs
import com.afghanjama.desktop.data.DesktopAutoBackup
import com.afghanjama.desktop.data.DesktopBackup
import com.afghanjama.desktop.data.LedgerStatusViewModel
import com.afghanjama.desktop.data.dataDir
import com.afghanjama.prefs.BackupPrefs
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * پشتیبان‌گیری و بازیابی روی مک و ویندوز.
 *
 * **چرا این صفحه در `:desktop` است و نه `:core`.** خودِ کار مشترک است
 * (`BackupArchive` در `:core`)، ولی *انتخابِ فایل* نیست: اندروید
 * `ActivityResultContracts.CreateDocument` دارد و کامپیوتر `JFileChooser`.
 *
 * از ۱.۱۰ سه چیزِ تازه دارد: کارتِ دفتر (پیش‌تر صفحهٔ اول)، پشتیبانِ
 * خودکارِ روزانه ([DesktopAutoBackup]) با پوشه‌ای که کارفرما انتخاب
 * می‌کند، و عکس‌ها در هر دو سو.
 */
@Composable
internal fun BackupScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var needsRestart by remember { mutableStateOf(false) }
    // حالِ دفتر — تا ۱.۹ روی صفحهٔ اولِ کامپیوتر بود؛ از ۱.۱۰ که صفحهٔ اول
    // «خانه» است، اینجاست: همان جایی که کسی می‌پرسد «دفتر کجاست و سالم است؟».
    val ledgerVm: LedgerStatusViewModel = viewModel { LedgerStatusViewModel() }
    val ledger by ledgerVm.ui.collectAsState()

    AppScreen(title = "پشتیبان و بازیابی", onBack = onBack) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LedgerCard(ledger)

            AutoBackupCard()

            AppCard {
                Text(
                    "پشتیبان‌گیریِ دستی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "یک فایلِ پشتیبان از کلِ دفتر و عکس‌های سفارش‌ها، هر جا که " +
                        "بخواهید. همان قالبی است که اپِ گوشی می‌سازد و می‌خوانَد، پس " +
                        "بینِ گوشی و کامپیوتر جابه‌جا می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    enabled = !busy,
                    shape = PillShape,
                    onClick = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
                        val target = chooseSave(DesktopBackup.suggestedName(stamp))
                        if (target != null) {
                            busy = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    DesktopBackup.backupWithPhotos(target)
                                }
                                busy = false
                                isError = r.isFailure
                                message = r.fold(
                                    { photos ->
                                        "پشتیبان ساخته شد" +
                                            (if (photos > 0) "، همراهِ ${photos.fa()} عکس" else "") +
                                            ":\n${target.absolutePath}"
                                    },
                                    { "خطا در پشتیبان‌گیری: ${it.message}" }
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "در حالِ کار…" else "ساختنِ پشتیبان")
                }
            }

            AppCard {
                Text(
                    "بازیابی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "دفترِ فعلی با فایلِ پشتیبان جایگزین می‌شود و عکس‌های پشتیبان " +
                        "کنارِ عکس‌های این کامپیوتر می‌نشینند. پیش از هر دست‌زدنی به " +
                        "دفترِ فعلی، فایل سنجیده می‌شود؛ اگر خراب یا از نسخهٔ جلوتر " +
                        "باشد رد می‌شود و دادهٔ فعلی دست‌نخورده می‌ماند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    enabled = !busy,
                    shape = PillShape,
                    onClick = {
                        val source = chooseOpen()
                        if (source != null) {
                            busy = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    DesktopBackup.restoreFrom(source)
                                }
                                busy = false
                                isError = r.isFailure
                                needsRestart = r.isSuccess
                                message = r.fold(
                                    { photos ->
                                        "بازیابی انجام شد" +
                                            (if (photos > 0) " و ${photos.fa()} عکس هم برگشت" else "") +
                                            ". برنامه را ببندید و دوباره باز کنید."
                                    },
                                    {
                                        // پیام خودش می‌گوید چه چیزی دست خورد و چه چیزی نه
                                        // (`DesktopBackup.restoreInto`). اگر دفتر برای جایگزینی
                                        // بسته شده بود، تا برنامه دوباره باز نشود باز نمی‌شود.
                                        "بازیابی نشد: ${it.message}" +
                                            if (DesktopBackup.ledgerClosed) {
                                                " برنامه را ببندید و دوباره باز کنید."
                                            } else ""
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("بازیابی از فایلِ پشتیبان")
                }
            }

            message?.let { m ->
                AppCard {
                    Text(
                        m,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (needsRestart) {
                AppCard {
                    Text(
                        "دفتر عوض شده و پنجرهٔ باز هنوز دفترِ قبلی را در دست " +
                            "دارد. تا بسته و باز نشود، عددهای روی صفحه از " +
                            "دفترِ قبلی‌اند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** «آخرین پشتیبانِ خودکار: …» — هم اینجا، هم در «تنظیمات». */
internal fun lastAutoText(lastAuto: Long): String =
    if (lastAuto > 0L) "آخرین پشتیبانِ خودکار: ${PersianDate.shortWithTime(lastAuto)}"
    else "هنوز پشتیبانِ خودکاری گرفته نشده."

/**
 * پشتیبانِ خودکارِ روزانه: کِی بود، کجاست، «همین حالا پشتیبان بگیرید»، و پوشه.
 */
@Composable
private fun AutoBackupCard() {
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val status by DesktopAutoBackup.status.collectAsState()
    var folder by remember { mutableStateOf(DesktopAutoBackup.folder(settings)) }
    var custom by remember { mutableStateOf(DeskPrefs.backupFolder(settings).isNotBlank()) }
    // پس از هر بار، عدد از همان جایی خوانده می‌شود که هشدار می‌خوانَد.
    val lastAuto = remember(status) { BackupPrefs.lastAuto(settings) }

    AppCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Backup, contentDescription = null, tint = Brand, modifier = Modifier.size(20.dp))
            Text(
                "پشتیبانِ خودکارِ روزانه",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            "هر روز یک پشتیبانِ کامل، با عکس‌ها، خودش گرفته می‌شود: سرِ باز شدنِ " +
                "برنامه اگر از آخرینش ۲۴ ساعت گذشته باشد، و بعد هر ۲۴ ساعت تا وقتی " +
                "برنامه باز است. هر پشتیبان پس از نوشتن باز و سنجیده می‌شود. " +
                "${DesktopAutoBackup.KEEP.fa()} تای آخر نگه داشته می‌شوند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            lastAutoText(lastAuto),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (lastAuto > 0L) Brand else Muted
        )
        status.last?.let { run ->
            Text(
                "${run.file.name}" + (if (run.photos > 0) " — همراهِ ${run.photos.fa()} عکس" else ""),
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                color = Muted
            )
            if (run.usedFallback) {
                Text(
                    "پوشهٔ انتخاب‌شده در دسترس نبود؛ پشتیبان در پوشهٔ پیش‌فرض نوشته شد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Bad
                )
            }
        }
        status.error?.let {
            Text("آخرین پشتیبانِ خودکار گرفته نشد: $it", style = MaterialTheme.typography.bodySmall, color = Bad)
        }

        Text(
            if (custom) "پوشه (انتخابِ شما):" else "پوشه (پیش‌فرض، کنارِ دفتر):",
            style = MaterialTheme.typography.labelMedium,
            color = Muted
        )
        Text(
            folder.absolutePath,
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
            color = Muted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !status.running,
                shape = PillShape,
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            DesktopAutoBackup.runIfDue(settings, force = true)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (status.running) "در حالِ پشتیبان‌گیری…" else "همین حالا پشتیبان بگیرید")
            }
            OutlinedButton(
                shape = PillShape,
                onClick = {
                    val picked = chooseFolder(folder)
                    if (picked != null) {
                        DeskPrefs.setBackupFolder(settings, picked.absolutePath)
                        folder = picked
                        custom = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("انتخابِ پوشه…")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { openFolder(folder) }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("باز کردنِ پوشه")
            }
            if (custom) {
                TextButton(onClick = {
                    DeskPrefs.setBackupFolder(settings, "")
                    folder = DesktopAutoBackup.folder(settings)
                    custom = false
                }) { Text("برگشت به پوشهٔ پیش‌فرض") }
            }
        }
        Text(
            "پوشه‌ای در iCloud Drive یا OneDrive، پشتیبان را از خودِ کامپیوتر هم " +
                "بیرون می‌برد؛ اگر دیسک بسوزد، حساب‌ها از دست نمی‌روند.",
            style = MaterialTheme.typography.labelSmall,
            color = Muted
        )
    }
}

/*
 * انتخابگرها روی همان رشته‌ای اجرا می‌شوند که رویداد از آن آمده (EDT) —
 * `JFileChooser` مودال است و همان‌جا می‌ایستد. کارِ سنگین (کپیِ فایل)
 * بیرونِ EDT و روی `Dispatchers.IO` انجام می‌شود، وگرنه پنجره یخ می‌زد.
 */

private fun chooseSave(suggested: String): File? {
    val chooser = JFileChooser(dataDir())
    chooser.dialogTitle = "جای ذخیرهٔ پشتیبان"
    chooser.selectedFile = File(suggested)
    chooser.fileFilter = FileNameExtensionFilter("پشتیبانِ خیاط‌یار (*.ajb)", "ajb")
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val f = chooser.selectedFile ?: return null
    // اگر کاربر پسوند را پاک کرد، خودمان می‌گذاریم — وگرنه فایل بی‌نشان
    // می‌ماند و دفعهٔ بعد پیدا نمی‌شود.
    return if (f.name.contains('.')) f else File(f.parentFile, "${f.name}.ajb")
}

private fun chooseOpen(): File? {
    val chooser = JFileChooser(dataDir())
    chooser.dialogTitle = "فایلِ پشتیبان را انتخاب کنید"
    chooser.fileFilter = FileNameExtensionFilter("پشتیبانِ خیاط‌یار (*.ajb)", "ajb")
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile
}

private fun chooseFolder(current: File): File? {
    val chooser = JFileChooser(current.takeIf { it.isDirectory } ?: dataDir())
    chooser.dialogTitle = "پوشهٔ پشتیبانِ خودکار"
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val dir = chooser.selectedFile ?: return null
    // پوشه‌ای که نمی‌شود در آن نوشت همان‌جا رد می‌شود، نه فردا شب.
    if (!dir.isDirectory || !dir.canWrite()) {
        DeskNotices.show("در این پوشه نمی‌شود نوشت؛ پوشهٔ دیگری انتخاب کنید.")
        return null
    }
    return dir
}

private fun openFolder(dir: File) {
    runCatching {
        dir.mkdirs()
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir)
    }.onFailure { DeskNotices.show("پوشه باز نشد.") }
}
