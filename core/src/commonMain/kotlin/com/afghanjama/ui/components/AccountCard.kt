package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.vm.AuthUi
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.label

/**
 * نامی که برای کسِ واردشده نشان داده می‌شود. صاحبِ دستگاه گاهی نامی ثبت
 * نکرده؛ آن‌وقت «صاحبِ دستگاه»، نه دوباره نامِ نقش که کنارش در قرص هست.
 */
fun accountName(ui: AuthUi): String =
    ui.userName.trim().ifBlank { if (ui.userId == null) "صاحبِ دستگاه" else ui.role.label }

/** برچسبِ نقش: مدیر، نقشِ دستگاه، یا «کاربر» برای کسی که مدیر ساخته. */
fun accountRole(ui: AuthUi): String = when {
    ui.access.isManager -> "مدیر"
    ui.userId != null -> "کاربر"
    else -> ui.role.label
}

/**
 * «حسابِ کاربری» — نام و نقش، «تغییرِ رمز»، «خروج از حساب».
 *
 * **چرا مشترک شد.** تا ۱.۱۱ سه نسخه بود: گوشی دو کارتِ جدا داشت (فرمِ
 * تغییرِ رمز در خودِ صفحه، با «رمز جدید (حداقل ۴ رقم)»، و کارتِ «حساب
 * کاربری» با دکمهٔ سرخ)، کامپیوتر یک کارت با دیالوگ («رمزِ تازه (دست‌کم ۴
 * رقم)»)، و آیفون فقط یک «خروج از حساب»ِ ساده و جمله‌ای که می‌گفت تغییرِ
 * رمز «هنوز نیست». حالا هر سه همین کارت‌اند.
 */
@Composable
fun AccountCard(auth: AuthViewModel, onLoggedOut: () -> Unit = {}) {
    val ui by auth.ui.collectAsState()
    var changing by remember { mutableStateOf(false) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(
                "حسابِ کاربری",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Pill(accountRole(ui), tone = if (ui.access.isManager) PillTone.Coral else PillTone.Brand)
        }
        Text(
            accountName(ui),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            OutlinedButton(onClick = { changing = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Text("تغییرِ رمز")
            }
            Button(
                onClick = {
                    auth.logout()
                    onLoggedOut()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Text("خروج از حساب")
            }
        }
    }
    if (changing) ChangePinDialog(auth, onDismiss = { changing = false })
}

/**
 * عوض کردنِ رمزِ همین نفر: رمزِ فعلی سنجیده می‌شود، رمزِ تازه دست‌کم
 * چهار رقم است و مالِ کسِ دیگری نیست (`AuthViewModel.changePin`).
 */
@Composable
fun ChangePinDialog(auth: AuthViewModel, onDismiss: () -> Unit) {
    val ui by auth.ui.collectAsState()
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    val close = {
        auth.clearMessage()
        onDismiss()
    }
    AppAlertDialog(
        onDismissRequest = close,
        title = { Text("تغییرِ رمز") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { oldPin = it.digitsOnly().take(8) },
                    label = { Text("رمزِ فعلی") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.digitsOnly().take(8) },
                    label = { Text("رمزِ تازه (دست‌کم ۴ رقم)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                ui.message?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ui.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = oldPin.isNotBlank() && newPin.length >= 4,
                onClick = {
                    auth.changePin(oldPin, newPin)
                    oldPin = ""
                    newPin = ""
                }
            ) { Text("ثبتِ رمزِ تازه") }
        },
        dismissButton = { TextButton(onClick = close) { Text("بستن") } }
    )
}
