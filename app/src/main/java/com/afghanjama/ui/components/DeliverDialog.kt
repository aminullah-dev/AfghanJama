package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.entities.Order
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.digitsOnly
import com.afghanjama.ui.format.fa

/**
 * دیالوگِ تحویلِ سفارش به مشتری — یک نسخه، هرجا که تحویل انجام می‌شود.
 *
 * عمداً بی‌حالت (stateless) نسبت به دیتابیس است: بیعانه را می‌گیرد و
 * تصمیمِ نهایی را برمی‌گرداند. هر صفحه‌ای که تحویل دارد باید همین را
 * صدا بزند تا هرگز دو فرمِ متفاوت برای یک کارِ پولی وجود نداشته باشد.
 *
 * @param prepay بیعانهٔ استفاده‌نشدهٔ همین مشتری
 * @param onConfirm (قیمتِ هر عدد، نقدِ دریافتی، بیعانهٔ اعمال‌شده)
 */
@Composable
fun DeliverDialog(
    order: Order,
    prepay: Long,
    onDismiss: () -> Unit,
    onConfirm: (unitPrice: Long, receivedNow: Long, applyPrepay: Long) -> Unit
) {
    val defaultUnit = if (order.qty > 0 && order.agreedPrice > 0) order.agreedPrice / order.qty else 0L
    var unitText by remember(order.id) {
        mutableStateOf(defaultUnit.takeIf { it > 0 }?.toString() ?: "")
    }
    var usePrepay by remember(order.id) { mutableStateOf(prepay > 0) }
    var receivedText by remember(order.id) { mutableStateOf("") }

    val unit = unitText.toLongOrNull() ?: 0L
    val total = unit * order.qty
    val applied = if (usePrepay) prepay.coerceAtMost(total) else 0L
    // خالی یعنی «باقی‌مانده را کامل نقد گرفتم» — حالتِ عادیِ پیشخوان
    val received = (receivedText.toLongOrNull() ?: (total - applied))
        .coerceIn(0L, total - applied)
    val remaining = total - applied - received

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحویل به ${order.customerName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${order.qty.fa()} عدد «${order.designTitle}» از انبار محصول کم می‌شود و " +
                        "سفارش «تحویل شد» می‌گیرد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = unitText,
                    onValueChange = { unitText = it.digitsOnly() },
                    label = { Text("قیمت هر عدد (؋)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "جمع: ${total.afn()}" +
                        if (order.agreedPrice > 0 && total != order.agreedPrice)
                            " • قیمت توافقیِ سفارش: ${order.agreedPrice.afn()}"
                        else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (order.agreedPrice > 0 && total != order.agreedPrice)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (prepay > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = usePrepay, onCheckedChange = { usePrepay = it })
                        Text("استفاده از بیعانهٔ ${prepay.afn()}")
                    }
                }

                OutlinedTextField(
                    value = receivedText,
                    onValueChange = { receivedText = it.digitsOnly() },
                    label = { Text("نقدِ دریافتی همین حالا (خالی = همه)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "از بیعانه ${applied.afn()} • نقد ${received.afn()} • " +
                        "باقی‌ماندهٔ طلب ${remaining.afn()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (remaining > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = unit > 0,
                onClick = { onConfirm(unit, received, applied) }
            ) { Text("تحویل شد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}
