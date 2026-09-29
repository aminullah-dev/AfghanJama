package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * «به این بخش دسترسی ندارید» — روی صفحه‌ای که این نفر تیکش را ندارد.
 *
 * **چرا روی صفحه و نه فقط پنهان کردنِ دکمه‌ها.** به هر صفحه از چند راه
 * می‌شود رسید: نوارِ پایین، کاشیِ خانه، هشدار، جست‌وجو، چیپِ مرحله‌ها.
 * پنهان کردنِ یکی‌شان کافی نیست؛ این پرده همهٔ راه‌ها را می‌بندد، هر
 * دکمه‌ای را هم که جا مانده باشد.
 *
 * تمامِ صفحه را می‌پوشاند و لمس را به زیرش نمی‌رساند.
 */
@Composable
fun NoAccessNotice(onHome: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Text(
                    "به این بخش دسترسی ندارید",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "مدیر از تنظیمات ← «کاربران و دسترسی‌ها» مشخص می‌کند هر نفر به کدام " +
                        "بخش‌ها راه دارد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onHome) { Text("برگشت به خانه") }
            }
        }
    }
}
