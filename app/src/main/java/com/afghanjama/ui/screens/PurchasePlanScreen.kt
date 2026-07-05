@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.FabricUnit
import com.afghanjama.data.entities.PaymentSource
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.vm.FinanceViewModel
import com.afghanjama.ui.vm.MasterDataViewModel
import com.afghanjama.ui.vm.PurchaseViewModel
import com.afghanjama.ui.vm.StockViewModel

@Composable
fun PurchasePlanScreen(
    vm: PurchaseViewModel,
    masterVm: MasterDataViewModel,
    financeVm: FinanceViewModel,
    stockVm: StockViewModel,
    onDone: () -> Unit
) {
    val stocks by stockVm.stocks.collectAsState()
    val ui by vm.ui.collectAsState()

    val walletBalance by financeVm.walletBalance.collectAsState(initial = 0L)
    val profitBalance by financeVm.profitBalance.collectAsState(initial = 0L)

    val fabricTypes by masterVm.fabricTypes.collectAsState()
    val fabricColors by masterVm.fabricColors.collectAsState()
    val sizes by masterVm.sizes.collectAsState()
    val designs by masterVm.designs.collectAsState()
    val customers by masterVm.customers.collectAsState()

    val workCosts by vm.workCosts.collectAsState()

    val designTitles = designs.map { it.title }.distinct().sorted()
    val customerNames = customers.map { it.name }.distinct().sorted()
    val typeTitles = fabricTypes.map { it.title }.distinct().sorted()
    val colorTitles = fabricColors.map { it.title }.distinct().sorted()
    val sizeTitles = sizes.map { it.title }.distinct().sorted()

    fun paymentLabel(v: String): String {
        val src = runCatching { PaymentSource.valueOf(v.trim().uppercase()) }.getOrNull()
        return when (src) {
            PaymentSource.WALLET -> "کیف پول"
            PaymentSource.PROFIT -> "فایده"
            PaymentSource.BANK -> "بانک"
            PaymentSource.CUSTOMER -> "مشتری"
            null -> "کیف پول"
        }
    }

    fun unitLabel(v: String): String {
        val raw = v.trim()
        if (raw.isBlank()) return "انتخاب کنید"
        val u = runCatching { FabricUnit.valueOf(raw.uppercase()) }.getOrNull()
        return when (u) {
            FabricUnit.METER -> "متر"
            FabricUnit.YARD -> "یارد"
            null -> "انتخاب کنید"
        }
    }

    var designExpanded by remember { mutableStateOf(false) }
    var customerExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var colorExpanded by remember { mutableStateOf(false) }
    var sizeExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }
    var workCostExpanded by remember { mutableStateOf(false) }
    var payExpanded by remember { mutableStateOf(false) }

    val qtyValid = (ui.qty.toIntOrNull() ?: 0) >= 1
    val canSubmit = ui.fabricUnit.isNotBlank() && qtyValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp)
    ) {
        Text(
            text = "پلان خرید و ثبت سفارش",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // طرح لباس
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = ui.designTitle,
                        onValueChange = vm::setDesignTitle,
                        label = { Text("طرح / نوع لباس") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(onClick = { designExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("انتخاب از لیست طرح‌ها")
                    }

                    DropdownMenu(expanded = designExpanded, onDismissRequest = { designExpanded = false }) {
                        designTitles.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    vm.setDesignTitle(t)
                                    designExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // تعداد
            item {
                OutlinedTextField(
                    value = ui.qty,
                    onValueChange = vm::setQty,
                    label = { Text("تعداد") },
                    placeholder = { Text("مثلاً: 10") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // مشتری
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = ui.customerName,
                        onValueChange = vm::setCustomerName,
                        label = { Text("نام مشتری") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(onClick = { customerExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("انتخاب از لیست مشتری‌ها")
                    }

                    DropdownMenu(expanded = customerExpanded, onDismissRequest = { customerExpanded = false }) {
                        customerNames.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    vm.setCustomerName(name)
                                    customerExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // شماره مشتری
            item {
                OutlinedTextField(
                    value = ui.customerPhone,
                    onValueChange = vm::setCustomerPhone,
                    label = { Text("شماره مشتری (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // نوع پارچه
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = ui.fabricType,
                        onValueChange = vm::setFabricType,
                        label = { Text("نوع پارچه") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("انتخاب نوع پارچه")
                    }

                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        typeTitles.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    vm.setFabricType(t)
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // رنگ پارچه
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = ui.fabricColor,
                        onValueChange = vm::setFabricColor,
                        label = { Text("رنگ پارچه") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(onClick = { colorExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("انتخاب رنگ")
                    }

                    DropdownMenu(expanded = colorExpanded, onDismissRequest = { colorExpanded = false }) {
                        colorTitles.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    vm.setFabricColor(t)
                                    colorExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // سایز
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = ui.size,
                        onValueChange = vm::setSize,
                        label = { Text("سایز") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(onClick = { sizeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("انتخاب سایز")
                    }

                    DropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                        sizeTitles.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    vm.setSize(t)
                                    sizeExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // مقدار + واحد
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = ui.fabricAmount,
                            onValueChange = vm::setFabricAmount,
                            label = { Text("مقدار (${unitLabel(ui.fabricUnit)})") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = unitLabel(ui.fabricUnit),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("واحد") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedButton(onClick = { unitExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("انتخاب واحد")
                            }

                            DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                                FabricUnit.values().forEach { u ->
                                    DropdownMenuItem(
                                        text = { Text(unitLabel(u.name)) },
                                        onClick = {
                                            vm.setFabricUnit(u.name)
                                            unitExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // منبع پارچه: خرید جدید یا از موجودی انبار
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("منبع پارچه", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isNew = ui.fabricSource != "STOCK"
                        Button(
                            onClick = { vm.setFabricSource("NEW") },
                            enabled = !isNew,
                            modifier = Modifier.weight(1f)
                        ) { Text("خرید جدید") }
                        Button(
                            onClick = { vm.setFabricSource("STOCK") },
                            enabled = isNew,
                            modifier = Modifier.weight(1f)
                        ) { Text("از موجودی انبار") }
                    }

                    if (ui.fabricSource == "STOCK") {
                        val match = stocks.firstOrNull {
                            it.fabricType.equals(ui.fabricType.trim(), true) &&
                                it.fabricColor.equals(ui.fabricColor.trim(), true) &&
                                it.fabricUnit.equals(ui.fabricUnit.trim(), true)
                        }
                        Text(
                            text = if (match != null)
                                "موجودی فعلی: ${match.amount} ${unitLabel(ui.fabricUnit)}"
                            else
                                "برای این نوع/رنگ موجودی ثبت نشده است.",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (match != null && match.amount > 0)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // قیمت پارچه (فقط برای خرید جدید)
            if (ui.fabricSource != "STOCK") {
                item {
                    OutlinedTextField(
                        value = ui.fabricPrice,
                        onValueChange = vm::setFabricPrice,
                        label = { Text("قیمت پارچه (؋)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // خرج کار
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val workLabel =
                        if (ui.workCostTitle.isBlank()) "انتخاب خرج کار (اختیاری)"
                        else "${ui.workCostTitle} — ${ui.workCostPrice} ؋"

                    OutlinedTextField(
                        value = workLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("خرج کار") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(onClick = { workCostExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("انتخاب از لیست خرج کار")
                    }

                    DropdownMenu(expanded = workCostExpanded, onDismissRequest = { workCostExpanded = false }) {
                        workCosts.forEach { w ->
                            DropdownMenuItem(
                                text = { Text("${w.title} — ${w.price} ؋") },
                                onClick = {
                                    vm.pickWorkCost(w.title, w.price)
                                    workCostExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // منبع پرداخت
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = paymentLabel(ui.paymentSource),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("منبع پرداخت") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(onClick = { payExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("انتخاب منبع پرداخت")
                    }

                    DropdownMenu(expanded = payExpanded, onDismissRequest = { payExpanded = false }) {
                        PaymentSource.values().forEach { src ->
                            DropdownMenuItem(
                                text = { Text(paymentLabel(src.name)) },
                                onClick = {
                                    vm.setPaymentSource(src.name)
                                    payExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // قیمت فروش توافقی
            item {
                OutlinedTextField(
                    value = ui.agreedPrice,
                    onValueChange = vm::setAgreedPrice,
                    label = { Text("قیمت فروش توافقی با مشتری (؋)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // پرداخت مشتری
            item {
                OutlinedTextField(
                    value = ui.customerPaid,
                    onValueChange = vm::setCustomerPaid,
                    label = { Text("پیش‌پرداخت مشتری (اختیاری) ؋") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // خلاصه
            item {
                val qty = ui.qty.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val fabricPrice = if (ui.fabricSource == "STOCK") 0L else ui.fabricPrice.toLongOrNull() ?: 0L
                val workTotal = ui.workCostPrice * qty
                val total = fabricPrice + workTotal

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (ui.workCostPrice > 0) {
                            Text("خرج کار: ${ui.workCostPrice.afn()} × $qty عدد = ${workTotal.afn()}")
                        }
                        if (ui.fabricSource == "STOCK") {
                            Text("پارچه از موجودی انبار مصرف می‌شود.")
                        } else {
                            Text("قیمت پارچه: ${fabricPrice.afn()}")
                        }
                        Text("جمع کل هزینه: ${total.afn()}", fontWeight = FontWeight.SemiBold)
                        Text("کیف پول: ${walletBalance.afn()}")
                        Text("فایده: ${profitBalance.afn()}")
                    }
                }
            }

            // ثبت
            item {
                Button(
                    onClick = { vm.completePurchase() },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            !qtyValid -> "تعداد را وارد کنید"
                            ui.fabricUnit.isBlank() -> "اول واحد را انتخاب کنید"
                            else -> "تکمیل خرید → ثبت سفارش"
                        }
                    )
                }
            }

            // پیام
            item {
                ui.message?.let { msg ->
                    AssistChip(
                        onClick = {},
                        label = { Text(msg) },
                        leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) }
                    )
                    if (!ui.isError) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                            Text("باشه")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
