@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afghanjama.AppInfo
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.components.IconBadge
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.UserRole

/**
 * صفحهٔ ورود — اولین چیزی که هر روز صبح دیده می‌شود.
 *
 * تا دیروز فقط یک «ورود» و یک کارتِ لخت بود؛ هیچ نشانی نمی‌داد که کاربر
 * وارد کدام برنامه شده. حالا نشانِ اپ و نامِ کارگاه بالای صفحه‌اند، پس
 * روی گوشیِ کارفرما این صفحه هویت دارد.
 *
 * انتخابِ نقش هم یک کنترل شد نه دو: تا حالا یک کادرِ فقط‌خواندنی بود
 * به‌اضافهٔ دکمهٔ جداگانهٔ «انتخاب نقش» زیرش، که برای یک تصمیم دو جای
 * کلیک می‌ساخت. حالا فلشِ داخلِ خودِ کادر منو را باز می‌کند.
 */
@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onLoggedIn: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    var pin by remember { mutableStateOf("") }
    var roleMenu by remember { mutableStateOf(false) }

    val settings = LocalSettings.current
    val coName = remember { CompanyPrefs.name(settings) }

    fun roleLabel(r: UserRole): String = when (r) {
        UserRole.MANAGER -> "مدیریت"
        UserRole.PURCHASE -> "مسئول خرید"
        UserRole.SEWING -> "مدیریت دوخت"
        UserRole.REVIEW -> "نظارت"
        UserRole.SALES -> "مدیریت فروش"
    }

    LaunchedEffect(ui.isLoggedIn) {
        if (ui.isLoggedIn) onLoggedIn()
    }

    if (ui.isLoggedIn) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---------- نشانِ اپ ----------
        IconBadge(Icons.Default.Checkroom, size = 68.dp)

        Spacer(Modifier.height(14.dp))

        Text(
            AppInfo.NAME,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            coName.ifBlank { "سامانهٔ مدیریتِ کارگاه خیاطی" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (ui.isSetupDone) "ورود" else "تنظیم رمز",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (ui.isSetupDone)
                            "نقش و نامتان را انتخاب کنید و رمز را بزنید."
                        else
                            "برای اولین بار یک رمزِ دستِ‌کم ۴ رقمی تنظیم کنید. " +
                                "این رمز بعداً از تنظیمات قابلِ تغییر است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // انتخابِ نقش — یک کنترل، نه دو
                Box {
                    OutlinedTextField(
                        value = roleLabel(ui.role),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("شما چه کسی هستید؟") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { roleMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "انتخاب نقش")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = roleMenu,
                        onDismissRequest = { roleMenu = false }
                    ) {
                        UserRole.entries.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(roleLabel(r)) },
                                onClick = {
                                    vm.setRole(r)
                                    roleMenu = false
                                }
                            )
                        }
                    }
                }

                // نامِ کاربر — برای دفترِ رویدادها: چه کسی چه کاری کرد
                OutlinedTextField(
                    value = ui.userName,
                    onValueChange = vm::setUserName,
                    label = { Text("نام شما") },
                    supportingText = { Text("روی رویدادهایی که ثبت می‌کنید می‌نشیند") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.digitsOnly().take(8) },
                    label = { Text("رمز") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )

                Button(
                    onClick = {
                        vm.clearMessage()
                        if (ui.isSetupDone) vm.login(pin) else vm.setupPin(pin)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    // تا دیروز اینجا Spacer با ارتفاعِ صفر بود، یعنی هیچ
                    // فاصله‌ای؛ در یک ردیف عرض لازم است نه ارتفاع.
                    Spacer(Modifier.width(8.dp))
                    Text(if (ui.isSetupDone) "ورود" else "ثبت رمز")
                }

                ui.message?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (ui.isError)
                                MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val onColor = if (ui.isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (ui.isError) Icons.Default.ErrorOutline else Icons.Default.Lock,
                                contentDescription = null,
                                tint = onColor
                            )
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = onColor
                            )
                        }
                    }
                }
            }
        }
    }
}
