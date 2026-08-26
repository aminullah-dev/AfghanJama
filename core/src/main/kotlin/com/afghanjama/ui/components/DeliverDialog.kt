package com.afghanjama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.afghanjama.ui.platform.AppAlertDialog
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
import com.afghanjama.ui.format.stockText

/**
 * دیالوگِ تحویلِ سفارش به مشتری — یک نسخه، هرجا که تحویل انجام می‌شود.
 *
 * تعداد و قیمت هر دو دستِ کاربرند: مشتری ممکن است امروز بخشی از سفارش
 * را ببرد، یا موجودیِ انبار کمتر از کلِ سفارش باشد (موجودی برای هر طرح
 * مشترک است، نه به‌ازای سفارش). مبلغ خودش از تعداد × قیمت ساخته می‌شود.
 *
 * @param available موجودیِ انبار برای طرحِ همین سفارش
 * @param prepay بیعانهٔ استفاده‌نشدهٔ همین مشتری
 * @param onConfirm (تعداد، قیمتِ هر عدد، نقدِ دریافتی، بیعانهٔ اعمال‌شده)
 */
@Composable
fun DeliverDialog(
    order: Order,
    available: Int,
    prepay: Long,
    onDismiss: () -> Unit,
    onConfirm: (qty: Int, unitPrice: Long, receivedNow: Long, applyPrepay: Long) -> Unit,
    allowShortage: Boolean = true
) {
    val remainingQty = (order.qty - order.deliveredQty).coerceAtLeast(0)
    // با اجازهٔ کسری، سقفْ باقی‌ماندهٔ خودِ سفارش است نه موجودیِ انبار.
    // قبلاً وقتی انبار صفر بود سقف صفر می‌شد و دکمهٔ تحویل هرگز فعال
    // نمی‌شد، بدونِ اینکه معلوم باشد چرا.
    val maxQty = if (allowShortage) remainingQty else minOf(remainingQty, available)
    val shortageQty = (remainingQty - available).coerceAtLeast(0)

    val defaultUnit = if (order.qty > 0 && order.agreedPrice > 0) order.agreedPrice / order.qty else 0L
    var qtyText by remember(order.id) { mutableStateOf(maxQty.takeIf { it > 0 }?.toString() ?: "") }
    var unitText by remember(order.id) {
        mutableStateOf(defaultUnit.takeIf { it > 0 }?.toString() ?: "")
    }
    var usePrepay by remember(order.id) { mutableStateOf(prepay > 0) }
    var receivedText by remember(order.id) { mutableStateOf("") }

    val qty = qtyText.toIntOrNull() ?: 0
    val unit = unitText.toLongOrNull() ?: 0L
    val total = unit * qty
    val applied = if (usePrepay) prepay.coerceAtMost(total) else 0L
    // خالی یعنی «باقی‌مانده را کامل نقد گرفتم» — حالتِ عادیِ پیشخوان
    val received = (receivedText.toLongOrNull() ?: (total - applied))
        .coerceIn(0L, total - applied)
    val remainingDue = total - applied - received

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحویل به ${order.customerName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "«${order.designTitle}» — از این سفارش ${remainingQty.fa()} عدد باقی است" +
                        (if (order.deliveredQty > 0) " (${order.deliveredQty.fa()} عدد قبلاً رفته)" else "") +
                        " • " + stockText(available),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (available < remainingQty) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (allowShortage && shortageQty > 0) {
                    Text(
                        "انبار به اندازهٔ این سفارش جنس ندارد. تحویل انجام می‌شود و " +
                            "${shortageQty.fa()} عدد به‌عنوان «کسری» ثبت می‌ماند تا ورودِ " +
                            "بعدی آن را جبران کند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.digitsOnly() },
                        label = { Text("تعداد") },
                        singleLine = true,
                        isError = qty > maxQty,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it.digitsOnly() },
                        label = { Text("قیمت هر عدد (؋)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (qty > maxQty) {
                    Text(
                        if (!allowShortage && available < remainingQty)
                            "بیشتر از موجودیِ انبار (${available.fa()} عدد) نمی‌شود تحویل داد."
                        else "بیشتر از باقی‌ماندهٔ سفارش (${remainingQty.fa()} عدد) نمی‌شود تحویل داد.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    "جمع: ${total.afn()}" +
                        if (order.agreedPrice > 0 && qty == order.qty && total != order.agreedPrice)
                            " • قیمت توافقیِ سفارش: ${order.agreedPrice.afn()}"
                        else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (order.agreedPrice > 0 && qty == order.qty && total != order.agreedPrice)
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
                        "باقی‌ماندهٔ طلب ${remainingDue.afn()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (remainingDue > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = unit > 0 && qty in 1..maxQty,
                onClick = { onConfirm(qty, unit, received, applied) }
            ) { Text(if (qty < remainingQty) "تحویلِ ${qty.fa()} عدد" else "تحویل شد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}
