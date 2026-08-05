package com.afghanjama.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.theme.Brand
import com.afghanjama.ui.theme.CopperBrush
import com.afghanjama.ui.theme.disabledContent
import com.afghanjama.ui.theme.disabledSurface

/*
 * اجزای «قهرمان» — جاهایی که باید در نگاهِ اول دیده شوند.
 *
 * قاعده‌ای که این‌ها را از [AppCard] جدا می‌کند: **در هر صفحه حداکثر
 * یکی.** اگر همه‌چیز مسی شود، هیچ‌چیز برجسته نیست و صفحه فقط شلوغ
 * می‌شود. مس برای موجودیِ نقد، خلاصهٔ مالی، دکمهٔ ثبت و صفحهٔ ورود
 * است؛ بقیه همان کارتِ آرام را می‌گیرند.
 */

/**
 * کارتِ قهرمان با رویهٔ مسی.
 *
 * رنگِ محتوا خودش [Brand.OnCopper] می‌شود، پس متن‌های داخلش لازم نیست
 * رنگ بگیرند — و مهم‌تر، **نمی‌توانند اشتباه بگیرند**. اگر این کار را
 * نمی‌کرد، هر متنِ داخلِ کارت `onSurface` را به ارث می‌برد که در
 * حالتِ تاریک تقریباً سفید است و روی مس خوانده نمی‌شود.
 */
@Composable
fun BrandCard(
    modifier: Modifier = Modifier,
    brush: Brush = CopperBrush,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(brush)
            .then(clickable)
    ) {
        CompositionLocalProvider(LocalContentColor provides Brand.OnCopper) {
            Column(
                Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

/**
 * دکمهٔ اصلیِ صفحه، با رویهٔ مسی.
 *
 * **چرا `Button`ِ متریال استفاده نشد.** رنگِ ظرفِ آن یک `Color` است، نه
 * `Brush`؛ برای گرادیان باید ظرف را شفاف می‌کردیم و زمینه را خودمان
 * می‌کشیدیم. آن‌وقت دو لایه روی هم بود که هرکدام گوشهٔ خودش را گرد
 * می‌کرد و در حالتِ فشرده‌شدن لبه‌ها از هم می‌افتادند. یک جعبهٔ ساده
 * صادق‌تر است.
 *
 * حالتِ غیرفعال عمداً گرادیان ندارد: رنگِ زنده یعنی «بزن»، و کاربر
 * وقتی چیزی نمی‌افتد دوباره می‌زند.
 */
@Composable
fun BrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    busyText: String = "در حال ثبت…",
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    leading: (@Composable () -> Unit)? = null
) {
    val live = enabled && !busy
    val surface = if (live) CopperBrush else SolidColor(disabledSurface())
    val ink = if (live) Brand.OnCopper else disabledContent()

    Box(
        modifier
            .clip(shape)
            .background(surface)
            .then(if (live) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            Row(
                Modifier.padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ink
                    )
                } else if (leading != null) {
                    leading()
                }
                Text(
                    if (busy) busyText else text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ink
                )
            }
        }
    }
}

/**
 * سطرِ عددِ درشت روی کارتِ قهرمان — برچسبِ کوچک بالا، عدد پایین.
 *
 * جدا شد چون هر سه جای مصرف (موجودیِ نقد، خلاصهٔ مالی، کارتِ ورود)
 * همین شکل را می‌خواستند و سه بار نوشتنش یعنی سه بار فرصتِ ناهماهنگی.
 */
@Composable
fun BrandStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Brand.OnCopperMuted
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Brand.OnCopper
            )
        }
        if (trailing != null) trailing()
    }
}
