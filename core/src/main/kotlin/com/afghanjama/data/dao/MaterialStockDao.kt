package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afghanjama.data.entities.MaterialStock
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialStockDao {

    @Query("SELECT * FROM material_stock ORDER BY name ASC")
    fun observeAll(): Flow<List<MaterialStock>>

    @Query("SELECT * FROM material_stock WHERE name = :name AND unit = :unit LIMIT 1")
    suspend fun find(name: String, unit: String): MaterialStock?

    /** اقلامی که به حد هشدار کمبود رسیده‌اند. */
    @Query("SELECT * FROM material_stock WHERE minLevel > 0 AND amount <= minLevel ORDER BY name ASC")
    suspend fun lowStock(): List<MaterialStock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stock: MaterialStock)

    @Query("DELETE FROM material_stock WHERE id = :id")
    suspend fun deleteById(id: Long)
}
