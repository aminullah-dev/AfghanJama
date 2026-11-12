package com.afghanjama.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * گردیِ گوشه‌ها — **یک تعریف برای هر سه سکو**، مثلِ پالت و مقیاسِ قلم.
 *
 * **وضعی که از آن آمدیم (۱.۱۱).** اندروید `Shapes`ِ خودش را داشت
 * (۶، ۱۰، ۱۴، ۲۰، ۲۸ — سه‌تایش بیرون از شبکهٔ ۴)، و آیفون و کامپیوتر
 * هیچ `shapes`ی نمی‌دادند و پیش‌فرضِ متریال را می‌گرفتند (۴، ۸، ۱۲، ۱۶،
 * ۲۸). یعنی همان کارت و همان دکمه با همان کد روی گوشی ۱۴ گرد بود و روی
 * کامپیوتر ۱۲، و دیالوگ روی هر سه یک‌جور نبود. کسی هم تا دو دستگاه را
 * کنارِ هم نمی‌گذاشت نمی‌فهمید.
 *
 * حالا هر سه از همین می‌خوانند و پله‌ها روی شبکهٔ ۴ (`Space`) اند.
 */
object Radius {
    /** نشانِ رنگِ کوچک در راهنمای نمودار. */
    val xs = 4.dp

    /** نشانِ کوچک. */
    val sm = 8.dp

    /** کادرِ ورودِ متن، نشانِ آیکن و سطرِ فهرست. */
    val md = 12.dp

    /** کاشیِ کوچک درونِ کارت. */
    val lg = 16.dp

    /** کارتِ پیش‌فرض، کارتِ قهرمان و کارتِ شیشه‌ای. */
    val xl = 20.dp

    /** سطحِ اصلیِ پنجره و ورقه‌ها. */
    val xxl = 24.dp

    /** دیالوگ — همان پیش‌فرضِ متریال برای «بسیار بزرگ». */
    val sheet = 28.dp
}

/**
 * `MaterialTheme.shapes` برای هر سه سکو.
 *
 * **یک پله نرم‌تر از ۱.۱۳.** کادرِ ورودِ متن ۸ بود و کنارِ دکمه‌های
 * قرصی تیز دیده می‌شد؛ کارت ۱۶ بود و کارتِ قهرمان ۲۰، یعنی دو کارتِ
 * کنارِ هم دو گوشهٔ متفاوت داشتند. حالا کادر ۱۲ است و هر کارتی ۲۰.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.md),
    small = RoundedCornerShape(Radius.md),
    medium = RoundedCornerShape(Radius.xl),
    large = RoundedCornerShape(Radius.xxl),
    extraLarge = RoundedCornerShape(Radius.sheet),
)

/** قرصِ کامل — نشانِ وضعیت، نقش، شمار و برچسب. */
val PillShape = RoundedCornerShape(50)
