package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.PurchaseInvoice
import com.afghanjama.data.entities.PurchaseItem
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM purchase_invoices WHERE code = :code LIMIT 1")
    suspend fun invoiceByCode(code: String): PurchaseInvoice?
}
