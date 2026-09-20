package com.afghanjama.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.theme.LocalReducedMotion
import com.afghanjama.ui.theme.Motion

/**
 * مبلغی که وقتی عوض می‌شود، **دیده می‌شود عوض شد**.
 *
 * **چرا لازم بود.** موجودیِ صندوق، ماندهٔ مشتری و سودِ فروش تا امروز
 * آنی می‌پریدند. کارفرما فروشی ثبت می‌کرد، عدد در یک قاب عوض می‌شد، و
 * هیچ نشانه‌ای نبود که همین حالا تغییر کرد یا از اول همین بود. در
 * دفتری که کارش پول است، این تفاوتِ کوچکی نیست.
 *
 * **چرا شمارش نمی‌کند.** عددِ چرخانِ هفت‌رقمی روی دفترِ کارگاه شلوغ است
 * و چشم را از خودِ رقم دور می‌کند؛ ضمناً شمارشِ `Long` از راهِ اعشار
 * روی مبالغِ بزرگ دقت را می‌خورد.
 *
 * **جهت، خودش خبر است.** مبلغی که زیاد شده از **پایین** بالا می‌آید و
 * مبلغی که کم شده از **بالا** پایین می‌رود — همان جهتی که ذهن برای
 * «بیشتر» و «کمتر» دارد. پس کاربر پیش از خواندنِ رقم می‌داند چه شد.
 *
 * سرِ اولین نمایش حرکتی نیست: `AnimatedContent` مقدارِ اول را
 * می‌نشاند، نه اینکه از صفر بیاوردش. صفحه‌ای که با عددهای در حالِ
 * پرواز باز شود، پرِ افه است نه پرِ اطلاعات.
 */
@Composable
fun AnimatedAfn(
    amount: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    // یک بار خوانده می‌شود و به specها داده می‌شود: `transitionSpec`
    // لامبدای غیر-composable است و خواندنِ CompositionLocal داخلش
    // ممنوع.
    val reduced = LocalReducedMotion.current

    AnimatedContent(
        targetState = amount,
        transitionSpec = {
            val grew = targetState > initialState
            val height: (Int) -> Int = { if (grew) it else -it }
            (
                slideInVertically(Motion.enter(reduced), initialOffsetY = height) +
                    fadeIn(Motion.enter(reduced))
                ) togetherWith (
                slideOutVertically(Motion.exit(reduced), targetOffsetY = { -height(it) }) +
                    fadeOut(Motion.exit(reduced))
                ) using
                // بی این، قاب سرِ عوض شدنِ تعدادِ رقم‌ها می‌پرد و
                // چیزهای کنارش را هل می‌دهد. اندازه هم با همان ریتم
                // عوض می‌شود.
                SizeTransform(clip = false) { _, _ -> Motion.normal(reduced) }
        },
        modifier = modifier,
        label = "afn"
    ) { value ->
        Text(value.afn(), style = style, color = color)
    }
}
