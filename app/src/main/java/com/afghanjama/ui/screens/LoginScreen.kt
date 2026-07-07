@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.UserRole

@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onLoggedIn: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    var pin by remember { mutableStateOf("") }

    var roleMenu by remember { mutableStateOf(false) }

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
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (ui.isSetupDone) "ورود" else "تنظیم رمز",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (ui.isSetupDone)
                "برای ورود، رمز را وارد کنید."
            else
                "برای اولین بار یک رمز ۴ رقمی (یا بیشتر) تنظیم کنید.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // ✅ انتخاب نقش
                OutlinedTextField(
                    value = roleLabel(ui.role),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("شما چه کسی هستید؟") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedButton(
                    onClick = { roleMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("انتخاب نقش") }

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

                // ✅ رمز
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
                    Icon(Icons.Default.Lock, null)
                    Spacer(Modifier.height(0.dp))
                    Text(if (ui.isSetupDone) "ورود" else "ثبت رمز")
                }

                ui.message?.let { msg ->
                    AssistChip(
                        onClick = { vm.clearMessage() },
                        label = { Text(msg) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) }
                    )
                }
            }
        }
    }
}
