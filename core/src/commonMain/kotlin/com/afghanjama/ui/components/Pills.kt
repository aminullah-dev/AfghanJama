package com.afghanjama.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.theme.PillShape
import com.afghanjama.ui.theme.Radius

/**
 * لحنِ یک قرص — رنگ از نقش، نه از عدد.
 *
 * هر لحن یک جفتِ «زمینه / متن» از پالت است که `contrast` از قبل
 * می‌سنجد؛ پس قرصِ تازه‌ای که کسی جایی بگذارد خودبه‌خود خواناست.
 */
enum class PillTone {
    Brand, Coral, Neutral, Calm, Bad,

    /** پُر، برای تنها چیزِ «روشن» در یک ردیف (مثلاً پنجرهٔ بازِ جفت شدن). */
    Strong,
}

/**
 * قرص — نقش، وضعیت، سطح، برچسب.
 *
 * **چرا یکی شد.** تا ۱.۱۱ دست‌کم پنج قرصِ جدا در کد بود: «نقش» در پایینِ
 * نوارِ کامپیوتر (۱۰×۲)، `Pill`ِ خصوصیِ صفحهٔ گوشیِ کارگاه (۱۲×۴)،
 * قرصِ «روشن/خاموش»ِ کارگاهِ وصل، نشانِ «سالم/زیرِ نظر/خطر»ِ وضعِ مالی
 * و `FilterChip`های گرد. هر کدام فاصله و وزنِ قلمِ خودش را داشت؛ کنارِ
 * هم دیده می‌شد که از یک خانواده نیستند. حالا یک اندازه (۱۲×۴)، یک
 * قلم (`labelMedium`، نیمه‌پررنگ) و شش لحن. تنها استثنا [CountPill] است:
 * عددِ تنها کنارِ بندِ نوار، که کوچک‌تر (۸×۲) است تا ردیف را بلند نکند.
 */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Brand,
    icon: ImageVector? = null,
) {
    if (text.isBlank()) return
    val (bg, fg) = pillColors(tone)
    Row(
        modifier
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** زمینه و متنِ هر لحن. */
@Composable
@ReadOnlyComposable
fun pillColors(tone: PillTone): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (tone) {
        PillTone.Brand -> c.primaryContainer to c.onPrimaryContainer
        PillTone.Coral -> c.secondaryContainer to c.onSecondaryContainer
        PillTone.Neutral -> c.surfaceVariant to c.onSurfaceVariant
        PillTone.Calm -> c.tertiaryContainer to c.onTertiaryContainer
        PillTone.Bad -> c.errorContainer to c.onErrorContainer
        PillTone.Strong -> c.primary to c.onPrimary
    }
}

/**
 * نشانِ شمار کنارِ یک بند یا کاشی — رنگِ پُر، چون باید از دور دیده شود.
 *
 * [urgent] مرجانی (چیزی فوری)، بقیه آبیِ برند. عدد همیشه فارسی.
 */
@Composable
fun CountPill(count: Int, modifier: Modifier = Modifier, urgent: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Text(
        count.fa(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (urgent) c.onSecondary else c.onPrimary,
        modifier = modifier
            .clip(PillShape)
            .background(if (urgent) c.secondary else c.primary)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/**
 * سطحِ شیشه‌ای — سفیدِ کمی شفاف، لبهٔ سفیدِ نازک، و سایهٔ بسیار نرم به
 * رنگِ برند.
 *
 * Compose 1.8 تارِ واقعیِ پشتِ لایه را ندارد، پس شیشه شبیه‌سازی می‌شود:
 * روی زمینه‌ای که لکه‌های نرمِ رنگِ برند دارد، همین شفافیتِ کم کافی است.
 * از ۱.۱۲ در `:core` است تا کامپیوتر و صفحه‌های مشترک یک شیشه داشته
 * باشند، نه دو شبیه‌سازیِ کمی متفاوت.
 */
fun Modifier.glassSurface(
    shape: Shape,
    fill: Color,
    edge: Color,
    shadowTint: Color,
    elevation: Dp = 8.dp,
): Modifier =
    this
        .shadow(elevation, shape, clip = false, ambientColor = shadowTint, spotColor = shadowTint)
        .clip(shape)
        .background(fill)
        .border(1.dp, edge, shape)

/** رنگ‌های شیشه از تمِ همین لحظه. */
object GlassTokens {
    const val FILL_ALPHA = 0.80f
    const val EDGE_ALPHA = 0.60f
    const val SHADOW_ALPHA = 0.08f
}

/** [glassSurface] با رنگ‌های تم. */
@Composable
fun Modifier.glass(shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xl), elevation: Dp = 8.dp): Modifier {
    val c = MaterialTheme.colorScheme
    return glassSurface(
        shape = shape,
        fill = c.surface.copy(alpha = GlassTokens.FILL_ALPHA),
        edge = c.surface.copy(alpha = GlassTokens.EDGE_ALPHA),
        shadowTint = c.primary.copy(alpha = GlassTokens.SHADOW_ALPHA),
        elevation = elevation,
    )
}

/**
 * کارتِ شیشه‌ای — همان [AppCard] برای جاهایی که زمینهٔ برند دارند
 * (کامپیوتر، صفحهٔ ورود): گوشهٔ [Radius.xl]، فاصلهٔ داخلیِ [CardPadding].
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.glass()) {
        Column(
            Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            content = content,
        )
    }
}
