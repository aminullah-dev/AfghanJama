package com.afghanjama.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.AppVersion
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.vm.AuthUi
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.label
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * حسابِ کسی که پشتِ کامپیوتر نشسته — پایینِ نوارِ کنار.
 *
 * **چرا لازم شد.** `logout()` و `changePin()` از ۱.۸ در `AuthViewModel`
 * بودند و روی کامپیوتر فقط آزمونِ `authSmoke` صدایشان می‌زد. یعنی کسی که
 * وارد می‌شد راهی برای بیرون رفتن یا عوض کردنِ رمزش نداشت جز بستنِ
 * برنامه — و کامپیوترِ دفتر را چند نفر با رمزِ خودشان باز می‌کنند.
 */

/**
 * نامی که پایینِ نوار نشان داده می‌شود. صاحبِ دستگاه گاهی نامی ثبت
 * نکرده؛ آن‌وقت «صاحبِ دستگاه»، نه دوباره نامِ نقش که کنارش در قرص هست.
 */
internal fun accountName(ui: AuthUi): String =
    ui.userName.trim().ifBlank { if (ui.userId == null) "صاحبِ دستگاه" else ui.role.label }

/** برچسبِ نقش: مدیر، نقشِ دستگاه، یا «کاربر» برای کسی که مدیر ساخته. */
internal fun accountRole(ui: AuthUi): String = when {
    ui.access.isManager -> "مدیر"
    ui.userId != null -> "کاربر"
    else -> ui.role.label
}

/**
 * پایینِ نوار: نام و قرصِ نقش، «تغییرِ رمز»، «خروج از حساب»، و نسخه.
 *
 * [auth] `null` یعنی پنجره بی‌ورود رسم شده (آزمونِ صفحه‌ها) — فقط نسخه.
 */
@Composable
internal fun AccountFooter(auth: AuthViewModel?, onAbout: () -> Unit) {
    var changing by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (auth != null) {
            val ui by auth.ui.collectAsState()
            val name = accountName(ui)
            Row(
                Modifier.fillMaxWidth()
                    .clip(PillShape)
                    .background(Bg.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(BrandSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.take(1),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Brand
                    )
                }
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    accountRole(ui),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(BrandSoft)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { changing = true }) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        "تغییرِ رمز",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                TextButton(onClick = { auth.logout() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = Bad,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "خروج از حساب",
                        style = MaterialTheme.typography.labelMedium,
                        color = Bad,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            if (changing) ChangePinDialog(auth, onDismiss = { changing = false })
        }

        // نسخه و کامیتِ همین ساخت، همیشه دیده — کلیک به «دربارهٔ خیاط‌یار».
        // ساختِ آزمایشی به رنگِ خطا، تا با نسخهٔ رسمی اشتباه نشود.
        Text(
            AppVersion.short(),
            style = MaterialTheme.typography.labelSmall,
            color = if (AppVersion.isRelease) Muted else MaterialTheme.colorScheme.error,
            modifier = Modifier
                .clip(PillShape)
                .clickable(onClick = onAbout)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * عوض کردنِ رمزِ همین نفر — همان `changePin`ِ گوشی: رمزِ فعلی سنجیده
 * می‌شود، رمزِ تازه دستِ‌کم چهار رقم است و مالِ کسِ دیگری نیست.
 */
@Composable
internal fun ChangePinDialog(auth: AuthViewModel, onDismiss: () -> Unit) {
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    label = { Text("رمزِ تازه (دستِ‌کم ۴ رقم)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                ui.message?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ui.isError) Bad else Brand
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

/**
 * آخرین باری که کسی موس یا صفحه‌کلید را زد — برای قفلِ خودکار.
 *
 * [minutes] همان عددِ «تنظیمات» است؛ صفر یعنی خاموش.
 */
internal object DeskIdle {
    @Volatile
    var last: Long = System.currentTimeMillis()
        private set

    fun touch() {
        last = System.currentTimeMillis()
    }

    val minutes = MutableStateFlow(0)
}
