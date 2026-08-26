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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import com.afghanjama.ui.platform.AppAlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.afghanjama.platform.LocalDocs
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.data.dao.PartyBalance
import com.afghanjama.data.entities.ledgerRefLabel
import com.afghanjama.data.entities.partyTypeLabel
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.LedgerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun typeLabel(t: String): String = partyTypeLabel(t)

private fun refLabel(r: String): String = ledgerRefLabel(r)

/** متنِ ماندهٔ حساب: مثبت = طرف بدهکار است، منفی = ما بدهکاریم. */
private fun balanceText(net: Long): String = when {
    net > 0 -> "بدهکار ${net.afn()}"
    net < 0 -> "بستانکار ${(-net).afn()}"
    else -> "تسویه"
}

/**
 * از چند روز بی‌حرکتی، حساب «کهنه» شمرده می‌شود.
 *
 * ۳۰ — یک دورهٔ کاریِ کارگاه. کمتر از آن هنوز جریانِ عادیِ کار است:
 * سفارشی ثبت شده، بیعانه‌اش آمده، و تا تحویل چند هفته فاصله هست.
 * بالای آن یعنی چیزی متوقف شده.
 */
private const val STALE_DAYS = 30

/** «۴۵ روز بی‌حرکت» — و برای امروز و دیروز، جملهٔ آدمیزاد. */
private fun idleLabel(days: Int): String = when {
    days <= 0 -> "امروز"
    days == 1 -> "دیروز"
    else -> "${days.fa()} روز بی‌حرکت"
}

@Composable
private fun balanceColor(net: Long): Color = when {
    net > 0 -> MaterialTheme.colorScheme.primary
    net < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun LedgerScreen(
    vm: LedgerViewModel,
    onBack: () -> Unit
) {
    val docs = LocalDocs.current
    val system = LocalSystemActions.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val balances by vm.balances.collectAsState()
    val entries by vm.entries.collectAsState()
    val phones by vm.phones.collectAsState()
    val message by vm.message.collectAsState()
    val orphanWorkCost by vm.orphanWorkCost.collectAsState()

    var typeFilter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<PartyBalance?>(null) }

    /*
     * جست‌وجوی نام، و «فقط تسویه‌نشده‌ها».
     *
     * دفتر کل پرمراجعه‌ترین صفحهٔ کارگاه است و فهرستش هرگز کوتاه
     * نمی‌شود: هر مشتری، هر خیاط و هر فروشنده‌ای که یک بار حساب داشته
     * تا ابد در آن می‌مانَد — حتی وقتی حسابش صفر شده. کارفرمایی که
     * دنبالِ یک نام است، بینِ صد ردیفِ تسویه‌شده می‌گردد.
     *
     * پالایهٔ نوع از قبل بود ولی جوابِ این نیست: «همهٔ مشتریان» هنوز
     * صد ردیف است.
     */
    var query by remember { mutableStateOf("") }
    var onlyOpen by remember { mutableStateOf(false) }

    /*
     * «چند روز است بی‌حرکت مانده؟»
     *
     * دفتر کل تا امروز فقط می‌گفت **چقدر**، و هرگز نمی‌گفت **از کِی**.
     * ولی سؤالی که کارفرما را نصفِ شب بیدار می‌کند دومی است: طلبِ
     * ۵۰۰۰ افغانی که هفتهٔ پیش ثبت شده کارِ عادیِ کارگاه است؛ همان
     * ۵۰۰۰ که شش ماه تکان نخورده یعنی پول رفته.
     *
     * تاریخِ این حرکت‌ها از روزِ اول در دفتر بود و هیچ‌جا دیده نمی‌شد.
     *
     * **معیار «آخرین حرکت» است، نه سنِ فاکتور.** سنِ دقیقِ بدهی یعنی
     * تخصیصِ FIFOِ پرداخت‌ها روی فاکتورها — حسابی که این دفتر نگه
     * نمی‌دارد و حدس زدنش بدتر از نگفتن است. «آخرین حرکت» چیزی است
     * که واقعاً می‌دانیم، و برای همان تصمیم هم کافی است: حسابی که
     * مانده دارد و ماه‌هاست هیچ اتفاقی در آن نیفتاده، همانی است که
     * باید سراغش رفت.
     */
    var staleFirst by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    // یک بار ساخته می‌شود، نه به‌ازای هر ردیف: با دویست طرفِ حساب و
    // چند هزار سند، جست‌وجوی خطی در هر ردیف صفحه را کند می‌کرد.
    val lastActivity = remember(entries) {
        entries.groupBy { it.partyType + "|" + it.partyName }
            .mapValues { (_, rows) -> rows.maxOf { it.at } }
    }

    fun idleDays(p: PartyBalance): Int? {
        val at = lastActivity[p.type + "|" + p.name] ?: return null
        return ((now - at) / 86_400_000L).toInt()
    }

    // ---------- حالتِ سند دستی ----------
    var manualOpen by remember { mutableStateOf(false) }
    var mType by remember { mutableStateOf("SUPPLIER") }
    var mName by remember { mutableStateOf("") }
    var mAmount by remember { mutableStateOf("") }
    var mIsPayment by remember { mutableStateOf(true) }
    var mNote by remember { mutableStateOf("") }

    val receivable = balances.filter { it.net > 0 }.sumOf { it.net }
    val payable = balances.filter { it.net < 0 }.sumOf { -it.net }

    val types = balances.map { it.type }.distinct()
    val openCount = balances.count { it.net != 0L }
    val shown = balances
        .filter { typeFilter == null || it.type == typeFilter }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .filter { !onlyOpen || it.net != 0L }
        .let { list ->
            if (staleFirst) list.sortedByDescending { idleDays(it) ?: -1 }
            else list.sortedByDescending { if (it.net < 0) -it.net else it.net }
        }

    // ---------- دیالوگ گردش حساب ----------
    selected?.let { p ->
        val rows = entries.filter { it.partyType == p.type && it.partyName == p.name }
        AppAlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    /*
                     * تماس — فقط وقتی شماره‌ای هست.
                     *
                     * سنِ طلب می‌گوید سراغِ **کی** باید رفت؛ این دکمه
                     * همان‌جا کارش را تمام می‌کند. شماره از روزِ اول در
                     * دفترچه بود و هیچ‌جا جز صفِ تحویل استفاده نمی‌شد،
                     * پس کارفرما برای یک زنگ باید از اپ بیرون می‌رفت و
                     * در مخاطبانِ گوشی می‌گشت.
                     *
                     * روی پی‌سی شماره در کلیپ‌بورد می‌نشیند — نزدیک‌ترین
                     * کارِ مفیدی که یک کامپیوتر می‌تواند بکند.
                     */
                    phones[p.type + "|" + p.name]?.let { phone ->
                        TextButton(onClick = { system.dial(phone) }) {
                            Icon(Icons.Default.Call, contentDescription = null)
                            Text("  تماس")
                        }
                    }
                    TextButton(onClick = {
                        // صورت‌حساب PDF برای اشتراک با خودِ طرف
                        scope.launch {
                            docs.partyStatement(
                                p.type, p.name, p.net, rows, "اشتراک صورت‌حساب"
                            )
                        }
                    }) { Text("PDF") }
                    TextButton(onClick = {
                        mType = p.type; mName = p.name; mAmount = ""; mNote = ""
                        mIsPayment = p.net < 0   // اگر ما به او بدهکاریم، پیش‌فرض «پرداخت»
                        selected = null
                        manualOpen = true
                    }) { Text("ثبت پرداخت/دریافت") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("بستن") } },
            title = { Text("${p.name} • ${typeLabel(p.type)}") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = balanceText(p.net),
                        color = balanceColor(p.net),
                        fontWeight = FontWeight.SemiBold
                    )
                    idleDays(p)?.let { idle ->
                        if (p.net != 0L) {
                            Text(
                                "آخرین حرکتِ این حساب: ${idleLabel(idle)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (idle >= STALE_DAYS) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // ---------- بدهیِ خرج‌کارِ بی‌صاحبِ گذشته ----------
                    // فقط تا وقتی دیده می‌شود که چیزی برای پاک کردن باشد؛
                    // بعد از تسویه خودش ناپدید می‌شود.
                    if (orphanWorkCost > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "بدهیِ خرج‌کارِ بی‌صاحب: ${orphanWorkCost.afn()}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "این خرج‌کارها پیش از اصلاحِ اپ ثبت شده‌اند: بدهی نوشته شد " +
                                        "ولی هرگز از صندوق کم نشد و طرفِ حسابی هم نداشت. " +
                                        "پس صندوقِ اپ از صندوقِ واقعیِ شما بیشتر نشان می‌دهد.\n\n" +
                                        "اگر این پول را در واقعیت پرداخت کرده‌اید، از اینجا ثبتش " +
                                        "کنید تا حساب با واقعیت بخوانَد. اگر هنوز بدهکارید، " +
                                        "دست نزنید و از «ثبت دستی» به نامِ همان شخص بنویسید.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        "WALLET" to "از کیف پول",
                                        "BANK" to "از بانک",
                                        "PROFIT" to "از فایده"
                                    ).forEach { (code, label) ->
                                        TextButton(onClick = { vm.settleOrphanWorkCost(code) }) {
                                            Text(label)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (rows.isEmpty()) {
                        Text("سندی ثبت نشده.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rows, key = { it.id }) { e ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(refLabel(e.refType), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            PersianDate.short(e.at) +
                                                (if (e.refId.isNotBlank()) " • ${e.refId}" else ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = if (e.debit > 0) "بدهکار ${e.debit.afn()}"
                                        else "بستانکار ${e.credit.afn()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (e.debit > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // ---------- دیالوگ سند دستی ----------
    if (manualOpen) {
        AppAlertDialog(
            onDismissRequest = { manualOpen = false },
            confirmButton = {
                Button(
                    enabled = mName.isNotBlank() && (mAmount.toLongOrNull() ?: 0L) > 0L,
                    onClick = {
                        vm.recordManual(mType, mName.trim(), mAmount.toLongOrNull() ?: 0L, mIsPayment, mNote.trim())
                        manualOpen = false
                        mName = ""; mAmount = ""; mNote = ""
                    }
                ) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { manualOpen = false }) { Text("انصراف") } },
            title = { Text("ثبت سند دستی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نوع طرف", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SUPPLIER", "CUSTOMER", "TAILOR", "INSPECTOR", "EMPLOYEE").forEach { t ->
                            FilterChip(
                                selected = mType == t,
                                onClick = { mType = t },
                                label = { Text(typeLabel(t)) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = mName,
                        onValueChange = { mName = it },
                        label = { Text("نام طرف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("جهت", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mIsPayment,
                            onClick = { mIsPayment = true },
                            label = { Text("پرداخت به طرف") }
                        )
                        FilterChip(
                            selected = !mIsPayment,
                            onClick = { mIsPayment = false },
                            label = { Text("دریافت از طرف") }
                        )
                    }
                    OutlinedTextField(
                        value = mAmount,
                        onValueChange = { mAmount = it.digitsOnly() },
                        label = { Text("مبلغ (؋)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mNote,
                        onValueChange = { mNote = it },
                        label = { Text("توضیح (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دفتر کل") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        mName = ""; mAmount = ""; mNote = ""; manualOpen = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "سند دستی")
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(Modifier.weight(1f), "طلب ما (بدهکاران)", receivable, MaterialTheme.colorScheme.primary)
                SummaryCard(Modifier.weight(1f), "بدهی ما (بستانکاران)", payable, MaterialTheme.colorScheme.error)
            }

            message?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::clearMessage) { Text("باشه") }
                    }
                }
            }

            if (balances.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("جستجوی نام") },
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
                            label = { Text(typeLabel(t)) }
                        )
                    }
                    // حسابِ تسویه‌شده تا ابد در فهرست می‌مانَد و کارِ
                    // امروز را شلوغ می‌کند. چیپ فقط وقتی هست که واقعاً
                    // حسابِ بازی هست.
                    if (openCount > 0 && openCount < balances.size) {
                        FilterChip(
                            selected = onlyOpen,
                            onClick = { onlyOpen = !onlyOpen },
                            label = { Text("فقط تسویه‌نشده (${openCount.fa()})") }
                        )
                    }
                    // مرتب‌سازی بر پایهٔ کهنگی — برای وقتی که سؤال
                    // «کی بیشترین بدهی را دارد» نیست، «کی مدت‌هاست
                    // خبری ازش نیست» است.
                    FilterChip(
                        selected = staleFirst,
                        onClick = { staleFirst = !staleFirst },
                        label = { Text("کهنه‌ترین اول") }
                    )
                }
            }

            if (shown.isEmpty()) {
                Text(
                    when {
                        balances.isEmpty() -> "هنوز حسابی در دفتر کل ثبت نشده."
                        query.isNotBlank() -> "حسابی با «${query.trim()}» پیدا نشد."
                        onlyOpen -> "همهٔ حساب‌های این دسته تسویه‌اند."
                        else -> "حسابی در این دسته نیست."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.type + "|" + it.name }) { p ->
                        Card(
                            onClick = { selected = p },
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
                                    Text(p.name, fontWeight = FontWeight.SemiBold)
                                    val idle = idleDays(p)
                                    Text(
                                        typeLabel(p.type) +
                                            if (idle != null && p.net != 0L) {
                                                " • " + idleLabel(idle)
                                            } else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        // فقط وقتی قرمز که واقعاً کهنه باشد؛
                                        // رنگی که همیشه هست، هشدار نیست.
                                        color = if (idle != null && p.net != 0L && idle >= STALE_DAYS)
                                            MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    balanceText(p.net),
                                    color = balanceColor(p.net),
                                    fontWeight = FontWeight.SemiBold,
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

@Composable
private fun SummaryCard(
    modifier: Modifier,
    title: String,
    amount: Long,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount.afn(), fontWeight = FontWeight.Bold, color = color)
        }
    }
}
