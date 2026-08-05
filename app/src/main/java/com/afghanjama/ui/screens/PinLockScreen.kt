@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.BrandButton
import com.afghanjama.ui.theme.Brand
import com.afghanjama.ui.theme.CopperBrush
import com.afghanjama.ui.theme.EmeraldBrush
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.util.AppLock

/** صفحهٔ قفل: تا رمز درست وارد نشود، اپ باز نمی‌شود. */
@Composable
fun PinLockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    // زمینهٔ زمردی، مثلِ صفحهٔ ورود — این دو در ذهنِ کاربر یک درِ
    // ورودی‌اند و نباید دو ظاهر داشته باشند.
    Box(Modifier.fillMaxSize().background(EmeraldBrush)) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // قفلِ ایموجی جایش را به نشانِ مسی داد: ایموجی روی هر گوشی
        // شکلِ خودش را دارد و اندازه‌اش با متن می‌پرد.
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(CopperBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Brand.OnCopper,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "رمز ورود را وارد کنید",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Brand.OnEmerald
        )
        Spacer(Modifier.height(20.dp))

        /*
         * کادر و دکمه داخلِ یک کارتِ `surface` می‌نشینند، نه مستقیم
         * روی زمرد.
         *
         * `OutlinedTextField` رنگش را از `colorScheme` می‌گیرد: در
         * حالتِ روشن متنش تقریباً سیاه و برچسبش خاکستریِ تیره است.
         * مستقیم روی زمینهٔ زمردیِ تیره، کاربر رمزی را که تایپ
         * می‌کند نمی‌دید. کارت همان سطحِ روشنی را برمی‌گرداند که
         * فیلد برایش ساخته شده — همان کاری که صفحهٔ ورود می‌کند.
         */
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.digitsOnly().take(8); error = false },
                    label = { Text("رمز عددی") },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text(
                        "رمز اشتباه است.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                BrandButton(
                    text = "باز کردن",
                    onClick = {
                        if (AppLock.check(context, pin)) onUnlock() else { error = true; pin = "" }
                    },
                    enabled = pin.length >= 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    }
}
