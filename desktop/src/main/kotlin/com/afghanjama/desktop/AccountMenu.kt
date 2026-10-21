package com.afghanjama.desktop

import com.afghanjama.ui.components.Pill
import com.afghanjama.ui.components.PillTone
import com.afghanjama.ui.components.Space
import com.afghanjama.ui.components.ChangePinDialog
import com.afghanjama.ui.components.accountName
import com.afghanjama.ui.components.accountRole
import com.afghanjama.ui.theme.PillShape
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
 * پایینِ نوار: نام و قرصِ نقش، «تغییرِ رمز»، «خروج از حساب»، و نسخه.
 *
 * [auth] `null` یعنی پنجره بی‌ورود رسم شده (آزمونِ صفحه‌ها) — فقط نسخه.
 */
@Composable
internal fun AccountFooter(auth: AuthViewModel?, onAbout: () -> Unit) {
    var changing by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(top = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        if (auth != null) {
            val ui by auth.ui.collectAsState()
            val name = accountName(ui)
            Row(
                Modifier.fillMaxWidth()
                    .clip(PillShape)
                    .background(Bg.copy(alpha = 0.7f))
                    .padding(horizontal = Space.sm, vertical = Space.sm),
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
                // همان قرصِ نقشِ کاشیِ ورود و کارتِ «حسابِ کاربری».
                Pill(accountRole(ui), tone = if (ui.access.isManager) PillTone.Coral else PillTone.Brand)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { changing = true }) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        "تغییرِ رمز",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = Space.sm)
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
                        modifier = Modifier.padding(start = Space.sm)
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
