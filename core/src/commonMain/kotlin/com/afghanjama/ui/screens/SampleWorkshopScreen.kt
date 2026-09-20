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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.afghanjama.data.SampleWorkshop
import com.afghanjama.ui.components.BusyButton
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.vm.SampleWorkshopViewModel

/**
 * کارگاهِ نمونه — برای دیدن، پیش از خریدن.
 *
 * **چرا این صفحه هست.** اپ خالی بالا می‌آید، و برای کسی که دارد
 * تصمیم می‌گیرد بخرد یا نه، خالی یعنی هیچ. هر صفحه‌ای باز می‌کند یک
 * جای خالی است — نه سفارشی، نه خیاطی، نه پارچه‌ای. با یک لمس، یک
 * کارگاهِ کاملِ در حالِ کار ساخته می‌شود و همان لحظه هر صفحه‌ای چیزی
 * برای گفتن دارد.
 *
 * دادهٔ نمونه از همان مسیرهایی می‌آید که کارِ واقعی می‌آید، پس دفتر و
 * انبار و ژورنالش به‌ناچار با هم می‌خوانند.
 */
@Composable
fun SampleWorkshopScreen(
    vm: SampleWorkshopViewModel,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val hasData by vm.hasData.collectAsState()
    var confirming by remember { mutableStateOf(false) }

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
                    "این کارگاه از قبل سفارش دارد. کارگاهِ نمونه چیزی را پاک نمی‌کند، " +
                        "ولی شش سفارشِ ساختگی و چند خیاط و مشتریِ نمونه به دفترِ شما " +
                        "اضافه می‌شود و بعد باید دستی پاکشان کنید.\n\n" +
                        "اگر می‌خواهید فقط اپ را ببینید، بهتر است روی یک نصبِ تازه " +
                        "امتحانش کنید.",
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
                title = { Text("کارگاهِ نمونه") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
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
                "یک کارگاهِ کامل، با یک لمس",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "اپ خالی بالا می‌آید، همان‌طور که یک کارگاهِ واقعی از روزِ اول خالی است. " +
                    "برای دیدنِ اینکه پُر شده چه شکلی است، این دکمه «${SampleWorkshop.NAME}» " +
                    "را می‌سازد: یک کارگاهِ در حالِ کار با سفارش‌هایی در هر مرحله، " +
                    "انبارِ پُر، و یک ماه حساب.",
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

            BusyButton(
                text = "ساختنِ کارگاهِ نمونه",
                onClick = { if (hasData) confirming = true else vm.create() },
                busy = ui.busy,
                busyText = "در حالِ ساخت…",
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "دادهٔ نمونه فقط روی همین دستگاه ساخته می‌شود — هیچ چیز جایی " +
                    "فرستاده نمی‌شود. برای پاک کردنش، از تنظیمات «بازنشانیِ داده» " +
                    "را بزنید و کارگاهِ خودتان را از صفر شروع کنید.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
