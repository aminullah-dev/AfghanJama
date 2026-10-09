package com.afghanjama.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import com.afghanjama.licence.LicenceGate
import com.afghanjama.licence.LicenceState
import com.afghanjama.licence.LicenceText
import com.afghanjama.licence.Licensing
import com.afghanjama.ui.platform.AppAlertDialog
import kotlinx.coroutines.delay

/**
 * بنرِ لایسنس روی خانه — فقط وقتی چیزی برای گفتن هست.
 *
 * آزمایشی، مهلت‌ها، فقط‌خواندنی، نزدیکیِ تاریخِ لایسنس، یا ساعتِ
 * عقب‌رفته. با روزهای مانده، و با یک ضربه به صفحهٔ لایسنس می‌رود. وقتی
 * لایسنس فعال و دور از تاریخ است، هیچ چیزی نشان نمی‌دهد.
 */
@Composable
fun LicenceBanner(onOpen: () -> Unit) {
    val status by LicenceGate.status.collectAsState()
    val s = status ?: return
    if (!s.needsBanner) return
    val readOnly = s.state == LicenceState.READ_ONLY
    AccentCard(
        container = if (readOnly) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.tertiaryContainer,
        onClick = onOpen
    ) {
        val on = if (readOnly) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onTertiaryContainer
        Text(
            LicenceText.banner(s),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = on
        )
        val remedy = if (s.coveredByMain && !readOnly) null else LicenceText.remedy(s)
        if (remedy != null) {
            Text(remedy, style = MaterialTheme.typography.bodySmall, color = on)
        }
    }
}

/**
 * پنجرهٔ «ثبت انجام نشد» — یک بار در ریشهٔ هر سکو.
 *
 * هر نوشتنی که `Repo` به خاطرِ لایسنس رد کند، پیامش اینجا می‌آید؛ چه از
 * صفحهٔ فروش باشد چه از اطلاعاتِ پایه. پیام می‌گوید چرا، چه هنوز کار
 * می‌کند، و چه باید کرد — و دکمه‌اش مستقیم به صفحهٔ لایسنس می‌رود.
 */
@Composable
fun LicenceRefusalDialog(onOpenLicence: () -> Unit) {
    val msg by LicenceGate.refusal.collectAsState()
    val text = msg ?: return
    AppAlertDialog(
        onDismissRequest = { LicenceGate.dismissRefusal() },
        title = { Text("ثبت انجام نشد") },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {
                LicenceGate.dismissRefusal()
                onOpenLicence()
            }) { Text("صفحهٔ لایسنس") }
        },
        dismissButton = {
            TextButton(onClick = { LicenceGate.dismissRefusal() }) { Text("بستن") }
        }
    )
}

/**
 * وضعیتِ لایسنس را سرِ راه‌اندازی و بعد هر ده دقیقه از نو می‌خوانَد.
 *
 * اپی که از شب تا صبح باز می‌ماند هم باید ببیند که دورهٔ آزمایشی تمام
 * شده یا تاریخِ لایسنس رسیده؛ و «دیرترین زمانِ دیده‌شده» باید جلو برود تا
 * عقب‌کشیدنِ ساعت گرفته شود. خودِ درِ نوشتن به این وابسته نیست — هر بار
 * با ساعتِ همان لحظه تصمیم می‌گیرد.
 */
@Composable
fun LicenceHeartbeat(licensing: Licensing) {
    LaunchedEffect(licensing) {
        while (true) {
            runCatching { licensing.refresh() }
            delay(REFRESH_MILLIS)
        }
    }
}

private const val REFRESH_MILLIS = 10L * 60 * 1000
