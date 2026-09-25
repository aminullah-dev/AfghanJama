@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.afghanjama.ui.components.AddButton
import com.afghanjama.ui.components.NameSuggestions
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.vm.AppUser
import com.afghanjama.ui.vm.Feature
import com.afghanjama.ui.vm.UsersViewModel

/**
 * کاربران و دسترسی‌ها — فقط مدیر.
 *
 * مدیر برای هر نفر نام و رمز می‌گذارد و تیک می‌زند به کدام بخش‌ها راه
 * دارد: برش، دوخت، نظارت، فروش، انبار، مالی و… . آن نفر در صفحهٔ ورود
 * فقط رمزِ خودش را می‌زند؛ رمز می‌گوید کیست و چه می‌بیند.
 *
 * رمز و تیک‌ها روی همین دستگاه‌اند — گوشی یا پی‌سی‌ای که چند نفر با آن
 * کار می‌کنند.
 */
@Composable
fun UsersScreen(vm: UsersViewModel, onBack: () -> Unit) {
    val users by vm.users.collectAsState()
    val people by vm.people.collectAsState()
    val message by vm.message.collectAsState()
    var editing by remember { mutableStateOf<AppUser?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AppUser?>(null) }

    message?.let { msg ->
        AppAlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text("ثبت نشد") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("باشه") } }
        )
    }

    if (adding || editing != null) {
        UserDialog(
            user = editing,
            people = people,
            onDismiss = { adding = false; editing = null },
            onSave = { name, pin, features ->
                if (vm.save(editing?.id, name, pin, features)) {
                    adding = false
                    editing = null
                }
            }
        )
    }

    deleting?.let { u ->
        AppAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("«${u.name}» حذف شود؟") },
            text = { Text("دیگر با رمزش وارد نمی‌شود. کارهایی که ثبت کرده سرِ جایشان می‌مانند.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(u.id); deleting = null }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("لغو") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کاربران و دسترسی‌ها") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "برای هر نفر رمزِ جدا بگذارید و تیک بزنید به کدام بخش‌ها راه دارد. " +
                        "در صفحهٔ ورود فقط رمزش را می‌زند؛ رمز می‌گوید کیست. رمزِ شما " +
                        "(مدیر) همه‌چیز را باز می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                AddButton(
                    text = "کاربرِ تازه",
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (users.isEmpty()) {
                item {
                    Text(
                        "هنوز کاربری نساخته‌اید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(users, key = { it.id }) { u ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(u.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                Feature.entries.filter { it in u.features }.joinToString("، ") { it.label },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editing = u }) {
                            Icon(Icons.Default.Edit, contentDescription = "ویرایش")
                        }
                        IconButton(onClick = { deleting = u }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف")
                        }
                    }
                }
            }
        }
    }
}

/** نام، رمز و تیک‌ها — برای نفرِ تازه یا ویرایشِ موجود. */
@Composable
private fun UserDialog(
    user: AppUser?,
    people: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, Set<Feature>) -> Unit
) {
    var name by remember(user?.id) { mutableStateOf(user?.name.orEmpty()) }
    var pin by remember(user?.id) { mutableStateOf("") }
    var picked by remember(user?.id) { mutableStateOf(user?.features ?: emptySet()) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (user == null) "کاربرِ تازه" else "ویرایشِ «${user.name}»") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("نام") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // نامِ همان خیاط یا کارمند — تا «کارِ من» و رویدادها او را بشناسند.
                NameSuggestions(query = name, names = people, onPick = { name = it }, existsNote = null)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.digitsOnly().take(8) },
                    label = {
                        Text(if (user == null) "رمز (دست‌کم ۴ رقم)" else "رمزِ تازه — خالی یعنی همان قبلی")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("پیش‌تنظیم", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Feature.PRESETS.forEach { (label, set) ->
                        AssistChip(onClick = { picked = set }, label = { Text(label) })
                    }
                }

                Feature.GROUPS.forEach { group ->
                    Text(
                        group,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Feature.entries.filter { it.group == group }.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = f in picked,
                                onCheckedChange = { on -> picked = if (on) picked + f else picked - f }
                            )
                            Text(f.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, pin, picked) }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}
