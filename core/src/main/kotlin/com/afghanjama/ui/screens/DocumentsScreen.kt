@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import com.afghanjama.ui.platform.AppAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.Document
import com.afghanjama.data.entities.docTypeLabel
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.components.SheetActions
import com.afghanjama.platform.LocalDocs
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.ui.vm.DocumentsViewModel

private fun receiptText(shop: String, d: Document): String = buildString {
    appendLine("$shop — ${docTypeLabel(d.type)}")
    appendLine("شماره: ${d.number}")
    appendLine("تاریخ: ${PersianDate.short(d.at)}")
    if (d.partyName.isNotBlank()) appendLine("طرف حساب: ${d.partyName}")
    appendLine("مبلغ: ${d.amount.afn()}")
    if (d.refId.isNotBlank()) appendLine("مرجع: ${d.refId}")
    if (d.note.isNotBlank()) appendLine("توضیح: ${d.note}")
}

@Composable
fun DocumentsScreen(
    vm: DocumentsViewModel,
    onBack: () -> Unit,
    /**
     * نشانِ QRِ متنِ رسید — هر سکو خودش می‌کشد.
     *
     * ساختِ ماتریسِ QR جاواست و پرتابل، ولی تبدیلش به تصویر نیست:
     * اندروید `Bitmap` دارد و ویندوز `BufferedImage`. به‌جای آوردنِ
     * ZXing به `:core` و `:desktop` برای یک نشانِ ۱۲۰ نقطه‌ای، همان
     * تکه به سکو سپرده شد.
     *
     * `null` یعنی این سکو QR ندارد و صفحه بی آن کارش را می‌کند —
     * روی پی‌سی کسی رسید را با گوشیِ خودش اسکن نمی‌کند.
     */
    qr: (@Composable (String) -> Unit)? = null
) {
    // به‌جای `Context`: سه مرزی که هر دو سکو دارند.
    val settings = LocalSettings.current
    val docs = LocalDocs.current
    val system = LocalSystemActions.current
    val documents by vm.documents.collectAsState()

    var typeFilter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Document?>(null) }
    val working by vm.working.collectAsState()
    val message by vm.message.collectAsState()

    message?.let { msg ->
        AppAlertDialog(
            onDismissRequest = { vm.clearMessage() },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.clearMessage() }) { Text("باشه") } }
        )
    }

    /*
     * جست‌وجو در اسناد.
     *
     * این فهرست فقط بلندتر می‌شود: هر خرید، فروش و تسویه یک سند
     * می‌سازد و هیچ‌کدام هرگز پاک نمی‌شوند. کارفرما با شمارهٔ سند یا
     * نامِ طرفِ حساب سراغش می‌آید — «فاکتورِ فلانی کجاست؟» — و پالایهٔ
     * نوع فقط دسته را کم می‌کند، نه فهرست را.
     */
    var query by remember { mutableStateOf("") }

    val types = documents.map { it.type }.distinct()
    val shown = documents
        .filter { typeFilter == null || it.type == typeFilter }
        .filter { d ->
            val q = query.trim()
            q.isBlank() ||
                d.number.contains(q, ignoreCase = true) ||
                d.partyName.contains(q, ignoreCase = true) ||
                d.refId.contains(q, ignoreCase = true) ||
                d.note.contains(q, ignoreCase = true)
        }

    // ---------- دیالوگ سند ----------
    selected?.let { d ->
        val shop = CompanyPrefs.shopName(settings)
        var paper by remember(d.id) { mutableStateOf(vm.paperFor(settings, d)) }
        AppAlertDialog(
            onDismissRequest = { if (!working) selected = null },
            confirmButton = {
                TextButton(onClick = {
                    system.shareText(
                        title = "اشتراک‌گذاری متن",
                        text = receiptText(shop, d)
                    )
                }) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text("  متن")
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }, enabled = !working) { Text("بستن") }
            },
            title = { Text("${docTypeLabel(d.type)} • ${d.number}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (qr != null) {
                        qr(receiptText(shop, d))
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(receiptText(shop, d), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    SheetActions(
                        paper = paper,
                        onPaperChange = {
                            paper = it
                            vm.rememberPaper(settings, d, it)
                        },
                        busy = working,
                        onAction = { vm.act(docs, d, paper, it) }
                    )
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اسناد") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (documents.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("جستجوی شماره، طرف حساب یا توضیح") },
                    singleLine = true,
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "پاک کردنِ جستجو")
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (types.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = typeFilter == null,
                        onClick = { typeFilter = null },
                        label = { Text("همه") }
                    )
                    types.forEach { t ->
                        FilterChip(
                            selected = typeFilter == t,
                            onClick = { typeFilter = t },
                            label = { Text(docTypeLabel(t)) }
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                Text(
                    when {
                        documents.isEmpty() ->
                            "هنوز سندی ثبت نشده. با هر خرید، فروش یا تسویه یک سند خودکار ساخته می‌شود."
                        query.isNotBlank() -> "سندی با «${query.trim()}» پیدا نشد."
                        else -> "سندی از این نوع نیست."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.id }) { d ->
                        Card(
                            onClick = { selected = d },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${docTypeLabel(d.type)} • ${d.number}",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        (if (d.partyName.isNotBlank()) "${d.partyName} • " else "") +
                                            PersianDate.short(d.at),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    d.amount.afn(),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
