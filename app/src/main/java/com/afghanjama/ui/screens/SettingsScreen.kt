@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import com.afghanjama.util.ShareUtil
import com.afghanjama.AppInfo
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.SalePrefs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.afghanjama.platform.LocalPhotos
import com.afghanjama.platform.Photos
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.util.AppLock
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.BackupViewModel
import com.afghanjama.ui.vm.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** تنظیمات: اطلاعات پایه، پشتیبان‌گیری، خروجی CSV، تغییر رمز، خروج. */
@Composable
fun SettingsScreen(
    authVm: AuthViewModel,
    backupVm: BackupViewModel,
    financeVm: FinanceViewModel,
    canManageMaster: Boolean,
    canBackup: Boolean,
    canResetData: Boolean,
    onGoMaster: () -> Unit,
    onGoSelfTest: () -> Unit,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
) {
    val ui by authVm.ui.collectAsState()
    val backupUi by backupVm.ui.collectAsState()
    val context = LocalContext.current
    val settings = LocalSettings.current
    var allowShortage by remember { mutableStateOf(SalePrefs.allowNegativeStock(settings)) }

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

    // ---------- ریست داده ----------
    val resetWorking by backupVm.working.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }
    if (showResetDialog) {
        var typed by remember { mutableStateOf("") }
        val phrase = backupVm.resetPhrase
        AlertDialog(
            onDismissRequest = { if (!resetWorking) showResetDialog = false },
            title = { Text("پاک‌کردن کارها و حساب‌ها") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "این کار برگشت ندارد.",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "پاک می‌شود: سفارش‌ها و مراحلشان، فروش‌ها، خریدها، دفتر کل، " +
                            "اسناد، انبار مواد و محصول، حضور و غیاب، و عکس‌ها. " +
                            "شمارهٔ سفارش هم از ۱ شروع می‌شود.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "می‌ماند: خیاط‌ها، ناظرها، کارکنان، پارچه‌ها، رنگ‌ها، سایزها، " +
                            "طرح‌ها، خرج‌کارها، و مشتری‌ها با اندازه‌هایشان.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "پیش از پاک‌کردن، یک پشتیبانِ کامل خودکار در " +
                            "Downloads/${AppInfo.NAME_LATIN} نوشته می‌شود. اگر آن نوشته نشود، " +
                            "هیچ چیزی پاک نمی‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("برای تأیید بنویسید: $phrase") },
                        singleLine = true,
                        enabled = !resetWorking,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.trim() == phrase && !resetWorking,
                    onClick = {
                        backupVm.resetData(context)
                        showResetDialog = false
                    }
                ) {
                    Text(
                        if (resetWorking) "در حال پاک‌کردن…" else "پاک کن",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    enabled = !resetWorking
                ) { Text("لغو") }
            }
        )
    }

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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            // ---------- پروفایلِ کارگاه ----------
            //
            // این کارت هویتِ کسب‌وکار است، نه هویتِ برنامه. هرچه اینجا
            // نوشته شود روی سرصفحهٔ فاکتور، رسیدهای اشتراکی و تابلوی
            // کارگاه می‌نشیند. تا وقتی نامی ثبت نشده، همه‌جا عنوانِ
            // خنثای «کارگاه خیاطی» دیده می‌شود.
            if (canManageMaster) {
                var coName by remember { mutableStateOf(CompanyPrefs.name(settings)) }
                var coPhone by remember { mutableStateOf(CompanyPrefs.phone(settings)) }
                var coAddr by remember { mutableStateOf(CompanyPrefs.address(settings)) }
                var coLogo by remember { mutableStateOf(CompanyPrefs.logo(settings)) }
                var coSaved by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "پروفایل کارگاه",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "نام و لوگویی که اینجا ثبت می‌کنید روی فاکتور، رسید و " +
                                "تابلوی کارگاه دیده می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        /*
                         * لوگو کنارِ نام مثلِ سربرگِ کاغذ — ولی دکمه‌ها در
                         * سطرِ خودشان.
                         *
                         * **چرا این‌طور شد.** تا دیروز کلِ `SinglePhotoPicker`
                         * در همین ردیف بود، کنارِ ستونی که `weight(1f)`
                         * داشت. آن ویجت خودش یک ردیفِ بی‌وزن است: ریزعکسِ
                         * ۷۲dp + دکمهٔ «لوگو» + «دوربین» + «برداشتن».
                         * `Row` در Compose اول بچه‌های **بی‌وزن** را اندازه
                         * می‌گیرد و هرچه ماند به وزن‌دارها می‌دهد — و
                         * اینجا چیزی نمی‌ماند: عرضِ داخلِ کارت روی گوشیِ
                         * ۳۶۰dp حدود ۳۰۰dp است و آن ویجت با لوگو بیش از
                         * همه‌اش را می‌گیرد. یعنی نامِ کارگاه و توضیحش در
                         * چند dp فشرده می‌شدند: هر حرف در یک سطر.
                         *
                         * **و چرا تازه حالا دیده شد:** تا پیش از درست شدنِ
                         * `PhotoStore.decodeBounds` هیچ لوگویی ذخیره
                         * نمی‌شد، پس ریزعکس و آن سه دکمه هرگز با هم روی
                         * صفحه نبودند. تعمیرِ آپلود این را بیرون انداخت.
                         *
                         * اینجا عمداً از `SinglePhotoPicker` استفاده
                         * نمی‌شود: آن ویجت برای انبار ساخته شده و چیدمانش
                         * مالِ خودش است. تنظیمات سربرگ می‌خواهد، پس همان
                         * دو تکهٔ مرزِ `Photos` را مستقیم برمی‌دارد.
                         * دوربین هم اینجا نیست — لوگوی کارگاه از فایل
                         * می‌آید، نه از عکسِ لحظه‌ای.
                         */
                        val photos = LocalPhotos.current
                        val pickLogo = photos.rememberPicker { name ->
                            // لوگوی قبلی نباید در حافظه جا بمانَد
                            val old = coLogo
                            CompanyPrefs.saveLogo(settings, name)
                            coLogo = name
                            if (old.isNotBlank()) photos.delete(old)
                        }
                        val logoThumb = photos.rememberThumb(coLogo, Photos.THUMB_SIDE)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (logoThumb != null) {
                                Image(
                                    bitmap = logoThumb,
                                    contentDescription = "لوگوی کارگاه",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    coName.ifBlank { CompanyPrefs.DEFAULT_SHOP },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (coLogo.isBlank())
                                        "لوگو ندارید — دکمهٔ زیر را بزنید تا از گالری انتخاب شود."
                                    else "لوگو روی اسنادِ چاپی هم می‌نشیند.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = pickLogo,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (coLogo.isBlank()) "انتخاب لوگو" else "تعویض لوگو")
                            }
                            if (coLogo.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        val old = coLogo
                                        CompanyPrefs.saveLogo(settings, "")
                                        coLogo = ""
                                        if (old.isNotBlank()) photos.delete(old)
                                    }
                                ) { Text("برداشتن") }
                            }
                        }

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
                                CompanyPrefs.save(settings, coName.trim(), coPhone.trim(), coAddr.trim())
                                coSaved = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (coSaved) "ذخیره شد" else "ذخیره اطلاعات کارگاه")
                        }
                    }
                }
            }

            // ---------- افزودنِ پول به صندوق یا بانک ----------
            //
            // پولی که از بیرون وارد کارگاه می‌شود (سرمایهٔ صاحب‌کار، وامِ
            // شخصی، پولی که از جای دیگری آورده) جایی برای ثبت نداشت.
            // بدونِ آن، موجودیِ صندوق در اپ با پولِ واقعیِ کشو نمی‌خواند و
            // هر گزارشی از همان‌جا کج می‌شد.
            //
            // اینجاست نه در «مالی»، چون کارِ صاحبِ کارگاه است نه کارِ
            // روزمرهٔ فروش.
            var topUpAmount by remember { mutableStateOf("") }
            var topUpNote by remember { mutableStateOf("") }
            var topUpBox by remember { mutableStateOf("WALLET") }
            var topUpDone by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "افزودن پول به صندوق یا بانک",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "پولی که از بیرون وارد کارگاه می‌شود — سرمایه، یا پولی که خودتان گذاشته‌اید.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("WALLET" to "کیف پول", "BANK" to "بانک").forEach { (code, label) ->
                            FilterChip(
                                selected = topUpBox == code,
                                onClick = { topUpBox = code; topUpDone = false },
                                label = { Text(label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = topUpAmount,
                        onValueChange = { topUpAmount = it.digitsOnly(); topUpDone = false },
                        label = { Text("مبلغ") },
                        suffix = { Text("؋") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = topUpNote,
                        onValueChange = { topUpNote = it; topUpDone = false },
                        label = { Text("بابت (مثلاً سرمایهٔ اولیه)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val topUpValue = topUpAmount.toLongOrNull() ?: 0L
                    Button(
                        enabled = topUpValue > 0,
                        onClick = {
                            val note = topUpNote.trim().ifBlank { "افزودن دستیِ پول" }
                            if (topUpBox == "BANK") financeVm.incomeBank(topUpValue, note)
                            else financeVm.incomeWallet(topUpValue, note)
                            topUpAmount = ""
                            topUpNote = ""
                            topUpDone = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (topUpDone) "ثبت شد" else "افزودن به موجودی")
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                SalePrefs.setAllowNegativeStock(settings, it)
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                "بکاپ خودکار روزانه فعال است — آخرین بکاپ: ${PersianDate.shortWithTime(lastAuto)} (پوشه Downloads/${AppInfo.NAME_LATIN})"
                            else
                                "بکاپ خودکار روزانه فعال است — اولین بکاپ به‌زودی در Downloads/${AppInfo.NAME_LATIN} ذخیره می‌شود.",
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
                            onClick = { backupLauncher.launch("${AppInfo.NAME_LATIN}-backup-${stamp()}.ajb") },
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            val mail = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:$SUPPORT_EMAIL")
                                putExtra(Intent.EXTRA_SUBJECT, "پشتیبانی اپ ${AppInfo.NAME}")
                            }
                            ShareUtil.launchChooser(
                                context, mail, "ارسال ایمیل به پشتیبانی"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(SUPPORT_EMAIL)
                    }
                }
            }

            // ---------- خودآزمایی ----------
            //
            // تا دیروز این یک بنرِ قهوه‌ایِ تمام‌عرض بالای داشبورد بود، با
            // برچسبِ «موقتی» روی خودش. برای اپی که به کارفرما تحویل می‌شود
            // اولین چیزی که صبح می‌بیند نباید داربستِ ساخت باشد. خودِ
            // قابلیت می‌مانَد — ابزارِ تشخیصِ کارآمدی است — ولی جایش
            // کنارِ پشتیبان و ریست است، نه روی میزِ کار.
            if (canResetData) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "خودآزمایی و سلامتِ داده",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "دفتر کل، موجودی انبار و صندوق‌ها را با هم تطبیق می‌دهد و " +
                                "می‌گوید کجا نمی‌خوانَد. فقط می‌خوانَد — هیچ چیزی را " +
                                "تغییر نمی‌دهد، پس هر وقت خواستید بی‌خطر است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onGoSelfTest, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Science, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("اجرای خودآزمایی")
                        }
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            // ---------- ریست داده — آخرین کارت، چون برگشت ندارد ----------
            if (canResetData) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "ریست داده",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "برای وقتی که تست تمام شده و می‌خواهید با دفترِ تمیز شروع " +
                                "کنید. سفارش‌ها، فروش‌ها، خریدها، دفتر کل، اسناد، انبار، " +
                                "حضور و غیاب و عکس‌ها پاک می‌شوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "خیاط‌ها، ناظرها، کارکنان، پارچه‌ها، رنگ‌ها، سایزها، طرح‌ها، " +
                                "خرج‌کارها و مشتری‌ها با اندازه‌هایشان می‌مانند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            enabled = !resetWorking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("پاک‌کردن کارها و حساب‌ها")
                        }
                    }
                }
            }
        }
    }
}

private const val SUPPORT_EMAIL = "aminhashemi979@gmail.com"
