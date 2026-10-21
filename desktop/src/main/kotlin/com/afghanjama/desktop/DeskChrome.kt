package com.afghanjama.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.nav.DeskSection
import com.afghanjama.ui.components.GlassTokens
import com.afghanjama.ui.components.glassSurface

/**
 * جای کاربر در پنجره — کدام بند باز است، و آیا سفارش یا مشتری‌ای از دلِ
 * آن باز شده.
 *
 * **چرا بیرونِ `Shell`.** تا ۱.۹ این‌ها `remember`ِ خودِ `Shell` بودند.
 * حالا منوی بالای مک («نما» ← «مرکزِ هشدار»…) هم باید بتواند جابه‌جا کند،
 * و آن منو بیرونِ `Shell` ساخته می‌شود. پس یک صاحب، دو خواننده.
 */
internal class DeskNav {
    var section by mutableStateOf(DeskSection.Home)
        private set

    /** جزئیاتِ مشتری — از دلِ «خریداران» یا «پیگیریِ طلب». */
    var openCustomer by mutableStateOf<Long?>(null)

    /** جزئیاتِ سفارش — از دلِ هر فهرستی. */
    var openOrder by mutableStateOf<String?>(null)

    /**
     * «جابه‌جاییِ پول» به حالتِ پرداخت باز شود یا دریافت.
     *
     * خانه دو دکمه دارد («پرداخت» و «دریافت»)؛ روی گوشی هر کدام مسیرِ خودش
     * است، اینجا هر دو یک بند‌اند و فقط حالتِ شروعشان فرق دارد.
     */
    var moneyAsPayment by mutableStateOf(false)
        private set

    fun go(s: DeskSection) {
        section = s
        openCustomer = null
        openOrder = null
        moneyAsPayment = false
    }

    fun goMoney(pay: Boolean) {
        go(DeskSection.MoneyMove)
        moneyAsPayment = pay
    }
}

/**
 * زمینهٔ پنجره: رنگِ زمینهٔ برند با دو لکهٔ بسیار نرم از رنگ‌های خودِ
 * برند (آبیِ نفتی و مرجانی) — پشتِ همه‌چیز، یک بار رسم.
 *
 * گرادیانِ شعاعی است نه تارکردن: همان نرمی را می‌دهد و در هر فریم هزینه‌ای
 * ندارد.
 */
@Composable
internal fun DeskBackdrop(modifier: Modifier = Modifier) {
    val petrol = MaterialTheme.colorScheme.primaryContainer
    val coral = MaterialTheme.colorScheme.secondaryContainer
    Canvas(modifier.fillMaxSize().background(Bg)) {
        val r1 = size.minDimension * 0.75f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(petrol.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.08f),
                radius = r1,
            ),
            radius = r1,
            center = Offset(size.width * 0.92f, size.height * 0.08f),
        )
        val r2 = size.minDimension * 0.6f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(coral.copy(alpha = 0.40f), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.95f),
                radius = r2,
            ),
            radius = r2,
            center = Offset(size.width * 0.12f, size.height * 0.95f),
        )
    }
}

/**
 * سطحِ شیشه‌ای — همان شیشهٔ `:core` ([glassSurface] و [GlassTokens])، با
 * رنگ‌های ثابتِ کامپیوتر که فعلاً فقط روشن است.
 *
 * تا ۱.۱۱ اینجا شیشهٔ سومی بود (۰٫۸۰ و سایهٔ ۸)، کنارِ دو شیشهٔ دیگر در
 * صفحه‌های کارگاهِ وصل (۰٫۸۶ و ۰٫۹۲). حالا هر سه از یک عدد می‌خوانند.
 */
internal fun Modifier.deskGlass(shape: Shape, elevation: Dp = 8.dp): Modifier =
    glassSurface(
        shape = shape,
        fill = CardBg.copy(alpha = GlassTokens.FILL_ALPHA),
        edge = CardBg.copy(alpha = GlassTokens.EDGE_ALPHA),
        shadowTint = Brand.copy(alpha = GlassTokens.SHADOW_ALPHA),
        elevation = elevation,
    )
