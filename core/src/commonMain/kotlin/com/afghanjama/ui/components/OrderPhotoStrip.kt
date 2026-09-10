package com.afghanjama.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.OrderPhoto
import com.afghanjama.platform.LocalPhotos
import com.afghanjama.platform.Photos
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.platform.AppAlertDialog

/**
 * نوارِ عکس‌های یک سفارش: نمایش، افزودن (دوربین یا گالری) و حذف.
 *
 * خیاطی کارِ چشمی است — عکسِ طرح یا نمونه‌ای که مشتری آورده، بیشتر از
 * هر توضیحی به برشکار و خیاط کمک می‌کند.
 *
 * **چرا از `:app` به اینجا آمد.** این تنها چیزی بود که صفحهٔ جزئیاتِ
 * سفارش و صفحهٔ برش را در `:app` نگه داشته بود — دو صفحه‌ای که هیچ
 * چیزِ اندرویدیِ دیگری نداشتند. با مرزِ [Photos] هر سه تکه‌اش پرتابل
 * شد: خواندنِ ریزعکس، انتخاب از گالری، و گرفتن با دوربین.
 *
 * **دوربین فقط جایی است که هست.** روی پی‌سی `rememberCapture` برابرِ
 * `null` است و آن دکمه اصلاً ساخته نمی‌شود — نه اینکه باشد و سرِ زدن
 * هیچ نکند. مجوزِ دوربین هم آن‌سوی مرز است، جایی که معنا دارد.
 */
@Composable
fun OrderPhotoStrip(
    photos: List<OrderPhoto>,
    canEdit: Boolean,
    onCaptured: (fileName: String) -> Unit,
    onDelete: (OrderPhoto) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "عکس‌های سفارش"
) {
    val store = LocalPhotos.current
    var viewing by remember { mutableStateOf<OrderPhoto?>(null) }

    val pick = store.rememberPicker(onCaptured)
    val capture = store.rememberCapture(onCaptured)

    // ---- نمایشِ بزرگ ----
    viewing?.let { p ->
        val big = store.rememberThumb(p.fileName, Photos.MAX_SIDE)
        AppAlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(p.orderCode) },
            text = {
                if (big != null) {
                    Image(
                        bitmap = big,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(320.dp)
                    )
                } else {
                    Text(
                        "عکس خوانده نشد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("بستن") } },
            dismissButton = if (canEdit) {
                {
                    TextButton(onClick = { onDelete(p); viewing = null }) {
                        Text("حذف عکس", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else null
        )
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (photos.isEmpty()) title else "$title (${photos.size.fa()})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (photos.isEmpty()) {
            Text(
                "عکسی ثبت نشده — عکسِ طرح یا نمونهٔ مشتری در برش و دوخت هم دیده می‌شود.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { p ->
                    Box {
                        val thumb = store.rememberThumb(p.fileName, Photos.THUMB_SIDE)
                        val box = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                        if (thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = box.clickable { viewing = p }
                            )
                        } else {
                            Box(box)
                        }
                        if (canEdit) {
                            // اندازهٔ صریح داده نمی‌شود: ناحیهٔ لمسِ
                            // پیش‌فرضِ IconButton ۴۸dp است و این دکمهٔ
                            // **حذف** است — خطای انگشت اینجا عکسِ سفارش
                            // را پاک می‌کند.
                            IconButton(
                                onClick = { onDelete(p) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "حذف عکس",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(Color(0x99000000), CircleShape)
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (canEdit) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (capture != null) {
                    OutlinedButton(onClick = capture, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Box(Modifier.size(8.dp))
                        Text("دوربین")
                    }
                }
                OutlinedButton(onClick = pick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Box(Modifier.size(8.dp))
                    Text(if (capture != null) "گالری" else "انتخاب عکس")
                }
            }
        }
    }
}
