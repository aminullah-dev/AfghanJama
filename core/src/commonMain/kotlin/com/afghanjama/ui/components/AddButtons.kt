package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/*
 * «افزودن» — یک شکل در همهٔ اپ.
 *
 * تا امروز هر صفحه دکمهٔ افزودنِ خودش را داشت: جایی «افزودن» بی نشانه،
 * جایی «+ وقت تازه» با بعلاوهٔ تایپ‌شده، جایی دکمهٔ گردِ بی‌متن. کاربری
 * که در یک صفحه یاد گرفته «+ یعنی چیزِ تازه»، در صفحهٔ بعد دنبالش
 * می‌گشت. حالا هر جا چیزی ساخته می‌شود همان نشانهٔ + است، و در فهرست‌های
 * بلند دکمه‌اش شناور و همیشه در دسترس.
 */

/** دکمهٔ پرِ «+ …» — برای فرمی که بالای یک فهرست نشسته. */
@Composable
fun AddButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

/** نسخهٔ کم‌رنگ — کنارِ عنوانِ یک بخش، جایی که دکمهٔ پر زیادی فریاد می‌زند. */
@Composable
fun AddTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text)
    }
}

/**
 * دکمهٔ شناورِ «+ …» برای صفحه‌ای که فهرستِ بلند دارد.
 *
 * **با متن، نه فقط نشانه.** دکمهٔ گردِ تنها برای کسی که با گوشی بزرگ
 * نشده معنایی ندارد؛ «+ مشتریِ تازه» خودش می‌گوید چه می‌کند.
 */
@Composable
fun AddFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text(text) },
    )
}
