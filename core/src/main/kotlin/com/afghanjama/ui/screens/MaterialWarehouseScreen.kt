@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.MaterialStock
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.decimalOnly
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.platform.AppAlertDialog
import com.afghanjama.ui.platform.AppDropdownMenu
import com.afghanjama.ui.platform.AppDropdownMenuItem
import com.afghanjama.ui.vm.WarehouseViewModel

private fun fmtAmount(v: Double): String =
    (if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()).toPersianDigits()

/** رشتهٔ لاتین برای مقداردهی اولیهٔ فیلد ورودی (تا toDoubleOrNull کار کند). */
private fun fmtAmountLatin(v: Double): String =
    if (v <= 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/**
 * کنشی که روی یک قلمِ انبار باز است.
 *
 * **چرا شمارش و نه چند `Boolean`.** هر کنش یک دیالوگِ خودش دارد و
 * هم‌زمان فقط یکی باز است؛ با پرچم‌های جدا دیر یا زود دو تا با هم باز
 * می‌شوند. یک حالتِ واحد این را غیرممکن می‌کند.
 */
private enum class ItemOp { RECEIVE, ISSUE, COUNT, ALERT }

private data class ItemTarget(val item: MaterialStock, val op: ItemOp)

/*
 * دلیل‌های آماده — کاربر تایپ نکند، انتخاب کند.
 *
 * **چرا «مصرف در تولید» و «برگشت از تولید» اینجا نیستند.** وسوسه‌اش
 * زیاد بود؛ هر دو کارِ روزمرهٔ انبارند. ولی سندشان با این مسیر فرق
 * دارد: موادی که به سفارش می‌رود «کار در جریان تولید» را بدهکار
 * می‌کند، نه هزینه را — و آن سند را خودِ صفحهٔ **برش** می‌زند. اگر
 * همان کار از اینجا هم شدنی باشد، یک بار مواد از انبار کم می‌شود و
 * حسابِ تولید بالا می‌رود، بارِ دوم همان مقدار به هزینه می‌رود:
 * موجودی دو بار کم و سود یک بار غلط.
 *
 * پس این مسیر فقط برای چیزهایی است که واقعاً **اصلاحِ انبار**اند و
 * طرفِ دیگرشان هزینه است. خرید هم از «خرید مواد» می‌آید، چون آنجا
 * پول جابه‌جا می‌شود.
 */
private val RECEIVE_REASONS = listOf("اضافیِ انبارگردانی", "ورودِ دستی")

private val ISSUE_REASONS = listOf("ضایعات", "کسریِ انبارگردانی", "مصرفِ متفرقه", "خروجِ دستی")

/**
 * انبار مواد — دیدن، و **کار کردن** روی هر قلم.
 *
 * **وضعی که از آن آمدیم.** این صفحه فهرست بود و یک دیالوگِ «اصلاح» که
 * سه کار را در هم می‌کرد: موجودی، حد هشدار، و ضایعات. نوشتنِ عدد در
 * کادرِ ضایعات باعث می‌شد کادرِ موجودی **بی‌صدا** نادیده گرفته شود —
 * کاربر دو عدد می‌داد و یکی‌شان اثر می‌کرد. و کارهای روزمرهٔ انبار
 * (پارچه‌ای که برگشت، قلمی که تازه شمرده شد، قلمی که اصلاً در اپ
 * نبود) هیچ راهی نداشتند جز رفتن به صفحهٔ خرید و ثبتِ یک خریدِ
 * ساختگی.
 *
 * حالا هر کنش دیالوگِ خودش را دارد، یک کار می‌کند، و بعدش می‌گوید
 * **واقعاً چه شد** — نه «ثبت شد»ِ همیشگی. اگر ۵ متر خواسته شود و ۳
 * متر باشد، پیام می‌گوید ۳ متر.
 */
@Composable
fun MaterialWarehouseScreen(
    vm: WarehouseViewModel,
    canAdjust: Boolean,
    onBack: () -> Unit
) {
    val materials by vm.materials.collectAsState()
    val ui by vm.ui.collectAsState()

    // کنشِ بازِ فعلی — `null` یعنی هیچ دیالوگی باز نیست.
    var target by remember { mutableStateOf<ItemTarget?>(null) }

    // کاردکسِ یک قلم — `null` یعنی بسته است.
    var historyOf by remember { mutableStateOf<MaterialStock?>(null) }

    // حذف تأیید می‌خواهد: ردیفِ انبار با یک لمسِ اشتباه نباید برود.
    var deleting by remember { mutableStateOf<MaterialStock?>(null) }

    // افزودنِ قلمِ تازه با موجودیِ اولیه (مهاجرت از دفتر یا اپِ قبلی).
    var adding by remember { mutableStateOf(false) }

    if (adding) {
        OpeningStockDialog(
            onDismiss = { adding = false },
            onConfirm = { name, unit, amount, price, note ->
                vm.addOpening(name, unit, amount, price, note)
                adding = false
            }
        )
    }

    deleting?.let { item ->
        AppAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف «${item.name}»") },
            text = {
                Text(
                    if (item.amount > 0.0)
                        "این قلم هنوز ${fmtAmount(item.amount)} ${item.unit} موجودی دارد. " +
                            "اول با «ثبت خروج» یا «انبارگردانی» صفرش کنید تا سندِ حسابداری‌اش " +
                            "هم بخورد؛ وگرنه ارزشش در دفتر می‌ماند و انبار از حساب جدا می‌شود."
                    else "ردیفِ خالیِ «${item.name}» برداشته شود؟ کاردکسش سرِ جایش می‌ماند.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                if (item.amount <= 0.0) {
                    TextButton(onClick = { vm.deleteRow(item); deleting = null }) {
                        Text("حذف", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { deleting = null }) { Text("فهمیدم") }
                }
            },
            dismissButton = if (item.amount <= 0.0) {
                { TextButton(onClick = { deleting = null }) { Text("لغو") } }
            } else null
        )
    }

    historyOf?.let { item ->
        val moves by vm.movementsOf(item).collectAsState(initial = emptyList())
        AppAlertDialog(
            onDismissRequest = { historyOf = null },
            title = { Text("ورود و خروج — ${item.name}") },
            text = {
                if (moves.isEmpty()) {
                    Text(
                        "برای این قلم هنوز حرکتی ثبت نشده.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(moves, key = { it.id }) { m ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.reason, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        PersianDate.shortWithTime(m.createdAt) +
                                            (if (m.note.isBlank()) "" else " — ${m.note}"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // ورود و خروج باید در نگاهِ اول از هم جدا
                                // باشند؛ علامتِ تنها کافی نیست چون در
                                // فهرستِ بلند خوانده نمی‌شود.
                                Text(
                                    (if (m.delta >= 0) "+" else "") +
                                        "${fmtAmount(m.delta)} ${item.unit}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (m.delta >= 0)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { historyOf = null }) { Text("بستن") }
            },
            dismissButton = null
        )
    }

    target?.let { (item, op) ->
        when (op) {
            ItemOp.RECEIVE -> MoveDialog(
                item = item,
                title = "ثبت ورود — ${item.name}",
                hint = "چه مقدار به انبار اضافه شد؟ " +
                    "خریدِ تازه را از «خرید مواد» ثبت کنید تا پولش هم حساب شود.",
                reasons = RECEIVE_REASONS,
                onDismiss = { target = null },
                onConfirm = { amount, reason, note ->
                    vm.receive(item, amount, reason, note)
                    target = null
                }
            )

            ItemOp.ISSUE -> MoveDialog(
                item = item,
                title = "ثبت خروج — ${item.name}",
                hint = "چه مقدار از انبار خارج شد؟ " +
                    "(موجودی: ${fmtAmount(item.amount)} ${item.unit}) " +
                    "موادی که به سفارش می‌رود از صفحهٔ «برش» کم می‌شود، نه از اینجا.",
                reasons = ISSUE_REASONS,
                onDismiss = { target = null },
                onConfirm = { amount, reason, note ->
                    vm.issue(item, amount, reason, note)
                    target = null
                }
            )

            ItemOp.COUNT -> CountDialog(
                item = item,
                onDismiss = { target = null },
                onConfirm = { counted, note ->
                    vm.countTo(item, counted, note)
                    target = null
                }
            )

            ItemOp.ALERT -> MinLevelDialog(
                item = item,
                onDismiss = { target = null },
                onConfirm = { level ->
                    vm.setMinLevel(item, level)
                    target = null
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("انبار مواد") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            // افزودنِ قلم تنها راهِ ورودِ کارگاهی است که تازه از دفتر یا
            // اپِ قبلی می‌آید. پیش از این صفحه می‌گفت «از بخش خرید وارد
            // کنید» — یعنی برای ثبتِ پارچه‌ای که دو سال است در انبار
            // است، باید یک خریدِ امروزی جعل می‌شد.
            if (canAdjust) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, contentDescription = "افزودن قلم به انبار")
                }
            }
        }
    ) { pad ->
        if (materials.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (canAdjust)
                        "انبار خالی است. با دکمهٔ + اقلامِ موجود را با مقدارِ فعلی‌شان " +
                            "وارد کنید، یا از بخش «خرید مواد» خریدِ تازه ثبت کنید."
                    else "انبار خالی است.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val totalValue = materials.sumOf { it.amount * it.avgPrice }.toLong()

        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ارزش تقریبی انبار", fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(totalValue.afn(), fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            ui.message?.let { msg ->
                item {
                    // پیام تا امروز تا ابد می‌ماند چون هیچ‌کس
                    // `clearMessage` را صدا نمی‌زد؛ نتیجه‌اش این بود که
                    // نتیجهٔ کارِ دیروز بالای صفحهٔ امروز نشسته بود.
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ui.isError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                msg,
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                                color = if (ui.isError) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            IconButton(onClick = { vm.clearMessage() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "بستنِ پیام",
                                    tint = if (ui.isError) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            items(materials, key = { it.id }) { item ->
                val low = item.minLevel > 0.0 && item.amount <= item.minLevel
                Card(
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
                        Column(
                            Modifier
                                .weight(1f)
                                // لمسِ خودِ ردیف کاردکس را باز می‌کند:
                                // پرمصرف‌ترین کارِ این صفحه نباید سه لمس
                                // بخواهد.
                                .clickable { historyOf = item }
                        ) {
                            Text(item.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "میانگین قیمت: ${item.avgPrice.toLong().afn()} / ${item.unit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (low) {
                                Text(
                                    "موجودی کم",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${fmtAmount(item.amount)} ${item.unit}",
                                fontWeight = FontWeight.Bold,
                                color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        /*
                         * «⋮» — و هر سطرش کاری می‌کند.
                         *
                         * منو جای فهرستِ آرزوها نیست: هر بند دیالوگی باز
                         * می‌کند که می‌نویسد و نتیجه‌اش را می‌گوید.
                         * چیزی که هنوز کار نمی‌کند، اینجا هم نیست.
                         *
                         * دیدنِ تاریخچه اجازه نمی‌خواهد — فهمیدنِ اینکه
                         * یک قلم کِی کم شد حقِ هرکسی است که صفحه را
                         * می‌بیند. فقط **تغییر دادن** `canAdjust` می‌خواهد.
                         */
                        var menu by remember(item.name, item.unit) { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "گزینه‌ها")
                            }
                            AppDropdownMenu(
                                expanded = menu,
                                onDismissRequest = { menu = false }
                            ) {
                                if (canAdjust) {
                                    AppDropdownMenuItem(
                                        text = { Text("ثبت ورود") },
                                        onClick = {
                                            menu = false
                                            target = ItemTarget(item, ItemOp.RECEIVE)
                                        }
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text("ثبت خروج") },
                                        onClick = {
                                            menu = false
                                            target = ItemTarget(item, ItemOp.ISSUE)
                                        }
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text("انبارگردانی (تنظیم روی شمارش)") },
                                        onClick = {
                                            menu = false
                                            target = ItemTarget(item, ItemOp.COUNT)
                                        }
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text("حد هشدار کمبود") },
                                        onClick = {
                                            menu = false
                                            target = ItemTarget(item, ItemOp.ALERT)
                                        }
                                    )
                                }
                                AppDropdownMenuItem(
                                    text = { Text("لیست ورود و خروج این قلم") },
                                    onClick = { menu = false; historyOf = item }
                                )
                                if (canAdjust) {
                                    AppDropdownMenuItem(
                                        text = {
                                            Text("حذف قلم", color = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = { menu = false; deleting = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

/**
 * ورود یا خروجِ یک قلم — مقدار، دلیل، و توضیحِ اختیاری.
 *
 * **دلیل انتخاب می‌شود، تایپ نمی‌شود.** کاردکس فقط وقتی ارزش دارد که
 * شش ماه بعد بشود رویش گروه‌بندی کرد؛ با متنِ آزاد «ضایعات»،
 * «ضایعاتی»، «ضایعات پارچه» سه چیزِ متفاوت می‌شوند. توضیحِ آزاد جای
 * جزئیات است، نه جای دسته‌بندی.
 */
@Composable
private fun MoveDialog(
    item: MaterialStock,
    title: String,
    hint: String,
    reasons: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, reason: String, note: String) -> Unit
) {
    var amountText by remember(item.id) { mutableStateOf("") }
    var reason by remember(item.id) { mutableStateOf(reasons.first()) }
    var note by remember(item.id) { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull() ?: 0.0

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.decimalOnly() },
                    label = { Text("مقدار (${item.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reasons.forEach { r ->
                        FilterChip(
                            selected = reason == r,
                            onClick = { reason = r },
                            label = { Text(r) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            // دکمه وقتی مقدار نیست خاموش است — نه اینکه بزنی و هیچ نشود.
            TextButton(
                onClick = { onConfirm(amount, reason, note.trim()) },
                enabled = amount > 0.0
            ) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}

/**
 * انبارگردانی: عددِ **شمرده‌شده** گرفته می‌شود، نه اختلاف.
 *
 * کسی که در انبار ایستاده و متر می‌زند، عددی که در دست دارد «۳۸ متر»
 * است نه «۴ متر کمتر از دفتر». اختلاف را اپ حساب می‌کند و همان‌جا
 * نشان می‌دهد تا پیش از ثبت دیده شود.
 */
@Composable
private fun CountDialog(
    item: MaterialStock,
    onDismiss: () -> Unit,
    onConfirm: (counted: Double, note: String) -> Unit
) {
    var countedText by remember(item.id) { mutableStateOf(fmtAmountLatin(item.amount)) }
    var note by remember(item.id) { mutableStateOf("") }
    val counted = countedText.toDoubleOrNull()
    val delta = if (counted == null) null else counted - item.amount

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انبارگردانی — ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "در دفتر ${fmtAmount(item.amount)} ${item.unit} ثبت است. " +
                        "هرچه در انبار شمرده‌اید بنویسید؛ اختلاف با دلیلِ «انبارگردانی» " +
                        "در کاردکس و دفترِ حساب ثبت می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = countedText,
                    onValueChange = { countedText = it.decimalOnly() },
                    label = { Text("مقدارِ شمرده‌شده (${item.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (delta != null && delta != 0.0) {
                    Text(
                        if (delta > 0)
                            "${fmtAmount(delta)} ${item.unit} بیشتر از دفتر — اضافه ثبت می‌شود."
                        else "${fmtAmount(-delta)} ${item.unit} کمتر از دفتر — کسری ثبت می‌شود.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (delta > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(counted ?: 0.0, note.trim()) },
                enabled = delta != null && delta != 0.0
            ) { Text("ثبت اختلاف") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}

/** حد هشدار کمبود — عددی که زیرش قلم «کم» شمرده می‌شود. صفر یعنی خاموش. */
@Composable
private fun MinLevelDialog(
    item: MaterialStock,
    onDismiss: () -> Unit,
    onConfirm: (level: Double) -> Unit
) {
    var levelText by remember(item.id) { mutableStateOf(fmtAmountLatin(item.minLevel)) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حد هشدار — ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "وقتی موجودی به این عدد یا کمتر برسد، این قلم «موجودی کم» " +
                        "علامت می‌خورد و در برنامهٔ خرید می‌آید. خالی یا صفر یعنی هشدار نمی‌خواهم.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it.decimalOnly() },
                    label = { Text("حد هشدار (${item.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(levelText.toDoubleOrNull() ?: 0.0) }) {
                Text("ذخیره")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}

/**
 * قلمِ تازه با موجودیِ اولیه — همان «حساب قبلی»، منتها برای انبار.
 *
 * قیمت اختیاری است و این عمدی است: کارفرمایی که نرخِ خریدِ پارچهٔ سه
 * سال پیش را نمی‌داند، نباید مجبور شود عددی از خودش بسازد تا فرم رد
 * شود. عددِ ساختگی بدتر از خالی است — خالی معلوم است که نیست، ولی
 * عددِ ساختگی تا ابد در ارزشِ انبار می‌مانَد.
 */
@Composable
private fun OpeningStockDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, unit: String, amount: Double, unitPrice: Long, note: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val ready = name.isNotBlank() && unit.isNotBlank() && amount > 0.0

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن قلم به انبار") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "برای موجودیِ فعلی — پارچه یا لوازمی که همین حالا در انبار است " +
                        "ولی هنوز در اپ ثبت نشده. پولی جابه‌جا نمی‌شود؛ این خرید نیست، " +
                        "شمارشِ شروع است. خریدِ تازه از بخش «خرید مواد» ثبت می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام قلم (مثلاً کتانِ سرمه‌ای)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("واحد") },
                        placeholder = { Text("متر / عدد / کیلو") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.decimalOnly() },
                        label = { Text("مقدار") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.digitsOnly() },
                    label = { Text("قیمت هر واحد (اختیاری، افغانی)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name.trim(),
                        unit.trim(),
                        amount,
                        priceText.toLongOrNull() ?: 0L,
                        note.trim()
                    )
                },
                enabled = ready
            ) { Text("افزودن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}
