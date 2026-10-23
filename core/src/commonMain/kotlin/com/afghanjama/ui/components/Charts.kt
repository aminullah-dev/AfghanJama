package com.afghanjama.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.theme.PillShape
import com.afghanjama.ui.theme.Radius

/**
 * گرادیانِ نرمِ یک رنگ — کمی روشن‌تر در بالا، مثلِ نورِ مایل روی سطحِ صیقلی.
 * همان حسی که کارت‌های شیشه‌ای دارند را به میله‌ها هم می‌دهد تا یک‌خانواده باشند.
 */
private fun sheen(color: Color): Brush =
    Brush.verticalGradient(listOf(lerp(color, Color.White, 0.22f), color))

/*
 * چارت‌های داشبورد — دست‌ساز، بی هیچ وابستگیِ تازه.
 *
 * **چرا دست‌ساز و نه یک کتابخانهٔ چارت.** شیشه، رنگِ برند، گوشهٔ گرد و
 * راست‌به‌چپ همه از همین سیستمِ طراحی می‌آیند؛ یک کتابخانهٔ بیرونی رنگ و
 * فرمِ خودش را می‌آورد و کنارِ کارت‌های شیشه‌ای مثلِ یک اپِ دیگر می‌شود —
 * همان «یک‌دست نبودن» که می‌خواهیم کم کنیم. اینجا هر چارت از `colorScheme`،
 * `Radius`، `Space` و پالتِ برند می‌خواند، پس با بقیهٔ صفحه یکی است.
 *
 * **سه قاعدهٔ خواندنی‌بودن (مثلِ خودِ داشبورد):**
 *  - میله‌های افقی و عمودی با **چیدمان** کشیده می‌شوند، نه Canvas — تا جهتِ
 *    راست‌به‌چپ را خودِ Compose درست ببرد (میله از راست پر شود).
 *  - **متن** همیشه رنگِ متن دارد (`onSurface`/`onSurfaceVariant`)، نه رنگِ
 *    میله؛ رنگ فقط روی خودِ شکل است تا هویت با رنگ‌تنها نباشد.
 *  - عددِ هر میله کنارش نوشته می‌شود؛ محورِ شلوغ لازم نیست.
 */

/**
 * یک میلهٔ افقیِ نازک: مسیرِ گردِ کم‌رنگ + پُرشدنِ گرد به نسبتِ [fraction] (۰..۱).
 *
 * راست‌به‌چپ را خودِ چیدمان می‌برد: جعبهٔ درونی از «شروع» پر می‌شود که در
 * RTL همان سمتِ راست است.
 */
@Composable
fun BarMeter(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(modifier.fillMaxWidth().height(height).clip(PillShape).background(track)) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(PillShape).background(sheen(color)))
        }
    }
}

/**
 * یک ردیفِ «برچسب — مقدار» با میلهٔ افقیِ زیرش (بزرگ‌ترین بدهکار، بارِ خیاط…).
 *
 * [fraction] نسبتِ این ردیف به بزرگ‌ترین ردیفِ همان فهرست است تا طول‌ها
 * قابلِ مقایسه باشند؛ آن را بیرون حساب کنید (مقدار تقسیم بر بیشینه).
 */
@Composable
fun BarRow(
    label: String,
    value: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) trailing()
            else Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        BarMeter(fraction, color)
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * یک ستونِ میله‌ایِ عمودی: برچسبِ زیر، مقدارِ [value] (برای قدِ میله) و
 * [top] (نوشتهٔ روی میله — خالی یعنی هیچ). [emphasize] میله را پررنگ‌تر می‌کند.
 */
data class ColumnDatum(val label: String, val value: Long, val top: String = "", val emphasize: Boolean = false)

/**
 * میله‌های عمودی برای یک مجموعهٔ ترتیبی (مرحله‌های تولید، یا روندِ ماهانه):
 * نوشتهٔ بالا (اختیاری)، میله به نسبتِ بیشینه، برچسب زیرش. میلهٔ صفر یک خطِ
 * کم‌رنگِ نازک است، نه هیچ.
 */
@Composable
fun ColumnBars(
    bars: List<ColumnDatum>,
    color: Color,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 84.dp,
) {
    if (bars.isEmpty()) return
    val max = (bars.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val emphColor = MaterialTheme.colorScheme.secondary
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.Bottom) {
        bars.forEach { b ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (b.top.isNotBlank()) {
                    Text(b.top, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(chartHeight), contentAlignment = Alignment.BottomCenter) {
                    val frac = if (b.value <= 0L) 0f else (b.value.toFloat() / max).coerceIn(0.08f, 1f)
                    val fill = if (b.emphasize) emphColor else color
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight(if (b.value <= 0L) 0.03f else frac)
                            .clip(RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm))
                            .background(if (b.value <= 0L) SolidColor(track) else sheen(fill)),
                    )
                }
                Text(
                    b.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** یک برشِ حلقه: برچسب، مقدار، رنگ. */
data class DonutSlice(val label: String, val value: Long, val color: Color)

/**
 * حلقهٔ ترکیب (پول در کدام صندوق، سهمِ هر چیز از کل). برش‌های با فاصلهٔ کوچک
 * و سرِ گرد — نرم و شیشه‌ای. وسطِ حلقه با [center] پر می‌شود (مثلاً جمعِ کل).
 *
 * اگر جمع صفر یا منفی باشد، فقط یک حلقهٔ کم‌رنگ کشیده می‌شود (بی عددِ قلابی).
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    ring: Dp = 18.dp,
    center: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val total = slices.sumOf { it.value.coerceAtLeast(0L) }
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = ring.toPx()
            val d = size.minDimension - stroke
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arc = Size(d, d)
            if (total <= 0L) {
                drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                return@Canvas
            }
            val positive = slices.filter { it.value > 0L }
            val gapDeg = if (positive.size > 1) 5f else 0f
            var start = -90f
            positive.forEach { s ->
                val sweep = (s.value.toFloat() / total) * 360f
                val draw = (sweep - gapDeg).coerceAtLeast(3f)
                drawArc(s.color, start + gapDeg / 2f, draw, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                start += sweep
            }
        }
        center()
    }
}

/** فهرستِ راهنمای حلقه: نقطهٔ رنگی + برچسب + مقدار (متن همیشه رنگِ متن). */
@Composable
fun DonutLegend(
    slices: List<DonutSlice>,
    valueText: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        slices.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(s.color))
                Text(s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(valueText(s.value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
