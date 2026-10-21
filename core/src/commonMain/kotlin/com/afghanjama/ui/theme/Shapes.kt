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
    /** نشانِ کوچک، کادرِ ورودِ متن. */
    val sm = 8.dp

    /** دکمه و سطرِ فهرست. */
    val md = 12.dp

    /** کارتِ پیش‌فرض. */
    val lg = 16.dp

    /** کارتِ شیشه‌ای و کاشیِ بزرگ. */
    val xl = 20.dp

    /** سطحِ اصلیِ پنجره و ورقه‌ها. */
    val xxl = 24.dp

    /** دیالوگ — همان پیش‌فرضِ متریال برای «بسیار بزرگ». */
    val sheet = 28.dp
}

/** `MaterialTheme.shapes` برای هر سه سکو. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.md),
    medium = RoundedCornerShape(Radius.lg),
    large = RoundedCornerShape(Radius.xl),
    extraLarge = RoundedCornerShape(Radius.sheet),
)

/** قرصِ کامل — نشانِ وضعیت، نقش، شمار و برچسب. */
val PillShape = RoundedCornerShape(50)
