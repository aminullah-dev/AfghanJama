package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.AppScreen

/**
 * پروفایلِ کارگاه — نام، تلفن و آدرسی که روی کاغذ می‌نشیند.
 *
 * **چرا این صفحه ساخته شد.** `CompanyPrefs.save` تا امروز **تنها یک
 * فراخوان در کلِ مخزن** داشت و آن هم در `SettingsScreen`ِ اندروید بود.
 * ولی `SettingsScreen` به انتخابگرِ عکس، قفلِ اثرانگشت و پشتیبان‌گیریِ
 * اندرویدی گره خورده و به `:core` نمی‌آید.
 *
 * نتیجه‌اش این بود: `DesktopDocsBridge` همین مقدارها را در سربرگِ **هر
 * PDFی** که ویندوز می‌سازد می‌نشاند، و چون هیچ راهی برای نوشتنشان روی
 * ویندوز نبود، هر کاغذی که پی‌سی چاپ می‌کرد نامِ پیش‌فرضِ «کارگاه
 * خیاطی» را داشت — نه نامِ خودِ کارگاه.
 *
 * پس فقط همان تکه‌ای که **خالص** است جدا شد: خواندن و نوشتنِ سه رشته از
 * `Settings`. نه عکس، نه قفل، نه پشتیبان.
 *
 * **لوگو عمداً اینجا نیست.** انتخابگرِ عکس و `PhotoStore` اندرویدی‌اند و
 * پی‌سیِ کارگاه دوربین ندارد. اگر روزی لازم شد، مرزِ جداگانهٔ خودش را
 * می‌خواهد؛ نه اینکه اینجا نصفه‌کاره تقلید شود.
 *
 * **دوتاییِ عمدی:** صفحهٔ تنظیماتِ اندروید همین سه خانه را در دلِ خودش
 * دارد و دست نخورد — عوض کردنِ چیدمانِ اپِ تحویل‌شده بهایی است که این
 * کار نمی‌ارزد. آنچه **مشترک** است قراردادِ ذخیره‌سازی است
 * (`CompanyPrefs`)، پس هر دو سکو یک جا و با یک کلید می‌نویسند و
 * نمی‌توانند از هم بیفتند.
 */
@Composable
fun ShopProfileScreen(onBack: () -> Unit) {
    val settings = LocalSettings.current

    var name by remember { mutableStateOf(CompanyPrefs.name(settings)) }
    var phone by remember { mutableStateOf(CompanyPrefs.phone(settings)) }
    var address by remember { mutableStateOf(CompanyPrefs.address(settings)) }
    var saved by remember { mutableStateOf(false) }

    AppScreen(title = "پروفایل کارگاه", onBack = onBack) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppCard {
                // `AppCard` خودش padding دارد؛ اینجا فقط فاصلهٔ عمودی.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "این نام روی فاکتور، رسید و صورت‌حساب چاپ می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        name.ifBlank { CompanyPrefs.DEFAULT_SHOP },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; saved = false },
                        label = { Text("نام کارگاه / شرکت") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it; saved = false },
                        label = { Text("تلفن (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it; saved = false },
                        label = { Text("آدرس (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            CompanyPrefs.save(
                                settings, name.trim(), phone.trim(), address.trim()
                            )
                            saved = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (saved) "ذخیره شد ✓" else "ذخیره اطلاعات کارگاه")
                    }
                }
            }
        }
    }
}
