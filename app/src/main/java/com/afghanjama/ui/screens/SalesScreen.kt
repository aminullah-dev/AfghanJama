// app/src/main/java/com/afghanjama/ui/screens/SalesScreen.kt
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import com.afghanjama.R
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
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.sound.PlayRawSoundOnce
import com.afghanjama.ui.vm.SalesViewModel
import java.util.UUID

@Composable
fun SalesScreen(
    vm: SalesViewModel,
    onBack: () -> Unit
) {
    val orders by vm.ordersInSales.collectAsState(initial = emptyList())
    val wallet by vm.walletBalance.collectAsState(initial = 0L)
    val profit by vm.profitBalance.collectAsState(initial = 0L)
    val ui by vm.ui.collectAsState()

    // مبلغ دریافتی برای هر سفارش
    val paidMap = remember { mutableStateMapOf<UUID, String>() }
    // تسویه کامل با تخفیف
    val discountMap = remember { mutableStateMapOf<UUID, Boolean>() }

    // ✅ پخش صدا وقتی earningSoundKey تغییر کند
    PlayRawSoundOnce(
        playKey = ui.earningSoundKey,
        resId = R.raw.earning_money,
        onConsumed = { vm.consumeEarningSound() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("واحد فروش") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("کیف پول: ${wallet.afn()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("فایده: ${profit.afn()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ui.message?.let {
                        Text(
                            it,
                            color = if (ui.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text("سفارش‌های آماده فروش", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (orders.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("فعلاً سفارشی برای فروش نیست.", fontWeight = FontWeight.SemiBold)
                        Text(
                            "وقتی از نظارت به فروش ارسال شود، اینجا نمایش داده می‌شود.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders, key = { it.id }) { o ->
                    val cost = o.fabricPrice + o.workCost + o.sewingCost
                    // پیش‌فرض: قیمت توافقی ثبت‌شده هنگام سفارش
                    val paidText = paidMap[o.id]
                        ?: o.agreedPrice.takeIf { it > 0 }?.toString().orEmpty()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    Text(
                                        text = "${o.orderCode} • تعداد: ${o.qty}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "هزینه: ${cost.afn()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            OutlinedTextField(
                                value = paidText,
                                onValueChange = { paidMap[o.id] = it.digitsOnly() },
                                label = { Text("مبلغ دریافتی از مشتری (؋)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) }
                            )

                            if (o.agreedPrice > 0) {
                                Text(
                                    text = "قیمت توافقی: ${o.agreedPrice.afn()}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                // اگر کمتر از قیمت توافقی دریافت می‌شود: تخفیف؟
                                val paidNow = paidText.toLongOrNull() ?: 0L
                                if (paidNow in 1 until o.agreedPrice) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = discountMap[o.id] == true,
                                            onCheckedChange = { discountMap[o.id] = it }
                                        )
                                        Text(
                                            "تسویه کامل با تخفیف (${(o.agreedPrice - paidNow).afn()})",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val paid = paidText.toLongOrNull() ?: 0L
                                    vm.completeSale(o.id, paid, discountMap[o.id] == true)
                                    paidMap.remove(o.id)
                                    discountMap.remove(o.id)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Done, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("تکمیل فروش")
                            }

                            TextButton(
                                onClick = { vm.backToReview(o.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("↩ برگشت به نظارت (اصلاح اشتباه)")
                            }

                            HorizontalDivider(thickness = 0.5.dp)
                            Text(
                                "پول مشتری وارد کیف پول می‌شود؛ سود (اگر باشد) از کیف پول به فایده منتقل می‌شود.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}
