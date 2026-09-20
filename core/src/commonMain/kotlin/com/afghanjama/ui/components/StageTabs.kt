package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** سه مرحلهٔ کارگاه، به ترتیبی که کار در آن‌ها جلو می‌رود. */
enum class WorkStage(val label: String) {
    CUT("برش"),
    SEW("دوخت"),
    CHECK("نظارت")
}

/**
 * جابه‌جایی بینِ سه مرحلهٔ کارگاه.
 *
 * **چرا ساخته شد.** نوارِ پایینِ گوشی برای مدیر **هفت** خانه داشت:
 * خانه، تولید، برش، دوخت، نظارت، فروش، مالی. متریال سه تا پنج
 * می‌گوید، و وقتی سنجیدم دلیلش آن نبود که اول فکر می‌کردم: روی
 * باریک‌ترین گوشیِ رایج (۳۶۰dp) هفت خانه ۵۱٫۴dp عرض می‌دهد، یعنی
 * هدفِ لمس **بالای** حدِ ۴۸dp می‌مانَد.
 *
 * مسئله جای دیگری است — **خواندن**. برچسبِ فارسی در ۵۱ نقطه جا
 * می‌شود ولی هیچ فضایی برای نفس کشیدن ندارد؛ هفت کلمه پشتِ سرِ هم
 * می‌نشینند و چشم برای پیدا کردنِ یکی، همه را می‌خواند. با پنج خانه
 * همان برچسب‌ها ۷۲ نقطه دارند و از هم جدا دیده می‌شوند.
 *
 * برش و دوخت و نظارت یک کارند که پشتِ سرِ هم می‌آیند، پس یک خانه
 * می‌گیرند و اینجا از هم جدا می‌شوند. نوارِ پایین پنج خانه شد.
 *
 * **چرا چیپ و نه تب.** نسخهٔ اول `TabRow` بود و روی `SewingScreen`
 * می‌شکست: آن صفحه از قبل تبِ خودش را دارد («تحویل به خیاط | در حال
 * دوخت | دوخته شده») و دو ردیفِ تبِ روی هم خودش ضدالگوی متریال است —
 * چشم نمی‌فهمد کدام سطحِ بالاتر است.
 *
 * چیپ از تب جدا دیده می‌شود، پس سلسله‌مراتب روشن می‌مانَد: چیپ
 * می‌گوید «کدام مرحله»، تب می‌گوید «کجای همان مرحله». و `FilterChip`
 * از قبل جای دیگری در همین پروژه روی هر دو سکو کامپایل شده، پس
 * ریسکِ APIِ آزمایشی هم ندارد.
 *
 * **و چرا [onSelect] می‌تواند null باشد.** این پاسخِ مسئلهٔ *نوارِ
 * پایینِ گوشی* است. پنجرهٔ ویندوز فهرستِ کنار دارد و هر سه مرحله آنجا
 * جداگانه‌اند؛ اینجا تکرار می‌شد. با null هیچ‌چیز کشیده نمی‌شود.
 */
@Composable
fun StageTabs(
    current: WorkStage,
    onSelect: ((WorkStage) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (onSelect == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WorkStage.entries.forEach { stage ->
            FilterChip(
                selected = stage == current,
                // زدنِ چیپِ همان صفحه‌ای که رویش هستیم ناوبریِ بی‌جاست:
                // پشته را شلوغ می‌کند و صفحه را از نو می‌سازد.
                onClick = { if (stage != current) onSelect(stage) },
                label = { Text(stage.label) }
            )
        }
    }
}
