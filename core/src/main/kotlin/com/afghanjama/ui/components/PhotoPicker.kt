package com.afghanjama.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.afghanjama.platform.LocalPhotos
import com.afghanjama.platform.Photos

/**
 * یک عکس — انتخاب، دیدن، برداشتن.
 *
 * پیش‌تر این در `:app` بود و مستقیم `PhotoStore` و انتخابگرِ اندروید را
 * صدا می‌زد؛ همان یک وابستگی، انبارِ محصول و تنظیمات را آنجا نگه داشته
 * بود. حالا از مرزِ [Photos] می‌گذرد و هر دو سکو دارندش.
 *
 * **دکمهٔ دوربین فقط جایی ساخته می‌شود که دوربین هست.** روی پی‌سی
 * `rememberCapture` برابرِ `null` است و آن دکمه اصلاً نیست — نه اینکه
 * باشد و کاری نکند.
 */
@Composable
fun SinglePhotoPicker(
    fileName: String,
    canEdit: Boolean,
    onPicked: (String) -> Unit,
    onCleared: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "عکس کالا"
) {
    val photos = LocalPhotos.current
    val pick = photos.rememberPicker(onPicked)
    val capture = photos.rememberCapture(onPicked)
    val thumb = photos.rememberThumb(fileName, Photos.THUMB_SIDE)

    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (thumb != null) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Image(
                    bitmap = thumb,
                    contentDescription = label,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (canEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = pick) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Text("  $label", style = MaterialTheme.typography.labelLarge)
                    }
                    // فقط روی سکویی که دوربین دارد
                    if (capture != null) {
                        OutlinedButton(onClick = capture) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Text("  دوربین", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (fileName.isNotBlank()) {
                        OutlinedButton(onClick = onCleared) {
                            Icon(Icons.Filled.Close, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Text("  برداشتن", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (fileName.isBlank()) {
                Text(
                    "عکسی انتخاب نشده.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
