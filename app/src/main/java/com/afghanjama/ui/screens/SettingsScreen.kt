@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Switch
import com.afghanjama.prefs.SalePrefs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.util.AppLock
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.BackupViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** تنظیمات: اطلاعات پایه، پشتیبان‌گیری، خروجی CSV، تغییر رمز، خروج. */
@Composable
fun SettingsScreen(
    authVm: AuthViewModel,
    backupVm: BackupViewModel,
    canManageMaster: Boolean,
    canBackup: Boolean,
    onGoMaster: () -> Unit,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
) {
    val ui by authVm.ui.collectAsState()
    val backupUi by backupVm.ui.collectAsState()
    val context = LocalContext.current
    var allowShortage by remember { mutableStateOf(SalePrefs.allowNegativeStock(context)) }

    fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { backupVm.backupTo(context, it) } }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { backupVm.restoreFrom(context, it) } }

    val ordersCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { backupVm.exportOrdersCsv(context, it) } }

    val txCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { backupVm.exportTransactionsCsv(context, it) } }

    // قفل اپ با رمز عددی
    var appLockSet by remember { mutableStateOf(AppLock.isPinSet(context)) }
    var showAppLockDialog by remember { mutableStateOf(false) }
    if (showAppLockDialog) {
        var newLockPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAppLockDialog = false },
            title = { Text(if (appLockSet) "تغییر رمز قفل اپ" else "تنظیم رمز قفل اپ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "با تنظیم رمز، هر بار باز کردن اپ رمز عددی خواسته می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newLockPin,
                        onValueChange = { newLockPin = it.digitsOnly().take(8) },
                        label = { Text("رمز عددی (حداقل ۴ رقم)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newLockPin.length >= 4,
                    onClick = {
                        AppLock.setPin(context, newLockPin)
                        appLockSet = true
                        showAppLockDialog = false
                    }
                ) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { showAppLockDialog = false }) { Text("لغو") } }
        )
    }

    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // اطلاعات پایه — فقط مدیر
            if (canManageMaster) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "اطلاعات پایه",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "مدیریت خیاط‌ها، ناظرها، پارچه‌ها، رنگ‌ها، سایزها، طرح‌ها، مشتری‌ها و خرج کار.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onGoMaster, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("باز کردن اطلاعات پایه")
                        }
                    }
                }
            }

            // اطلاعات کارگاه روی رسیدها/PDF — فقط مدیر
            if (canManageMaster) {
                val ctx = LocalContext.current
                var coName by remember { mutableStateOf(CompanyPrefs.name(ctx)) }
                var coPhone by remember { mutableStateOf(CompanyPrefs.phone(ctx)) }
                var coAddr by remember { mutableStateOf(CompanyPrefs.address(ctx)) }
                var coSaved by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "اطلاعات کارگاه (روی رسید و PDF)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = coName,
                            onValueChange = { coName = it; coSaved = false },
                            label = { Text("نام کارگاه / شرکت") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = coPhone,
                            onValueChange = { coPhone = it; coSaved = false },
                            label = { Text("تلفن (اختیاری)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = coAddr,
                            onValueChange = { coAddr = it; coSaved = false },
                            label = { Text("آدرس (اختیاری)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                CompanyPrefs.save(ctx, coName.trim(), coPhone.trim(), coAddr.trim())
                                coSaved = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (coSaved) "ذخیره شد ✓" else "ذخیره اطلاعات کارگاه")
                        }
                    }
                }
            }

            // قفل اپ با رمز عددی
            // ---------- سیاستِ فروش ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "فروشِ بیشتر از موجودی (کسری)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (allowShortage)
                            "فروش و تحویل حتی وقتی انبار خالی است انجام می‌شود و کمبود " +
                                "به‌عنوان «کسری» با رنگِ قرمز نشان داده می‌شود. بهای " +
                                "تمام‌شده با آخرین میانگین برآورد می‌شود و با ورودِ بعدیِ " +
                                "همان کالا خودش اصلاح می‌گردد."
                        else
                            "فروشِ بیشتر از موجودی رد می‌شود. امن‌تر است، ولی اگر جنسی " +
                                "پیش از ثبتِ ورودش فروخته شود، فروشنده گیر می‌کند.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = allowShortage,
                            onCheckedChange = {
                                allowShortage = it
                                SalePrefs.setAllowNegativeStock(context, it)
                            }
                        )
                        Text(if (allowShortage) "اجازه هست" else "اجازه نیست")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("قفل اپ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (appLockSet) "قفل فعال است؛ هنگام باز کردن اپ رمز خواسته می‌شود."
                        else "با تنظیم رمز عددی، اپ هنگام باز شدن قفل می‌شود.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAppLockDialog = true }, modifier = Modifier.weight(1f)) {
                            Text(if (appLockSet) "تغییر رمز" else "تنظیم رمز")
                        }
                        if (appLockSet) {
                            OutlinedButton(
                                onClick = { AppLock.clearPin(context); appLockSet = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("حذف قفل") }
                        }
                    }
                }
            }

            // تغییر رمز
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "تغییر رمز",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it.digitsOnly().take(8) },
                        label = { Text("رمز فعلی") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation()
                    )

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it.digitsOnly().take(8) },
                        label = { Text("رمز جدید (حداقل ۴ رقم)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Button(
                        onClick = {
                            authVm.changePin(oldPin, newPin)
                            oldPin = ""
                            newPin = ""
                        },
                        enabled = oldPin.isNotBlank() && newPin.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("ثبت رمز جدید")
                    }

                    ui.message?.let { msg ->
                        Text(
                            msg,
                            color = if (ui.isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // پشتیبان‌گیری و خروجی داده‌ها — فقط مدیر
            if (canBackup) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "پشتیبان‌گیری و خروجی داده‌ها",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "از کل دیتابیس نسخه پشتیبان بگیرید یا خروجی اکسل (CSV) تهیه کنید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // وضعیت بکاپ خودکار روزانه
                        val lastAuto = backupVm.lastAutoBackupTime(context)
                        Text(
                            if (lastAuto > 0)
                                "🔄 بکاپ خودکار روزانه فعال است — آخرین بکاپ: ${PersianDate.shortWithTime(lastAuto)} (پوشه Downloads/AfghanJama)"
                            else
                                "🔄 بکاپ خودکار روزانه فعال است — اولین بکاپ به‌زودی در Downloads/AfghanJama ذخیره می‌شود.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "بکاپ خودکار فقط دیتابیس است و روی همین گوشی می‌ماند (۷ نسخهٔ آخر). " +
                                "بکاپی که خودتان می‌گیرید یا می‌فرستید، عکس‌ها را هم دارد — " +
                                "همان است که اگر گوشی گم شود به کارتان می‌آید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = { backupVm.shareBackup(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ارسال بکاپ کامل به Drive / واتساپ")
                        }

                        OutlinedButton(
                            onClick = { backupLauncher.launch("afghanjama-backup-${stamp()}.ajb") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("پشتیبان‌گیری کامل (با عکس‌ها)")
                        }

                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("بازیابی از فایل پشتیبان")
                        }

                        OutlinedButton(
                            onClick = { ordersCsvLauncher.launch("orders-${stamp()}.csv") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("خروجی سفارش‌ها (CSV)")
                        }

                        OutlinedButton(
                            onClick = { txCsvLauncher.launch("transactions-${stamp()}.csv") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("خروجی تراکنش‌ها (CSV)")
                        }

                        backupUi.message?.let { msg ->
                            Text(
                                msg,
                                color = if (backupUi.isError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (backupUi.restartRequired) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // 📞 پشتیبانی — برای همه نقش‌ها
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "پشتیبانی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "برای گزارش مشکل، پیشنهاد یا سؤال با ما در تماس شوید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:$SUPPORT_EMAIL")
                                    putExtra(Intent.EXTRA_SUBJECT, "پشتیبانی اپ AfghanJama")
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, "ارسال ایمیل به پشتیبانی")
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(SUPPORT_EMAIL)
                    }
                }
            }

            // خروج از حساب
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "حساب کاربری",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = {
                            authVm.logout()
                            onLoggedOut()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("خروج از حساب")
                    }
                }
            }
        }
    }
}

private const val SUPPORT_EMAIL = "aminhashemi979@gmail.com"
