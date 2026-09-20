@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.afghanjama.data.entities.Order
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.components.OrderCodeLine
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.SalePrefs
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.components.DeliverDialog
import com.afghanjama.ui.components.EmptyState
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.DeliveryQueueViewModel
import com.afghanjama.ui.vm.DeliveryRow
import com.afghanjama.util.UUID

/** بیشتر از این چند روز، معطلی در انبار غیرعادی است. */
private const val WAIT_WARN_DAYS = 3

/**
 * صفِ تحویل — کارهای تمام‌شده‌ای که صاحب‌شان هنوز نبرده.
 *
 * قدیمی‌ترین بالا، چون همان است که هم جا گرفته و هم پولش وصول نشده.
 */
@Composable
fun DeliveryQueueScreen(
    vm: DeliveryQueueViewModel,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val designCodeByOrder by vm.designCodeByOrder.collectAsState()
    val prepays by vm.prepayOf.collectAsState()
    val system = LocalSystemActions.current
    // نامِ کارگاه در پیام می‌آید، وگرنه مشتری نمی‌داند از کجاست.
    val shopName = CompanyPrefs.name(LocalSettings.current)
    val settings = LocalSettings.current

    var deliverTarget by remember { mutableStateOf<UUID?>(null) }

    deliverTarget?.let { id ->
        ui.rows.firstOrNull { it.order.id == id }?.let { row ->
            DeliverDialog(
                order = row.order,
                available = row.available,
                prepay = prepays[row.order.customerName.trim()] ?: 0L,
                onDismiss = { deliverTarget = null },
                onConfirm = { qty, unit, received, applied ->
                    vm.deliver(row.order.id, qty, unit, received, applied)
                    deliverTarget = null
                },
                allowShortage = SalePrefs.allowNegativeStock(settings)
            )
        }
    }

    AppScreen(title = "آمادهٔ تحویل", onBack = onBack) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ui.message?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ui.isError) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                msg,
                                modifier = Modifier.weight(1f),
                                color = if (ui.isError) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            TextButton(onClick = vm::clearMessage) { Text("باشه") }
                        }
                    }
                }
            }

            if (ui.rows.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.LocalShipping,
                        title = "چیزی منتظرِ تحویل نیست",
                        hint = "سفارشی که نظارت تأییدش کند و مشتری داشته باشد، " +
                            "همین‌جا می‌آید تا فراموش نشود."
                    )
                }
            } else {
                item {
                    val late = ui.rows.count { it.waitingDays >= WAIT_WARN_DAYS }
                    val owed = ui.rows.sumOf { it.customerBalance.coerceAtLeast(0L) }
                    Text(
                        "${ui.rows.size.fa()} کار آمادهٔ تحویل" +
                            (if (late > 0) " • ${late.fa()} تای‌شان بیش از ${WAIT_WARN_DAYS.fa()} روز معطل" else "") +
                            (if (owed > 0) " • ${owed.afn()} طلبِ وصول‌نشده" else ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(ui.rows, key = { it.order.id }) { row ->
                val o = row.order
                val slow = row.waitingDays >= WAIT_WARN_DAYS
                Card(
                    // کارِ تحویل‌شده از صف بیرون می‌رود و بقیه بالا
                    // می‌آیند — همان چیزی که روی میزِ واقعی می‌افتد.
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(
                        1.dp,
                        if (slow) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    o.customerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${o.designTitle} • ${o.qty.fa()} عدد • سایز ${o.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "${row.waitingDays.fa()} روز",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (slow) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OrderCodeLine(
                            designCode = designCodeByOrder[o.orderCode].orEmpty(),
                            orderCode = o.orderCode,
                            trailing = "آماده از ${PersianDate.short(
                                if (o.stageChangedAt > 0) o.stageChangedAt else o.createdAt
                            )}"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // ماندهٔ حساب: مثبت یعنی بدهکار است، منفی یعنی بیعانه دارد
                        Text(
                            when {
                                row.customerBalance > 0 ->
                                    "طلبِ ما از این مشتری: ${row.customerBalance.afn()}"
                                row.customerBalance < 0 ->
                                    "بیعانهٔ نزدِ ما: ${(-row.customerBalance).afn()}"
                                else -> "حسابش تسویه است"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (row.customerBalance > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )

                        // خبرِ قبلی، اگر بوده. صریح نوشته می‌شود چون
                        // همین یک خط است که کارفرما را از نگه‌داشتنِ
                        // دفترچهٔ ذهنی خلاص می‌کند.
                        row.notifiedAt?.let { at ->
                            Text(
                                "خبر داده شد — ${PersianDate.short(at)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        /*
                         * **دو ردیف، نه سه دکمه کنارِ هم.**
                         *
                         * با آمدنِ «خبر بده» سه دکمه در یک ردیف نشستند و
                         * روی گوشیِ واقعی هر کدام حدودِ ۱۰۰dp ماند — یعنی
                         * آیکن به‌اضافهٔ متن جا نشد و «تحویل» و «تماس»
                         * **وسطِ کلمه** شکستند. `weight` عرض را تقسیم
                         * می‌کند، ولی کلمهٔ فارسی تقسیم نمی‌شود.
                         *
                         * حالا کارهای فرعی بالا و کارِ اصلی تمام‌عرض
                         * پایین — که ترتیبِ درست‌تری هم هست: «تحویل»
                         * کاری است که پول جابه‌جا می‌کند و نباید
                         * هم‌اندازهٔ «تماس» دیده شود.
                         */
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (o.customerPhone.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        system.dial(o.customerPhone)
                                        vm.markNotified(row, "تماس")
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Call,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    // `maxLines = 1` نگهبانِ همان اشکال
                                    // است: اگر روزی دکمهٔ چهارمی اضافه
                                    // شود، متن به‌جای شکستن کوتاه می‌شود
                                    // و ایراد دیده می‌شود، نه اینکه
                                    // بدشکل شود.
                                    Text("تماس", maxLines = 1)
                                }
                            }
                            // «خبر بده» — متنِ آماده به هر اپی که کاربر
                            // دارد (واتس‌اپ، پیامک، …). شمارهٔ مشتری لازم
                            // نیست: شاید کارفرما بخواهد در گروهِ خانوادگی
                            // یا هر جای دیگری بفرستد.
                            OutlinedButton(
                                onClick = {
                                    system.shareText(
                                        "خبر به ${o.customerName}",
                                        readyMessage(o, row, shopName)
                                    )
                                    vm.markNotified(row, "پیام")
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("خبر بده", maxLines = 1)
                            }
                        }
                        Button(
                            onClick = {
                                vm.lookupPrepay(o.customerName)
                                deliverTarget = o.id
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("تحویل", maxLines = 1)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

/**
 * متنِ «کارتان آماده است».
 *
 * **چرا اینجا و نه در ViewModel.** این یک رشتهٔ نمایشی است، نه قاعدهٔ
 * دامنه — و همان‌جایی می‌نشیند که خوانده می‌شود.
 *
 * مبلغِ باقی فقط وقتی می‌آید که واقعاً باقی‌ای باشد. نوشتنِ «باقی: ۰»
 * برای مشتری‌ای که تسویه کرده، پیامِ بی‌ربطی است که حسِ مطالبه می‌دهد.
 */
private fun readyMessage(order: Order, row: DeliveryRow, shopName: String): String = buildString {
    append("سلام ${order.customerName} جان،\n")
    append("سفارشِ شما آماده است:\n")
    append("${order.designTitle} — ${order.qty.fa()} عدد، سایز ${order.size}\n")
    append("شمارهٔ سفارش: ${order.orderCode}\n")
    if (row.customerBalance > 0) {
        append("باقیِ حساب: ${row.customerBalance.afn()}\n")
    }
    if (shopName.isNotBlank()) append("\n$shopName")
}
