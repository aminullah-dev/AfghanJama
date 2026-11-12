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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.theme.Brand
import com.afghanjama.ui.theme.PillShape
import com.afghanjama.ui.theme.CoralBrush
import com.afghanjama.ui.theme.InkBrush
import com.afghanjama.ui.theme.Radius
import com.afghanjama.ui.theme.disabledContent
import com.afghanjama.ui.theme.disabledSurface

/*
 * اجزای «قهرمان» — جاهایی که باید در نگاهِ اول دیده شوند.
 *
 * قاعده‌ای که این‌ها را از [AppCard] جدا می‌کند: **در هر صفحه حداکثر
 * یکی.** اگر همه‌چیز رنگی شود، هیچ‌چیز برجسته نیست و صفحه فقط شلوغ
 * می‌شود. کارتِ قهرمان برای موجودیِ نقد و خلاصهٔ مالی است و دکمهٔ
 * مرجانی برای کارِ اصلیِ صفحه؛ بقیه همان کارتِ آرام را می‌گیرند.
 */

/**
 * کارتِ قهرمان با رویهٔ نفتیِ عمیق ([InkBrush]).
 *
 * **تا ۱.۱۳ مرجانیِ براق بود.** بزرگ‌ترین سطحِ صفحه هم‌رنگِ دکمهٔ اصلی
 * بود و این دو سرِ توجه رقابت می‌کردند. حالا سطحِ بزرگ آرام است و عددِ
 * روشن رویش می‌نشیند.
 *
 * یک **هالهٔ روشنِ کم‌رنگ** در گوشهٔ بالاییِ انتها می‌افتد — نه برای
 * زینت، برای اینکه سطحِ تیرهٔ پهن تخت و مرده دیده نشود. رنگش همان رنگِ
 * متنِ کارت است با کدریِ ۰٫۰۸ (هالهٔ مرجانی روی نفتی خاکستریِ کدر
 * می‌داد). روی روشن‌ترین پلهٔ نفتی هم متنِ اصلی بالای ۵٫۵ و متنِ فرعی
 * بالای ۳٫۲ می‌ماند (حدها ۴٫۵ و ۳٫۰).
 *
 * رنگِ محتوا خودش [Brand.OnPetrol] می‌شود، پس متن‌های داخلش لازم نیست
 * رنگ بگیرند — و مهم‌تر، **نمی‌توانند اشتباه بگیرند**: `onSurface` در
 * حالتِ روشن تقریباً سیاه است و روی نفتی خوانده نمی‌شود.
 */
@Composable
fun BrandCard(
    modifier: Modifier = Modifier,
    brush: Brush = InkBrush,
    shape: RoundedCornerShape = RoundedCornerShape(Radius.xl),
    contentPadding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // کارتی که کار می‌کند باید برای صفحه‌خوان هم «دکمه» باشد، نه یک
    // ناحیهٔ بی‌نام. کارتِ بی‌کنش نقشی نمی‌گیرد تا بی‌جهت اعلام نشود.
    val clickable =
        if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
        else Modifier
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(brush)
            .drawBehind {
                // هاله در گوشهٔ بالاییِ انتها (در راست‌به‌چپ: بالا-چپ)،
                // دور از جایی که عنوان و عدد شروع می‌شوند.
                val cx = if (layoutDirection == LayoutDirection.Rtl) 0f else size.width
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Brand.OnPetrol.copy(alpha = HERO_GLOW_ALPHA),
                        1f to Color.Transparent,
                        center = Offset(cx, 0f),
                        radius = size.minDimension * 1.4f
                    ),
                    radius = size.minDimension * 1.4f,
                    center = Offset(cx, 0f)
                )
            }
            .then(clickable)
    ) {
        CompositionLocalProvider(LocalContentColor provides Brand.OnPetrol) {
            Column(
                Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

/** کدریِ هالهٔ روشنِ کارتِ قهرمان — دلیلِ عدد در توضیحِ [BrandCard]. */
private const val HERO_GLOW_ALPHA = 0.08f

/**
 * دکمهٔ اصلیِ صفحه، با رویهٔ مرجانی.
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
    busyText: String = "در حالِ ثبت…",
    // قرص، مثلِ هر دکمهٔ دیگرِ متریال در اپ — تا ۱.۱۱ تنها دکمه‌ای بود که
    // گوشهٔ ۱۴ داشت و کنارِ دکمه‌های قرصیِ کامپیوتر جدا دیده می‌شد.
    shape: RoundedCornerShape = PillShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    leading: (@Composable () -> Unit)? = null
) {
    val live = enabled && !busy
    val surface = if (live) CoralBrush else SolidColor(disabledSurface())
    val ink = if (live) Brand.OnCoral else disabledContent()

    /*
     * `role = Role.Button` و `enabled` صریح‌اند، نه تزئینی.
     *
     * این یک `Box` است نه `Button`ِ متریال (چون رنگِ ظرفِ آن `Color`
     * است و گرادیان نمی‌گیرد). ولی جعبهٔ ساده برای TalkBack «دکمه»
     * نیست — فقط یک چیزِ قابلِ لمس. کاربری که با صفحه‌خوان کار
     * می‌کند نمی‌فهمید این دکمهٔ «ثبتِ فروش» است.
     *
     * `enabled` هم به خودِ `clickable` داده می‌شود نه فقط با
     * برداشتنِ آن: این‌طور صفحه‌خوان «غیرفعال» را هم اعلام می‌کند و
     * موجِ لمس (ripple) روی دکمهٔ خاموش نمی‌افتد.
     *
     * ارتفاعِ کمینه ۴۸dp است — حدِ اندروید برای هدفِ لمسی.
     */
    Box(
        modifier
            // سایهٔ نرمِ هم‌رنگ زیرِ دکمهٔ زنده: دکمه «برآمده» خوانده می‌شود
            // بی آنکه خط یا براقی بخواهد. دکمهٔ خاموش سایه ندارد — چیزی که
            // زده نمی‌شود نباید از صفحه بیرون بزند.
            .then(
                if (live) Modifier.shadow(
                    elevation = 6.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Brand.CoralDeep.copy(alpha = 0.35f),
                    spotColor = Brand.CoralDeep.copy(alpha = 0.45f)
                ) else Modifier
            )
            .clip(shape)
            .background(surface)
            .clickable(
                enabled = live,
                role = Role.Button,
                onClickLabel = if (busy) busyText else text,
                onClick = onClick
            )
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
 * سطرِ هشدار: آیکنِ برداری + متن، هم‌تراز از بالا.
 *
 * **جایگزینِ «⚠ …» شد.** ایموجی روی هر گوشی شکلِ خودش را دارد (روی
 * یکی زرد و تخت، روی دیگری قرمز و برجسته)، اندازه‌اش با متن می‌پرد،
 * از پالت رنگ نمی‌گیرد، و صفحه‌خوان آن را «علامتِ تعجب در مثلث»
 * می‌خواند وسطِ جمله. آیکنِ برداری هر چهار مشکل را ندارد.
 *
 * `Alignment.Top` عمدی است: متنِ دوخطی نباید آیکن را وسط بیندازد.
 */
@Composable
fun WarningLine(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.error,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    icon: ImageVector = Icons.Default.WarningAmber
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            // متن خودش همه‌چیز را می‌گوید؛ اعلامِ دوبارهٔ «هشدار» فقط
            // صفحه‌خوان را پرحرف می‌کند.
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(text, style = style, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * یک مرحله از خطِ تولید: آیکن، نام، عدد.
 *
 * پیش از این کلِ ضربانِ خطِ تولید یک رشته بود:
 * یک رشتهٔ سه‌ایموجیِ به‌هم‌چسبیده. سه ایموجی، سه فاصلهٔ دستی،
 * و هیچ راهی برای اینکه ستون‌ها زیرِ هم بیفتند یا عددها از نام جدا
 * دیده شوند.
 */
@Composable
fun StageChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
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
                color = Brand.OnPetrolMuted
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Brand.OnPetrol
            )
        }
        if (trailing != null) trailing()
    }
}
