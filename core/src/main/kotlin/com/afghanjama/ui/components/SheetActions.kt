@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.pdf.Paper

/** کاری که کاربر با برگه می‌خواهد بکند. */
enum class SheetAction { PRINT, PDF, IMAGE }

/**
 * نوارِ «چاپ / PDF / تصویر» با انتخابِ اندازهٔ کاغذ.
 *
 * یک جا نوشته شده تا هر برگه‌ای در اپ — فاکتور، رسید، سند — دقیقاً همان
 * دکمه‌ها را داشته باشد و کاربر لازم نباشد در هر صفحه چیزِ تازه‌ای یاد
 * بگیرد.
 *
 * دکمه‌ها `BusyButton`اند چون ساختِ برگه چند ثانیه طول می‌کشد و دو بار
 * زدن یعنی دو فایل و دو کارِ چاپ.
 */
@Composable
fun SheetActions(
    paper: Paper,
    onPaperChange: (Paper) -> Unit,
    busy: Boolean,
    onAction: (SheetAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "اندازهٔ کاغذ",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Paper.ALL.forEach { p ->
                FilterChip(
                    selected = p.label == paper.label,
                    onClick = { onPaperChange(p) },
                    enabled = !busy,
                    label = { Text(p.label) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BusyButton(
                text = "چاپ",
                onClick = { onAction(SheetAction.PRINT) },
                modifier = Modifier.weight(1f),
                busy = busy,
                busyText = "…",
                leading = { Icon(Icons.Default.Print, contentDescription = null) }
            )
            BusyButton(
                text = "PDF",
                onClick = { onAction(SheetAction.PDF) },
                modifier = Modifier.weight(1f),
                busy = busy,
                busyText = "…",
                leading = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
            )
            BusyButton(
                text = "تصویر",
                onClick = { onAction(SheetAction.IMAGE) },
                modifier = Modifier.weight(1f),
                busy = busy,
                busyText = "…",
                leading = { Icon(Icons.Default.Image, contentDescription = null) }
            )
        }
    }
}
