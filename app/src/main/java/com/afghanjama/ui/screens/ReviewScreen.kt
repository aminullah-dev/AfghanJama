// app/src/main/java/com/afghanjama/ui/screens/ReviewScreen.kt
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.afghanjama.ui.vm.ReviewViewModel
import java.util.UUID

@Composable
fun ReviewScreen(
    vm: ReviewViewModel,
    onBack: () -> Unit,
    onGoSales: () -> Unit,
    onGoSewing: () -> Unit
) {
    val orders by vm.ordersInReview.collectAsState(initial = emptyList())
    val inspectors by vm.inspectors.collectAsState(initial = emptyList())

    val pickMap = remember { mutableStateMapOf<UUID, String>() }
    val menuMap = remember { mutableStateMapOf<UUID, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نظارت / بازرسی") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (orders.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders, key = { it.id }) { o ->
                    val picked = pickMap[o.id].orEmpty()
                    val menuOpen = menuMap[o.id] == true

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    Text(
                                        text = "${o.orderCode} • تعداد: ${o.qty}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "دوخته شده: ${o.doneSewCount}/${o.qty}",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

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

                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuMap[o.id] = false }
                            ) {
                                if (inspectors.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("هیچ ناظری ثبت نشده") },
                                        onClick = { menuMap[o.id] = false }
                                    )
                                } else {
                                    inspectors.forEach { ins ->
                                        val label = "[${ins.code}] ${ins.name}"
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        vm.backToSewing(o.id, picked)
                                        onGoSewing()
                                    },
                                    enabled = picked.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("برگشت به دوخت")
                                }

                                Button(
                                    onClick = {
                                        vm.approve(o.id, picked)
                                        onGoSales()
                                    },
                                    enabled = picked.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("تایید → فروش")
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
