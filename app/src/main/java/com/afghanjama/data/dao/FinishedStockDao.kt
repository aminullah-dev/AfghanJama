package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afghanjama.data.entities.FinishedSale
import com.afghanjama.data.entities.FinishedStock
import kotlinx.coroutines.flow.Flow

@Dao
interface FinishedStockDao {

    @Query("SELECT * FROM finished_stock WHERE qty > 0 ORDER BY name ASC")
    fun observeAvailable(): Flow<List<FinishedStock>>

    @Query("SELECT * FROM finished_stock ORDER BY name ASC")
    fun observeAll(): Flow<List<FinishedStock>>

    @Query("SELECT * FROM finished_stock WHERE name = :name AND size = :size LIMIT 1")
    suspend fun find(name: String, size: String): FinishedStock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stock: FinishedStock)

    @Insert
    suspend fun insertSale(sale: FinishedSale)

    @Query("SELECT * FROM finished_sales ORDER BY createdAt DESC")
    fun observeSales(): Flow<List<FinishedSale>>

    /**
     * افزایشِ تعدادِ مرجوعِ یک فروش. شرطِ سقف داخلِ خودِ UPDATE است تا دو
     * برگشتِ هم‌زمان هرگز بیشتر از تعدادِ فروخته‌شده را مرجوع نکنند.
     *
     * @return تعداد سطرهای به‌روزشده؛ ۰ یعنی سقف اجازه نداد.
     */
    @Query(
        "UPDATE finished_sales SET returnedQty = returnedQty + :qty " +
            "WHERE id = :id AND returnedQty + :qty <= qty"
    )
    suspend fun addReturnedQty(id: java.util.UUID, qty: Int): Int
}
