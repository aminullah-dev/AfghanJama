package com.afghanjama.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.desktop.data.DesktopBackup
import com.afghanjama.desktop.data.dataDir
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.AppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * پشتیبان‌گیری و بازیابی روی ویندوز.
 *
 * **چرا این صفحه در `:desktop` است و نه `:core`.** خودِ کار مشترک است
 * (`BackupArchive` در `:core`)، ولی *انتخابِ فایل* نیست: اندروید
 * `ActivityResultContracts.CreateDocument` دارد و ویندوز
 * `JFileChooser`. صفحهٔ تنظیماتِ اندروید هم انتخابگرهای خودش را دارد و
 * دست‌نخورده می‌ماند.
 *
 * ساختنِ یک مرزِ مشترک برای «فایل را از کاربر بگیر» شدنی بود، ولی برای
 * دو دکمه ارزشش را نداشت — و مرزِ `FileExport` که برای CSV هست عمداً
 * فقط متن می‌دهد.
 */
@Composable
internal fun BackupScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var needsRestart by remember { mutableStateOf(false) }

    AppScreen(title = "پشتیبان و بازیابی", onBack = onBack) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "پشتیبان‌گیری",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "یک فایلِ پشتیبان از کلِ دفتر می‌سازد. همان قالبی است " +
                            "که اپِ گوشی می‌سازد و می‌خوانَد، پس بینِ گوشی و " +
                            "پی‌سی جابه‌جا می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "دفتر اینجاست: ${dataDir().absolutePath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        enabled = !busy,
                        onClick = {
                            val stamp = SimpleDateFormat(
                                "yyyy-MM-dd-HHmm", Locale.US
                            ).format(Date())
                            val target = chooseSave(DesktopBackup.suggestedName(stamp))
                            if (target != null) {
                                busy = true
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) {
                                        DesktopBackup.backupTo(target)
                                    }
                                    busy = false
                                    isError = r.isFailure
                                    message = r.fold(
                                        { "✅ پشتیبان ساخته شد:\n${target.absolutePath}" },
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
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "بازیابی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "دفترِ فعلی با فایلِ پشتیبان جایگزین می‌شود. پیش از هر " +
                            "دست‌زدنی به دفترِ فعلی، فایل سنجیده می‌شود؛ اگر خراب " +
                            "یا از نسخهٔ جلوتر باشد رد می‌شود و دادهٔ فعلی " +
                            "دست‌نخورده می‌ماند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        enabled = !busy,
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
                                        {
                                            "✅ بازیابی انجام شد. برنامه را ببندید و " +
                                                "دوباره باز کنید."
                                        },
                                        { "بازیابی نشد: ${it.message}" }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("بازیابی از فایلِ پشتیبان")
                    }
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
