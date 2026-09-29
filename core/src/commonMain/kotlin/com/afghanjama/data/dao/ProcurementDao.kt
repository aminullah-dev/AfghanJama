package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.PurchaseInvoice
import com.afghanjama.data.entities.PurchaseItem
import kotlinx.coroutines.flow.Flow

/** قیمتِ یک قلم در یک خریدِ گذشته — خروجیِ [ProcurementDao.observePriceHistory]. */
data class PurchasePrice(val name: String, val unit: String, val unitPrice: Long, val at: Long)

@Dao
interface ProcurementDao {

    @Insert
    suspend fun insertInvoice(invoice: PurchaseInvoice)

    @Insert
    suspend fun insertItems(items: List<PurchaseItem>)

    @Query("SELECT * FROM purchase_invoices ORDER BY createdAt DESC")
    fun observeInvoices(): Flow<List<PurchaseInvoice>>

    @Query("SELECT * FROM purchase_items WHERE invoiceId = :invoiceId ORDER BY id ASC")
    fun observeItems(invoiceId: String): Flow<List<PurchaseItem>>

    /**
     * اقلامِ یک فاکتور خرید بر اساسِ **کد** — سندِ مالی کد را نگه می‌دارد
     * نه UUID را، پس برای چاپ باید از همین راه رسید.
     */
    @Query(
        "SELECT i.* FROM purchase_items i " +
            "JOIN purchase_invoices v ON i.invoiceId = v.id " +
            "WHERE v.code = :code ORDER BY i.id ASC"
    )
    suspend fun itemsByInvoiceCode(code: String): List<PurchaseItem>

    /**
     * قیمتِ خریدهای گذشتهٔ هر قلم، با زمانِ فاکتورش — برای نگهبانِ خطا
     * («۱۵۰۰ برای یک متر؟ معمولاً ۱۵۰ می‌خرید»). سقفِ ۲۰۰۰ سطر: نگهبان فقط
     * چند خریدِ آخرِ هر قلم را می‌خواهد.
     */
    @Query(
        "SELECT i.name AS name, i.unit AS unit, i.unitPrice AS unitPrice, v.createdAt AS at " +
            "FROM purchase_items i JOIN purchase_invoices v ON i.invoiceId = v.id " +
            "ORDER BY v.createdAt DESC LIMIT 2000"
    )
    fun observePriceHistory(): Flow<List<PurchasePrice>>

    @Query("SELECT * FROM purchase_invoices WHERE code = :code LIMIT 1")
    suspend fun invoiceByCode(code: String): PurchaseInvoice?
}
