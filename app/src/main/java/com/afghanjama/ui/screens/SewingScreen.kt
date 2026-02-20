@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width // ✅ مهم
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.Order
import com.afghanjama.ui.vm.SewingViewModel

@Composable
fun SewingScreen(
    vm: SewingViewModel,
    onBack: () -> Unit,
    onGoReview: () -> Unit
) {
    val cutDone by vm.ordersCutDone.collectAsState(initial = emptyList())
    val sewing by vm.ordersSewing.collectAsState(initial = emptyList())

    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("آماده دوخت", "در حال دوخت")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مدیریت دوخت") },
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
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }

            Spacer(Modifier.height(10.dp))

            val list = if (tab == 0) cutDone else sewing

            if (list.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("هیچ موردی موجود نیست.", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (tab == 0) "وقتی برش تمام شد، اینجا می‌آید."
                            else "وقتی دوخت شروع شود، اینجا نمایش می‌شود.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = list, key = { it.id }) { o ->
                        SewingOrderCard(
                            order = o,
                            primaryLabel = if (tab == 0) "شروع دوخت" else "ارسال به بازرسی",
                            primaryIcon = if (tab == 0) Icons.Default.PlayArrow else Icons.Default.Done,
                            onPrimary = {
                                if (tab == 0) vm.startSewing(o.id)
                                else {
                                    vm.sendToReview(o.id)
                                    onGoReview()
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SewingOrderCard(
    order: Order,
    primaryLabel: String,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onPrimary: () -> Unit
) {
    val total = order.fabricPrice + order.workCost

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
                        text = order.designTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${order.orderCode} • تعداد: ${order.qty}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("$total ؋", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            Text(
                text = "پارچه: ${order.fabricType} • رنگ: ${order.fabricColor} • سایز: ${order.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(thickness = 0.5.dp)

            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                Icon(primaryIcon, contentDescription = null)
                Spacer(Modifier.width(8.dp)) // ✅ حالا بدون خطا
                Text(primaryLabel)
            }
        }
    }
}
