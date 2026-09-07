package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.afghanjama.data.Margin
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.format.decimalOnly
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa

/**
 * قیمت‌دهی — وقتی مشتری می‌پرسد «چند می‌گیری؟»
 *
 * **چرا این صفحه ساخته شد.** اپ بهای تمام‌شده را بعد از ثبتِ سفارش
 * دقیق می‌داند، ولی قیمت **پیش از** ثبت زده می‌شود — سرِ صحبت با
 * مشتری، وقتی هنوز هیچ سفارشی در دفتر نیست. تا امروز آن حساب روی
 * کاغذ یا در ذهن انجام می‌شد.
 *
 * چیزی را ثبت نمی‌کند و به دیتابیس دست نمی‌زند؛ فقط حساب می‌کند. به
 * همین دلیل ViewModel ندارد و حالتش محلی است — مثلِ `GuideScreen` و
 * `ShopProfileScreen`.
 *
 * **حساب از [Margin] می‌آید، نه از خودِ این صفحه.** همان شیئی که سودِ
 * فروش‌های ثبت‌شده را نشان می‌دهد. اگر این صفحه فرمولِ خودش را
 * می‌نوشت، روزی یکی از آن دو عوض می‌شد و کارگاه دو عددِ متفاوت برای
 * یک معامله می‌دید.
 */
@Composable
fun QuoteCalculatorScreen(onBack: () -> Unit) {
    var qtyText by remember { mutableStateOf("1") }
    var fabricPerPiece by remember { mutableStateOf("") }
    var fabricRate by remember { mutableStateOf("") }
    var wage by remember { mutableStateOf("") }
    var other by remember { mutableStateOf("") }
    var marginText by remember { mutableStateOf("20") }

    val qty = qtyText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val fabricCost = ((fabricPerPiece.toDoubleOrNull() ?: 0.0) *
        (fabricRate.toLongOrNull() ?: 0L)).toLong()
    val unitCost = fabricCost + (wage.toLongOrNull() ?: 0L) + (other.toLongOrNull() ?: 0L)
    val percent = marginText.toIntOrNull()?.coerceIn(0, 500) ?: 0

    val unitPrice = Margin.priceFor(unitCost, percent)
    val line = Margin.Line(cost = unitCost, price = unitPrice, qty = qty)

    AppScreen(title = "قیمت‌دهی", onBack = onBack) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "بهای تمام‌شده را بسازید، سودِ دلخواه را بگویید، و قیمت را " +
                    "همین‌جا ببینید. چیزی ثبت نمی‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "بهای تمام‌شدهٔ هر عدد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fabricPerPiece,
                            onValueChange = { fabricPerPiece = it.decimalOnly() },
                            label = { Text("پارچهٔ هر عدد") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fabricRate,
                            onValueChange = { fabricRate = it.digitsOnly() },
                            label = { Text("نرخِ هر واحد (؋)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = wage,
                        onValueChange = { wage = it.digitsOnly() },
                        label = { Text("مزدِ خیاط برای هر عدد (؋)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = other,
                        onValueChange = { other = it.digitsOnly() },
                        label = { Text("خرجِ دیگرِ هر عدد — تکمه، آستر، … (اختیاری)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "بهای تمام‌شدهٔ هر عدد: ${unitCost.fa()} ؋",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "سود و تعداد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = marginText,
                            onValueChange = { marginText = it.digitsOnly() },
                            label = { Text("سودِ دلخواه (٪)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it.digitsOnly() },
                            label = { Text("تعداد") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "قیمت",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (unitCost <= 0L) {
                        // عددِ ساختگی بدتر از نگفتن است — همان قاعده‌ای که
                        // `Margin.percent` با `unknownCost` نگه می‌دارد.
                        Text(
                            "اول بهای تمام‌شده را وارد کنید تا قیمت حساب شود.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "قیمتِ هر عدد: ${unitPrice.fa()} ؋",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "جمعِ ${qty.fa()} عدد: ${line.revenue.fa()} ؋",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "سودِ شما: ${line.profit.fa()} ؋",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
