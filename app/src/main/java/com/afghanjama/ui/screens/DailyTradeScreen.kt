@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * معاملاتِ روزمره — چهار کارِ روزانهٔ کارگاه یک‌جا و بزرگ.
 *
 * عمداً هر کدام یک کارتِ پهنِ ساده با یک عنوان و یک توضیحِ کوتاه است،
 * نه ردیفی از دکمه‌های ریز و شبیهِ هم؛ کاربر باید در یک نگاه بفهمد
 * کدام را می‌خواهد.
 */
@Composable
fun DailyTradeScreen(
    onGoPurchase: () -> Unit,
    onGoSale: () -> Unit,
    onGoPurchaseReturn: () -> Unit,
    onGoSaleReturn: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("معاملات روزمره") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TradeCard(
                    title = "خرید",
                    sub = "خریدِ مواد از فروشنده — نقد یا نسیه",
                    icon = Icons.Default.AddShoppingCart,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onGoPurchase
                )
            }
            item {
                TradeCard(
                    title = "فروش",
                    sub = "فروش از انبارِ محصولِ نهایی",
                    icon = Icons.Default.Sell,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onGoSale
                )
            }

            item {
                Text(
                    "برگشتی‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item {
                TradeCard(
                    title = "برگشت از خرید",
                    sub = "پس‌دادنِ موادِ معیوب یا اضافی به فروشنده",
                    icon = Icons.Default.Undo,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    onContainer = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onGoPurchaseReturn
                )
            }
            item {
                TradeCard(
                    title = "برگشت از فروش",
                    sub = "پس‌گرفتنِ کالای فروخته‌شده از مشتری",
                    icon = Icons.AutoMirrored.Filled.ReplyAll,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    onContainer = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onGoSaleReturn
                )
            }

            item {
                Text(
                    "برگشت از فروش از داخلِ خودِ سفارش انجام می‌شود تا معلوم باشد " +
                        "کدام کالا برمی‌گردد؛ این دکمه شما را به فهرستِ سفارش‌ها می‌برد.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TradeCard(
    title: String,
    sub: String,
    icon: ImageVector,
    container: Color,
    onContainer: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer
                )
                Text(sub, style = MaterialTheme.typography.bodySmall, color = onContainer)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = onContainer
            )
        }
    }
}
