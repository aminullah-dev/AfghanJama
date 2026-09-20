package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.WorkshopLoadViewModel
import com.afghanjama.work.WorkshopLoad

/**
 * بارِ کارگاه — «تا کِی می‌توانم قبول کنم؟».
 *
 * صفحه عمداً **جواب** می‌دهد، نه جدول. کارفرما سرِ گرفتنِ سفارش وقتِ
 * خواندنِ نمودار ندارد؛ یک جمله می‌خواهد.
 */
@Composable
fun WorkshopLoadScreen(
    vm: WorkshopLoadViewModel,
    onBack: () -> Unit,
) {
    val s by vm.ui.collectAsState()

    AppScreen(title = "بارِ کارگاه", onBack = onBack) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!s.hasCapacityData) {
                /*
                 * **عددِ ساختگی ساخته نمی‌شود.** بی تاریخچه، هر ظرفیتی
                 * که بنویسیم حدس است — و کارفرما بر اساسش به مشتری قول
                 * می‌دهد. سکوتِ صریح بهتر از عددی است که کسی نمی‌تواند
                 * وارسی‌اش کند.
                 */
                AppCard {
                    Text("هنوز نمی‌شود ظرفیت را سنجید", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "در ${s.windowDays.fa()} روزِ گذشته هیچ تحویلِ دوختی ثبت نشده، " +
                            "پس عددی برای «چند دست در روز» وجود ندارد. " +
                            "بعد از چند تحویل، همین صفحه خودش جواب می‌دهد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AppCard {
                    Text(
                        "ظرفیت: ${fmt(s.piecesPerDay)} دست در روز",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // پایهٔ عدد صریح نوشته می‌شود تا قابلِ وارسی باشد.
                    Text(
                        "بر پایهٔ ${s.windowPieces.fa()} دستِ تمام‌شده در " +
                            "${s.windowDays.fa()} روزِ گذشته.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val free = s.firstFree
                AppCard {
                    Text(
                        if (free == null) "چهار هفتهٔ آینده پر است"
                        else "اولین جای خالی: ${PersianDate.short(free.startMs)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (free == null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        if (free == null)
                            "هر تعهدِ تازه‌ای یا باید بعد از این چهار هفته باشد، " +
                                "یا کارِ موجود را عقب می‌اندازد."
                        else "در آن هفته ${free.free.fa()} دست جا هست.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            Text("چهار هفتهٔ آینده", style = MaterialTheme.typography.titleSmall)
            s.buckets.forEach { b -> WeekRow(b, s.hasCapacityData) }

            if (s.overduePieces > 0 || s.undatedPieces > 0) {
                HorizontalDivider()
                AppCard {
                    if (s.overduePieces > 0) {
                        Text(
                            "${s.overduePieces.fa()} دست مهلتش گذشته و هنوز ساخته نشده",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (s.undatedPieces > 0) {
                        // بی مهلت یعنی بیرونِ هر برنامه‌ای — و این خودش
                        // خبرِ بدی است، نه جزئیاتِ بی‌اهمیت.
                        Text(
                            "${s.undatedPieces.fa()} دست اصلاً مهلت ندارد، " +
                                "پس در هیچ هفته‌ای شمرده نشده.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                "جمعِ کارِ مانده: ${s.remainingPieces.fa()} دست",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekRow(b: WorkshopLoad.Bucket, hasCapacity: Boolean) = AppCard {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "از ${PersianDate.short(b.startMs)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (hasCapacity) "${b.pieces.fa()} از ${b.capacity.fa()}"
            else "${b.pieces.fa()} دست",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (b.over) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
    }
    if (hasCapacity && b.capacity > 0) {
        LinearProgressIndicator(
            progress = { (b.pieces.toFloat() / b.capacity).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = if (b.over) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
        )
    }
}

/** یک رقمِ اعشار، با ارقامِ فارسی. */
private fun fmt(v: Double): String {
    val whole = v.toInt()
    val tenth = ((v - whole) * 10).toInt().coerceIn(0, 9)
    return if (tenth == 0) whole.fa() else "${whole.fa()}٫${tenth.fa()}"
}
