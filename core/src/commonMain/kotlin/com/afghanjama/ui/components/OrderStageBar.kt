package com.afghanjama.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afghanjama.data.OrderPipeline
import com.afghanjama.ui.format.fa

/**
 * نوارِ پیشرفتِ سفارش: پنج نقطه از ثبت تا تحویل، با قدمِ جاری برجسته.
 *
 * **چرا این‌جا.** صفحهٔ سفارش وضعیت را فقط با یک کلمه در گوشه می‌گفت
 * («دوخت»). آن کلمه می‌گوید «کجاست» ولی نمی‌گوید «چقدر راه مانده». این
 * نوار همان یک کلمه را روی یک خط می‌نشاند تا چشم در یک نگاه ببیند چند
 * قدم طی شده و چند قدم باقی است. نگاشتِ وضعیت به قدم در [OrderPipeline]
 * است، نه این‌جا، تا همین نوار بعدها هر جای دیگری هم بی‌تکرار بیاید.
 *
 * نقطهٔ سبزِ تیک = قدمِ طی‌شده، نقطهٔ حلقه‌دار = قدمِ جاری، نقطهٔ توخالی =
 * پیشِ‌رو. خطِ بینِ نقطه‌ها تا جایی که کار رسیده پررنگ است.
 */
@Composable
fun OrderStageBar(status: String, modifier: Modifier = Modifier) {
    val steps = OrderPipeline.steps(status)
    val scheme = MaterialTheme.colorScheme

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            steps.forEachIndexed { i, step ->
                val reached = step.state != OrderPipeline.State.UPCOMING   // کار به این نقطه رسیده
                val passed = step.state == OrderPipeline.State.DONE          // کار از این نقطه گذشته
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    // نیمهٔ چپِ رابط (برای اولین نقطه کشیده نمی‌شود)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .then(
                                if (i == 0) Modifier
                                else Modifier.background(if (reached) scheme.primary else scheme.outlineVariant)
                            )
                    )
                    StageDot(step.state, scheme)
                    // نیمهٔ راستِ رابط (برای آخرین نقطه کشیده نمی‌شود)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .then(
                                if (i == steps.lastIndex) Modifier
                                else Modifier.background(if (passed) scheme.primary else scheme.outlineVariant)
                            )
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth()) {
            steps.forEach { step ->
                Text(
                    step.stage.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (step.state == OrderPipeline.State.CURRENT) FontWeight.Bold else FontWeight.Normal,
                    color = when (step.state) {
                        OrderPipeline.State.CURRENT -> scheme.primary
                        OrderPipeline.State.DONE -> scheme.onSurface
                        OrderPipeline.State.UPCOMING -> scheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            captionOf(status),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StageDot(state: OrderPipeline.State, scheme: androidx.compose.material3.ColorScheme) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        when (state) {
            OrderPipeline.State.DONE ->
                Box(
                    Modifier.size(18.dp).background(scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }

            OrderPipeline.State.CURRENT ->
                Box(
                    Modifier
                        .size(24.dp)
                        .border(2.dp, scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(12.dp).background(scheme.primary, CircleShape))
                }

            OrderPipeline.State.UPCOMING ->
                Box(
                    Modifier
                        .size(16.dp)
                        .background(scheme.surfaceVariant, CircleShape)
                        .border(1.5.dp, scheme.outlineVariant, CircleShape)
                )
        }
    }
}

/** خطِ زیرنویس: قدمِ چندم از پنج، یا «تکمیل شد» وقتی کار بیرون رفته. */
private fun captionOf(status: String): String {
    val cur = OrderPipeline.indexOf(status)
    return when {
        cur < 0 -> "وضعیتِ نامشخص"
        OrderPipeline.isComplete(status) -> "تکمیل شد · ${OrderPipeline.STAGES[cur].label}"
        else -> "قدمِ ${(cur + 1).fa()} از ۵ · ${OrderPipeline.STAGES[cur].label}"
    }
}
