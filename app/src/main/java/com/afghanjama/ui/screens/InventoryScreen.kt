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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.InventoryViewModel
import com.afghanjama.ui.vm.UserRole

@Composable
fun InventoryScreen(
    vm: InventoryViewModel,
    financeVm: FinanceViewModel,
    role: UserRole,
    onGoPurchase: () -> Unit,
    onGoWallet: () -> Unit,
    onGoMaster: () -> Unit,
    onGoCutting: () -> Unit
) {
    val orders by vm.ordersInStock.collectAsState(initial = emptyList())
    val wallet by financeVm.walletBalance.collectAsState(initial = 0L)
    val profit by financeVm.profitBalance.collectAsState(initial = 0L)
    val uiState by vm.state.collectAsState()

    val canManage = role == UserRole.MANAGER
    val canPurchase = role == UserRole.MANAGER || role == UserRole.PURCHASE

    LaunchedEffect(uiState.navigateToCutting) {
        if (uiState.navigateToCutting) {
            vm.clearNavigation()
            onGoCutting()
        }
    }

    if (uiState.isError && uiState.message != null) {
        AlertDialog(
            onDismissRequest = { vm.clearMessage() },
            confirmButton = { TextButton(onClick = { vm.clearMessage() }) { Text("باشه") } },
            title = { Text("خطا") },
            text = { Text(uiState.message!!) }
        )
    }

    var showAddMoney by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf("WALLET") } // WALLET | PROFIT
    var addAmountText by remember { mutableStateOf("") }
    var addNote by remember { mutableStateOf("") }

    fun statusLabel(s: String): String = when (s) {
        OrderStatus.IN_STOCK.name -> "در انبار"
        OrderStatus.CUTTING.name -> "برش"
        OrderStatus.CUT_DONE.name -> "برش تمام"
        OrderStatus.SEWING.name -> "دوخت"
        OrderStatus.REVIEW.name -> "بازرسی"
        OrderStatus.SALES.name -> "فروش"
        OrderStatus.SENT.name -> "ارسال شد"
        else -> s
    }

    if (showAddMoney && canManage) {
        AlertDialog(
            onDismissRequest = { showAddMoney = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amount = addAmountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        if (amount > 0L) {
                            val note = addNote.trim().ifBlank {
                                if (addTarget == "WALLET") "افزایش کیف پول" else "افزایش فایده"
                            }
                            if (addTarget == "WALLET") financeVm.incomeWallet(amount, note)
                            else financeVm.incomeProfit(amount, note)
                        }
                        addAmountText = ""
                        addNote = ""
                        showAddMoney = false
                    }
                ) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { showAddMoney = false }) { Text("لغو") } },
            title = { Text(if (addTarget == "WALLET") "افزایش کیف پول" else "افزایش فایده") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = addAmountText,
                        onValueChange = { addAmountText = it.filter(Char::isDigit) },
                        label = { Text("مبلغ (؋)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addNote,
                        onValueChange = { addNote = it },
                        label = { Text("یادداشت (اختیاری)") },
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
                title = { Text("انبار") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    if (canManage) {
                        IconButton(onClick = onGoWallet) {
                            Icon(Icons.Default.Payments, contentDescription = "کیف پول")
                        }
                        IconButton(onClick = onGoMaster) {
                            Icon(Icons.Default.Settings, contentDescription = "اطلاعات پایه")
                        }
                    }
                }
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

            // کارت مالی فقط برای مدیر
            if (canManage) {
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
                            Column {
                                Text("کیف پول", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$wallet ؋", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { addTarget = "WALLET"; showAddMoney = true }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("افزایش")
                            }
                        }

                        Divider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("فایده", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$profit ؋", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { addTarget = "PROFIT"; showAddMoney = true }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("افزایش")
                            }
                        }
                    }
                }
            }

            // لیست انبار
            if (orders.isEmpty()) {
                EmptyInventory(
                    onAdd = onGoPurchase,
                    canAdd = canPurchase,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "سفارش‌های داخل انبار",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(orders, key = { it.id }) { o ->
                        OrderCard(
                            order = o.copy(status = statusLabel(o.status)),
                            canSend = canPurchase,
                            onSendToCutting = {
                                vm.sendToCutting(o.id, o.designTitle)
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }

            // CTA پایین فقط برای خرید/مدیر
            if (canPurchase) {
                Button(
                    onClick = onGoPurchase,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ثبت خرید جدید")
                }
            }
        }
    }
}

@Composable
private fun EmptyInventory(
    onAdd: () -> Unit,
    canAdd: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Inventory2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "هیچ سفارشی در انبار نیست",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "برای شروع، یک خرید ثبت کن تا سفارش وارد انبار شود.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (canAdd) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ثبت خرید")
            }
        }
    }
}

@Composable
private fun OrderCard(
    order: Order,
    canSend: Boolean,
    onSendToCutting: () -> Unit
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
                Text(
                    text = "$total ؋",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "پارچه: ${order.fabricType} • رنگ: ${order.fabricColor} • سایز: ${order.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "وضعیت: ${order.status}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(thickness = 0.5.dp)

            if (canSend) {
                Button(
                    onClick = onSendToCutting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ارسال به برش")
                }
            }
        }
    }
}
