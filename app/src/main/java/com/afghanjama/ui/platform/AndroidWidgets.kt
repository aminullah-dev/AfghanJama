package com.afghanjama.ui.platform

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable

/**
 * [Widgets] روی اندروید.
 *
 * **این فایل عمداً در `:app` است و نه در `:core`.** کلِ فایده‌اش همین
 * است: اینجا `DropdownMenu` به `AndroidMenu_androidKt` بسته می‌شود و
 * دوقلوی رومیزی‌اش به `SkikoMenu_skikoKt`. اگر همین کد در `:core`
 * می‌بود، یک بار کامپایل می‌شد و روی یکی از دو سکو سرِ اجرا می‌ترکید —
 * که دقیقاً همان اتفاقی بود که افتاد.
 */
object AndroidWidgets : Widgets {

    @Composable
    override fun Dialog(
        onDismissRequest: () -> Unit,
        title: (@Composable () -> Unit)?,
        text: @Composable () -> Unit,
        confirmButton: @Composable () -> Unit,
        dismissButton: (@Composable () -> Unit)?
    ) = AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )

    @Composable
    override fun Menu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        content: @Composable ColumnScope.() -> Unit
    ) = DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        content = content
    )

    @Composable
    override fun MenuItem(
        text: @Composable () -> Unit,
        onClick: () -> Unit,
        enabled: Boolean
    ) = DropdownMenuItem(text = text, onClick = onClick, enabled = enabled)
}
