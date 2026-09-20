package com.afghanjama.ios

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import com.afghanjama.ui.platform.Widgets

/**
 * [Widgets] روی iOS — نیمهٔ سومِ همان مرز.
 *
 * کدش با نسخهٔ اندرویدی و ویندوزی **کلمه‌به‌کلمه یکی است**، درست مثلِ
 * آن دو که با هم یکی‌اند. آنجا این تکرار توجیه داشت: هر فراخوانی به
 * پیاده‌سازیِ سکوی خودش بسته می‌شد.
 *
 * **ولی حالا که سه‌تا شده، توجیهش سست شده.** `material3` در
 * Compose Multiplatform روی هر سه سکو همین یک API است، پس این سه فایل
 * می‌توانند یکی شوند و به `commonMain` بروند. آن کار به `:app` و
 * `:desktop` هم دست می‌زند و جای این کامیت نیست؛ اینجا نوشته می‌شود تا
 * فراموش نشود.
 */
object IosWidgets : Widgets {

    @Composable
    override fun Dialog(
        onDismissRequest: () -> Unit,
        title: (@Composable () -> Unit)?,
        text: @Composable () -> Unit,
        confirmButton: @Composable () -> Unit,
        dismissButton: (@Composable () -> Unit)?,
    ) = AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )

    @Composable
    override fun Menu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ) = DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        content = content,
    )

    @Composable
    override fun MenuItem(
        text: @Composable () -> Unit,
        onClick: () -> Unit,
        enabled: Boolean,
    ) = DropdownMenuItem(text = text, onClick = onClick, enabled = enabled)
}
