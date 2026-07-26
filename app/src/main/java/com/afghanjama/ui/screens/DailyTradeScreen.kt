@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.AppScreen

/**
 * معاملاتِ روزمره — چهار کارِ روزانهٔ کارگاه یک‌جا.
 *
 * هر کارت یک پرسشِ کوتاه دارد («فروش داشتید؟») به‌جای توضیحِ خشک، و یک
 * نوارِ رنگیِ کنارِ لبه که خانوادهٔ کار را نشان می‌دهد: سبز پولِ ورودی،
 * قرمز پولِ خروجی، خاکستری برگشتی‌ها. چشم با رنگ پیدا می‌کند، نه با
 * خواندنِ همهٔ عنوان‌ها.
 */
@Composable
fun DailyTradeScreen(
    onGoPurchase: () -> Unit,
    onGoSale: () -> Unit,
    onGoPurchaseReturn: () -> Unit,
    onGoSaleReturn: () -> Unit,
    onBack: () -> Unit
) {
    AppScreen(title = "معاملات روزمره", onBack = onBack) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TradeCard(
                    title = "فروش جدید",
                    question = "فروش داشتید؟",
                    sub = "فاکتور با هر تعداد طرحِ مختلف",
                    icon = Icons.Default.Sell,
                    stripe = MoneyIn,
                    onClick = onGoSale
                )
            }
            item {
                TradeCard(
                    title = "خرید جدید",
                    question = "خرید کردید؟",
                    sub = "خریدِ مواد از فروشنده — نقد یا نسیه",
                    icon = Icons.Default.AddShoppingCart,
                    stripe = MoneyOut,
                    onClick = onGoPurchase
                )
            }

            item {
                Text(
                    "برگشتی‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                TradeCard(
                    title = "برگشت از فروش",
                    question = "کالا برگشت داده شده؟",
                    sub = "پس‌گرفتنِ کالای فروخته‌شده از مشتری",
                    icon = Icons.AutoMirrored.Filled.ReplyAll,
                    stripe = Neutral,
                    onClick = onGoSaleReturn
                )
            }
            item {
                TradeCard(
                    title = "برگشت از خرید",
                    question = "کالا برگردانده‌اید؟",
                    sub = "پس‌دادنِ موادِ معیوب یا اضافی به فروشنده",
                    icon = Icons.Default.Undo,
                    stripe = Neutral,
                    onClick = onGoPurchaseReturn
                )
            }

            item {
                Text(
                    "برگشت از فروش از روی خودِ فروشِ ثبت‌شده انجام می‌شود تا معلوم باشد " +
                        "کدام کالا و با چه قیمتی برمی‌گردد؛ این دکمه شما را به انبارِ محصول " +
                        "می‌برد، پایینِ صفحه کنارِ هر فروش دکمهٔ «برگشت» هست.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

// نوارِ کنارِ کارت‌ها: ورودی / خروجی / خنثی. عمداً ثابت‌اند تا در تمِ
// روشن و تاریک یک معنا بدهند.
private val MoneyIn = Color(0xFF2E7D32)
private val MoneyOut = Color(0xFFC62828)
private val Neutral = Color(0xFF757575)

/** ارتفاعِ نوارِ رنگی — به بلندیِ محتوای کارت. */
private val StripeHeight = 74.dp

@Composable
private fun TradeCard(
    title: String,
    question: String,
    sub: String,
    icon: ImageVector,
    stripe: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 5.dp, height = StripeHeight)
                    .background(stripe)
            )
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        question,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(icon, contentDescription = null, tint = stripe, modifier = Modifier.size(30.dp))
            }
        }
    }
}
