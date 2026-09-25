@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.afghanjama.data.SampleWorkshop
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.components.BusyButton
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.vm.SampleWorkshopViewModel

/**
 * کارگاهِ نمونه — شروعِ استانداردِ هر کارگاه، و قفلش.
 *
 * **دو راه به این صفحه می‌رسد.**
 *  - [firstRun]: کارگاه کاملاً خالی است و مدیر تازه وارد شده. صفحه جای
 *    همهٔ اپ را می‌گیرد و راهِ برگشت ندارد؛ هر کارگاه از همین نمونه شروع
 *    می‌کند و بعد نام‌ها، قیمت‌ها، آدم‌ها و مشتری‌ها را از آنِ خودش
 *    می‌کند.
 *  - از تنظیمات: کارگاه چیزی دارد، پس دکمه قفل است و رمزِ ورود می‌خواهد.
 *
 * دادهٔ نمونه از همان مسیرهایی می‌آید که کارِ واقعی می‌آید، پس دفتر و
 * انبار و ژورنالش به‌ناچار با هم می‌خوانند.
 */
@Composable
fun SampleWorkshopScreen(
    vm: SampleWorkshopViewModel,
    onBack: () -> Unit,
    firstRun: Boolean = false
) {
    val ui by vm.ui.collectAsState()
    val gate by vm.gate.collectAsState()
    val settings = LocalSettings.current
    var confirming by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    ui.message?.let { msg ->
        AppAlertDialog(
            onDismissRequest = { vm.clearMessage() },
            title = { Text(if (ui.isError) "ناتمام ماند" else "آماده است") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.clearMessage() }) { Text("باشه") } }
        )
    }

    if (confirming) {
        AppAlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("روی دادهٔ فعلی اضافه شود؟") },
            text = {
                Text(
                    "این کارگاه از قبل داده دارد. ساختنِ دوباره چیزی را پاک نمی‌کند " +
                        "و کارگاه را به نمونه برنمی‌گرداند: یک نسخهٔ دیگر از شش سفارشِ " +
                        "ساختگی، خیاط‌ها، مشتری‌ها و پولِ نمونه روی دفترِ شما اضافه " +
                        "می‌شود و بعد باید دستی پاکشان کنید.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = false; vm.create() }) {
                    Text("می‌دانم، بساز", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("لغو") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (firstRun) "شروعِ کارگاه" else "کارگاهِ نمونه") },
                navigationIcon = {
                    // شروعِ اجباری راهِ برگشت ندارد: پشتِ این صفحه یک کارگاهِ
                    // خالی است، و همین خالی بودن دلیلِ آمدن به اینجاست.
                    if (!firstRun) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (firstRun) "اول کارگاهِ استاندارد، بعد کارگاهِ خودتان"
                else "یک کارگاهِ کامل، با یک لمس",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (firstRun) {
                    "هر کارگاه از «${SampleWorkshop.NAME}» شروع می‌کند: طرح‌ها، پارچه‌ها، " +
                        "رنگ‌ها، اندازه‌ها، خرجِ کار، خیاط‌ها، مشتری‌ها و یک ماه کار و حساب. " +
                        "بعد همه را از آنِ خودتان می‌کنید — نام‌ها، قیمت‌ها و آدم‌ها " +
                        "قابلِ ویرایش‌اند.\n\n" +
                        "سفارش‌ها، پول و انبارِ نمونه واقعی نیستند. پیش از ثبتِ کارِ واقعی، از " +
                        "تنظیمات «پاک‌کردن کارها و حساب‌ها» را بزنید: آن‌ها می‌روند و آنچه " +
                        "شخصی کرده‌اید می‌ماند، و گزارشِ مالی از صفر و درست شروع می‌شود."
                } else {
                    "برای دیدنِ اینکه کارگاهِ پُر چه شکلی است، این دکمه «${SampleWorkshop.NAME}» " +
                        "را می‌سازد: یک کارگاهِ در حالِ کار با سفارش‌هایی در هر مرحله، " +
                        "انبارِ پُر، و یک ماه حساب."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                    Text("چه چیزی ساخته می‌شود", fontWeight = FontWeight.SemiBold)
                    listOf(
                        "۶ سفارش" to "در هر مرحله یکی — از تازه‌ثبت‌شده تا تحویل‌شده",
                        "۴ خیاط و ۲ ناظر" to "با کارمزدِ فی‌عدد و کارِ در جریان",
                        "۵ مشتری" to "با شماره تماس، اندازه، و دو بدهیِ باز",
                        "۶ قلم انبار" to "با کاردکس، و یکی زیرِ حدِ هشدار",
                        "یک ماه حساب" to "خرید، فروش، هزینه و کارمزد — پس گزارش‌ها پُرند"
                    ).forEach { (title, detail) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(0.4f)
                            )
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                    }
                }
            }

            if (gate == SampleWorkshop.Gate.LOCKED) {
                val pinError = ui.pinError
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Text("قفل است", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "این کارگاه داده دارد. ساختنِ دوباره کارگاه را به نمونه " +
                                "برنمی‌گرداند؛ نسخهٔ دیگری از دادهٔ ساختگی را روی کارِ " +
                                "واقعی اضافه می‌کند. برای ادامه، رمزِ ورودِ مدیر را بزنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it.digitsOnly().take(8) },
                            label = { Text("رمزِ ورود") },
                            singleLine = true,
                            isError = pinError != null,
                            supportingText = if (pinError != null) {
                                { Text(pinError) }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        BusyButton(
                            text = "باز کردنِ قفل",
                            onClick = {
                                vm.unlock(settings, pin)
                                pin = ""
                            },
                            enabled = pin.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                BusyButton(
                    text = if (firstRun) "ساختنِ کارگاهِ نمونه و شروع" else "ساختنِ کارگاهِ نمونه",
                    onClick = {
                        if (gate == SampleWorkshop.Gate.OPEN) confirming = true else vm.create()
                    },
                    enabled = gate != SampleWorkshop.Gate.CHECKING,
                    busy = ui.busy,
                    busyText = "در حالِ ساخت…",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                "دادهٔ نمونه فقط روی همین دستگاه ساخته می‌شود — هیچ چیز جایی " +
                    "فرستاده نمی‌شود. «بازنشانیِ داده» در تنظیمات سفارش‌ها و حساب‌ها " +
                    "را پاک می‌کند، ولی خیاط‌ها، مشتری‌ها، کارکنان و اطلاعاتِ پایه " +
                    "می‌مانند تا شخصی‌شان کنید.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
