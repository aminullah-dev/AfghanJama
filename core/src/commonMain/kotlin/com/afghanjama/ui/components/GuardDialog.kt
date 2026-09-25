package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.EntryGuard
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.platform.AppAlertDialog

/**
 * «یک لحظه — دوباره نگاه کنید.» پیش از ثبتِ پولی که شبیهِ اشتباه است.
 *
 * هشدار است نه منع: «درست است، ثبت کن» همیشه هست. اگر نگهبان حدس زده
 * منظور چه مبلغی بوده (صفرِ اضافه یا کم)، یک دکمه همان را می‌گذارد تا
 * کاربر دوباره تایپ نکند.
 */
@Composable
fun GuardDialog(
    warnings: List<EntryGuard.Warning>,
    onConfirm: () -> Unit,
    onUse: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val suggested = warnings.firstNotNullOfOrNull { it.suggested }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("یک لحظه — دوباره نگاه کنید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                warnings.forEach { w ->
                    Text(
                        "• " + w.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (w.kind == EntryGuard.Kind.DUPLICATE) FontWeight.SemiBold
                        else FontWeight.Normal
                    )
                }
                if (suggested != null) {
                    OutlinedButton(onClick = { onUse(suggested) }) {
                        Text("${suggested.afn()} را بگذار")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("درست است، ثبت کن") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("برگرد") } }
    )
}
