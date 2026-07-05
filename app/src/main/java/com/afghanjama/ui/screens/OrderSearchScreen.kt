@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

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
import android.content.Intent
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.vm.OrderSearchViewModel

/** ترتیب مراحل تولید برای نمایش پیشرفت سفارش. */
private val stageOrder = listOf(
    OrderStatus.IN_STOCK.name to "انبار",
    OrderStatus.CUTTING.name to "برش",
    OrderStatus.CUT_DONE.name to "برش تمام",
    OrderStatus.SEWING.name to "دوخت",
    OrderStatus.REVIEW.name to "نظارت",
    OrderStatus.SALES.name to "فروش",
    OrderStatus.SENT.name to "تحویل شد"
)

@Composable
fun OrderSearchScreen(
    vm: OrderSearchViewModel,
    onBack: () -> Unit,
    onOpenDetail: (Order) -> Unit
) {
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پیگیری سفارش") },
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
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                label = { Text("کد سفارش، کد کوتاه، نام مشتری یا طرح") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            when {
                query.isBlank() -> {
                    Text(
                        "برای پیگیری، بخشی از کد سفارش (مثل AJ-2026)، کد کوتاه، نام مشتری یا نام طرح را بنویسید.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                results.isEmpty() -> {
                    Text(
                        "سفارشی با این مشخصات پیدا نشد.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Text(
                        "${results.size} سفارش پیدا شد",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(results, key = { it.id }) { o ->
                            SearchResultCard(o, onClick = { onOpenDetail(o) })
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

private fun receiptText(order: Order, stageLabel: String): String = buildString {
    appendLine("🧵 رسید سفارش — AfghanJama")
    appendLine("──────────────")
    appendLine("کد سفارش: ${order.orderCode}")
    appendLine("کد کوتاه: ${order.shortCode}")
    appendLine("طرح: ${order.designTitle}")
    appendLine("تعداد: ${order.qty}")
    appendLine("پارچه: ${order.fabricType} • ${order.fabricColor} • ${order.fabricAmount}")
    if (order.customerName.isNotBlank()) appendLine("مشتری: ${order.customerName}")
    if (order.customerPhone.isNotBlank()) appendLine("تلفن: ${order.customerPhone}")
    if (order.agreedPrice > 0) appendLine("قیمت توافقی: ${order.agreedPrice.afn()}")
    appendLine("وضعیت فعلی: $stageLabel")
}

@Composable
private fun SearchResultCard(order: Order, onClick: () -> Unit) {
    val context = LocalContext.current
    val stageIndex = stageOrder.indexOfFirst { it.first == order.status }.coerceAtLeast(0)
    val stageLabel = stageOrder.getOrNull(stageIndex)?.second ?: order.status
    val progress = (stageIndex + 1) / stageOrder.size.toFloat()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        order.designTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${order.orderCode} • ${order.shortCode} • تعداد: ${order.qty}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    (order.fabricPrice + order.workCost).afn(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (order.customerName.isNotBlank()) {
                Text(
                    "مشتری: ${order.customerName}" +
                        (order.customerPhone.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (order.agreedPrice > 0) {
                Text(
                    "قیمت توافقی: ${order.agreedPrice.afn()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            order.assignedTailor?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "خیاط: $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(thickness = 0.5.dp)

            // نوار پیشرفت مراحل
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "مرحله فعلی: $stageLabel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${stageIndex + 1} از ${stageOrder.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, receiptText(order, stageLabel))
                    }
                    context.startActivity(Intent.createChooser(intent, "اشتراک رسید سفارش"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("اشتراک رسید")
            }
        }
    }
}
