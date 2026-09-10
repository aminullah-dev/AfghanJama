// app/src/main/java/com/afghanjama/ui/screens/ReviewScreen.kt
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import com.afghanjama.util.nowMillis
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import com.afghanjama.ui.platform.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.afghanjama.ui.platform.AppDropdownMenu
import com.afghanjama.ui.platform.AppDropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.afghanjama.ui.components.WorkStage
import com.afghanjama.ui.components.StageTabs
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.ui.components.OrderCodeLine
import com.afghanjama.data.entities.Order
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.STAGE_WARN_DAYS
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.stageDays
import com.afghanjama.ui.vm.ReviewViewModel
import com.afghanjama.util.UUID

@Composable
fun ReviewScreen(
    vm: ReviewViewModel,
    onBack: () -> Unit,
    onGoSewing: () -> Unit,
    /**
     * جابه‌جایی به مرحلهٔ دیگر — فقط روی گوشی.
     *
     * `null` یعنی تب کشیده نشود. پنجرهٔ ویندوز فهرستِ کنار
     * دارد و هر سه مرحله آنجا جداگانه‌اند.
     */
    onGoStage: ((WorkStage) -> Unit)? = null
) {
    val system = LocalSystemActions.current
    val settings = LocalSettings.current
    val orders by vm.ordersInReview.collectAsState(initial = emptyList())
    val designCodeByOrder by vm.designCodeByOrder.collectAsState()
    val inspectors by vm.inspectors.collectAsState(initial = emptyList())

    val pickMap = remember { mutableStateMapOf<UUID, String>() }
    val menuMap = remember { mutableStateMapOf<UUID, Boolean>() }

    // هدفِ برگشت برای اصلاح: (شناسه سفارش، ناظر)
    var rejectTarget by remember { mutableStateOf<Pair<UUID, String>?>(null) }
    val orderTailors by vm.tailorsOfOrder.collectAsState(initial = emptyList())

    rejectTarget?.let { (oid, insp) ->
        var problem by remember(oid) { mutableStateOf("") }
        // خالی = «معلوم نیست»؛ همان رفتارِ قبلی، فقط حالا یک انتخابِ صریح است.
        var blamedTailor by remember(oid) { mutableStateOf("") }

        AppAlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("برگشت برای اصلاح") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مشکل را ثبت کنید تا در تاریخچهٔ کیفیت بماند و خیاط بداند چه اصلاح شود.")
                    OutlinedTextField(
                        value = problem,
                        onValueChange = { problem = it },
                        label = { Text("شرح مشکل") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // فقط وقتی سفارش بینِ چند خیاط تقسیم شده معنا دارد؛
                    // سفارشِ تک‌خیاطه خودش معلوم است و پرسیدن اضافی است.
                    if (orderTailors.size > 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "این سفارش بینِ ${orderTailors.size.fa()} خیاط تقسیم شده. کارِ کدام‌شان برگشت خورد؟",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "اگر مشخص کنید، این برگشت در کارنامهٔ همان خیاط ثبت می‌شود؛ " +
                                "وگرنه سفارش از محاسبهٔ نرخِ برگشت کنار گذاشته می‌شود تا " +
                                "به گردنِ بی‌گناه نیفتد.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        orderTailors.forEach { name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { blamedTailor = if (blamedTailor == name) "" else name },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = blamedTailor == name,
                                    onClick = { blamedTailor = if (blamedTailor == name) "" else name }
                                )
                                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { blamedTailor = "" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = blamedTailor.isBlank(),
                                onClick = { blamedTailor = "" }
                            )
                            Text("معلوم نیست")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = problem.isNotBlank(),
                    onClick = {
                        vm.backToSewing(oid, insp, problem, blamedTailor)
                        rejectTarget = null
                        onGoSewing()
                    }
                ) { Text("برگشت به دوخت") }
            },
            dismissButton = { TextButton(onClick = { rejectTarget = null }) { Text("لغو") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نظارت / بازرسی") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                actions = {
                    // رسیدِ ناظر: فهرستِ کارهای منتظرِ بررسی + یادآوری
                    IconButton(
                        onClick = {
                            system.shareText(
                                "اشتراک رسید ناظر",
                                inspectorBrief(CompanyPrefs.shopName(settings), orders)
                            )
                        },
                        enabled = orders.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "رسید ناظر")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StageTabs(WorkStage.CHECK, onGoStage)
            if (orders.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("سفارشی برای بازرسی وجود ندارد.", fontWeight = FontWeight.SemiBold)
                        Text(
                            "وقتی دوخت به مرحله بازرسی برسد، اینجا می‌آید.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(orders, key = { it.id }) { o ->
                    val picked = pickMap[o.id].orEmpty()
                    val menuOpen = menuMap[o.id] == true

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = o.designTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    OrderCodeLine(
                                        designCode = designCodeByOrder[o.orderCode].orEmpty(),
                                        orderCode = o.orderCode,
                                        trailing = "کلِ سفارش: ${o.qty.fa()}"
                                    )
                                }
                                // عددی که تأیید واردِ انبار می‌کند همین است،
                                // نه کلِ سفارش. ناظر باید پیش از زدنِ دکمه
                                // ببیند چند عدد جلویش است.
                                Text(
                                    text = "این ارسال: ${o.reviewQty.fa()} عدد",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (o.storedQty > 0) {
                                // «باقی هنوز در دوخت است» همیشه درست نبود:
                                // وقتی این دسته آخرین است، چیزی در دوخت
                                // نمانده و آن جمله ناظر را گمراه می‌کرد.
                                val stillSewing = o.qty - o.storedQty - o.reviewQty
                                Text(
                                    text = "از این سفارش ${o.storedQty.fa()} عدد از قبل وارد انبار شده" +
                                        if (stillSewing > 0)
                                            "؛ ${stillSewing.fa()} عدد هنوز در دوخت است."
                                        else "؛ این آخرین دسته است و سفارش کامل می‌شود.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val days = stageDays(o.stageChangedAt, o.createdAt)
                            Text(
                                text = "⏱ ${days.fa()} روز در این مرحله" +
                                    if (days >= STAGE_WARN_DAYS) " — معطل مانده!" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (days >= STAGE_WARN_DAYS) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedButton(
                                onClick = { menuMap[o.id] = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (picked.isBlank()) "انتخاب ناظر" else picked,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            AppDropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuMap[o.id] = false }
                            ) {
                                if (inspectors.isEmpty()) {
                                    AppDropdownMenuItem(
                                        text = { Text("هیچ ناظری ثبت نشده") },
                                        onClick = { menuMap[o.id] = false }
                                    )
                                } else {
                                    inspectors.forEach { ins ->
                                        val label = "[${ins.code}] ${ins.name}"
                                        AppDropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                pickMap[o.id] = label
                                                menuMap[o.id] = false
                                            }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        vm.loadTailorsOfOrder(o.id)
                                        rejectTarget = o.id to picked
                                    },
                                    enabled = picked.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("برگشت به دوخت")
                                }

                                Button(
                                    onClick = { vm.approve(o.id, picked) },
                                    enabled = picked.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("تایید → انبار محصول")
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

/**
 * رسیدِ ناظر: فهرستِ کارهایی که همین حالا منتظرِ بررسی‌اند + معطلی هر
 * کدام + یادآوریِ ثبتِ سریعِ نتیجه.
 */
private fun inspectorBrief(shop: String, orders: List<Order>): String = buildString {
    appendLine("🛡 رسید ناظر — $shop")
    appendLine("تاریخ: ${PersianDate.short(nowMillis())}")
    appendLine("کارهای منتظرِ بررسی: ${orders.size.fa()} مورد")
    appendLine("──────────────")
    orders.forEach { o ->
        val days = stageDays(o.stageChangedAt, o.createdAt)
        appendLine(
            "• ${o.designTitle.ifBlank { o.orderCode }} — ${o.reviewQty.fa()} عدد" +
                (if (o.reviewQty < o.qty) " از ${o.qty.fa()}" else "") +
                " • ${o.orderCode} • ${days.fa()} روز در انتظار" +
                (if (days >= STAGE_WARN_DAYS) " ⚠️" else "")
        )
    }
    appendLine("──────────────")
    appendLine("⏰ یادآوری: نتیجهٔ هر بررسی (تأیید یا برگشت برای اصلاح) را همان روز ثبت کنید؛ همان تعدادی که تأیید شود خودکار وارد انبار محصول می‌شود و باقیِ سفارش در دوخت می‌مانَد.")
}
