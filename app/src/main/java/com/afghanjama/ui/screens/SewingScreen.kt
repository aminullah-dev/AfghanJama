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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.Order
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.vm.SewingViewModel
import java.util.UUID

@Composable
fun SewingScreen(
    vm: SewingViewModel,
    onBack: () -> Unit,
    onGoReview: () -> Unit
) {
    val cutDone by vm.ordersCutDone.collectAsState(initial = emptyList())
    val sewing by vm.ordersSewing.collectAsState(initial = emptyList())
    val tailors by vm.tailors.collectAsState(initial = emptyList())

    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("آماده دوخت", "در حال دوخت")

    // انتخاب خیاط برای هر سفارش (تب آماده دوخت)
    val pickMap = remember { mutableStateMapOf<UUID, String>() }
    val menuMap = remember { mutableStateMapOf<UUID, Boolean>() }

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
                        if (tab == 0) {
                            val picked = pickMap[o.id].orEmpty()
                            SewingOrderCard(
                                order = o,
                                primaryLabel = "شروع دوخت",
                                primaryIcon = Icons.Default.PlayArrow,
                                primaryEnabled = picked.isNotBlank(),
                                disabledLabel = "اول خیاط را انتخاب کنید",
                                onPrimary = {
                                    vm.startSewing(o.id, picked)
                                },
                                secondaryLabel = "↩ برگشت به برش",
                                onSecondary = { vm.backToCutting(o.id) },
                                tailorPicker = {
                                    OutlinedButton(
                                        onClick = { menuMap[o.id] = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.ContentCut, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (picked.isBlank()) "انتخاب خیاط" else picked,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuMap[o.id] == true,
                                        onDismissRequest = { menuMap[o.id] = false }
                                    ) {
                                        if (tailors.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("هیچ خیاطی ثبت نشده (اطلاعات پایه)") },
                                                onClick = { menuMap[o.id] = false }
                                            )
                                        } else {
                                            tailors.forEach { t ->
                                                val label = "[${t.code}] ${t.name}"
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        pickMap[o.id] = label
                                                        menuMap[o.id] = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        } else {
                            SewingOrderCard(
                                order = o,
                                primaryLabel = "ارسال به بازرسی",
                                primaryIcon = Icons.Default.Done,
                                primaryEnabled = true,
                                disabledLabel = "",
                                onPrimary = {
                                    vm.sendToReview(o.id)
                                    onGoReview()
                                },
                                secondaryLabel = "↩ برگشت به آماده دوخت",
                                onSecondary = { vm.backToCutDone(o.id) },
                                tailorPicker = null
                            )
                        }
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
    primaryEnabled: Boolean,
    disabledLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tailorPicker: (@Composable () -> Unit)?
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
                Text(total.afn(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            Text(
                text = "پارچه: ${order.fabricType} • رنگ: ${order.fabricColor} • سایز: ${order.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // خیاط تعیین‌شده (در حال دوخت)
            order.assignedTailor?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "خیاط: $it • کارمزد: ${order.workCost.afn()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            tailorPicker?.invoke()

            HorizontalDivider(thickness = 0.5.dp)

            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(primaryIcon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (primaryEnabled) primaryLabel else disabledLabel)
            }

            if (secondaryLabel != null && onSecondary != null) {
                TextButton(
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}
