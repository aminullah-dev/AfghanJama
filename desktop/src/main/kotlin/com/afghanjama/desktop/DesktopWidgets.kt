package com.afghanjama.desktop

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import com.afghanjama.ui.platform.Widgets

/**
 * [Widgets] روی ویندوز — نیمهٔ دومِ همان مرز.
 *
 * کدش با نسخهٔ اندرویدی **کلمه‌به‌کلمه یکی است** و این تکرار عمدی است:
 * تفاوت در متن نیست، در جایی است که کامپایل می‌شود. اینجا همین
 * فراخوانی‌ها به `SkikoMenu_skikoKt` و `AlertDialog_skikoKt` بسته
 * می‌شوند، آنجا به دوقلوی اندرویدی‌شان.
 */
object DesktopWidgets : Widgets {

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
