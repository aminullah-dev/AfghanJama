package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.afghanjama.util.UUID
import com.afghanjama.util.randomUuid
import com.afghanjama.util.uuidOf

/**
 * سابقهٔ یک فروش جزئی از انبار محصول نهایی.
 */
@Entity(tableName = "finished_sales")
data class FinishedSale(
    @PrimaryKey val id: UUID = randomUuid(),
    val code: String,
    val productName: String,
    val size: String,
    val qty: Int,
    val unitPrice: Long,
    val total: Long,                  // qty × unitPrice − discount
    val cost: Long,                   // qty × بهای تمام‌شده
    val customerName: String = "",

    /** تخفیفِ همین ردیف، که از جمعِ ردیف کم شده است. */
    @ColumnInfo(defaultValue = "0")
    val discount: Long = 0,

    /** چند عدد از این فروش تا حالا مرجوع شده (برگشت از فروش). */
    @ColumnInfo(defaultValue = "0")
    val returnedQty: Int = 0,

    val createdAt: Long = nowMillis()
) {
    /** تعدادِ باقی‌مانده که هنوز قابلِ برگشت است. */
    val returnableQty: Int get() = (qty - returnedQty).coerceAtLeast(0)

    /** بهای تمام‌شدهٔ هر عدد در همین فروش — فقط برای نمایش. */
    val unitCost: Long get() = if (qty > 0) cost / qty else 0L

    /**
     * سهمِ [n] عدد از یک مبلغِ کلِ ردیف، وقتی [returned] عدد قبلاً برگشته.
     *
     * دو نکته که ساده‌ترین فرمول («تعداد × قیمتِ هر عدد») هر دو را خراب
     * می‌کرد:
     *  - با تخفیف، جمعِ ردیف دیگر «تعداد × فی» نیست؛ برگشتِ کامل بیشتر از
     *    چیزی می‌شد که مشتری داده بود.
     *  - تقسیمِ صحیح ته‌مانده جا می‌گذارد. اینجا سهم از روی **جمعِ تجمعی**
     *    حساب می‌شود، پس آخرین برگشت هرچه مانده را می‌برد و جمعِ برگشت‌ها
     *    دقیقاً برابرِ مبلغِ فروش می‌شود.
     */
    private fun share(amount: Long, n: Int, returned: Int): Long {
        if (qty <= 0 || n <= 0) return 0L
        val doneSoFar = if (returned >= qty) amount else amount * returned / qty
        val remaining = qty - returned
        return if (n >= remaining) amount - doneSoFar
        else amount * (returned + n) / qty - doneSoFar
    }

    /** مبلغی که برای برگشتِ [n] عدد باید به مشتری پس داده شود. */
    fun refundFor(n: Int): Long = share(total, n, returnedQty)

    /** بهای تمام‌شده‌ای که با برگشتِ [n] عدد به انبار برمی‌گردد. */
    fun costFor(n: Int): Long = share(cost, n, returnedQty)

    /** فروشِ خالص پس از کسرِ مرجوعی‌ها. */
    val netTotal: Long
        get() = if (returnedQty >= qty) 0L else total - total * returnedQty / qty

    /** بهای تمام‌شدهٔ خالص پس از کسرِ مرجوعی‌ها. */
    val netCost: Long
        get() = if (returnedQty >= qty) 0L else cost - cost * returnedQty / qty
}
